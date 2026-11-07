package com.example.coletai;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Conversão e gravação compartilhadas pelos repositórios locais. */
final class LocalJson {
    private LocalJson() { }
    static JSONObject object(String value) {
        try { return new JSONObject(value); }
        catch (JSONException e) { throw new IllegalStateException("Registro local inválido", e); }
    }
    static JSONArray array(String value) {
        try { return new JSONArray(value); }
        catch (JSONException e) { throw new IllegalStateException("Lista local inválida", e); }
    }
    static JSONObject put(JSONObject object, String key, Object value) {
        try { object.put(key, value); return object; }
        catch (JSONException e) { throw new IllegalStateException("Não foi possível preparar o registro", e); }
    }
    static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Não foi possível salvar. Tente novamente.");
    }
    static String account(SharedPreferences prefs) {
        return prefs.getString("conta_atual", prefs.getString("usuario_tipo_atual", ""));
    }
}
