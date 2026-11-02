package com.example.coletai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real OSM facilities; suitability is based on explicit accepted-material tags. */
final class DisposalPoint {
  final String id, name, category, address, hours, sourceUrl, sourceLabel, notes, verifiedOn;
  final double latitude, longitude;
  final Set<String> materials;
  final boolean publicAccess;
  private static final Map<String, String[]> TAGS = new LinkedHashMap<>();
  static {
    TAGS.put("Papel", new String[]{"paper"});
    TAGS.put("Papelão", new String[]{"cardboard"});
    TAGS.put("Embalagens cartonadas", new String[]{"cartons", "beverage_cartons"});
    TAGS.put("Plástico", new String[]{"plastic"});
    TAGS.put("Embalagens plásticas", new String[]{"plastic_packaging"});
    TAGS.put("Garrafas plásticas", new String[]{"plastic_bottles"});
    TAGS.put("Vidro", new String[]{"glass"});
    TAGS.put("Garrafas de vidro", new String[]{"glass_bottles"});
    TAGS.put("Metal", new String[]{"metal", "scrap_metal"});
    TAGS.put("Alumínio", new String[]{"aluminium"});
    TAGS.put("Latas", new String[]{"cans", "aluminium_cans", "steel_cans"});
    TAGS.put("Óleo de cozinha", new String[]{"cooking_oil", "vegetable_oil"});
    TAGS.put("Eletrônicos", new String[]{"electronics", "electrical_appliances"});
    TAGS.put("Pequenos eletrodomésticos", new String[]{"small_appliances"});
    TAGS.put("Computadores", new String[]{"computers"});
    TAGS.put("Celulares", new String[]{"mobile_phones"});
    TAGS.put("Pilhas e baterias", new String[]{"batteries"});
    TAGS.put("Baterias automotivas", new String[]{"car_batteries"});
    TAGS.put("Medicamentos", new String[]{"drugs", "medicines", "medications"});
    TAGS.put("Lâmpadas", new String[]{"fluorescent_tubes", "light_bulbs"});
    TAGS.put("Pneus", new String[]{"tyres", "tires"});
    TAGS.put("Entulho", new String[]{"concrete", "building_materials", "rubble"});
    TAGS.put("Gesso", new String[]{"plasterboard", "gypsum"});
    TAGS.put("Roupas e tecidos", new String[]{"clothes", "textiles"});
    TAGS.put("Resíduos orgânicos", new String[]{"organic", "green_waste"});
    TAGS.put("Lixo comum", new String[]{"general_waste", "residual_waste", "household_waste"});
  }

  private DisposalPoint(JSONObject row) {
    id = row.optString("id"); name = row.optString("name"); category = row.optString("category");
    address = row.optString("address"); hours = row.optString("hours"); sourceUrl = row.optString("source");
    sourceLabel = row.optString("source_label", "OpenStreetMap");
    notes = row.optString("notes"); verifiedOn = row.optString("verified_on");
    latitude = row.optDouble("latitude", Double.NaN); longitude = row.optDouble("longitude", Double.NaN);
    MapServices.validPoint(latitude, longitude);
    publicAccess = row.optBoolean("public", true);
    materials = new LinkedHashSet<>();
    JSONArray values = row.optJSONArray("materials");
    if (values != null) for (int i = 0; i < values.length(); i++) materials.add(values.optString(i));
  }

  JSONObject json() {
    JSONObject row = new JSONObject();
    LocalJson.put(row, "id", id); LocalJson.put(row, "name", name); LocalJson.put(row, "category", category);
    LocalJson.put(row, "address", address); LocalJson.put(row, "hours", hours); LocalJson.put(row, "source", sourceUrl);
    LocalJson.put(row, "source_label", sourceLabel); LocalJson.put(row, "notes", notes); LocalJson.put(row, "verified_on", verifiedOn);
    LocalJson.put(row, "latitude", latitude); LocalJson.put(row, "longitude", longitude);
    LocalJson.put(row, "public", publicAccess); LocalJson.put(row, "materials", new JSONArray(materials));
    return row;
  }

  static List<DisposalPoint> read(JSONArray rows) {
    List<DisposalPoint> points = new ArrayList<>();
    if (rows != null) for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row == null) continue;
      try { points.add(new DisposalPoint(row)); } catch (IllegalArgumentException ignored) { }
    }
    return points;
  }

  static List<DisposalPoint> parse(JSONObject response) {
    JSONArray elements = response.optJSONArray("elements");
    Map<String, DisposalPoint> unique = new LinkedHashMap<>();
    if (elements == null || response.has("remark")) throw new IllegalStateException("A consulta de pontos não foi concluída. Tente novamente.");
    for (int i = 0; i < elements.length(); i++) {
      JSONObject e = elements.optJSONObject(i);
      if (e == null) continue;
      JSONObject tags = e.optJSONObject("tags"), center = e.optJSONObject("center");
      if (tags == null || "yes".equals(tags.optString("disused")) || "yes".equals(tags.optString("abandoned"))) continue;
      String amenity = tags.optString("amenity"), access = tags.optString("access");
      Set<String> accepts = new LinkedHashSet<>();
      Set<String> waste = new LinkedHashSet<>(Arrays.asList(tags.optString("waste").split(";")));
      for (Map.Entry<String, String[]> material : TAGS.entrySet())
        for (String tag : material.getValue()) {
          if ("yes".equals(tags.optString("recycling:" + tag)) || (waste.contains(tag) && !"no".equals(tags.optString("recycling:" + tag)))) {
            accepts.add(material.getKey()); break;
          }
        }
      if (!Arrays.asList("recycling", "waste_disposal", "waste_transfer_station").contains(amenity) && accepts.isEmpty()) continue;
      String category = accepts.contains("Medicamentos") ? "medicine" :
          accepts.stream().anyMatch(m -> Arrays.asList("Eletrônicos", "Pilhas e baterias", "Baterias automotivas", "Lâmpadas", "Óleo de cozinha", "Pneus").contains(m)) ? "special" :
          "recycling".equals(amenity) ? "recycling" : "waste";
      String type = e.optString("type"), id = type + "/" + e.optLong("id");
      if (!Arrays.asList("node", "way", "relation").contains(type) || e.optLong("id") <= 0) continue;
      double lat = e.optDouble("lat", center == null ? Double.NaN : center.optDouble("lat", Double.NaN));
      double lng = e.optDouble("lon", center == null ? Double.NaN : center.optDouble("lon", Double.NaN));
      try { MapServices.validPoint(lat, lng); } catch (IllegalArgumentException invalid) { continue; }
      JSONObject row = new JSONObject();
      LocalJson.put(row, "id", id);
      String name = tags.optString("name").trim();
      if (name.isEmpty()) name = tags.optString("operator", categoryName(category)).trim();
      LocalJson.put(row, "name", name.isEmpty() ? categoryName(category) : name);
      LocalJson.put(row, "category", category);
      LocalJson.put(row, "materials", new JSONArray(accepts));
      LocalJson.put(row, "latitude", lat);
      LocalJson.put(row, "longitude", lng);
      LocalJson.put(row, "source", "https://www.openstreetmap.org/" + id);
      LocalJson.put(row, "hours", tags.optString("opening_hours"));
      LocalJson.put(row, "public", !Arrays.asList("private", "no", "customers", "permit", "delivery").contains(access));
      LocalJson.put(row, "address", (tags.optString("addr:street") + " " + tags.optString("addr:housenumber") + " " + tags.optString("addr:city")).trim());
      try { unique.put(id, new DisposalPoint(row)); } catch (IllegalArgumentException ignored) { }
    }
    return new ArrayList<>(unique.values());
  }

  static String categoryName(String category) {
    switch (category) {
      case "medicine": return "Descarte de medicamentos";
      case "special": return "Resíduos específicos";
      case "recycling": return "Ponto de reciclagem";
      default: return "Ponto de coleta de resíduos";
    }
  }

  private static String normalized(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
  }

  static String material(String name) {
    String n = normalized(name);
    for (String known : TAGS.keySet()) if (normalized(known).equals(n)) return known;
    if (n.equals("pilhas") || n.equals("baterias")) return "Pilhas e baterias";
    if (n.equals("remedios") || n.equals("medicamento")) return "Medicamentos";
    if (n.equals("oleo")) return "Óleo de cozinha";
    if (n.equals("eletronico")) return "Eletrônicos";
    return "";
  }

  boolean accepts(JSONObject order) {
    JSONArray items = order.optJSONArray("items");
    if (!publicAccess || items == null || items.length() == 0) return false;
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) return false;
      String required = material(item.optString("name"));
      if (materials.contains(required)) continue;
      String broader = required.equals("Garrafas plásticas") || required.equals("Embalagens plásticas") ? "Plástico"
          : required.equals("Garrafas de vidro") ? "Vidro"
          : required.equals("Latas") || required.equals("Alumínio") ? "Metal"
          : Arrays.asList("Pequenos eletrodomésticos", "Computadores", "Celulares").contains(required) ? "Eletrônicos" : "";
      if (broader.isEmpty() || !materials.contains(broader)) return false;
    }
    return true;
  }

  static double distance(double lat, double lng, double otherLat, double otherLng) {
    MapServices.validPoint(lat, lng); MapServices.validPoint(otherLat, otherLng);
    double dLat = Math.toRadians(otherLat - lat), dLng = Math.toRadians(otherLng - lng);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(otherLat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 6371000 * 2 * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
  }

  static DisposalPoint nearest(List<DisposalPoint> points, JSONObject order, double lat, double lng) {
    MapServices.validPoint(lat, lng);
    DisposalPoint selected = null; double minimum = Double.POSITIVE_INFINITY;
    for (DisposalPoint point : points) {
      if (!point.accepts(order)) continue;
      double distance = distance(lat, lng, point.latitude, point.longitude);
      if (distance > 15000) continue;
      if (distance < minimum || (distance == minimum && selected != null && point.id.compareTo(selected.id) < 0)) { selected = point; minimum = distance; }
    }
    return selected;
  }
}
