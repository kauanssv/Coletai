package com.example.coletai;

import static org.maplibre.android.style.layers.PropertyFactory.*;
import static org.maplibre.android.style.expressions.Expression.get;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;

/** Persistent style layers: clearing an order marker or drawing a route never removes these POIs. */
final class DisposalMapLayer implements DefaultLifecycleObserver {
  private static final String SOURCE = "coletai-disposal", ICONS = "coletai-disposal-icons";
  private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
  interface Result { void loaded(DisposalRepository.Result result); }
  private final FragmentActivity owner;
  private final MapLibreMap map;
  private final TextView status;
  private final GeoJsonSource source;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final MapLibreMap.OnCameraIdleListener idle = this::schedule;
  private final MapLibreMap.OnMapClickListener click = this::clicked;
  private final Runnable fetch = this::fetch;
  private List<DisposalPoint> points = new ArrayList<>();
  private String tile = "";
  private long lastRequest;
  private boolean active, disposed;

  static DisposalRepository repository(Context context) {
    return new DisposalRepository(context.getSharedPreferences("disposal_points", Context.MODE_PRIVATE), BundledDisposalPoints.load(context));
  }

  static void nearby(Context context, double lat, double lng, Result callback) {
    Context app = context.getApplicationContext();
    WORK.execute(() -> {
      DisposalRepository.Result result = BluetoothPlatform.active()
          ? repository(app).cached(lat, lng, System.currentTimeMillis())
          : repository(app).nearby(lat, lng, System.currentTimeMillis(), MapServices::disposalPoints);
      new Handler(Looper.getMainLooper()).post(() -> callback.loaded(result));
    });
  }

  DisposalMapLayer(FragmentActivity owner, MapLibreMap map, Style style, TextView status) {
    this.owner = owner; this.map = map; this.status = status;
    source = new GeoJsonSource(SOURCE, "{\"type\":\"FeatureCollection\",\"features\":[]}");
    style.addSource(source);
    for (String category : new String[]{"medicine", "special", "recycling", "waste"})
      style.addImage("disposal-" + category, icon(category));
    style.addLayer(new SymbolLayer(ICONS, SOURCE).withProperties(
        iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(.85f)));
    SymbolLayer labels = new SymbolLayer("coletai-disposal-labels", SOURCE).withProperties(
        textField(get("name")), textSize(11f), textColor("#0B3D37"), textHaloColor("#FFFFFF"),
        textHaloWidth(1.5f), textOffset(new Float[]{0f, 1.6f}), textMaxWidth(12f), textAllowOverlap(false));
    labels.setMinZoom(13);
    style.addLayer(labels);
    map.addOnCameraIdleListener(idle);
    map.addOnMapClickListener(click);
    status.setVisibility(View.VISIBLE);
    status.setText("♻ Pontos de descarte");
    status.setOnClickListener(v -> list());
    show(new DisposalRepository.Result(BundledDisposalPoints.load(owner), BundledDisposalPoints.VERIFIED_AT, false, ""));
    owner.getLifecycle().addObserver(this);
  }

  private Bitmap icon(String category) {
    int size = PlatformUi.dp(owner, 30);
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    canvas.scale(size / 30f, size / 30f);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.WHITE); canvas.drawCircle(15, 15, 14, p);
    p.setColor("medicine".equals(category) ? 0xFF276A9A : "special".equals(category) ? 0xFFB77910 : "recycling".equals(category) ? 0xFF0F7667 : 0xFF536761);
    canvas.drawCircle(15, 15, 12, p);
    p.setColor(Color.WHITE); p.setStrokeWidth(3);
    if ("medicine".equals(category)) {
      canvas.drawLine(15, 9, 15, 21, p); canvas.drawLine(9, 15, 21, 15, p);
    } else if ("recycling".equals(category)) {
      android.graphics.drawable.Drawable icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(owner, R.drawable.ic_recycle).mutate();
      icon.setTint(Color.WHITE); icon.setBounds(7, 7, 23, 23); icon.draw(canvas);
    } else if ("special".equals(category)) {
      canvas.drawRoundRect(10, 10, 20, 22, 2, 2, p); canvas.drawRect(13, 7, 17, 10, p);
    } else {
      canvas.drawRect(10, 12, 20, 21, p); canvas.drawLine(9, 9, 21, 9, p);
    }
    return bitmap;
  }

  private void schedule() {
    handler.removeCallbacks(fetch);
    if (active && !disposed) handler.postDelayed(fetch, 650);
  }

  private void fetch() {
    if (!active || disposed || map.getCameraPosition().zoom < 10) return;
    org.maplibre.android.geometry.LatLng center = map.getCameraPosition().target;
    if (center == null) return;
    String next = DisposalRepository.tile(center.getLatitude(), center.getLongitude());
    long now = System.currentTimeMillis();
    if (next.equals(tile) && now - lastRequest < 60000) return;
    if (!next.equals(tile) && now - lastRequest < 20000) {
      handler.postDelayed(fetch, 20000 - (now - lastRequest));
      return;
    }
    tile = next; lastRequest = now;
    DisposalRepository.Result saved = repository(owner).cached(center.getLatitude(), center.getLongitude(), now);
    if (saved.updated != 0) show(saved);
    if (!saved.stale) return;
    status.setText("♻ Buscando pontos…");
    nearby(owner, center.getLatitude(), center.getLongitude(), result -> {
      if (disposed || !active || !next.equals(tile)) return;
      if (result.updated != 0 || result.error.isEmpty()) show(result);
      if (!result.error.isEmpty()) status.setText(points.isEmpty() ? "♻ Pontos indisponíveis · Tentar" : "♻ Pontos salvos · Sem atualização");
    });
  }

  private void show(DisposalRepository.Result result) {
    points = result.points;
    JSONArray features = new JSONArray();
    for (DisposalPoint point : points) {
      JSONObject feature = new JSONObject(), geometry = new JSONObject(), properties = new JSONObject();
      LocalJson.put(feature, "type", "Feature");
      LocalJson.put(geometry, "type", "Point");
      LocalJson.put(geometry, "coordinates", new JSONArray(java.util.Arrays.asList(point.longitude, point.latitude)));
      LocalJson.put(properties, "id", point.id); LocalJson.put(properties, "name", point.name);
      LocalJson.put(properties, "icon", "disposal-" + point.category);
      LocalJson.put(feature, "geometry", geometry); LocalJson.put(feature, "properties", properties);
      features.put(feature);
    }
    JSONObject collection = new JSONObject();
    LocalJson.put(collection, "type", "FeatureCollection"); LocalJson.put(collection, "features", features);
    source.setGeoJson(collection.toString());
    status.setText("♻ " + points.size() + " pontos de descarte" + (result.stale ? " · Salvos" : ""));
  }

  private boolean clicked(org.maplibre.android.geometry.LatLng location) {
    if (disposed) return false;
    android.graphics.PointF screen = map.getProjection().toScreenLocation(location);
    int hit = PlatformUi.dp(owner, 18);
    List<org.maplibre.geojson.Feature> selected = map.queryRenderedFeatures(
        new android.graphics.RectF(screen.x - hit, screen.y - hit, screen.x + hit, screen.y + hit), ICONS);
    if (selected.isEmpty()) return false;
    String id = selected.get(0).getStringProperty("id");
    for (DisposalPoint point : points) if (point.id.equals(id)) { details(point); return true; }
    return false;
  }

  private void list() {
    if (points.isEmpty()) {
      new MaterialAlertDialogBuilder(owner).setTitle("Pontos de descarte")
          .setMessage("Aproxime o mapa da região desejada para buscar pontos cadastrados. Verde: reciclagem; azul: medicamentos; amarelo: resíduos específicos; cinza: coleta de resíduos. A cobertura depende dos cadastros no OpenStreetMap.")
          .setPositiveButton("Buscar novamente", (d, n) -> { tile = ""; schedule(); })
          .setNegativeButton("Fechar", null).show();
      return;
    }
    List<DisposalPoint> displayed = new ArrayList<>(points);
    org.maplibre.android.geometry.LatLng center = map.getCameraPosition().target;
    if (center != null) displayed.sort(java.util.Comparator.comparingDouble(point -> DisposalPoint.distance(center.getLatitude(), center.getLongitude(), point.latitude, point.longitude)));
    String[] names = new String[displayed.size()];
    for (int i = 0; i < names.length; i++) names[i] = displayed.get(i).name + " · " + DisposalPoint.categoryName(displayed.get(i).category);
    new MaterialAlertDialogBuilder(owner).setTitle("Pontos de descarte · " + names.length)
        .setItems(names, (d, i) -> details(displayed.get(i))).setNegativeButton("Fechar", null).show();
  }

  private void details(DisposalPoint point) {
    if (owner.getSupportFragmentManager().isStateSaved()
        || owner.getSupportFragmentManager().findFragmentByTag(DisposalPointDialog.TAG) != null) return;
    DisposalPointDialog.create(point).show(owner.getSupportFragmentManager(), DisposalPointDialog.TAG);
  }

  void dispose() {
    disposed = true; active = false;
    handler.removeCallbacksAndMessages(null);
    map.removeOnCameraIdleListener(idle); map.removeOnMapClickListener(click);
    owner.getLifecycle().removeObserver(this);
  }
  @Override public void onStart(LifecycleOwner owner) { active = true; schedule(); }
  @Override public void onStop(LifecycleOwner owner) { active = false; handler.removeCallbacks(fetch); }
  @Override public void onDestroy(LifecycleOwner owner) { dispose(); }
}
