package com.example.coletai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Full road geometry returned by the routing service, in its original order. */
final class StreetRoute {
  static final class Point {
    final double latitude, longitude;
    Point(double latitude, double longitude) { this.latitude = latitude; this.longitude = longitude; }
  }
  final List<Point> points;
  final double meters, seconds;

  private StreetRoute(List<Point> points, double meters, double seconds) {
    this.points = Collections.unmodifiableList(points);
    this.meters = meters;
    this.seconds = seconds;
  }

  static StreetRoute parse(JSONObject response) {
    try {
      JSONObject feature = response.getJSONArray("features").getJSONObject(0);
      JSONObject geometry = feature.getJSONObject("geometry");
      if (!"LineString".equals(geometry.getString("type"))) throw new IllegalArgumentException();
      JSONArray coordinates = geometry.getJSONArray("coordinates");
      List<Point> points = new ArrayList<>();
      for (int i = 0; i < coordinates.length(); i++) {
        JSONArray pair = coordinates.getJSONArray(i);
        double lng = pair.getDouble(0), lat = pair.getDouble(1);
        MapServices.validPoint(lat, lng);
        points.add(new Point(lat, lng));
      }
      JSONObject summary = feature.getJSONObject("properties").getJSONObject("summary");
      double meters = summary.getDouble("distance"), seconds = summary.getDouble("duration");
      if (points.size() < 2 || !Double.isFinite(meters) || !Double.isFinite(seconds) || meters < 0 || seconds < 0)
        throw new IllegalArgumentException();
      return new StreetRoute(points, meters, seconds);
    } catch (org.json.JSONException | IllegalArgumentException | NullPointerException error) {
      throw new IllegalArgumentException("Não foi possível traçar a rota pelas ruas.");
    }
  }
}
