package com.example.coletai;

import android.content.SharedPreferences;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pedidos, propostas aprovadas pelo cliente e acompanhamento ate o descarte. */
public final class CollectionRepository {
  public static final String ORDERS = "requests_v14",
      ADDRESSES = "addresses_v14",
      MATERIALS = "materials_v14",
      NOTIFICATIONS = "notifications_v14";
  private final SharedPreferences prefs;
  private final ProfileRepository profiles;

  public CollectionRepository(SharedPreferences prefs) {
    this.prefs = prefs;
    profiles = new ProfileRepository(prefs);
    if (!prefs.contains(MATERIALS)) {
      JSONArray materials = new JSONArray();
      String[] names = {
        "Papel", "Papelão", "Plástico", "Vidro", "Metal", "Óleo de cozinha", "Eletrônicos"
      };
      for (int i = 0; i < names.length; i++) {
        JSONObject material = new JSONObject();
        put(material, "id", "m" + (i + 1));
        put(material, "name", names[i]);
        put(material, "active", true);
        put(material, "description", "");
        materials.put(material);
      }
      LocalJson.commit(prefs.edit().putString(MATERIALS, materials.toString()));
    }
  }

  public void migrateLegacy() {
    if (PlatformGateway.configured()) return;
    if (prefs.getBoolean("requests_v14_migrated", false)) return;
    JSONArray output = array(ORDERS);
    Set<String> existing = new HashSet<>();
    for (JSONObject row : list(output)) existing.add(row.optString("id"));
    for (JSONObject account : profiles.list()) {
      if (!"cliente".equals(account.optString("role"))) continue;
      String owner = account.optString("id");
      List<JSONObject> old = list(LocalJson.array(prefs.getString("historico_" + owner, "[]")));
      JSONObject current = LocalJson.object(prefs.getString("perfil_pedido_" + owner, "{}"));
      if (owner.equals(LocalJson.account(prefs))) {
        current = new JSONObject();
        for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet())
          if (e.getKey().startsWith("pedido_")
              || e.getKey().startsWith("proposta_")
              || e.getKey().equals("coleta_etapa")) put(current, e.getKey(), e.getValue());
      }
      if (!current.optString("pedido_id").isEmpty()
          && !current.optString("pedido_status").isEmpty()) {
        put(current, "id", current.optString("pedido_id"));
        put(current, "status", current.optString("pedido_status"));
        old.add(current);
      }
      for (JSONObject row : old) {
        String id = row.optString("id");
        if (id.isEmpty() || !existing.add(id)) continue;
        JSONObject migrated = new JSONObject(), address = new JSONObject(), item = new JSONObject();
        put(migrated, "id", id);
        put(migrated, "citizen_id", owner);
        put(migrated, "citizen_name", account.optString("name"));
        put(migrated, "legacy", true);
        String status = row.optString("status");
        put(
            migrated,
            "status",
            "done".equals(status) || "concluido".equals(status)
                ? "concluida"
                : "cancelado".equals(status)
                    ? "cancelada"
                    : "active".equals(status) ? "aceita" : "aberta");
        put(
            migrated,
            "responsible_id",
            "active".equals(status)
                ? row.optString("proposta_coletor_tipo", "legacy_unresolved")
                : "");
        put(migrated, "responsible_name", row.optString("proposta_coletor_nome"));
        put(migrated, "created_at", row.optLong("data", System.currentTimeMillis()));
        put(migrated, "desired_date", today());
        put(migrated, "revision", 1);
        put(migrated, "description", row.optString("pedido_descricao"));
        put(migrated, "notes", row.optString("pedido_observacao"));
        put(address, "legacy_text", row.optString("pedido_endereco_completo"));
        put(migrated, "address", address);
        put(item, "material_id", "legacy");
        put(item, "name", row.optString("pedido_tipo"));
        put(item, "weight", row.optString("pedido_peso").replace(" kg", ""));
        put(migrated, "items", new JSONArray().put(item));
        put(migrated, "deleted", prefs.getBoolean("admin_deleted_" + owner + "_" + id, false));
        output.put(migrated);
      }
    }
    LocalJson.commit(
        prefs
            .edit()
            .putString(ORDERS, output.toString())
            .putBoolean("requests_v14_migrated", true));
  }

  private void put(JSONObject object, String key, Object value) {
    LocalJson.put(object, key, value);
  }

  private JSONObject actor() {
    JSONObject profile = profiles.current();
    if (!profiles.available(profile.optString("id")))
      throw new SecurityException("Conta indisponível");
    return profile;
  }

  private void citizen() {
    if (!"cliente".equals(actor().optString("role")))
      throw new SecurityException("Apenas cidadãos podem realizar esta operação");
  }

  private JSONArray array(String key) {
    return LocalJson.array(prefs.getString(key, "[]"));
  }

  private List<JSONObject> list(JSONArray array) {
    List<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) result.add(array.optJSONObject(i));
    return result;
  }

  public List<JSONObject> materials(boolean includeInactive) {
    if (includeInactive) profiles.requireAdmin();
    List<JSONObject> result = new ArrayList<>();
    for (JSONObject item : list(array(MATERIALS)))
      if (includeInactive || item.optBoolean("active")) result.add(item);
    return result;
  }

  public void saveMaterial(JSONObject input) {
    if (PlatformGateway.configured()) {
      PlatformGateway.mutate(prefs, "material.save", input);
      return;
    }
    profiles.requireAdmin();
    String name = input.optString("name").trim();
    if (name.isEmpty() || name.length() > 80)
      throw new IllegalArgumentException("Informe um nome de até 80 caracteres");
    JSONObject after = LocalJson.object(input.toString());
    String id = after.optString("id");
    JSONObject before = null;
    for (JSONObject material : list(array(MATERIALS))) {
      if (id.equals(material.optString("id"))) before = material;
      if (!id.equals(material.optString("id")) && name.equalsIgnoreCase(material.optString("name")))
        throw new IllegalArgumentException("Este material já está cadastrado");
    }
    if (id.isEmpty()) id = UUID.randomUUID().toString();
    put(after, "id", id);
    put(after, "name", name);
    if (!after.has("active")) put(after, "active", true);
    persist(MATERIALS, after, before, "Catálogo de materiais atualizado");
  }

  public List<JSONObject> addresses() {
    citizen();
    List<JSONObject> result = new ArrayList<>();
    for (JSONObject a : list(array(ADDRESSES)))
      if (LocalJson.account(prefs).equals(a.optString("owner_id")) && !a.optBoolean("deleted"))
        result.add(a);
    return result;
  }

  public void saveAddress(JSONObject input) {
    if (PlatformGateway.configured()) {
      PlatformGateway.mutate(prefs, "address.save", input);
      return;
    }
    citizen();
    JSONObject after = LocalJson.object(input.toString());
    JSONObject before = null;
    for (JSONObject a : list(array(ADDRESSES)))
      if (a.optString("id").equals(after.optString("id"))) before = a;
    if (before != null && !LocalJson.account(prefs).equals(before.optString("owner_id")))
      throw new SecurityException("Endereço de outra conta");
    for (String field :
        new String[] {"nickname", "street", "number", "district", "city", "state", "zip"}) {
      String value = after.optString(field).trim();
      if (value.isEmpty() || value.length() > 150)
        throw new IllegalArgumentException(
            "Preencha apelido, rua, número, bairro, cidade, UF e CEP");
      put(after, field, value);
    }
    for (String[] field : new String[][]{{"nickname", "Apelido"}, {"number", "Número"},
        {"complement", "Complemento"}, {"district", "Bairro"}, {"city", "Cidade"}})
      if (after.optString(field[0]).length() > addressLimit(field[0]))
        throw new IllegalArgumentException(field[1] + " deve ter até " + addressLimit(field[0]) + " caracteres");
    if (!after.optString("zip").replaceAll("\\D", "").matches("[0-9]{8}"))
      throw new IllegalArgumentException("Informe um CEP com 8 dígitos");
    if (!after
        .optString("state")
        .matches(
            "(?i)AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO"))
      throw new IllegalArgumentException("Informe uma UF válida");
    if (after.has("latitude") || after.has("longitude")) validateCoordinates(after);
    if (before == null) put(after, "id", UUID.randomUUID().toString());
    put(after, "owner_id", LocalJson.account(prefs));
    put(after, "deleted", false);
    persist(ADDRESSES, after, before, "Endereço atualizado");
  }

  private static int addressLimit(String field) {
    switch (field) {
      case "nickname": return 50;
      case "number": return 20;
      case "district": case "city": case "complement": return 100;
      case "state": return 2;
      case "zip": return 8;
      default: return 150;
    }
  }

  private static void validateCoordinates(JSONObject value) {
    double lat = value.optDouble("latitude", Double.NaN), lng = value.optDouble("longitude", Double.NaN);
    if (value.opt("latitude") instanceof Boolean || value.opt("longitude") instanceof Boolean
        || !Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
      throw new IllegalArgumentException("Coordenadas inválidas");
  }

  public void deleteAddress(String id) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      put(p, "id", id);
      PlatformGateway.mutate(prefs, "address.delete", p);
      return;
    }
    citizen();
    for (JSONObject a : addresses())
      if (id.equals(a.optString("id"))) {
        JSONObject before = LocalJson.object(a.toString());
        put(a, "deleted", true);
        persist(ADDRESSES, a, before, "Endereço excluído");
        return;
      }
    throw new IllegalArgumentException("Endereço não encontrado");
  }

  public static String addressText(JSONObject a) {
    if (a == null) return "Endereço não informado";
    if (a.has("legacy_text")) return a.optString("legacy_text");
    if (a.optString("street").trim().isEmpty() && a.has("latitude") && a.has("longitude"))
      return String.format(Locale.forLanguageTag("pt-BR"), "Local da coleta: %.5f, %.5f",
          a.optDouble("latitude"), a.optDouble("longitude"));
    return a.optString("street")
        + ", "
        + a.optString("number")
        + (a.optString("complement").isEmpty() ? "" : " · " + a.optString("complement"))
        + " · "
        + a.optString("district")
        + " · "
        + a.optString("city")
        + "/"
        + a.optString("state")
        + " · "
        + a.optString("zip");
  }

  private JSONObject address(String id) {
    for (JSONObject a : addresses()) if (id.equals(a.optString("id"))) return a;
    throw new IllegalArgumentException("Selecione um endereço cadastrado");
  }

  public void saveOrder(JSONObject input) {
    if (PlatformGateway.configured()) {
      PlatformGateway.mutate(prefs, "request.save", input);
      return;
    }
    JSONObject who = actor();
    String id = input.optString("id");
    JSONObject before = id.isEmpty() ? null : find(id);
    boolean admin = "admin".equals(who.optString("role"));
    if (before == null) citizen();
    else if (!admin
        && (!LocalJson.account(prefs).equals(before.optString("citizen_id"))
            || !"aberta".equals(before.optString("status"))))
      throw new SecurityException("Somente solicitações abertas podem ser editadas pelo cidadão");
    if (before != null && input.optInt("revision", -1) != before.optInt("revision"))
      throw new IllegalStateException("A solicitação mudou. Reabra antes de editar.");
    JSONObject after = before == null ? new JSONObject() : LocalJson.object(before.toString());
    if (after.optBoolean("deleted"))
      throw new IllegalArgumentException("Restaure a solicitação antes de editar");
    String desired = input.optString("desired_date");
    if (!validDate(desired) || (before == null && desired.compareTo(today()) < 0))
      throw new IllegalArgumentException("Informe uma data válida a partir de hoje (AAAA-MM-DD)");
    JSONArray items = input.optJSONArray("items");
    if (items == null || items.length() == 0)
      throw new IllegalArgumentException("Adicione pelo menos um material e seu peso");
    JSONArray checked = new JSONArray();
    Set<String> used = new HashSet<>();
    Set<String> previousMaterials = new HashSet<>();
    if (before != null && before.optJSONArray("items") != null)
      for (JSONObject old : list(before.optJSONArray("items"))) previousMaterials.add(old.optString("material_id"));
    for (JSONObject item : list(items)) {
      JSONObject material = null;
      for (JSONObject m : list(array(MATERIALS)))
        if (m.optString("id").equals(item.optString("material_id"))) material = m;
      if (material == null || (!material.optBoolean("active") && !previousMaterials.contains(material.optString("id"))))
        throw new IllegalArgumentException("Selecione materiais ativos do catálogo");
      if (!used.add(material.optString("id")))
        throw new IllegalArgumentException("Informe cada material apenas uma vez");
      JSONObject copy = new JSONObject();
      put(copy, "material_id", material.optString("id"));
      put(copy, "name", material.optString("name"));
      BigDecimal weight = DecimalValues.positive(item.optString("weight"), 2);
      put(copy, "weight", weight.setScale(2).toPlainString());
      checked.put(copy);
    }
    JSONObject snapshot;
    if (input.has("current_location")) {
      if (admin) throw new SecurityException("Somente o cidadão escolhe a localização atual");
      snapshot = locationSnapshot(input.optJSONObject("current_location"));
    } else if (before != null && (admin || input.optString("address_id").equals(before.optString("address_id"))))
      snapshot = LocalJson.object(before.optJSONObject("address").toString());
    else snapshot = LocalJson.object(address(input.optString("address_id")).toString());
    put(after, "address", snapshot);
    put(after, "address_id", snapshot.optString("id"));
    put(after, "desired_date", desired);
    put(after, "items", checked);
    put(after, "description", input.optString("description").trim());
    put(after, "notes", input.optString("notes").trim());
    if (input.has("photos")) put(after, "photos", PhotoRefs.copy(input.optJSONArray("photos")));
    else if (before == null) put(after, "photos", new JSONArray());
    if (before == null) {
      put(after, "id", UUID.randomUUID().toString());
      put(after, "citizen_id", who.optString("id"));
      put(after, "citizen_name", who.optString("name"));
      put(after, "status", "aberta");
      put(after, "responsible_id", "");
      put(after, "created_at", System.currentTimeMillis());
      put(after, "revision", 0);
    }
    persistOrder(
        before, after, before == null ? "Solicitação criada" : "Solicitação atualizada", "");
  }

  private JSONObject locationSnapshot(JSONObject location) {
    if (location == null) throw new IllegalArgumentException("Localização indisponível. Tente novamente.");
    validateCoordinates(location);
    double lat = location.optDouble("latitude", Double.NaN), lng = location.optDouble("longitude", Double.NaN);
    if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
      throw new IllegalArgumentException("Localização inválida. Tente novamente.");
    JSONObject snapshot = new JSONObject();
    put(snapshot, "id", "__current_location");
    put(snapshot, "source", "device_location");
    put(snapshot, "nickname", "Localização atual");
    put(snapshot, "latitude", lat);
    put(snapshot, "longitude", lng);
    for (String field : new String[] {"street", "number", "complement", "district", "city", "state", "zip"}) {
      String value = location.optString(field).trim();
      if ("zip".equals(field)) value = value.replaceAll("\\D", "");
      if (value.length() > addressLimit(field)) throw new IllegalArgumentException("Endereço muito longo");
      put(snapshot, field, value);
    }
    return snapshot;
  }

  private static boolean validDate(String value) {
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
    format.setLenient(false);
    ParsePosition p = new ParsePosition(0);
    return format.parse(value, p) != null && p.getIndex() == value.length();
  }

  public static String today() {
    return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
  }

  public List<JSONObject> orders() {
    JSONObject who = actor();
    String id = who.optString("id"), role = who.optString("role");
    List<JSONObject> result = new ArrayList<>();
    for (JSONObject order : list(array(ORDERS))) {
      if ("admin".equals(role)) {
        result.add(order);
        continue;
      }
      if (order.optBoolean("deleted")) continue;
      if ("cliente".equals(role) && id.equals(order.optString("citizen_id"))) result.add(order);
      if (PedidoRules.coletor(role)
          && (id.equals(order.optString("responsible_id"))
              || id.equals(order.optString("previous_responsible_id"))
              || attended(order, id)
              || ("aberta".equals(order.optString("status")) && accepts(who, order))))
        result.add(order);
    }
    if (PedidoRules.coletor(role)) {
      List<JSONObject> projected = new ArrayList<>();
      for (JSONObject row : result) {
        JSONObject copy = LocalJson.object(row.toString());
        if (!approved(row) || !id.equals(row.optString("responsible_id"))) {
          JSONObject old = row.optJSONObject("address"), region = new JSONObject();
          if (old != null) for (String key : new String[]{"district", "city", "state"}) put(region, key, old.optString(key));
          put(copy, "address", region);
        }
        projected.add(copy);
      }
      result = projected;
    }
    result.sort((a, b) -> Long.compare(b.optLong("created_at"), a.optLong("created_at")));
    return result;
  }

  public JSONObject order(String id) {
    for (JSONObject o : orders()) if (id.equals(o.optString("id"))) return o;
    throw new SecurityException("Solicitação não disponível para esta conta");
  }

  private JSONObject find(String id) {
    for (JSONObject o : list(array(ORDERS))) if (id.equals(o.optString("id"))) return o;
    throw new IllegalArgumentException("Solicitação não encontrada");
  }

  private boolean attended(JSONObject order, String id) {
    JSONArray attempts = order.optJSONArray("attendances");
    if (attempts == null) return false;
    for (JSONObject a : list(attempts)) if (id.equals(a.optString("responsible_id"))) return true;
    return false;
  }

  private boolean accepts(JSONObject who, JSONObject order) {
    JSONArray allowed =
        LocalJson.array(prefs.getString("accepted_materials_" + who.optString("id"), "[]"));
    if (allowed.length() == 0) return true;
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < allowed.length(); i++) ids.add(allowed.optString(i));
    for (JSONObject item : list(order.optJSONArray("items")))
      if (!ids.contains(item.optString("material_id"))) return false;
    return true;
  }

  public void acceptedMaterials(JSONArray ids) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      put(p, "ids", ids);
      PlatformGateway.mutate(prefs, "accepted.save", p);
      return;
    }
    if (!PedidoRules.coletor(actor().optString("role")))
      throw new SecurityException("Apenas responsáveis escolhem os materiais atendidos");
    Set<String> active = new HashSet<>();
    for (JSONObject m : materials(false)) active.add(m.optString("id"));
    for (int i = 0; i < ids.length(); i++)
      if (!active.contains(ids.optString(i)))
        throw new IllegalArgumentException("Material indisponível");
    LocalJson.commit(
        prefs.edit().putString("accepted_materials_" + LocalJson.account(prefs), ids.toString()));
  }

  public JSONArray acceptedMaterials() {
    actor();
    return LocalJson.array(prefs.getString("accepted_materials_" + LocalJson.account(prefs), "[]"));
  }

  public static boolean ended(JSONObject order) {
    return "concluida".equals(order.optString("status")) || "cancelada".equals(order.optString("status"));
  }

  public static boolean approved(JSONObject order) {
    return java.util.Arrays.asList("aceita", "a_caminho", "coletando", "descartando", "concluida").contains(order.optString("status"));
  }

  public void transition(String id, String action, int revision) {
    change(id, action, revision, new JSONObject());
  }

  public void propose(String id, int revision, String price, String message) {
    JSONObject payload = new JSONObject();
    put(payload, "price", DecimalValues.positive(price, 2).setScale(2).toPlainString());
    if (message.length() > 500) throw new IllegalArgumentException("Mensagem de ate 500 caracteres");
    put(payload, "message", message.trim());
    change(id, "propose", revision, payload);
  }

  private void change(String id, String action, int revision, JSONObject payload) {
    if (PlatformGateway.configured()) {
      put(payload, "id", id);
      put(payload, "action", action);
      put(payload, "revision", revision);
      PlatformGateway.mutate(prefs, "request.transition", payload);
      return;
    }
    order(id); // Authorize before accessing the full snapshot.
    JSONObject who = actor(), before = find(id), after = LocalJson.object(before.toString());
    if (revision != before.optInt("revision")) throw new IllegalStateException("O pedido mudou. Atualize a tela.");
    if (before.optBoolean("deleted")) throw new IllegalArgumentException("Pedido excluido");
    String status = before.optString("status"), actorId = who.optString("id");
    boolean assigned = actorId.equals(before.optString("responsible_id"));
    boolean owner = actorId.equals(before.optString("citizen_id"));
    String notification = "";
    if ("accept".equals(action)) {
      if (!PedidoRules.coletor(who.optString("role")) || !"aberta".equals(status) || !accepts(who, before))
        throw new SecurityException("Pedido indisponivel para aceite");
      JSONArray rejected = before.optJSONArray("rejected_by");
      if (rejected != null) for (int i = 0; i < rejected.length(); i++)
        if (actorId.equals(rejected.optString(i))) throw new SecurityException("Pedido recusado por esta conta");
      put(after, "status", "negociando");
      put(after, "responsible_id", actorId);
      String name = "empresa".equals(who.optString("role")) ? who.optString("company") : who.optString("name");
      put(after, "responsible_name", name.isEmpty() ? who.optString("name") : name);
    } else if ("propose".equals(action)) {
      if (!assigned || !"negociando".equals(status)) throw new SecurityException("Aceite o pedido antes de enviar a proposta");
      put(after, "proposal_price", payload.optString("price"));
      put(after, "proposal_message", payload.optString("message"));
      put(after, "proposed_at", System.currentTimeMillis());
      put(after, "status", "proposta");
      notification = "Nova proposta de " + before.optString("responsible_name") + ".";
    } else if ("approve".equals(action)) {
      if (!owner || !"proposta".equals(status)) throw new SecurityException("Apenas o cliente pode aprovar a proposta");
      put(after, "status", "aceita");
      put(after, "accepted_at", System.currentTimeMillis());
      notification = "Proposta aceita. O coletor pode iniciar o deslocamento.";
    } else if ("decline".equals(action) || "release".equals(action)) {
      if ("decline".equals(action) ? !owner || !"proposta".equals(status) : !assigned || ended(before))
        throw new SecurityException("Esta acao nao esta disponivel");
      JSONArray attempts = after.optJSONArray("attendances");
      if (attempts == null) attempts = new JSONArray();
      JSONObject attempt = new JSONObject();
      put(attempt, "responsible_id", before.optString("responsible_id"));
      put(attempt, "released_at", System.currentTimeMillis());
      attempts.put(attempt);
      put(after, "attendances", attempts);
      put(after, "responsible_id", "");
      put(after, "responsible_name", "");
      put(after, "status", "aberta");
      for (String key : new String[]{"proposal_price", "proposal_message", "proposed_at", "accepted_at", "going_at", "arrived_at", "collected_at"}) after.remove(key);
      notification = "Continuamos procurando outro coletor para seu pedido.";
    } else if ("reject".equals(action)) {
      if (!PedidoRules.coletor(who.optString("role")) || !"aberta".equals(status)) throw new SecurityException("Recusa nao permitida");
      JSONArray rejected = after.optJSONArray("rejected_by");
      if (rejected == null) rejected = new JSONArray();
      rejected.put(actorId);
      put(after, "rejected_by", rejected);
    } else if ("cancel".equals(action)) {
      if ((!owner && !"admin".equals(who.optString("role"))) || ended(before)) throw new SecurityException("Cancelamento nao permitido");
      put(after, "previous_responsible_id", before.optString("responsible_id"));
      put(after, "responsible_id", "");
      put(after, "status", "cancelada");
      notification = "Pedido cancelado.";
    } else {
      if (!assigned) throw new SecurityException("Apenas o coletor aprovado pode atualizar a coleta");
      String next;
      String stamp;
      if ("going".equals(action) && "aceita".equals(status)) { next = "a_caminho"; stamp = "going_at"; notification = "O coletor esta a caminho."; }
      else if ("arrive".equals(action) && "a_caminho".equals(status)) { next = "coletando"; stamp = "arrived_at"; notification = "O coletor chegou ao local."; }
      else if ("collect".equals(action) && "coletando".equals(status)) { next = "descartando"; stamp = "collected_at"; notification = "Materiais coletados. Aguardando descarte."; }
      else if ("complete".equals(action) && "descartando".equals(status)) { next = "concluida"; stamp = "completed_at"; notification = "Descarte confirmado. Coleta concluida!"; }
      else throw new IllegalArgumentException("Etapa invalida. Siga a ordem da coleta.");
      put(after, "status", next);
      put(after, stamp, System.currentTimeMillis());
    }
    persistOrder(before, after, "Pedido: " + action, notification);
  }

  public void review(String id, int score, String comment) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      put(p, "id", id);
      put(p, "score", score);
      put(p, "comment", comment);
      PlatformGateway.mutate(prefs, "request.review", p);
      return;
    }
    citizen();
    JSONObject before = order(id);
    if (!"concluida".equals(before.optString("status"))
        || before.has("review")
        || score < 1
        || score > 5
        || comment.length() > 500)
      throw new IllegalArgumentException(
          "Avalie uma coleta concluída uma única vez, com nota de 1 a 5");
    JSONObject after = LocalJson.object(before.toString()), review = new JSONObject();
    put(review, "score", score);
    put(review, "comment", comment.trim());
    put(review, "created_at", System.currentTimeMillis());
    put(after, "review", review);
    persistOrder(before, after, "Coleta avaliada", "");
  }

  public List<JSONObject> notifications() {
    actor();
    List<JSONObject> result = new ArrayList<>();
    for (JSONObject n : list(array(NOTIFICATIONS)))
      if (LocalJson.account(prefs).equals(n.optString("owner_id"))) result.add(0, n);
    return result;
  }

  public void markRead(String id) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      put(p, "id", id);
      PlatformGateway.mutate(prefs, "notification.read", p);
      return;
    }
    for (JSONObject n : notifications())
      if (id.equals(n.optString("id"))) {
        put(n, "read", true);
        persist(NOTIFICATIONS, n, null, "Notificação lida");
        return;
      }
    throw new SecurityException("Notificação de outra conta");
  }

  public void setDeleted(String id, boolean deleted) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      put(p, "id", id);
      put(p, "deleted", deleted);
      PlatformGateway.mutate(prefs, "request.deleted", p);
      return;
    }
    profiles.requireAdmin();
    JSONObject before = find(id), after = LocalJson.object(before.toString());
    if (deleted
        && !("concluida".equals(before.optString("status"))
            || "cancelada".equals(before.optString("status"))))
      throw new IllegalArgumentException("Encerre a solicitação antes de excluir");
    put(after, "deleted", deleted);
    put(after, "deleted_at", deleted ? System.currentTimeMillis() : 0);
    persistOrder(before, after, deleted ? "Solicitação excluída" : "Solicitação restaurada", "");
  }

  private void persistOrder(
      JSONObject before, JSONObject after, String action, String notification) {
    put(after, "revision", before == null ? 1 : before.optInt("revision") + 1);
    put(after, "updated_at", System.currentTimeMillis());
    SharedPreferences.Editor editor = prefs.edit();
    replace(editor, ORDERS, after);
    if (!notification.isEmpty()) {
      JSONArray notifications = array(NOTIFICATIONS);
      JSONObject n = new JSONObject();
      put(n, "id", UUID.randomUUID().toString());
      put(n, "owner_id", after.optString("citizen_id"));
      put(n, "request_id", after.optString("id"));
      put(n, "message", notification);
      put(n, "created_at", System.currentTimeMillis());
      put(n, "read", false);
      notifications.put(n);
      editor.putString(NOTIFICATIONS, notifications.toString());
    }
    new AuditRepository(prefs)
        .append(editor, action, "order", after.optString("id"), before, after);
    LocalJson.commit(editor);
  }

  private void persist(String key, JSONObject after, JSONObject before, String action) {
    SharedPreferences.Editor editor = prefs.edit();
    replace(editor, key, after);
    new AuditRepository(prefs).append(editor, action, key, after.optString("id"), before, after);
    LocalJson.commit(editor);
  }

  private void replace(SharedPreferences.Editor editor, String key, JSONObject after) {
    JSONArray output = new JSONArray();
    boolean exists = false;
    for (JSONObject old : list(array(key))) {
      if (old.optString("id").equals(after.optString("id"))) {
        output.put(after);
        exists = true;
      } else output.put(old);
    }
    if (!exists) output.put(after);
    editor.putString(key, output.toString());
  }

  public static String itemSummary(JSONObject request) {
    JSONArray items = request.optJSONArray("items");
    if (items == null) return "";
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (i > 0) text.append("\n");
      text.append(item.optString("name"))
          .append(" · ")
          .append(item.optString("weight"))
          .append(" kg");
    }
    return text.toString();
  }

  public JSONObject information() {
    return LocalJson.object(prefs.getString("platform_information", "{}"));
  }

  public void saveInformation(JSONObject input) {
    profiles.requireAdmin();
    if (PlatformGateway.configured()) {
      PlatformGateway.mutate(prefs, "info.save", input);
      return;
    }
    JSONObject after = new JSONObject();
    for (String field : new String[] {"name", "description", "support_email"})
      put(after, field, input.optString(field).trim());
    SharedPreferences.Editor editor =
        prefs.edit().putString("platform_information", after.toString());
    new AuditRepository(prefs)
        .append(
            editor,
            "Informações da plataforma atualizadas",
            "information",
            "platform",
            information(),
            after);
    LocalJson.commit(editor);
  }
}
