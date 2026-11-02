package com.example.coletai;

import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cliente HTTPS; nunca troca uma falha do servidor por uma alteração local silenciosa. */
final class PlatformGateway {
  static final class SessionExpired extends IllegalStateException {
    SessionExpired() {
      super("Sessão expirada. Entre novamente.");
    }
  }

  static boolean configured() {
    return !BluetoothPlatform.localExecution() && (BluetoothPlatform.active() || !BuildConfig.API_BASE_URL.isEmpty());
  }

  static boolean demoAllowed() {
    return BuildConfig.DEBUG && !configured();
  }

  static JSONObject login(SharedPreferences prefs, String email, String password) {
    JSONObject payload = new JSONObject();
    LocalJson.put(payload, "email", email);
    LocalJson.put(payload, "password", password);
    JSONObject response = call(prefs, "login", payload, "POST");
    String account = response.optString("account_id");
    LocalJson.commit(
        prefs
            .edit()
            .putString("api_token", response.optString("token"))
            .putString("api_account_id", account));
    apply(prefs, response.optJSONObject("state"));
    return new ProfileRepository(prefs).get(account);
  }

  static void register(SharedPreferences prefs, JSONObject payload) {
    call(prefs, "register", payload, "POST");
  }

  static synchronized void refresh(SharedPreferences prefs) {
    apply(prefs, call(prefs, "state", null, "GET").optJSONObject("state"));
  }

  static JSONObject uploadPhoto(SharedPreferences prefs, byte[] image) {
    if (image.length == 0 || image.length > PhotoRefs.MAX_BYTES) throw new IllegalArgumentException("Foto muito grande");
    JSONObject ref = LocalJson.object(new String(photoRequest(prefs, "photos", image), StandardCharsets.UTF_8));
    return PhotoRefs.copy(new JSONArray().put(ref)).optJSONObject(0);
  }

  static byte[] downloadPhoto(SharedPreferences prefs, String id) {
    if (!PhotoRefs.validId(id)) throw new IllegalArgumentException("Foto inválida");
    return photoRequest(prefs, "photos/" + id, null);
  }

  private static byte[] photoRequest(SharedPreferences prefs, String path, byte[] image) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
      throw new IllegalStateException("A operação de rede deve executar em segundo plano");
    if (BluetoothPlatform.active()) {
      JSONObject payload = new JSONObject();
      if (image != null) {
        LocalJson.put(payload, "data", BluetoothBytes.encode(image));
        return BluetoothPlatform.current().request("photo.upload", payload, prefs.getString("api_token", ""))
            .optJSONObject("photo").toString().getBytes(StandardCharsets.UTF_8);
      }
      LocalJson.put(payload, "photo_id", path.substring("photos/".length()));
      return BluetoothBytes.decode(BluetoothPlatform.current().request("photo.download", payload,
          prefs.getString("api_token", "")).optString("data"));
    }
    HttpURLConnection connection = null;
    try {
      URL url = new URL(BuildConfig.API_BASE_URL.replaceAll("/+$", "") + "/v1/" + path);
      if (!"https".equals(url.getProtocol())) throw new IllegalArgumentException("A API precisa utilizar HTTPS");
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(20000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Authorization", "Bearer " + prefs.getString("api_token", ""));
      connection.setRequestProperty("Accept", image == null ? "image/jpeg" : "application/json");
      if (image != null) {
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "image/jpeg");
        connection.setFixedLengthStreamingMode(image.length);
        try (java.io.OutputStream output = connection.getOutputStream()) { output.write(image); }
      }
      int status = connection.getResponseCode();
      if (status == 401) throw new SessionExpired();
      if (status < 200 || status >= 300) throw new IllegalStateException("Não foi possível enviar ou abrir a foto. Tente novamente.");
      try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
          if (output.size() + count > PhotoRefs.MAX_BYTES) throw new IllegalStateException("Foto muito grande");
          output.write(buffer, 0, count);
        }
        return output.toByteArray();
      }
    } catch (java.io.IOException error) { throw new IllegalStateException("Sem conexão para enviar ou abrir a foto. Tente novamente."); }
    finally { if (connection != null) connection.disconnect(); }
  }

  static synchronized void mutate(SharedPreferences prefs, String action, JSONObject payload) {
    JSONObject request = new JSONObject();
    LocalJson.put(request, "action", action);
    LocalJson.put(request, "payload", payload);
    apply(prefs, call(prefs, "action", request, "POST").optJSONObject("state"));
  }

  static void logout(SharedPreferences prefs) {
    try {
      call(prefs, "logout", new JSONObject(), "POST");
    } catch (RuntimeException ignored) {
      // A saída local continua possível quando a rede ou a sessão já expirou.
    } finally {
      LocalJson.commit(prefs.edit().clear());
    }
  }

  private static JSONObject call(
      SharedPreferences prefs, String path, JSONObject payload, String method) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
      throw new IllegalStateException("A operação de rede deve executar em segundo plano");
    if (BluetoothPlatform.active())
      return BluetoothPlatform.current().request(path, payload, prefs.getString("api_token", ""));
    HttpURLConnection connection = null;
    try {
      URL url = new URL(BuildConfig.API_BASE_URL.replaceAll("/+$", "") + "/v1/" + path);
      if (!"https".equals(url.getProtocol()))
        throw new IllegalArgumentException("A API precisa utilizar HTTPS");
      connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(20000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Accept", "application/json");
      String token = prefs.getString("api_token", "");
      if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
      if (payload != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (java.io.OutputStream out = connection.getOutputStream()) {
          out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
      int status = connection.getResponseCode();
      InputStream input =
          status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      if (input != null)
        try (InputStream in = input) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = in.read(buffer)) != -1) {
            if (bytes.size() + count > 8 * 1024 * 1024)
              throw new IllegalStateException("A resposta excedeu o limite de dados");
            bytes.write(buffer, 0, count);
          }
        }
      JSONObject result = LocalJson.object(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      if (status == 401 && !"login".equals(path)) throw new SessionExpired();
      if (status < 200 || status >= 300)
        throw new IllegalStateException(
            result.optString("detail", "Não foi possível concluir a operação no servidor"));
      return result;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(
          "Sem comunicação com o servidor. Nenhum dado foi alterado localmente.", e);
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private static void apply(SharedPreferences prefs, JSONObject state) {
    if (state == null) throw new IllegalStateException("Resposta inválida da API");
    SharedPreferences.Editor editor = prefs.edit();
    String[][] mappings = {
      {"profiles", "profiles_v1"},
      {"requests", CollectionRepository.ORDERS},
      {"addresses", CollectionRepository.ADDRESSES},
      {"materials", CollectionRepository.MATERIALS},
      {"notifications", CollectionRepository.NOTIFICATIONS},
      {"audit", "admin_audit"}
    };
    for (String[] mapping : mappings) {
      JSONArray array = state.optJSONArray(mapping[0]);
      if (array == null) throw new IllegalStateException("Resposta incompleta da API");
      editor.putString(mapping[1], array.toString());
    }
    editor.putString(
        "platform_information",
        state.optJSONObject("info") == null ? "{}" : state.optJSONObject("info").toString());
    editor.putString(
        "accepted_materials_" + prefs.getString("api_account_id", ""),
        state.optJSONArray("accepted_materials") == null
            ? "[]"
            : state.optJSONArray("accepted_materials").toString());
    editor.putBoolean("requests_v14_migrated", true);
    LocalJson.commit(editor);
  }
}
