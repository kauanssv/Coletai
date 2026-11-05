package com.example.coletai;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Address suggestions; invoke network and geocoding methods off the main thread. */
final class AddressLookup {
  static JSONObject postalResult(JSONObject response) {
    if (response.optBoolean("erro")) throw new IllegalArgumentException("CEP não encontrado. Confira os números.");
    JSONObject address = new JSONObject();
    String[][] keys = {{"zip", "cep"}, {"street", "logradouro"}, {"district", "bairro"},
        {"city", "localidade"}, {"state", "uf"}};
    for (String[] key : keys) LocalJson.put(address, key[0], response.optString(key[1]).trim());
    LocalJson.put(address, "zip", address.optString("zip").replaceAll("\\D", ""));
    if (!address.optString("zip").matches("[0-9]{8}") || address.optString("city").isEmpty() || stateCode(address.optString("state")).isEmpty())
      throw new IllegalArgumentException("Não foi possível identificar o endereço deste CEP.");
    LocalJson.put(address, "state", stateCode(address.optString("state")));
    return address;
  }

  static JSONObject postal(String zip) {
    if (!zip.matches("[0-9]{8}")) throw new IllegalArgumentException("Informe um CEP com 8 dígitos.");
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL("https://viacep.com.br/ws/" + zip + "/json/").openConnection();
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(10000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("Accept", "application/json");
      if (connection.getResponseCode() != 200) throw new java.io.IOException();
      try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[2048];
        int count;
        while ((count = input.read(buffer)) != -1) {
          if (bytes.size() + count > 32768) throw new java.io.IOException();
          bytes.write(buffer, 0, count);
        }
        return postalResult(new JSONObject(bytes.toString("UTF-8")));
      }
    } catch (IllegalArgumentException error) { throw error; }
    catch (Exception error) { throw new IllegalStateException("Não foi possível consultar o CEP. Verifique a internet e tente novamente."); }
    finally { if (connection != null) connection.disconnect(); }
  }

  @SuppressWarnings("deprecation")
  static JSONObject reverse(Context context, double lat, double lng) {
    MapServices.validPoint(lat, lng);
    try {
      if (!Geocoder.isPresent()) throw new java.io.IOException();
      List<Address> matches = new Geocoder(context, Locale.forLanguageTag("pt-BR")).getFromLocation(lat, lng, 1);
      if (matches == null || matches.isEmpty()) throw new java.io.IOException();
      Address match = matches.get(0);
      if (match.getCountryCode() != null && !"BR".equalsIgnoreCase(match.getCountryCode()))
        throw new IllegalArgumentException("Selecione um endereço no Brasil.");
      JSONObject result = new JSONObject();
      LocalJson.put(result, "street", clean(match.getThoroughfare()));
      LocalJson.put(result, "number", clean(match.getSubThoroughfare()));
      LocalJson.put(result, "district", clean(match.getSubLocality()));
      LocalJson.put(result, "city", clean(match.getLocality()).isEmpty() ? clean(match.getSubAdminArea()) : clean(match.getLocality()));
      LocalJson.put(result, "state", stateCode(clean(match.getAdminArea())));
      LocalJson.put(result, "zip", clean(match.getPostalCode()).replaceAll("\\D", ""));
      // A postal lookup can supply missing district/city data without replacing the selected street or number.
      if (result.optString("zip").matches("[0-9]{8}")) {
        try {
          JSONObject postal = postal(result.optString("zip"));
          for (String key : new String[]{"street", "district", "city", "state"})
            if (result.optString(key).isEmpty()) LocalJson.put(result, key, postal.optString(key));
        } catch (RuntimeException ignored) { }
      }
      return result;
    } catch (IllegalArgumentException error) { throw error; }
    catch (Exception error) { throw new IllegalStateException("Local marcado. Não foi possível buscar o endereço. Verifique a internet ou informe o CEP."); }
  }

  private static String clean(String text) { return text == null ? "" : text.trim(); }

  static String stateCode(String name) {
    String normalized = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    String[] names = {"ACRE", "ALAGOAS", "AMAPA", "AMAZONAS", "BAHIA", "CEARA", "DISTRITO FEDERAL", "ESPIRITO SANTO", "GOIAS", "MARANHAO", "MATO GROSSO", "MATO GROSSO DO SUL", "MINAS GERAIS", "PARA", "PARAIBA", "PARANA", "PERNAMBUCO", "PIAUI", "RIO DE JANEIRO", "RIO GRANDE DO NORTE", "RIO GRANDE DO SUL", "RONDONIA", "RORAIMA", "SANTA CATARINA", "SAO PAULO", "SERGIPE", "TOCANTINS"};
    String[] codes = {"AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"};
    for (int i = 0; i < codes.length; i++) if (codes[i].equals(normalized) || names[i].equals(normalized)) return codes[i];
    return "";
  }
}
