package com.example.coletai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RatingBar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReviewFlowTest {
  @SuppressWarnings("unchecked") private void restore(SharedPreferences prefs, Map<String, ?> values) {
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

  private String completed(SharedPreferences prefs, CollectionRepository repo, String address) {
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    JSONObject draft = LocalJson.object("{\"desired_date\":\"2099-01-02\",\"description\":\"Coleta para avaliação\",\"items\":[{\"material_id\":\"m1\",\"weight\":\"2.00\"}]}");
    LocalJson.put(draft, "address_id", address); repo.saveOrder(draft);
    String id = repo.orders().get(0).optString("id");
    new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
    repo.transition(id, "accept", repo.order(id).optInt("revision"));
    repo.propose(id, repo.order(id).optInt("revision"), "20", "");
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    repo.transition(id, "approve", repo.order(id).optInt("revision"));
    new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
    for (String step : new String[] {"going", "arrive", "collect", "complete"}) repo.transition(id, step, repo.order(id).optInt("revision"));
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    return id;
  }

  private void screenshot(Context context, String name) throws Exception {
    android.os.SystemClock.sleep(300);
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    File directory = new File(context.getExternalFilesDir(null), "review-flow");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(image);
    try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
      assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
    } finally { image.recycle(); }
  }

  @Test public void ratesCompletedCollectionWithFiveStarControlAndUpdatesCollectorAverage() throws Exception {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context);
    Map<String, ?> old = prefs.getAll(), oldDevice = device.getAll();
    try {
      assertTrue(prefs.edit().clear().commit()); new ProfileRepository(prefs);
      assertTrue(device.edit().putBoolean("automatic", false).putLong("latitude", Double.doubleToLongBits(-23.55))
          .putLong("longitude", Double.doubleToLongBits(-46.63)).commit());
      new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
      CollectionRepository repo = new CollectionRepository(prefs);
      repo.saveAddress(LocalJson.object("{\"nickname\":\"Casa\",\"street\":\"Rua Teste\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Sao Paulo\",\"state\":\"SP\",\"zip\":\"01001000\"}"));
      String address = repo.addresses().get(0).optString("id");
      String first = completed(prefs, repo, address); repo.review(first, 5, "Excelente");
      String second = completed(prefs, repo, address);
      Intent intent = new Intent(context, OrderFlowActivity.class).putExtra("request_id", second);
      try (ActivityScenario<OrderFlowActivity> scenario = ActivityScenario.launch(intent)) {
        onView(withText("Enviar avaliação")).inRoot(isDialog()).check(matches(not(isEnabled())));
        onView(isAssignableFrom(RatingBar.class)).inRoot(isDialog()).check((view, error) -> {
          assertNull(error); RatingBar stars = (RatingBar) view;
          assertEquals(5, stars.getNumStars()); assertEquals(1, stars.getStepSize(), .001); assertEquals(0, stars.getRating(), .001);
        }).perform(new androidx.test.espresso.ViewAction() {
          public org.hamcrest.Matcher<android.view.View> getConstraints() { return isAssignableFrom(RatingBar.class); }
          public String getDescription() { return "Selecionar três estrelas"; }
          public void perform(androidx.test.espresso.UiController controller, android.view.View view) { ((RatingBar) view).setRating(3); }
        });
        onView(withHint("Comentário (opcional)")).inRoot(isDialog()).perform(replaceText("Coleta concluída"), closeSoftKeyboard());
        scenario.recreate();
        onView(isAssignableFrom(RatingBar.class)).inRoot(isDialog()).check((view, error) -> assertEquals(3, ((RatingBar) view).getRating(), .001));
        screenshot(context, "rating-dialog");
        onView(withText("Enviar avaliação")).inRoot(isDialog()).check(matches(isEnabled())).perform(click());
        long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
        while (!repo.order(second).has("review") && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50);
        assertEquals(3, repo.order(second).optJSONObject("review").optInt("score"));
        assertEquals("Coleta concluída", repo.order(second).optJSONObject("review").optString("comment"));
        onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Voltar ao início")));
      }
      // Reopening the same completed order must show the saved rating, never a new dialog.
      JSONObject stale = LocalJson.object(repo.order(second).toString());
      stale.remove("review");
      assertTrue(prefs.edit().remove("review_prompted_cliente_" + second).commit());
      try (ActivityScenario<OrderFlowActivity> scenario = ActivityScenario.launch(intent)) {
        scenario.onActivity(activity -> ReviewDialog.promptOnce(activity, stale));
        onView(isAssignableFrom(RatingBar.class)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        onView(withId(R.id.orderFlowPrimary)).check(matches(withText("Voltar ao início")));
        scenario.recreate();
        onView(isAssignableFrom(RatingBar.class)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
      }
      // An older unrated collection must not trigger a new popup whenever the profile opens.
      completed(prefs, repo, address);
      for (int reopen = 0; reopen < 2; reopen++) {
        try (ActivityScenario<WorkspaceActivity> scenario = ActivityScenario.launch(WorkspaceActivity.class)) {
          scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).setSelectedItemId(R.id.workProfile));
          scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).setSelectedItemId(R.id.workHome));
          onView(isAssignableFrom(RatingBar.class)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        }
      }
      new PedidoRepository(prefs).selecionarConta("ambulante", "ambulante");
      JSONObject profile = new ProfileRepository(prefs).current();
      assertEquals(2, profile.optInt("rating_count")); assertEquals(4, profile.optDouble("rating_average"), .001);
      try (ActivityScenario<WorkspaceActivity> scenario = ActivityScenario.launch(WorkspaceActivity.class)) {
        scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).setSelectedItemId(R.id.workProfile));
        onView(withText("★ 4,0")).check(matches(isDisplayed()));
        onView(withText("Média de 2 avaliações")).check(matches(isDisplayed()));
        screenshot(context, "collector-average");
      }
    } finally { restore(prefs, old); restore(device, oldDevice); }
  }
}
