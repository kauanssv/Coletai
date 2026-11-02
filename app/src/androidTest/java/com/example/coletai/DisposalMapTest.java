package com.example.coletai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.sources.GeoJsonSource;

/** Emulator-only fixtures; no invented facility is written to a user's phone. */
@RunWith(AndroidJUnit4.class)
public class DisposalMapTest {
  @SuppressWarnings("unchecked")
  private void restore(SharedPreferences prefs, Map<String, ?> backup) {
    SharedPreferences.Editor editor = prefs.edit().clear();
    for (Map.Entry<String, ?> entry : backup.entrySet()) {
      String key = entry.getKey(); Object value = entry.getValue();
      if (value instanceof String) editor.putString(key, (String) value);
      else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
      else if (value instanceof Integer) editor.putInt(key, (Integer) value);
      else if (value instanceof Long) editor.putLong(key, (Long) value);
      else if (value instanceof Float) editor.putFloat(key, (Float) value);
      else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }
    assertTrue(editor.commit());
  }

  private void await(CountDownLatch latch) {
    try { assertTrue("Map/GPS did not become ready within 25 seconds", latch.await(25, TimeUnit.SECONDS)); }
    catch (InterruptedException e) { throw new AssertionError(e); }
  }

  @Test public void installedCatalogueRendersWithAnEmptyNearbyCache() {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured() && MapServices.configured());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context), cache = context.getSharedPreferences("disposal_points", Context.MODE_PRIVATE);
    Map<String, ?> oldPrefs = prefs.getAll(), oldDevice = device.getAll(), oldCache = cache.getAll();
    ActivityScenario<WorkspaceActivity> scenario = null;
    try {
      prefs.edit().clear().commit(); cache.edit().clear().commit();
      device.edit().putBoolean("automatic", false).putLong("latitude", Double.doubleToLongBits(-23.538)).putLong("longitude", Double.doubleToLongBits(-46.56)).commit();
      new ProfileRepository(prefs);
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      CountDownLatch rendered = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      java.util.concurrent.atomic.AtomicBoolean framed = new java.util.concurrent.atomic.AtomicBoolean();
      scenario = ActivityScenario.launch(WorkspaceActivity.class);
      scenario.onActivity(activity -> {
        assertEquals(135, BundledDisposalPoints.load(activity).size());
        assertFalse(DisposalMapLayer.repository(activity).cached(-23.538, -46.56, System.currentTimeMillis()).stale);
        CollectionMapView container = (CollectionMapView) ((android.widget.FrameLayout) activity.findViewById(R.id.workMapContainer)).getChildAt(0);
        ((MapView) container.getChildAt(0)).getMapAsync(map -> {
          Handler handler = new Handler(Looper.getMainLooper());
          long deadline = android.os.SystemClock.elapsedRealtime() + 23000;
          handler.post(new Runnable() {
            @Override public void run() {
              try {
                org.maplibre.android.maps.Style style = map.getStyle();
                GeoJsonSource source = style == null ? null : style.getSourceAs("coletai-disposal");
                if (source != null && framed.compareAndSet(false, true)) map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(-23.538, -46.56), 13));
                if (source != null && !source.querySourceFeatures(null).isEmpty()
                    && !map.queryRenderedFeatures(new android.graphics.RectF(0, 0, container.getWidth(), container.getHeight()), "coletai-disposal-icons").isEmpty()) {
                  assertNotNull(style.getLayer("coletai-disposal-icons"));
                  rendered.countDown();
                } else if (android.os.SystemClock.elapsedRealtime() < deadline) handler.postDelayed(this, 150);
                else {
                  android.graphics.RectF viewport = new android.graphics.RectF(0, 0, container.getWidth(), container.getHeight());
                  String details = "Installed markers never rendered; camera=" + map.getCameraPosition()
                      + "; viewport=" + viewport + "; source=" + (source == null ? -1 : source.querySourceFeatures(null).size())
                      + "; features=" + map.queryRenderedFeatures(viewport).size()
                      + "; icon=" + (style == null ? null : style.getImage("disposal-recycling"))
                      + "; Badra screen=" + map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(-23.537149, -46.547398));
                  failure.set(new AssertionError(details)); rendered.countDown();
                }
              } catch (Throwable error) { failure.set(error); rendered.countDown(); }
            }
          });
        });
      });
      await(rendered);
      if (failure.get() != null) throw new AssertionError(failure.get());
      assertTrue("São Paulo points must render without querying Overpass", cache.getAll().isEmpty());
      android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null), "fixed-disposal-points.png"))) {
        if (image != null) image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
      } catch (java.io.IOException error) { throw new AssertionError(error); }
    } finally {
      if (scenario != null) scenario.close();
      restore(prefs, oldPrefs); restore(device, oldDevice); restore(cache, oldCache);
    }
  }

  @Test public void referencesSurviveClearAndNearestUsesCollectorGps() {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured() && MapServices.configured());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context), cache = context.getSharedPreferences("disposal_points", Context.MODE_PRIVATE);
    Map<String, ?> oldPrefs = prefs.getAll(), oldDevice = device.getAll(), oldCache = cache.getAll();
    ActivityScenario<?> scenario = null;
    try {
      prefs.edit().clear().commit(); cache.edit().clear().commit();
      device.edit().putBoolean("automatic", false).putLong("latitude", Double.doubleToLongBits(-23.54)).putLong("longitude", Double.doubleToLongBits(-46.63)).commit();
      java.util.List<DisposalPoint> fixtures = DisposalPoint.parse(LocalJson.object("{\"elements\":[{\"type\":\"node\",\"id\":900001,\"lat\":-23.54,\"lon\":-46.63,\"tags\":{\"amenity\":\"recycling\",\"name\":\"TESTE perto do cliente\",\"recycling:paper\":\"yes\"}},{\"type\":\"node\",\"id\":900002,\"lat\":-23.56,\"lon\":-46.64,\"tags\":{\"amenity\":\"recycling\",\"name\":\"TESTE perto do coletor\",\"recycling:paper\":\"yes\"}}]}"));
      for (double lat : new double[]{-23.58, -23.54, -23.50})
        for (double lng : new double[]{-46.67, -46.63, -46.59})
          new DisposalRepository(cache).nearby(lat, lng, System.currentTimeMillis(), (a, b) -> fixtures);
      new ProfileRepository(prefs);
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      CountDownLatch references = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      ActivityScenario<WorkspaceActivity> home = ActivityScenario.launch(WorkspaceActivity.class);
      scenario = home;
      home.onActivity(activity -> {
        CollectionMapView container = (CollectionMapView) ((android.widget.FrameLayout) activity.findViewById(R.id.workMapContainer)).getChildAt(0);
        ((MapView) container.getChildAt(0)).getMapAsync(map -> {
          Handler handler = new Handler(Looper.getMainLooper());
          long deadline = android.os.SystemClock.elapsedRealtime() + 23000;
          handler.post(new Runnable() {
            @Override public void run() {
              try {
                org.maplibre.android.maps.Style style = map.getStyle();
                GeoJsonSource source = style == null ? null : style.getSourceAs("coletai-disposal");
                if (source != null && !source.querySourceFeatures(null).isEmpty()) {
                  map.clear();
                  assertNotNull(style.getLayer("coletai-disposal-icons"));
                  assertFalse(source.querySourceFeatures(null).isEmpty());
                  references.countDown();
                } else if (android.os.SystemClock.elapsedRealtime() < deadline) handler.postDelayed(this, 150);
                else { failure.set(new AssertionError("Reference layer never loaded")); references.countDown(); }
              } catch (Throwable error) { failure.set(error); references.countDown(); }
            }
          });
        });
      });
      await(references);
      if (failure.get() != null) throw new AssertionError(failure.get());
      scenario.close(); scenario = null;

      CollectionRepository repo = new CollectionRepository(prefs);
      JSONObject address = LocalJson.object("{\"nickname\":\"Casa teste\",\"street\":\"Rua Teste\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"São Paulo\",\"state\":\"SP\",\"zip\":\"01001000\",\"latitude\":-23.54,\"longitude\":-46.63}");
      repo.saveAddress(address);
      JSONObject draft = LocalJson.object("{\"desired_date\":\"2099-01-02\",\"items\":[{\"material_id\":\"m1\",\"weight\":\"2.00\"}]}");
      LocalJson.put(draft, "address_id", repo.addresses().get(0).optString("id"));
      repo.saveOrder(draft); String id = repo.orders().get(0).optString("id");
      new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
      repo.transition(id, "accept", repo.order(id).optInt("revision"));
      repo.propose(id, repo.order(id).optInt("revision"), "10", "Teste");
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      repo.transition(id, "approve", repo.order(id).optInt("revision"));
      new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
      android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
      automation.grantRuntimePermission(context.getPackageName(), android.Manifest.permission.ACCESS_COARSE_LOCATION);
      automation.grantRuntimePermission(context.getPackageName(), android.Manifest.permission.ACCESS_FINE_LOCATION);
      ActivityScenario<OrderFlowActivity> flow = ActivityScenario.launch(new Intent(context, OrderFlowActivity.class).putExtra("request_id", id));
      scenario = flow;
      onView(withId(R.id.orderFlowPrimary)).perform(click());
      CountDownLatch chosen = new CountDownLatch(1);
      flow.onActivity(activity -> {
        Handler handler = new Handler(Looper.getMainLooper());
        long deadline = android.os.SystemClock.elapsedRealtime() + 23000;
        handler.post(new Runnable() {
          @Override public void run() {
            JSONObject destination = LocalJson.object(prefs.getString("disposal_ambulante_" + id, "{}"));
            if ("node/900002".equals(destination.optString("id"))) chosen.countDown();
            else if (android.os.SystemClock.elapsedRealtime() < deadline) handler.postDelayed(this, 150);
            else chosen.countDown();
          }
        });
      });
      await(chosen);
      JSONObject destination = LocalJson.object(prefs.getString("disposal_ambulante_" + id, "{}"));
      assertEquals("Nearest point must use the emulator GPS (-23.56,-46.64), not the manual/customer address", "node/900002", destination.optString("id"));
      assertTrue(destination.optBoolean("automatic"));
    } finally {
      if (scenario != null) scenario.close();
      restore(prefs, oldPrefs); restore(device, oldDevice); restore(cache, oldCache);
    }
  }
}
