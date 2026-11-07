package com.example.coletai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProposalFlowTest {
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

  private void checkAutomaticOpen(int button, String title) {
    android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    android.app.Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(OrderFlowActivity.class.getName(), null, false);
    android.app.Activity opened = null;
    ActivityScenario<WorkspaceActivity> home = null;
    try {
      home = ActivityScenario.launch(WorkspaceActivity.class);
      opened = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
      assertNotNull("The request or proposal must open in full screen", opened);
      onView(withId(button)).check(matches(withText(title)));
    } finally {
      if (opened != null) {
        android.app.Activity activity = opened;
        instrumentation.runOnMainSync(activity::finish);
      }
      if (home != null) home.close();
      instrumentation.removeMonitor(monitor);
    }
  }

  @Test public void waitingProposalApprovalPickupAndDisposal() { runFlow("ambulante"); }
  @Test public void companyProposalApprovalPickupAndDisposal() { runFlow("empresa"); }

  private void runFlow(String collectorRole) {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured());
    if (Build.VERSION.SDK_INT >= 28) {
      android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
      String target = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
      automation.grantRuntimePermission(target, android.Manifest.permission.ACCESS_COARSE_LOCATION);
      automation.grantRuntimePermission(target, android.Manifest.permission.ACCESS_FINE_LOCATION);
    }
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context);
    Map<String, ?> backup = prefs.getAll(), deviceBackup = device.getAll();
    ActivityScenario<?> scenario = null;
    try {
      assertTrue(prefs.edit().clear().commit());
      assertTrue(device.edit().putBoolean("automatic", false).putLong("latitude", Double.doubleToLongBits(-23.55)).putLong("longitude", Double.doubleToLongBits(-46.63)).commit());
      CollectionRepository repo = new CollectionRepository(prefs);
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      JSONObject address = LocalJson.object("{\"nickname\":\"Casa\",\"street\":\"Rua Teste\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"São Paulo\",\"state\":\"SP\",\"zip\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}");
      repo.saveAddress(address);
      JSONObject draft = new JSONObject();
      LocalJson.put(draft, "address_id", repo.addresses().get(0).optString("id"));
      LocalJson.put(draft, "desired_date", "2099-01-02");
      LocalJson.put(draft, "description", "Papel para reciclar");
      LocalJson.put(draft, "items", new JSONArray().put(LocalJson.object("{\"material_id\":\"m1\",\"weight\":\"2.50\"}")));
      repo.saveOrder(draft);
      String id = repo.orders().get(0).optString("id");
      scenario = ActivityScenario.launch(WorkspaceActivity.class);
      onView(withId(R.id.orderHomeTimer)).check(matches(isDisplayed()));
      onView(withId(R.id.orderHomeCancel)).check(matches(withText("Cancelar pedido")));
      long base = repo.order(id).optLong("created_at");
      scenario.recreate();
      onView(withId(R.id.orderHomeTimer)).check(matches(isDisplayed()));
      assertEquals(base, repo.order(id).optLong("created_at"));
      scenario.close(); scenario = null;

      new PedidoRepository(prefs).selecionarConta(collectorRole, collectorRole);
      checkAutomaticOpen(R.id.btnEnviarProposta, "Aceitar pedido");
      Intent flow = new Intent(context, OrderFlowActivity.class).putExtra("request_id", id);
      scenario = ActivityScenario.launch(flow);
      onView(withId(R.id.btnEnviarProposta)).check(matches(withText("Aceitar pedido"))).perform(click());
      onView(withId(R.id.edtPreco)).perform(replaceText("45,50"), closeSoftKeyboard());
      onView(withId(R.id.edtMensagem)).perform(replaceText("Chego em 20 minutos"), closeSoftKeyboard());
      scenario.recreate();
      onView(withId(R.id.edtPreco)).check(matches(withText("45,50")));
      onView(withId(R.id.edtMensagem)).check(matches(withText("Chego em 20 minutos")));
      onView(withId(R.id.btnEnviarProposta)).perform(click());
      assertEquals("proposta", repo.order(id).optString("status"));
      assertFalse(repo.order(id).optJSONObject("address").has("latitude"));
      scenario.close(); scenario = null;

      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      checkAutomaticOpen(R.id.orderFlowPrimary, "Aceitar proposta");
      scenario = ActivityScenario.launch(flow);
      onView(withText("Cancelar pedido")).check(doesNotExist());
      onView(withId(R.id.orderFlowSecondary)).check(matches(withText("Recusar proposta")));
      onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Aceitar proposta"))).perform(click());
      onView(allOf(withText("Confirmar"), isDisplayed())).inRoot(isDialog()).perform(click());
      assertEquals("aceita", repo.order(id).optString("status"));
      scenario.close(); scenario = null;

      new PedidoRepository(prefs).selecionarConta(collectorRole, collectorRole);
      scenario = ActivityScenario.launch(flow);
      onView(withId(R.id.orderFlowPrimary)).check((view, error) -> {
        assertNull(error);
        assertEquals(android.graphics.Color.WHITE, ((android.widget.TextView) view).getCurrentTextColor());
      });
      onView(withText("LOCAL DE DESCARTE")).check(doesNotExist());
      onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Ir até o cliente"))).perform(click());
      onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Cheguei ao local"))).perform(click());
      onView(withText("LOCAL DE DESCARTE")).check(doesNotExist());
      onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Confirmar coleta")));
      onView(withId(R.id.orderFlowPrimary)).perform(click());
      onView(allOf(withText("Confirmar"), isDisplayed())).inRoot(isDialog()).perform(click());
      assertEquals("descartando", repo.order(id).optString("status"));
      onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Finalizar coleta"))).perform(click());
      onView(allOf(withText("Confirmar"), isDisplayed())).inRoot(isDialog()).perform(click());
      assertEquals("concluida", repo.order(id).optString("status"));
    } finally {
      if (scenario != null) scenario.close();
      restore(prefs, backup);
      restore(device, deviceBackup);
    }
  }
}
