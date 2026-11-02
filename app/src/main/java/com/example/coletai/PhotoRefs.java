package com.example.coletai;

import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small persistent references; image bytes never enter a Bundle or SharedPreferences. */
final class PhotoRefs {
  static final int LIMIT = 3;
  static final int MAX_BYTES = 256 * 1024;
  static boolean validId(String id) {
    return id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
  }
  static JSONArray copy(JSONArray photos) {
    if (photos == null || photos.length() > LIMIT) throw new IllegalArgumentException("Adicione até 3 fotos por pedido");
    JSONArray result = new JSONArray();
    Set<String> used = new HashSet<>();
    for (int i = 0; i < photos.length(); i++) {
      JSONObject photo = photos.optJSONObject(i);
      if (photo == null || !validId(photo.optString("id"))
          || !("local".equals(photo.optString("source")) || "remote".equals(photo.optString("source")))
          || !used.add(photo.optString("source") + photo.optString("id")))
        throw new IllegalArgumentException("Foto inválida. Escolha a imagem novamente.");
      JSONObject ref = new JSONObject();
      LocalJson.put(ref, "id", photo.optString("id"));
      LocalJson.put(ref, "source", photo.optString("source"));
      result.put(ref);
    }
    return result;
  }
}
