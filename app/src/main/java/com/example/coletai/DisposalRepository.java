package com.example.coletai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** A bounded disk cache shared by map screens; cached points survive network failures. */
final class DisposalRepository {
  static final long TTL = 24 * 60 * 60 * 1000L;
  interface Fetch { List<DisposalPoint> load(double lat, double lng); }
  static final class Result {
    final List<DisposalPoint> points;
    final long updated;
    final boolean stale;
    final String error;
    Result(List<DisposalPoint> points, long updated, boolean stale, String error) {
      this.points = points; this.updated = updated; this.stale = stale; this.error = error;
    }
  }
  private final SharedPreferences cache;
  private final List<DisposalPoint> fixed;
  DisposalRepository(SharedPreferences cache) { this(cache, java.util.Collections.emptyList()); }
  DisposalRepository(SharedPreferences cache, List<DisposalPoint> fixed) { this.cache = cache; this.fixed = fixed; }

  private List<DisposalPoint> merged(List<DisposalPoint> online) {
    java.util.Map<String, DisposalPoint> values = new java.util.LinkedHashMap<>();
    for (DisposalPoint point : fixed) values.put(point.id, point);
    for (DisposalPoint point : online) if (!values.containsKey(point.id)) values.put(point.id, point);
    return new ArrayList<>(values.values());
  }

  static String tile(double lat, double lng) {
    MapServices.validPoint(lat, lng);
    return (int) Math.floor(lat * 20) + "_" + (int) Math.floor(lng * 20);
  }

  Result cached(double lat, double lng, long now) {
    JSONObject row = LocalJson.object(cache.getString("tile_" + tile(lat, lng), "{}"));
    long updated = row.optLong("updated");
    boolean installed = !fixed.isEmpty();
    return new Result(merged(DisposalPoint.read(row.optJSONArray("points"))),
        installed ? Math.max(updated, BundledDisposalPoints.VERIFIED_AT) : updated,
        !(installed && BundledDisposalPoints.covers(lat, lng)) && (updated == 0 || now < updated || now - updated > TTL), "");
  }

  synchronized Result nearby(double lat, double lng, long now, Fetch fetch) {
    Result saved = cached(lat, lng, now);
    if (!saved.stale) return saved;
    JSONObject prior = LocalJson.object(cache.getString("tile_" + tile(lat, lng), "{}"));
    if (now < prior.optLong("retry_after")) return new Result(saved.points, saved.updated, true, "Não foi possível atualizar os pontos de descarte.");
    try {
      // Query a stable cell center, so the cached 20 km circle covers 15 km searches within the cell.
      double centerLat = Math.min(90, (Math.floor(lat * 20) + .5) / 20);
      double centerLng = Math.min(180, (Math.floor(lng * 20) + .5) / 20);
      List<DisposalPoint> points = fetch.load(centerLat, centerLng);
      JSONArray values = new JSONArray();
      for (DisposalPoint point : points) values.put(point.json());
      JSONObject row = new JSONObject();
      LocalJson.put(row, "updated", now); LocalJson.put(row, "points", values);
      store(lat, lng, row);
      return new Result(merged(points), now, false, "");
    } catch (RuntimeException error) {
      JSONObject row = new JSONObject();
      LocalJson.put(row, "updated", prior.optLong("updated"));
      LocalJson.put(row, "points", prior.optJSONArray("points") == null ? new JSONArray() : prior.optJSONArray("points"));
      LocalJson.put(row, "retry_after", now + 60000);
      store(lat, lng, row);
      return new Result(saved.points, saved.updated, true, "Não foi possível atualizar os pontos de descarte.");
    }
  }

  private void store(double lat, double lng, JSONObject row) {
    String current = "tile_" + tile(lat, lng);
    SharedPreferences.Editor editor = cache.edit().putString(current, row.toString());
    List<String> keys = new ArrayList<>();
    for (String key : cache.getAll().keySet()) if (key.startsWith("tile_") && !key.equals(current)) keys.add(key);
    keys.sort(Comparator.comparingLong(key -> LocalJson.object(cache.getString(key, "{}")).optLong("updated")));
    while (keys.size() >= 16) editor.remove(keys.remove(0));
    editor.apply();
  }
}
