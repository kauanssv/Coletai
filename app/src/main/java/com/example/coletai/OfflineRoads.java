package com.example.coletai;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.*;

/** Bundled OSM extract: offline street search and road-graph routing for the presentation area. */
final class OfflineRoads {
  private static Context context;
  private static JSONObject data;
  private static final Map<String, OfflineRoads> graphs = new HashMap<>();
  private final Map<Long, Node> nodes = new HashMap<>();
  private Node connectedArea;
  static void initialize(Context value) { context = value.getApplicationContext(); }
  static synchronized JSONObject data() {
    if (data == null) {
      if (context == null) throw new IllegalStateException("Mapa offline indisponível");
      try (InputStream input = context.getAssets().open("offline_streets_sao_paulo.geojson"); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] bytes = new byte[8192]; int count;
        while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        data = LocalJson.object(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
      } catch (IOException error) { throw new IllegalStateException("Não foi possível abrir o mapa offline", error); }
    }
    return data;
  }
  static synchronized OfflineRoads graph(String profile) {
    OfflineRoads result = graphs.get(profile);
    if (result == null) { graphs.clear(); result = new OfflineRoads(data(), profile); graphs.put(profile, result); }
    return result;
  }
  private static String normalized(String value) { return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }
  static JSONArray search(String query) {
    String needle = normalized(query); JSONArray result = new JSONArray(); Set<String> seen = new HashSet<>();
    for (DisposalPoint point : BundledDisposalPoints.load(context)) {
      String label = point.name + " · " + point.address;
      if (normalized(label).contains(needle)) result.put(feature(label, point.latitude, point.longitude));
      if (result.length() >= 10) return result;
    }
    JSONArray ways = data().optJSONArray("features");
    for (int i = 0; i < ways.length() && result.length() < 10; i++) {
      JSONObject way = ways.optJSONObject(i), tags = way.optJSONObject("properties"); String name = tags.optString("name");
      if (name.isEmpty() || !normalized(name).contains(needle) || !seen.add(name)) continue;
      JSONArray points = way.optJSONObject("geometry").optJSONArray("coordinates"), middle = points.optJSONArray(points.length() / 2);
      result.put(feature(name + " · São Paulo · mapa offline", middle.optDouble(1), middle.optDouble(0)));
    }
    return result;
  }
  private static JSONObject feature(String label, double lat, double lng) {
    JSONObject result = new JSONObject(); LocalJson.put(result, "place_name", label);
    LocalJson.put(result, "center", new JSONArray(Arrays.asList(lng, lat))); return result;
  }
  static final class Node {
    final long id; final double lat, lng; final List<Edge> edges = new ArrayList<>();
    Node parent = this; int incoming;
    Node(long id, double lat, double lng) { this.id = id; this.lat = lat; this.lng = lng; }
  }
  static final class Edge {
    final Node to; final double meters, seconds;
    Edge(Node to, double meters, double speed) { this.to = to; this.meters = meters; seconds = meters / speed; }
  }
  static final class Visit {
    final Node node; final double score, cost;
    Visit(Node node, double score, double cost) { this.node = node; this.score = score; this.cost = cost; }
  }
  OfflineRoads(JSONObject source, String profile) {
    if (!Arrays.asList("driving-car", "cycling-regular", "foot-walking").contains(profile)) throw new IllegalArgumentException("Transporte inválido");
    JSONArray features = source.optJSONArray("features");
    boolean car = "driving-car".equals(profile), foot = "foot-walking".equals(profile);
    for (int i = 0; i < features.length(); i++) {
      JSONObject feature = features.optJSONObject(i), tags = feature.optJSONObject("properties");
      String type = tags.optString("highway"), access = tags.optString("access");
      if (Arrays.asList("no", "private").contains(access)) continue;
      String permission = tags.optString(car ? "motor_vehicle" : foot ? "foot" : "bicycle", tags.optString(car ? "vehicle" : "access"));
      if (Arrays.asList("no", "private").contains(permission)) continue;
      if (car && Arrays.asList("footway", "pedestrian", "path", "steps", "cycleway", "bridleway", "corridor").contains(type)) continue;
      if (!car && Arrays.asList("motorway", "motorway_link", "trunk", "trunk_link").contains(type)) continue;
      JSONArray ids = tags.optJSONArray("nodes"), coordinates = feature.optJSONObject("geometry").optJSONArray("coordinates");
      if (ids == null || ids.length() != coordinates.length()) continue;
      String oneway = tags.optString("oneway", "roundabout".equals(tags.optString("junction")) ? "yes" : "no");
      boolean directed = !foot && (car || !"no".equals(tags.optString("oneway:bicycle"))) && Arrays.asList("yes", "1", "true", "-1").contains(oneway);
      double speed = foot ? 1.3 : car ? 8.3 : 4.2;
      Node previous = null;
      for (int p = 0; p < ids.length(); p++) {
        long id = ids.optLong(p); JSONArray coordinate = coordinates.optJSONArray(p);
        Node node = nodes.get(id);
        if (node == null) { node = new Node(id, coordinate.optDouble(1), coordinate.optDouble(0)); nodes.put(id, node); }
        if (previous != null) {
          root(node).parent = root(previous);
          double meters = meters(previous.lat, previous.lng, node.lat, node.lng);
          if (!directed || !"-1".equals(oneway)) { previous.edges.add(new Edge(node, meters, speed)); node.incoming++; }
          if (!directed || "-1".equals(oneway)) { node.edges.add(new Edge(previous, meters, speed)); previous.incoming++; }
        }
        previous = node;
      }
    }
    Map<Node, Integer> sizes = new HashMap<>(); int largest = 0;
    for (Node node : nodes.values()) {
      Node root = root(node); int size = sizes.getOrDefault(root, 0) + 1; sizes.put(root, size);
      if (size > largest) { largest = size; connectedArea = root; }
    }
  }
  private static Node root(Node node) {
    Node current = node;
    while (current.parent != current) current = current.parent;
    while (node.parent != node) { Node next = node.parent; node.parent = current; node = next; }
    return current;
  }
  private Node nearest(double lat, double lng, boolean origin) {
    Node nearest = null; double best = 250;
    for (Node node : nodes.values()) {
      if (root(node) != connectedArea || (origin ? node.edges.isEmpty() : node.incoming == 0)) continue;
      double distance = meters(lat, lng, node.lat, node.lng);
      if (distance < best) { best = distance; nearest = node; }
    }
    if (nearest == null) throw new IllegalArgumentException("Este local está fora do mapa offline. Escolha um ponto na região Carrão/Tatuapé de São Paulo.");
    return nearest;
  }
  JSONObject route(double startLat, double startLng, double endLat, double endLng) {
    MapServices.validPoint(startLat, startLng); MapServices.validPoint(endLat, endLng);
    Node start = nearest(startLat, startLng, true), end = nearest(endLat, endLng, false);
    Map<Long, Double> costs = new HashMap<>(); Map<Long, Node> previous = new HashMap<>(); Map<Long, Edge> incoming = new HashMap<>();
    PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(v -> v.score));
    costs.put(start.id, 0.0); queue.add(new Visit(start, 0, 0));
    while (!queue.isEmpty()) {
      Visit current = queue.remove();
      if (current.cost > costs.getOrDefault(current.node.id, Double.POSITIVE_INFINITY)) continue;
      if (current.node == end) break;
      for (Edge edge : current.node.edges) {
        double next = current.cost + edge.meters;
        if (next < costs.getOrDefault(edge.to.id, Double.POSITIVE_INFINITY)) {
          costs.put(edge.to.id, next); previous.put(edge.to.id, current.node); incoming.put(edge.to.id, edge);
          queue.add(new Visit(edge.to, next + meters(edge.to.lat, edge.to.lng, end.lat, end.lng), next));
        }
      }
    }
    if (!costs.containsKey(end.id)) throw new IllegalArgumentException("Não há ligação entre estes locais no mapa offline. Escolha outro ponto próximo.");
    List<Node> route = new ArrayList<>(); double seconds = 0;
    for (Node node = end; node != null; node = previous.get(node.id)) { route.add(node); Edge edge = incoming.get(node.id); if (edge != null) seconds += edge.seconds; }
    Collections.reverse(route); JSONArray points = new JSONArray(); points.put(new JSONArray(Arrays.asList(startLng, startLat)));
    for (Node node : route) points.put(new JSONArray(Arrays.asList(node.lng, node.lat)));
    points.put(new JSONArray(Arrays.asList(endLng, endLat)));
    JSONObject summary = new JSONObject(); LocalJson.put(summary, "distance", costs.get(end.id) + meters(startLat, startLng, start.lat, start.lng) + meters(endLat, endLng, end.lat, end.lng));
    LocalJson.put(summary, "duration", seconds);
    JSONObject geometry = LocalJson.put(LocalJson.put(new JSONObject(), "type", "LineString"), "coordinates", points);
    JSONObject feature = LocalJson.put(LocalJson.put(new JSONObject(), "geometry", geometry), "properties", LocalJson.put(new JSONObject(), "summary", summary));
    return LocalJson.put(LocalJson.put(new JSONObject(), "features", new JSONArray().put(feature)), "offline", true);
  }
  static double meters(double a, double b, double c, double d) {
    double lat = Math.toRadians(c - a), lng = Math.toRadians(d - b);
    double h = Math.sin(lat / 2) * Math.sin(lat / 2) + Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c)) * Math.sin(lng / 2) * Math.sin(lng / 2);
    return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
  }
}
