package com.example.coletai;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Provider contracts isolated from UI; all calls must run on a worker thread. */
final class MapServices {
  static boolean configured() {
    return BluetoothPlatform.active() || !BuildConfig.MAPTILER_API_KEY.trim().isEmpty();
  }

  static String styleUrl() {
    if (BluetoothPlatform.active()) return "asset://offline_style.json";
    return "https://api.maptiler.com/maps/streets-v2/style.json?key="
        + encode(BuildConfig.MAPTILER_API_KEY);
  }

  static JSONArray search(String query) {
    if (BluetoothPlatform.active()) return OfflineRoads.search(query);
    if (!configured())
      throw new IllegalStateException("A busca de endereços ainda não está disponível.");
    return request(
            "https://api.maptiler.com/geocoding/"
                + encode(query)
                + ".json?language=pt&limit=5&key="
                + encode(BuildConfig.MAPTILER_API_KEY),
            null,
            null)
        .optJSONArray("features");
  }

  static JSONObject route(
      double originLat, double originLng, double lat, double lng, String profile) {
    if (BluetoothPlatform.active()) return OfflineRoads.graph(profile).route(originLat, originLng, lat, lng);
    if (BuildConfig.ORS_API_KEY.trim().isEmpty())
      throw new IllegalStateException(
          "O cálculo de rotas ainda não está disponível nesta instalação.");
    if (!java.util.Arrays.asList("driving-car", "cycling-regular", "foot-walking")
        .contains(profile)) throw new IllegalArgumentException("Modo de transporte inválido.");
    validPoint(originLat, originLng);
    validPoint(lat, lng);
    JSONObject body = routeBody(originLat, originLng, lat, lng);
    return request(
        "https://api.heigit.org/openrouteservice/v2/directions/" + profile + "/geojson",
        body,
        BuildConfig.ORS_API_KEY);
  }

  static String disposalQuery(double lat, double lng) {
    validPoint(lat, lng);
    String area = String.format(java.util.Locale.ROOT, "(around:20000,%.5f,%.5f)", lat, lng);
    return "[out:json][timeout:20];(nwr" + area
        + "[amenity~\"^(recycling|waste_disposal|waste_transfer_station)$\"];"
        + "nwr" + area + "[\"recycling:drugs\"=yes];"
        + "nwr" + area + "[\"recycling:medicines\"=yes];"
        + "nwr" + area + "[waste~\"(^|;)(drugs|medicines|medications)(;|$)\"];);out center tags;";
  }

  static java.util.List<DisposalPoint> disposalPoints(double lat, double lng) {
    return DisposalPoint.parse(request("https://overpass-api.de/api/interpreter", null, null,
        "data=" + encode(disposalQuery(lat, lng))));
  }

  static JSONObject routeBody(double originLat, double originLng, double lat, double lng) {
    validPoint(originLat, originLng);
    validPoint(lat, lng);
    JSONObject body = new JSONObject();
    try {
      LocalJson.put(
          body,
          "coordinates",
          new JSONArray()
              .put(new JSONArray().put(originLng).put(originLat))
              .put(new JSONArray().put(lng).put(lat)));
    } catch (org.json.JSONException e) {
      throw new IllegalArgumentException("Coordenadas inválidas.");
    }
    LocalJson.put(body, "language", "pt");
    LocalJson.put(body, "instructions", true);
    return body;
  }

  static void validPoint(double lat, double lng) {
    if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180)
      throw new IllegalArgumentException("Coordenadas inválidas.");
  }

  static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    } catch (java.io.UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private static JSONObject request(String url, JSONObject body, String token) {
    return request(url, body, token, null);
  }

  private static JSONObject request(String url, JSONObject body, String token, String form) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(20000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Accept", body == null ? "application/json" : "application/geo+json");
      connection.setRequestProperty("User-Agent", "Coletai/1.4 Android");
      if (token != null) connection.setRequestProperty("Authorization", token);
      if (body != null || form != null) {
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", form == null ? "application/json; charset=utf-8" : "application/x-www-form-urlencoded; charset=utf-8");
        try (java.io.OutputStream output = connection.getOutputStream()) {
          output.write((form == null ? body.toString() : form).getBytes(StandardCharsets.UTF_8));
        }
      }
      int code = connection.getResponseCode();
      if (code == 401 || code == 403)
        throw new IllegalStateException(
            "Serviço de mapas indisponível: verifique a configuração de acesso.");
      if (code == 429)
        throw new IllegalStateException(
            "Limite do serviço de mapas atingido. Tente novamente mais tarde.");
      if (code < 200 || code >= 300)
        throw new IllegalStateException(
            "Não foi possível obter o mapa ou a rota (" + code + "). Tente outro local.");
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
          if (bytes.size() + n > 4 * 1024 * 1024)
            throw new IllegalStateException("Resposta do mapa muito extensa.");
          bytes.write(buffer, 0, n);
        }
        return new JSONObject(bytes.toString("UTF-8"));
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Sem conexão com o serviço de mapas. Verifique a internet e tente novamente.");
    } finally {
      if (connection != null) connection.disconnect();
    }
  }
}
