package com.example.coletai;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reviewed real facilities shipped with the APK, available before any network request. */
final class BundledDisposalPoints {
  static final String ASSET = "disposal_points_sao_paulo.json";
  static final long VERIFIED_AT = 1793404800000L; // 2026-10-31 UTC
  private static volatile List<DisposalPoint> loaded;

  static List<DisposalPoint> load(Context context) {
    if (loaded != null) return loaded;
    synchronized (BundledDisposalPoints.class) {
      if (loaded == null) {
        try (InputStream input = context.getAssets().open(ASSET);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[8192]; int count;
          while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > 1024 * 1024) throw new IOException("Catálogo de descarte muito extenso");
            bytes.write(buffer, 0, count);
          }
          loaded = parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException("Não foi possível abrir os pontos de descarte instalados", e); }
      }
      return loaded;
    }
  }

  static List<DisposalPoint> parse(String json) {
    JSONObject catalogue = LocalJson.object(json);
    JSONArray rows = catalogue.optJSONArray("points");
    List<DisposalPoint> points = DisposalPoint.read(rows);
    if (rows == null || rows.length() == 0 || rows.length() != points.size())
      throw new IllegalArgumentException("Catálogo de descarte inválido");
    java.util.Set<String> ids = new java.util.HashSet<>();
    for (DisposalPoint point : points)
      if (point.id.isEmpty() || point.name.trim().isEmpty() || point.address.trim().isEmpty()
          || !point.sourceUrl.startsWith("https://") || !ids.add(point.id))
        throw new IllegalArgumentException("Ponto de descarte sem identificação ou fonte");
    return Collections.unmodifiableList(points);
  }

  static boolean covers(double lat, double lng) {
    // São Paulo and the immediate surroundings covered by the installed catalogue.
    return lat >= -24.1 && lat <= -23.3 && lng >= -46.95 && lng <= -46.2;
  }
}
