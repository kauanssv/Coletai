package com.example.coletai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.*;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Android gateway/service/map with the host transport; remote radio requires a second phone. */
@RunWith(AndroidJUnit4.class)
public class BluetoothOfflineFlowTest {
  @Test public void gatewayOfflineExercitaPedidosPerfisAdminERuasSemInternet() throws Exception {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    BluetoothPlatform platform = BluetoothPlatform.get(context);
    assumeTrue(!BluetoothPlatform.active());
    assumeTrue(platform.supported() && platform.enabled());
    android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    if (Build.VERSION.SDK_INT >= 31) automation.grantRuntimePermission(context.getPackageName(), android.Manifest.permission.BLUETOOTH_CONNECT);
    automation.grantRuntimePermission(context.getPackageName(), android.Manifest.permission.ACCESS_FINE_LOCATION);
    automation.grantRuntimePermission(context.getPackageName(), android.Manifest.permission.ACCESS_COARSE_LOCATION);
    SharedPreferences db = context.getSharedPreferences("coletai_bluetooth_host", 0), cache = context.getSharedPreferences("coletai_bluetooth", 0), device = DeviceLocation.settings(context);
    SnapshotPreferences dbBackup = new SnapshotPreferences(db), cacheBackup = new SnapshotPreferences(cache), deviceBackup = new SnapshotPreferences(device);
    androidx.test.espresso.IdlingResource tasks = new androidx.test.espresso.IdlingResource() {
      public String getName() { return "Bluetooth offline operations"; }
      public boolean isIdleNow() { return PlatformTasks.idle(); }
      public void registerIdleTransitionCallback(ResourceCallback callback) { PlatformTasks.idleListener = callback::onTransitionToIdle; }
    };
    ActivityScenario<?> scenario = null;
    try {
      db.edit().clear().commit(); cache.edit().clear().commit();
      device.edit().putBoolean("automatic", false).putLong("latitude", Double.doubleToLongBits(-23.538)).putLong("longitude", Double.doubleToLongBits(-46.56)).commit();
      // FGS startup follows a visible activity, just like the presentation setup screen.
      scenario = ActivityScenario.launch(OfflineBluetoothActivity.class);
      scenario.onActivity(activity -> platform.host());
      androidx.test.espresso.IdlingRegistry.getInstance().register(tasks);
      assertTrue(PlatformGateway.configured());
      assertEquals("asset://offline_style.json", MapServices.styleUrl());
      SharedPreferences prefs = PlatformStore.open(context);
      login(prefs, "cliente");
      CollectionRepository repo = new CollectionRepository(prefs);
      JSONObject draft = LocalJson.object("{\"desired_date\":\"2099-01-02\",\"current_location\":{\"latitude\":-23.538,\"longitude\":-46.56},\"description\":\"Coleta pelo Bluetooth\",\"items\":[{\"material_id\":\"m1\",\"weight\":\"2.50\"}]}");
      repo.saveOrder(draft); String id = repo.orders().get(0).optString("id");
      scenario.close(); scenario = null;
      login(prefs, "empresa");
      scenario = ActivityScenario.launch(new Intent(context, OrderFlowActivity.class).putExtra("request_id", id));
      onView(withId(R.id.btnEnviarProposta)).perform(click());
      onView(withId(R.id.edtPreco)).perform(replaceText("30,50"), closeSoftKeyboard());
      onView(withId(R.id.edtMensagem)).perform(replaceText("Proposta offline"), closeSoftKeyboard());
      onView(withId(R.id.btnEnviarProposta)).perform(click());
      assertEquals("proposta", repo.order(id).optString("status"));
      scenario.close(); scenario = null;
      login(prefs, "cliente");
      scenario = ActivityScenario.launch(new Intent(context, OrderFlowActivity.class).putExtra("request_id", id));
      onView(withId(R.id.orderFlowPrimary)).perform(click());
      onView(withText("Confirmar")).inRoot(isDialog()).perform(click());
      assertEquals("aceita", repo.order(id).optString("status"));
      scenario.close(); scenario = null;
      login(prefs, "empresa");
      device.edit().putLong("latitude", Double.doubleToLongBits(-23.542)).putLong("longitude", Double.doubleToLongBits(-46.57)).commit();
      scenario = ActivityScenario.launch(new Intent(context, OrderFlowActivity.class).putExtra("request_id", id));
      onView(withId(R.id.orderFlowPrimary)).perform(click());
      java.util.concurrent.CountDownLatch manualRoute = new java.util.concurrent.CountDownLatch(1);
      scenario.onActivity(activity -> {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(new Runnable() {
          int attempts;
          public void run() {
            android.widget.TextView info = activity.findViewById(R.id.orderFlowRouteInfo);
            if (info != null && info.getText().toString().contains("Offline")) manualRoute.countDown();
            else if (++attempts < 100) handler.postDelayed(this, 200);
          }
        });
      });
      assertTrue("Presentation route must use the chosen manual origin without GPS", manualRoute.await(25, java.util.concurrent.TimeUnit.SECONDS));
      scenario.close(); scenario = null;
      for (String step : new String[]{"arrive", "collect", "complete"}) repo.transition(id, step, repo.order(id).optInt("revision"));
      login(prefs, "cliente"); repo.review(id, 5, "Funcionou offline");
      login(prefs, "admin");
      assertTrue(new AuditRepository(prefs).list().size() >= 8);
      scenario = ActivityScenario.launch(AdminActivity.class);
      onView(withId(R.id.adminBluetooth)).check(matches(isDisplayed()));
      scenario.close(); scenario = null;
      // Native style source must load the bundled roads, with no MapTiler/glyph dependency.
      login(prefs, "ambulante");
      scenario = ActivityScenario.launch(WorkspaceActivity.class);
      java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
      scenario.onActivity(activity -> {
        android.widget.FrameLayout holder = activity.findViewById(R.id.workMapContainer);
        CollectionMapView map = (CollectionMapView) holder.getChildAt(0);
        org.maplibre.android.maps.MapView nativeMap = (org.maplibre.android.maps.MapView) map.getChildAt(0);
        nativeMap.getMapAsync(value -> {
          android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
          handler.post(new Runnable() {
            int attempts;
            public void run() {
              org.maplibre.android.maps.Style style = value.getStyle();
              org.maplibre.android.style.sources.GeoJsonSource roads = style == null ? null : style.getSourceAs("streets");
              if (roads != null && roads.querySourceFeatures(null).size() > 100) loaded.countDown();
              else if (++attempts < 100) handler.postDelayed(this, 200);
            }
          });
        });
      });
      assertTrue("Bundled street map must render offline", loaded.await(25, java.util.concurrent.TimeUnit.SECONDS));
      assertTrue(MapServices.search("Tatuapé").length() > 0);
      assertTrue(StreetRoute.parse(MapServices.route(-23.538, -46.56, -23.542, -46.57, "driving-car")).meters > 100);
    } finally {
      if (scenario != null) scenario.close();
      androidx.test.espresso.IdlingRegistry.getInstance().unregister(tasks); PlatformTasks.idleListener = null;
      platform.leave(); dbBackup.persistTo(db); cacheBackup.persistTo(cache); deviceBackup.persistTo(device);
    }
  }
  private void login(SharedPreferences prefs, String role) {
    String email = "admin".equals(role) ? "admin@coletai.app" : role + "@gmail.com";
    JSONObject account = PlatformGateway.login(prefs, email, "12345");
    new PedidoRepository(prefs).selecionarConta(account.optString("id"), role);
    PlatformGateway.refresh(prefs);
  }
}
