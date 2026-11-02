package com.example.coletai;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Authoritative offline server. One atomic transaction per command across all phones. */
final class BluetoothEngine {
  private final SharedPreferences database;
  BluetoothEngine(SharedPreferences database) { this.database = database; }

  synchronized JSONObject execute(JSONObject command) {
    return BluetoothPlatform.locally(() -> executeLocal(command));
  }

  private JSONObject executeLocal(JSONObject command) {
    String operation = command.optString("operation"), token = command.optString("token");
    String requestId = command.optString("id");
    JSONObject response = new JSONObject();
    LocalJson.put(response, "id", requestId);
    try {
      if (command.optInt("version") != 1 || !PhotoRefs.validId(requestId))
        throw new IllegalArgumentException("Versão Bluetooth incompatível. Atualize os aparelhos.");
      SnapshotPreferences tx = new SnapshotPreferences(database);
      ProfileRepository profiles = new ProfileRepository(tx);
      CollectionRepository orders = new CollectionRepository(tx);
      JSONObject payload = command.optJSONObject("payload");
      if (payload == null) payload = new JSONObject();
      String account = "";
      JSONObject sessions = LocalJson.object(tx.getString("bt_sessions", "{}"));
      if (!"login".equals(operation) && !"register".equals(operation)) {
        JSONObject session = sessions.optJSONObject(token);
        if (session == null || session.optLong("expires") < System.currentTimeMillis()
            || !profiles.available(session.optString("account"))) {
          LocalJson.put(response, "code", 401);
          throw new SecurityException("Sessão expirada ou conta bloqueada. Entre novamente.");
        }
        account = session.optString("account");
        new PedidoRepository(tx).selecionarConta(account, profiles.get(account).optString("role"));
      }
      String dedupKey = token + ":" + requestId;
      JSONObject replies = LocalJson.object(tx.getString("bt_replies", "{}"));
      JSONObject cached = replies.optJSONObject(dedupKey);
      if (cached != null) {
        if (!cached.optString("command").equals(fingerprint(command))) throw new SecurityException("Identificador de operação reutilizado");
        JSONObject restored = LocalJson.object(cached.optJSONObject("response").toString());
        if (!account.isEmpty()) LocalJson.put(restored, "state", state(tx, profiles, orders));
        return restored;
      }
      if ("login".equals(operation)) {
        JSONObject user = profiles.authenticate(payload.optString("email"), payload.optString("password"));
        account = user.optString("id");
        token = UUID.randomUUID().toString() + UUID.randomUUID();
        JSONObject session = new JSONObject();
        LocalJson.put(session, "account", account);
        LocalJson.put(session, "expires", System.currentTimeMillis() + 12 * 60 * 60 * 1000L);
        // Remove expired sessions so a demonstration can run repeatedly without growing forever.
        List<String> sessionKeys = new ArrayList<>();
        for (Iterator<String> it = sessions.keys(); it.hasNext();) sessionKeys.add(it.next());
        for (String key : sessionKeys)
          if (sessions.optJSONObject(key).optLong("expires") < System.currentTimeMillis()) sessions.remove(key);
        LocalJson.put(sessions, token, session);
        LocalJson.put(response, "token", token);
        LocalJson.put(response, "account_id", account);
        new PedidoRepository(tx).selecionarConta(account, user.optString("role"));
      } else if ("register".equals(operation)) {
        profiles.register(payload, payload.optString("password"));
      } else if ("logout".equals(operation)) {
        sessions.remove(token);
      } else if ("action".equals(operation)) {
        mutate(tx, profiles, orders, payload.optString("action"), payload.optJSONObject("payload"));
      } else if ("photo.upload".equals(operation)) {
        byte[] bytes = BluetoothBytes.decode(payload.optString("data"));
        if (bytes.length == 0 || bytes.length > PhotoRefs.MAX_BYTES) throw new IllegalArgumentException("Foto muito grande");
        String id = UUID.randomUUID().toString();
        LocalJson.commit(tx.edit().putString("bt_photo_" + id, payload.optString("data")).putString("bt_photo_owner_" + id, account));
        LocalJson.put(response, "photo", LocalJson.object("{\"source\":\"remote\",\"id\":\"" + id + "\"}"));
      } else if ("photo.download".equals(operation)) {
        String id = payload.optString("photo_id");
        boolean allowed = account.equals(tx.getString("bt_photo_owner_" + id, ""));
        for (JSONObject order : orders.orders())
          if (hasPhoto(order, id)) allowed = true;
        if (!allowed) throw new SecurityException("Foto indisponível para esta conta");
        String data = tx.getString("bt_photo_" + id, "");
        if (data.isEmpty()) throw new IllegalArgumentException("Foto não encontrada");
        LocalJson.put(response, "data", data);
      } else if (!"state".equals(operation)) throw new IllegalArgumentException("Operação Bluetooth desconhecida");
      LocalJson.put(response, "ok", true);
      if (!account.isEmpty() && !"logout".equals(operation) && !operation.startsWith("photo."))
        LocalJson.put(response, "state", state(tx, profiles, orders));
      tx.edit().putString("bt_sessions", sessions.toString()).commit();
      if ("action".equals(operation) || "photo.upload".equals(operation) || "register".equals(operation)) {
        JSONObject saved = new JSONObject();
        JSONObject compact = LocalJson.object(response.toString()); compact.remove("state");
        LocalJson.put(saved, "command", fingerprint(command)); LocalJson.put(saved, "response", compact);
        LocalJson.put(replies, dedupKey, saved);
        JSONArray sequence = LocalJson.array(tx.getString("bt_reply_order", "[]"));
        sequence.put(dedupKey);
        while (sequence.length() > 32) replies.remove(sequence.remove(0).toString());
        tx.edit().putString("bt_replies", replies.toString()).putString("bt_reply_order", sequence.toString()).commit();
      }
      if (!"state".equals(operation) && !"photo.download".equals(operation)) tx.persistTo(database);
    } catch (RuntimeException error) {
      LocalJson.put(response, "ok", false);
      LocalJson.put(response, "error", error.getMessage() == null ? "Não foi possível concluir a operação" : error.getMessage());
    }
    return response;
  }

  private static String fingerprint(JSONObject command) {
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(command.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return BluetoothBytes.encode(digest);
    } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
  }

  private static boolean hasPhoto(JSONObject order, String id) {
    JSONArray photos = order.optJSONArray("photos");
    if (photos != null) for (int i = 0; i < photos.length(); i++) if (id.equals(photos.optJSONObject(i).optString("id"))) return true;
    return false;
  }

  private void mutate(SharedPreferences tx, ProfileRepository profiles, CollectionRepository orders, String action, JSONObject p) {
    if (p == null) throw new IllegalArgumentException("Dados da operação ausentes");
    String id = p.optString("id");
    switch (action) {
      case "profile.save":
        boolean admin = "admin".equals(profiles.current().optString("role"));
        if (id.isEmpty()) profiles.createByAdmin(p, p.optString("password")); else profiles.save(p, admin);
        break;
      case "profile.status": profiles.setStatus(id, p.optString("status")); break;
      case "profile.password":
        if (!id.equals(profiles.current().optString("id"))) throw new SecurityException("Conta de outra pessoa");
        profiles.changePassword(p.optString("current_password"), p.optString("password")); break;
      case "address.save": orders.saveAddress(p); break;
      case "address.delete": orders.deleteAddress(id); break;
      case "accepted.save": orders.acceptedMaterials(p.optJSONArray("ids")); break;
      case "material.save": orders.saveMaterial(p); break;
      case "info.save": orders.saveInformation(p); break;
      case "request.save":
        if (p.has("photos")) {
          JSONArray photos = PhotoRefs.copy(p.optJSONArray("photos"));
          JSONObject previous = id.isEmpty() ? new JSONObject() : orders.order(id);
          for (int i = 0; i < photos.length(); i++) {
            JSONObject photo = photos.optJSONObject(i);
            if (!"remote".equals(photo.optString("source"))
                || (!profiles.current().optString("id").equals(tx.getString("bt_photo_owner_" + photo.optString("id"), ""))
                    && !("admin".equals(profiles.current().optString("role")) && hasPhoto(previous, photo.optString("id")))))
              throw new SecurityException("Foto de outra conta ou não enviada");
          }
        }
        orders.saveOrder(p); break;
      case "request.transition":
        if ("propose".equals(p.optString("action"))) orders.propose(id, p.optInt("revision", -1), p.optString("price"), p.optString("message"));
        else orders.transition(id, p.optString("action"), p.optInt("revision", -1)); break;
      case "request.review": orders.review(id, p.optInt("score"), p.optString("comment")); break;
      case "request.deleted": orders.setDeleted(id, p.optBoolean("deleted")); break;
      case "notification.read": orders.markRead(id); break;
      default: throw new IllegalArgumentException("Operação desconhecida");
    }
  }

  private JSONObject state(SharedPreferences tx, ProfileRepository profiles, CollectionRepository orders) {
    JSONObject result = new JSONObject(), user = profiles.current();
    boolean admin = "admin".equals(user.optString("role"));
    LocalJson.put(result, "profiles", new JSONArray(admin ? profiles.list() : Collections.singletonList(user)));
    LocalJson.put(result, "requests", new JSONArray(orders.orders()));
    LocalJson.put(result, "addresses", "cliente".equals(user.optString("role")) ? new JSONArray(orders.addresses()) : new JSONArray());
    LocalJson.put(result, "materials", new JSONArray(orders.materials(admin)));
    LocalJson.put(result, "notifications", new JSONArray(orders.notifications()));
    LocalJson.put(result, "accepted_materials", orders.acceptedMaterials());
    LocalJson.put(result, "info", orders.information());
    LocalJson.put(result, "audit", admin ? new JSONArray(new AuditRepository(tx).list()) : new JSONArray());
    return result;
  }
}
