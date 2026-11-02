package com.example.coletai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Preserva autor, data e cópias dos dados antes/depois na mesma transação da alteração. */
public final class AuditRepository {
  private final SharedPreferences prefs;

  public AuditRepository(SharedPreferences prefs) {
    this.prefs = prefs;
  }

  void append(
      SharedPreferences.Editor editor,
      String action,
      String entity,
      String target,
      JSONObject before,
      JSONObject after) {
    appendAs(editor, LocalJson.account(prefs), action, entity, target, before, after);
  }

  void appendAs(
      SharedPreferences.Editor editor,
      String actor,
      String action,
      String entity,
      String target,
      JSONObject before,
      JSONObject after) {
    JSONArray items = LocalJson.array(prefs.getString("admin_audit", "[]"));
    JSONObject item = new JSONObject();
    LocalJson.put(item, "id", UUID.randomUUID().toString());
    LocalJson.put(item, "data", System.currentTimeMillis());
    LocalJson.put(item, "actor", actor);
    LocalJson.put(item, "action", action);
    LocalJson.put(item, "entity", entity);
    LocalJson.put(item, "target", target);
    LocalJson.put(item, "before", before);
    LocalJson.put(item, "after", after);
    items.put(item);
    editor.putString("admin_audit", items.toString());
  }

  public List<JSONObject> list() {
    JSONArray items = LocalJson.array(prefs.getString("admin_audit", "[]"));
    List<JSONObject> result = new ArrayList<>();
    for (int i = items.length() - 1; i >= 0; i--) {
      JSONObject item = items.optJSONObject(i);
      if (item != null) result.add(item);
    }
    result.sort((a, b) -> Long.compare(b.optLong("data"), a.optLong("data")));
    return result;
  }
}
