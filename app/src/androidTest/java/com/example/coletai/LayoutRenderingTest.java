package com.example.coletai;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Emulator-only layout regression checks, also usable with increased system font scale. */
@RunWith(AndroidJUnit4.class)
public class LayoutRenderingTest {
  private void screenshot(Context context, String name) throws Exception {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    // A main-thread idle event can precede composition of the new window.
    android.os.SystemClock.sleep(300);
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    File folder = new File(context.getExternalFilesDir(null), "layout-review");
    assertTrue(folder.isDirectory() || folder.mkdirs());
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
      assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
    } finally { bitmap.recycle(); }
  }

  private void fields(View view) {
    if (view instanceof TextInputLayout) {
      TextInputLayout field = (TextInputLayout) view;
      assertFalse("Field label must stay above its content", field.isExpandedHintEnabled());
      assertNotNull(field.getEditText());
      assertTrue(field.getEditText().getPaddingLeft() > 0);
      assertTrue(field.getEditText().getPaddingTop() > 0);
      assertTrue(field.getEditText().getWidth() <= field.getWidth());
    }
    if (view instanceof TextView && "Adicionar".contentEquals(((TextView) view).getText()))
      assertEquals(Gravity.CENTER_HORIZONTAL, ((TextView) view).getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) fields(((ViewGroup) view).getChildAt(i));
  }

  private ScrollView scroll(View view) {
    if (view instanceof ScrollView) return (ScrollView) view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        ScrollView result = scroll(((ViewGroup) view).getChildAt(i));
        if (result != null) return result;
      }
    return null;
  }

  private View compass(View view) {
    if (view instanceof org.maplibre.android.maps.widgets.CompassView) return view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View found = compass(((ViewGroup) view).getChildAt(i));
        if (found != null) return found;
      }
    return null;
  }

  @SuppressWarnings("unchecked")
  private void restore(SharedPreferences prefs, Map<String, ?> values) {
    SharedPreferences.Editor editor = prefs.edit().clear();
    for (Map.Entry<String, ?> entry : values.entrySet()) {
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

  @Test public void rendersAccountScreensAndSeparatesFieldLabelsFromPlaceholders() throws Exception {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context);
    Map<String, ?> old = prefs.getAll(), oldDevice = device.getAll();
    try {
      assertTrue(prefs.edit().clear().commit());
      new ProfileRepository(prefs);
      assertTrue(device.edit().putBoolean("automatic", false)
          .putLong("latitude", Double.doubleToLongBits(-23.55))
          .putLong("longitude", Double.doubleToLongBits(-46.63)).commit());
      try (ActivityScenario<MainActivity> login = ActivityScenario.launch(MainActivity.class)) {
        screenshot(context, "login");
      }
      try (ActivityScenario<RegistrationActivity> registration = ActivityScenario.launch(RegistrationActivity.class)) {
        registration.onActivity(activity -> fields(activity.getWindow().getDecorView()));
        screenshot(context, "registration");
      }
      for (String role : new String[] {"cliente", "ambulante", "empresa"}) {
        new PedidoRepository(prefs).selecionarConta(role, role);
        try (ActivityScenario<WorkspaceActivity> scenario = ActivityScenario.launch(WorkspaceActivity.class)) {
          screenshot(context, role + "-home");
          scenario.onActivity(activity -> {
            assertEquals("cliente".equals(role) ? View.GONE : View.VISIBLE, activity.findViewById(R.id.workOrders).getVisibility());
            View compass = compass(activity.findViewById(R.id.workMapContainer));
            assertNotNull(compass);
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) compass.getLayoutParams();
            View notices = activity.findViewById(R.id.workNotices);
            assertTrue("Compass must sit below notifications", margins.topMargin >= notices.getBottom());
          });
          scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).setSelectedItemId(R.id.workProfile));
          screenshot(context, role + "-profile");
          scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).setSelectedItemId(R.id.workHistory));
          screenshot(context, role + "-history");
          if ("cliente".equals(role)) {
            scenario.onActivity(activity -> {
              ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).setSelectedItemId(R.id.workHome);
              activity.findViewById(R.id.workAction).performClick();
              activity.getSupportFragmentManager().executePendingTransactions();
            });
            screenshot(context, "request-top");
            scenario.onActivity(activity -> {
              RequestFormDialog form = (RequestFormDialog) activity.getSupportFragmentManager().findFragmentByTag("request_form");
              assertNotNull(form);
              View content = form.requireDialog().getWindow().getDecorView();
              fields(content);
              ScrollView scroll = scroll(content);
              assertNotNull(scroll);
              scroll.fullScroll(View.FOCUS_DOWN);
            });
            screenshot(context, "request-bottom");
          }
        }
      }
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      CollectionRepository repo = new CollectionRepository(prefs);
      repo.saveAddress(LocalJson.object("{\"nickname\":\"Casa\",\"street\":\"Rua Teste\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zip\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}"));
      org.json.JSONObject draft = new org.json.JSONObject();
      LocalJson.put(draft, "address_id", repo.addresses().get(0).optString("id"));
      LocalJson.put(draft, "desired_date", "2099-01-02");
      LocalJson.put(draft, "description", "Materiais separados para coleta. Papel, caixas e embalagens limpas.");
      LocalJson.put(draft, "notes", "Tocar a campainha ao chegar.");
      LocalJson.put(draft, "items", new org.json.JSONArray().put(LocalJson.object("{\"material_id\":\"m1\",\"weight\":\"2.50\"}")));
      repo.saveOrder(draft);
      String orderId = repo.orders().get(0).optString("id");
      new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
      android.content.Intent flow = new android.content.Intent(context, OrderFlowActivity.class).putExtra("request_id", orderId);
      try (ActivityScenario<OrderFlowActivity> details = ActivityScenario.launch(flow)) {
        screenshot(context, "order-details");
      }
      repo.transition(orderId, "accept", repo.order(orderId).optInt("revision"));
      try (ActivityScenario<OrderFlowActivity> proposal = ActivityScenario.launch(flow)) {
        screenshot(context, "proposal");
      }
      repo.propose(orderId, repo.order(orderId).optInt("revision"), "25.00", "Posso realizar a coleta pela manhã.");
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      try (ActivityScenario<OrderFlowActivity> progress = ActivityScenario.launch(flow)) {
        screenshot(context, "order-progress");
      }
      repo.transition(orderId, "approve", repo.order(orderId).optInt("revision"));
      new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
      String[] transitions = {"", "going", "arrive", "collect"};
      for (int step = 0; step < transitions.length; step++) {
        if (!transitions[step].isEmpty()) repo.transition(orderId, transitions[step], repo.order(orderId).optInt("revision"));
        try (ActivityScenario<OrderFlowActivity> collector = ActivityScenario.launch(flow)) {
          collector.onActivity(activity -> assertEquals(android.graphics.Color.WHITE,
              ((TextView) activity.findViewById(R.id.orderFlowPrimary)).getCurrentTextColor()));
          screenshot(context, "collector-step-" + (step + 1));
        }
      }
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      repo.transition(orderId, "cancel", repo.order(orderId).optInt("revision"));
      try (ActivityScenario<WorkspaceActivity> settings = ActivityScenario.launch(WorkspaceActivity.class)) {
        settings.onActivity(activity -> {
          new LocationSettingsDialog().show(activity.getSupportFragmentManager(), "location_settings");
          activity.getSupportFragmentManager().executePendingTransactions();
        });
        screenshot(context, "location-settings");
      }
      try (ActivityScenario<WorkspaceActivity> address = ActivityScenario.launch(WorkspaceActivity.class)) {
        address.onActivity(activity -> {
          AddressEditorDialog.create(new org.json.JSONObject()).show(activity.getSupportFragmentManager(), "address_editor");
          activity.getSupportFragmentManager().executePendingTransactions();
        });
        screenshot(context, "address-editor");
      }
      new PedidoRepository(prefs).selecionarConta("admin", "admin");
      try (ActivityScenario<AdminActivity> admin = ActivityScenario.launch(AdminActivity.class)) {
        screenshot(context, "admin");
      }
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      try (ActivityScenario<WorkspaceActivity> details = ActivityScenario.launch(WorkspaceActivity.class)) {
        details.onActivity(activity -> {
          DisposalPoint point = BundledDisposalPoints.load(context).stream()
              .filter(p -> p.name.equals("Ecoponto Jardim São Paulo")).findFirst().orElse(BundledDisposalPoints.load(context).get(0));
          DisposalPointDialog.create(point).show(activity.getSupportFragmentManager(), DisposalPointDialog.TAG);
          activity.getSupportFragmentManager().executePendingTransactions();
        });
        screenshot(context, "disposal-point-details");
      }
    } finally {
      restore(prefs, old);
      restore(device, oldDevice);
    }
  }
}
