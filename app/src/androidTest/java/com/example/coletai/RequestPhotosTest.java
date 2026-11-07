package com.example.coletai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RequestPhotosTest {
  @SuppressWarnings("unchecked")
  private void restore(SharedPreferences prefs, Map<String, ?> values) {
    SharedPreferences.Editor edit = prefs.edit().clear();
    for (Map.Entry<String, ?> entry : values.entrySet()) {
      Object value = entry.getValue(); String key = entry.getKey();
      if (value instanceof String) edit.putString(key, (String) value);
      else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
      else if (value instanceof Integer) edit.putInt(key, (Integer) value);
      else if (value instanceof Long) edit.putLong(key, (Long) value);
      else if (value instanceof Float) edit.putFloat(key, (Float) value);
      else if (value instanceof Set) edit.putStringSet(key, (Set<String>) value);
    }
    assertTrue(edit.commit());
  }

  private void screenshot(Context context, String name) throws Exception {
    android.os.SystemClock.sleep(300);
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    File directory = new File(context.getExternalFilesDir(null), "photos-review");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(screen);
    try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
      assertTrue(screen.compress(Bitmap.CompressFormat.PNG, 100, out));
    } finally { screen.recycle(); }
  }

  @Test public void importsRotatedPhotoStripsMetadataPersistsPreviewsAndRemovesIt() throws Exception {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context);
    Map<String, ?> saved = prefs.getAll(), savedDevice = device.getAll();
    File source = new File(context.getCacheDir(), "photo-test-original.jpg");
    JSONObject photo = null;
    try {
      assertTrue(prefs.edit().clear().commit());
      new ProfileRepository(prefs);
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      assertTrue(device.edit().putBoolean("automatic", false).putLong("latitude", Double.doubleToLongBits(-23.55))
          .putLong("longitude", Double.doubleToLongBits(-46.63)).commit());
      Bitmap original = Bitmap.createBitmap(1280, 640, Bitmap.Config.ARGB_8888);
      original.eraseColor(Color.rgb(18, 144, 121));
      try (FileOutputStream out = new FileOutputStream(source)) { assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 95, out)); }
      original.recycle();
      android.media.ExifInterface exif = new android.media.ExifInterface(source.getAbsolutePath());
      exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6");
      exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, "23/1,33/1,0/1");
      exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, "S");
      exif.saveAttributes();
      photo = RequestPhotoStore.importImage(context, Uri.fromFile(source));
      File imported = RequestPhotoStore.file(context, photo);
      assertTrue(imported.length() <= PhotoRefs.MAX_BYTES);
      Bitmap decoded = BitmapFactory.decodeFile(imported.getAbsolutePath());
      assertNotNull(decoded);
      assertEquals(640, decoded.getWidth()); assertEquals(1280, decoded.getHeight());
      decoded.recycle();
      assertNull(new android.media.ExifInterface(imported.getAbsolutePath()).getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE));

      CollectionRepository repo = new CollectionRepository(prefs);
      repo.saveAddress(LocalJson.object("{\"nickname\":\"Casa\",\"street\":\"Rua Teste\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zip\":\"01001000\"}"));
      JSONObject draft = LocalJson.object("{\"desired_date\":\"2099-01-02\",\"description\":\"Pedido com foto\",\"items\":[{\"material_id\":\"m1\",\"weight\":\"2.00\"}]}");
      LocalJson.put(draft, "address_id", repo.addresses().get(0).optString("id"));
      LocalJson.put(draft, "photos", new JSONArray().put(photo));
      repo.saveOrder(RequestPhotoStore.prepareOrder(context, draft));
      String orderId = repo.orders().get(0).optString("id");
      try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
        scenario.onActivity(activity -> {
          RequestFormDialog.create(orderId).show(activity.getSupportFragmentManager(), "request_form");
          activity.getSupportFragmentManager().executePendingTransactions();
        });
        scenario.recreate();
        onView(withContentDescription("Editar foto 1")).inRoot(isDialog()).check(matches(isDisplayed())).perform(click());
        onView(withText("Ver foto")).inRoot(isDialog()).perform(click());
        screenshot(context, "photo-preview");
        onView(withText("Fechar")).perform(click());
      }
      new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
      try (ActivityScenario<OrderFlowActivity> scenario = ActivityScenario.launch(new Intent(context, OrderFlowActivity.class).putExtra("request_id", orderId))) {
        onView(withContentDescription("Abrir foto 1 do pedido")).check(matches(isDisplayed())).perform(click());
        screenshot(context, "collector-photo");
        onView(withText("Fechar")).perform(click());
      }
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
        scenario.onActivity(activity -> {
          RequestFormDialog.create(orderId).show(activity.getSupportFragmentManager(), "request_form");
          activity.getSupportFragmentManager().executePendingTransactions();
        });
        onView(withContentDescription("Editar foto 1")).inRoot(isDialog()).perform(click());
        onView(withText("Remover foto")).inRoot(isDialog()).perform(click());
        onView(withText("Salvar alterações")).inRoot(isDialog()).perform(click());
        long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
        while (repo.order(orderId).optJSONArray("photos").length() != 0 && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(repo.order(orderId).optJSONArray("photos").length() == 0);
      }
    } finally {
      restore(prefs, saved); restore(device, savedDevice);
      source.delete();
      if (photo != null) RequestPhotoStore.file(context, photo).delete();
    }
  }
}
