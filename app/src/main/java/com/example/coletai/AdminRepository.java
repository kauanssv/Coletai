package com.example.coletai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Operações administrativas sobre os mesmos registros usados pelas telas comuns. */
public final class AdminRepository {
  private final SharedPreferences prefs;
  private final ProfileRepository profiles;

  public AdminRepository(SharedPreferences prefs) {
    this.prefs = prefs;
    profiles = new ProfileRepository(prefs);
  }

  private JSONObject currentOrder(String account) {
    if (!account.equals(LocalJson.account(prefs))) {
      return LocalJson.object(prefs.getString("perfil_pedido_" + account, "{}"));
    }
    JSONObject result = new JSONObject();
    for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
      if (orderKey(e.getKey())) LocalJson.put(result, e.getKey(), e.getValue());
    }
    return result;
  }

  private static boolean orderKey(String key) {
    return key.startsWith("pedido_") || key.startsWith("proposta_") || "coleta_etapa".equals(key);
  }

  public List<JSONObject> orders() {
    profiles.requireAdmin();
    List<JSONObject> result = new ArrayList<>();
    for (JSONObject source : new CollectionRepository(prefs).orders()) {
      JSONObject item = LocalJson.object(source.toString());
      LocalJson.put(item, "v14", true);
      LocalJson.put(item, "account_id", source.optString("citizen_id"));
      LocalJson.put(item, "account_name", source.optString("citizen_name"));
      LocalJson.put(item, "pedido_tipo", CollectionRepository.itemSummary(source));
      LocalJson.put(item, "pedido_descricao", source.optString("description"));
      LocalJson.put(
          item,
          "pedido_endereco_completo",
          CollectionRepository.addressText(source.optJSONObject("address")));
      LocalJson.put(item, "proposta_preco", source.optString("proposal_price"));
      LocalJson.put(item, "proposta_mensagem", source.optString("proposal_message"));
      String status = source.optString("status");
      LocalJson.put(item, "current", !("concluida".equals(status) || "cancelada".equals(status)));
      result.add(item);
    }
    for (JSONObject profile : profiles.list()) {
      if ("admin".equals(profile.optString("role"))) continue;
      String account = profile.optString("id");
      JSONArray history = LocalJson.array(prefs.getString("historico_" + account, "[]"));
      JSONObject current = currentOrder(account);
      String currentId = current.optString("pedido_id");
      boolean inHistory = false;
      for (int i = history.length() - 1; i >= 0; i--) {
        JSONObject item = LocalJson.object(history.optJSONObject(i).toString());
        boolean migrated = false;
        for (JSONObject added : result)
          if (added.optString("id").equals(item.optString("id"))
              && account.equals(added.optString("account_id"))) migrated = true;
        if (migrated) {
          if (currentId.equals(item.optString("id"))) inHistory = true;
          continue;
        }
        if (currentId.equals(item.optString("id"))) inHistory = true;
        decorate(item, profile, false);
        result.add(item);
      }
      if (!currentId.isEmpty() && !current.optString("pedido_status").isEmpty() && !inHistory) {
        boolean migrated = false;
        for (JSONObject added : result)
          if (added.optString("id").equals(currentId)
              && account.equals(added.optString("account_id"))) migrated = true;
        if (migrated) continue;
        LocalJson.put(current, "id", currentId);
        LocalJson.put(current, "status", current.optString("pedido_status"));
        decorate(current, profile, true);
        result.add(0, current);
      }
    }
    return result;
  }

  private void decorate(JSONObject item, JSONObject profile, boolean current) {
    String account = profile.optString("id");
    LocalJson.put(item, "account_id", account);
    LocalJson.put(item, "account_name", profile.optString("name"));
    LocalJson.put(item, "account_role", profile.optString("role"));
    LocalJson.put(item, "current", current);
    LocalJson.put(
        item, "deleted", prefs.getBoolean(deletedKey(account, item.optString("id")), false));
    LocalJson.put(item, "revision", prefs.getInt(revisionKey(account, item.optString("id")), 0));
  }

  public JSONObject order(String account, String id) {
    for (JSONObject item : orders())
      if (account.equals(item.optString("account_id")) && id.equals(item.optString("id")))
        return item;
    throw new IllegalArgumentException("Pedido não encontrado");
  }

  public void editOrder(String account, String id, JSONObject changed, int expectedRevision) {
    profiles.requireAdmin();
    JSONObject before = order(account, id);
    if (before.optBoolean("deleted"))
      throw new IllegalArgumentException("Restaure o pedido antes de editar");
    if (before.optInt("revision") != expectedRevision)
      throw new IllegalStateException("O pedido mudou. Reabra os dados para editar.");
    JSONObject after = LocalJson.object(before.toString());
    for (String key :
        new String[] {
          "pedido_tipo",
          "pedido_descricao",
          "pedido_peso",
          "pedido_endereco_completo",
          "pedido_observacao",
          "proposta_preco"
        }) {
      if (changed.has(key)) LocalJson.put(after, key, changed.optString(key).trim());
    }
    if (after.optString("pedido_tipo").isEmpty()
        || after.optString("pedido_descricao").isEmpty()
        || after.optString("pedido_endereco_completo").isEmpty()) {
      throw new IllegalArgumentException("Preencha tipo, descrição e endereço");
    }
    String weight = after.optString("pedido_peso").replace(" kg", "");
    LocalJson.put(
        after,
        "pedido_peso",
        DecimalValues.positive(weight, 3).stripTrailingZeros().toPlainString() + " kg");
    if (!after.optString("proposta_preco").isEmpty()) {
      LocalJson.put(
          after,
          "proposta_preco",
          DecimalValues.positive(after.optString("proposta_preco"), 2).setScale(2).toPlainString());
    }
    LocalJson.put(after, "revision", expectedRevision + 1);
    SharedPreferences.Editor editor = prefs.edit();
    if (before.optBoolean("current")) {
      JSONObject data = currentOrder(account);
      for (String key :
          new String[] {
            "pedido_tipo",
            "pedido_descricao",
            "pedido_peso",
            "pedido_endereco_completo",
            "pedido_observacao",
            "proposta_preco"
          }) {
        LocalJson.put(data, key, after.optString(key));
      }
      saveCurrent(editor, account, data);
      updatePending(editor, account, id, data);
    } else replaceHistory(editor, account, id, after);
    editor.putInt(revisionKey(account, id), expectedRevision + 1);
    new AuditRepository(prefs)
        .append(editor, "Pedido atualizado", "order", account + ":" + id, before, after);
    LocalJson.commit(editor);
  }

  public void cancelOrder(String account, String id) {
    profiles.requireAdmin();
    JSONObject before = order(account, id);
    if (!before.optBoolean("current") || "done".equals(before.optString("status"))) {
      throw new IllegalArgumentException("O pedido já está encerrado");
    }
    JSONObject history = LocalJson.object(before.toString());
    LocalJson.put(history, "status", "cancelado");
    LocalJson.put(history, "data", System.currentTimeMillis());
    LocalJson.put(history, "current", false);
    JSONArray items = LocalJson.array(prefs.getString("historico_" + account, "[]"));
    items.put(history);
    SharedPreferences.Editor editor =
        prefs.edit().putString("historico_" + account, items.toString());
    JSONObject data = currentOrder(account);
    data.remove("pedido_status");
    data.remove("coleta_etapa");
    saveCurrent(editor, account, data);
    JSONArray queue = LocalJson.array(prefs.getString("sync_outbox", "[]"));
    JSONArray retained = new JSONArray();
    for (int i = 0; i < queue.length(); i++) {
      JSONObject event = queue.optJSONObject(i);
      if (!account.equals(event.optString("conta_id", event.optString("remetente")))
          || !id.equals(event.optString("pedido_id"))) retained.put(event);
    }
    editor.putString("sync_outbox", retained.toString());
    new AuditRepository(prefs)
        .append(
            editor,
            "Pedido cancelado pelo administrador",
            "order",
            account + ":" + id,
            before,
            history);
    LocalJson.commit(editor);
  }

  public void setDeleted(String account, String id, boolean deleted) {
    profiles.requireAdmin();
    JSONObject before = order(account, id);
    if (before.optBoolean("current"))
      throw new IllegalArgumentException("Cancele ou conclua o pedido antes de excluir");
    JSONObject after = LocalJson.object(before.toString());
    LocalJson.put(after, "deleted", deleted);
    SharedPreferences.Editor editor =
        prefs
            .edit()
            .putBoolean(deletedKey(account, id), deleted)
            .putLong(deletedKey(account, id) + "_at", deleted ? System.currentTimeMillis() : 0);
    new AuditRepository(prefs)
        .append(
            editor,
            deleted ? "Pedido excluído" : "Pedido restaurado",
            "order",
            account + ":" + id,
            before,
            after);
    LocalJson.commit(editor);
  }

  private void replaceHistory(
      SharedPreferences.Editor editor, String account, String id, JSONObject after) {
    JSONArray old = LocalJson.array(prefs.getString("historico_" + account, "[]"));
    JSONArray items = new JSONArray();
    for (int i = 0; i < old.length(); i++) {
      JSONObject item = old.optJSONObject(i);
      items.put(id.equals(item.optString("id")) ? after : item);
    }
    editor.putString("historico_" + account, items.toString());
  }

  private void saveCurrent(SharedPreferences.Editor editor, String account, JSONObject data) {
    editor.putString("perfil_pedido_" + account, data.toString());
    if (!account.equals(LocalJson.account(prefs))) return;
    for (String key : prefs.getAll().keySet()) if (orderKey(key)) editor.remove(key);
    Iterator<String> keys = data.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = data.opt(key);
      if ("proposta_coletor_avaliacao".equals(key))
        editor.putFloat(key, ((Number) value).floatValue());
      else if ("proposta_coletor_coletas".equals(key))
        editor.putInt(key, ((Number) value).intValue());
      else editor.putString(key, String.valueOf(value));
    }
  }

  private void updatePending(
      SharedPreferences.Editor editor, String account, String id, JSONObject data) {
    JSONArray queue = LocalJson.array(prefs.getString("sync_outbox", "[]"));
    for (int i = 0; i < queue.length(); i++) {
      JSONObject event = queue.optJSONObject(i);
      if (!account.equals(event.optString("conta_id", event.optString("remetente")))
          || !id.equals(event.optString("pedido_id"))) continue;
      // Only events never assigned to a device can be safely rewritten.
      if (!event.optString("destino").isEmpty()) continue;
      if ("NOVO_PEDIDO".equals(event.optString("evento"))) {
        for (String field : new String[] {"tipo", "descricao", "peso", "observacao"})
          LocalJson.put(event, field, data.optString("pedido_" + field));
        LocalJson.put(event, "enderecoCompleto", data.optString("pedido_endereco_completo"));
      }
    }
    editor.putString("sync_outbox", queue.toString());
  }

  private String deletedKey(String account, String id) {
    return "admin_deleted_" + account + "_" + id;
  }

  private String revisionKey(String account, String id) {
    return "admin_order_revision_" + account + "_" + id;
  }
}
