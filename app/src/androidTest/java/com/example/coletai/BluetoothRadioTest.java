package com.example.coletai;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;

/** Opt-in, two-emulator RFCOMM integration. Never runs against a real user's phone. */
public class BluetoothRadioTest {
  @Test public void twoAndroidInstancesUseRealRfcommSockets() throws Exception {
    String side = InstrumentationRegistry.getArguments().getString("radio_side", "");
    assumeTrue(!side.isEmpty());
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    if (Build.VERSION.SDK_INT >= 31) automation.grantRuntimePermission(context.getPackageName(), android.Manifest.permission.BLUETOOTH_CONNECT);
    SharedPreferences db = context.getSharedPreferences("coletai_bluetooth_host", 0), cache = context.getSharedPreferences("coletai_bluetooth", 0);
    SnapshotPreferences dbBackup = new SnapshotPreferences(db), cacheBackup = new SnapshotPreferences(cache);
    BluetoothPlatform platform = BluetoothPlatform.get(context);
    try (ActivityScenario<OfflineBluetoothActivity> scenario = ActivityScenario.launch(OfflineBluetoothActivity.class)) {
      assertFalse("Run with no existing presentation session", BluetoothPlatform.active());
      db.edit().clear().commit(); cache.edit().clear().commit();
      if ("host".equals(side)) scenario.onActivity(activity -> platform.host());
      else {
        BluetoothAdapter adapter = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(InstrumentationRegistry.getArguments().getString("radio_host", "BB:BB:BB:00:00:01"));
        scenario.onActivity(activity -> platform.join(device));
      }
      long deadline = SystemClock.elapsedRealtime() + 150000;
      int pairingPoll = 0;
      while (!platform.ready() && SystemClock.elapsedRealtime() < deadline) {
        if (++pairingPoll % 10 == 0) expandPairingNotifications(automation);
        confirmPairing(automation.getRootInActiveWindow()); SystemClock.sleep(300);
      }
      assertTrue(platform.status(), platform.ready());
      SharedPreferences prefs = PlatformStore.open(context);
      if ("host".equals(side)) {
        if ("true".equals(InstrumentationRegistry.getArguments().getString("radio_three"))) {
          while (platform.participantCount() < 2 && SystemClock.elapsedRealtime() < deadline) {
            if (++pairingPoll % 6 == 0) expandPairingNotifications(automation);
            confirmPairing(automation.getRootInActiveWindow()); SystemClock.sleep(300);
          }
          assertEquals("Two different participant devices must connect at the same time", 2, platform.participantCount());
        }
        login(prefs, "ambulante");
        CollectionRepository orders = new CollectionRepository(prefs); JSONObject found = null;
        while (SystemClock.elapsedRealtime() < deadline) {
          if (++pairingPoll % 6 == 0) expandPairingNotifications(automation);
          confirmPairing(automation.getRootInActiveWindow()); PlatformGateway.refresh(prefs);
          for (JSONObject order : orders.orders()) if ("Teste RFCOMM entre Androids".equals(order.optString("description"))) found = order;
          if (found != null) break; SystemClock.sleep(500);
        }
        assertNotNull("Remote phone must create a request on the host", found);
        assertEquals(1, found.optJSONArray("photos").length());
        byte[] remotePhoto = PlatformGateway.downloadPhoto(prefs, found.optJSONArray("photos").optJSONObject(0).optString("id"));
        assertTrue(remotePhoto.length > 10); assertEquals((byte) 0xff, remotePhoto[0]);
        String id = found.optString("id"); orders.transition(id, "accept", found.optInt("revision"));
        orders.propose(id, orders.order(id).optInt("revision"), "12,50", "Proposta pelo rádio");
        while (!"aceita".equals(orders.order(id).optString("status")) && SystemClock.elapsedRealtime() < deadline) {
          PlatformGateway.refresh(prefs); SystemClock.sleep(300);
        }
        assertEquals("aceita", orders.order(id).optString("status"));
        for (String step : new String[]{"going", "arrive", "collect", "complete"}) orders.transition(id, step, orders.order(id).optInt("revision"));
        while (!orders.order(id).has("review") && SystemClock.elapsedRealtime() < deadline) { PlatformGateway.refresh(prefs); SystemClock.sleep(300); }
        assertEquals(5, orders.order(id).optJSONObject("review").optInt("score"));
        // Keep the socket available for the client's final administration check.
        SystemClock.sleep(5000);
      } else if ("observer".equals(side)) {
        login(prefs, "admin"); boolean reviewed = false;
        while (SystemClock.elapsedRealtime() < deadline) {
          PlatformGateway.refresh(prefs);
          for (JSONObject order : new CollectionRepository(prefs).orders()) if (order.has("review")) reviewed = true;
          if (reviewed) break; SystemClock.sleep(300);
        }
        assertTrue("Third phone must see the completed and reviewed collection", reviewed);
        assertTrue(new AuditRepository(prefs).list().size() >= 8);
      } else {
        BluetoothDevice coordinator = ((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter()
            .getRemoteDevice(InstrumentationRegistry.getArguments().getString("radio_host", "BB:BB:BB:00:00:01"));
        login(prefs, "cliente"); CollectionRepository orders = new CollectionRepository(prefs);
        JSONObject input = LocalJson.object("{\"desired_date\":\"2099-01-02\",\"current_location\":{\"latitude\":-23.538,\"longitude\":-46.56},\"description\":\"Teste RFCOMM entre Androids\",\"items\":[{\"material_id\":\"m1\",\"weight\":\"2.50\"}]}");
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream photo = new java.io.ByteArrayOutputStream(); bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, photo); bitmap.recycle();
        JSONObject uploaded = PlatformGateway.uploadPhoto(prefs, photo.toByteArray()); LocalJson.put(input, "photos", new JSONArray().put(uploaded));
        orders.saveOrder(input); String id = orders.orders().get(0).optString("id");
        scenario.onActivity(activity -> platform.join(coordinator));
        while (!platform.ready() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200);
        assertTrue("Reconnect must preserve the authenticated session", platform.ready()); PlatformGateway.refresh(prefs);
        while (!"proposta".equals(orders.order(id).optString("status")) && SystemClock.elapsedRealtime() < deadline) { PlatformGateway.refresh(prefs); SystemClock.sleep(300); }
        assertEquals("proposta", orders.order(id).optString("status"));
        orders.transition(id, "approve", orders.order(id).optInt("revision"));
        while (!"concluida".equals(orders.order(id).optString("status")) && SystemClock.elapsedRealtime() < deadline) { PlatformGateway.refresh(prefs); SystemClock.sleep(300); }
        assertEquals("concluida", orders.order(id).optString("status")); orders.review(id, 5, "Rádio validado");
        login(prefs, "admin"); assertTrue(new AuditRepository(prefs).list().size() >= 8);
      }
    } finally { platform.leave(); dbBackup.persistTo(db); cacheBackup.persistTo(cache); }
  }
  private static JSONObject radio(BluetoothSocket socket, String token, String operation, JSONObject payload) throws java.io.IOException {
    JSONObject command = new JSONObject(); LocalJson.put(command, "version", 1); LocalJson.put(command, "id", java.util.UUID.randomUUID().toString());
    LocalJson.put(command, "token", token); LocalJson.put(command, "operation", operation); LocalJson.put(command, "payload", payload);
    BluetoothFrames.write(socket.getOutputStream(), command); return BluetoothFrames.read(socket.getInputStream());
  }
  private static void login(SharedPreferences prefs, String role) {
    JSONObject user = PlatformGateway.login(prefs, "admin".equals(role) ? "admin@coletai.app" : role + "@gmail.com", "12345");
    new PedidoRepository(prefs).selecionarConta(user.optString("id"), role);
  }
  private static void confirmPairing(AccessibilityNodeInfo node) {
    if (node == null) return;
    CharSequence text = node.getText();
    if (text != null && java.util.Arrays.asList("Pair", "Parear", "Emparelhar", "PAIR", "PAREAR", "Pair & connect", "Parear e conectar", "Emparelhar e conectar", "Pairing request").contains(text.toString())) {
      AccessibilityNodeInfo target = node;
      while (target != null && !target.isClickable()) target = target.getParent();
      if (target != null) target.performAction(AccessibilityNodeInfo.ACTION_CLICK); return;
    }
    for (int i = 0; i < node.getChildCount(); i++) confirmPairing(node.getChild(i));
  }
  private static void expandPairingNotifications(android.app.UiAutomation automation) {
    try (android.os.ParcelFileDescriptor command = automation.executeShellCommand("cmd statusbar expand-notifications")) { }
    catch (java.io.IOException ignored) { }
  }
}
