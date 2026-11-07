package com.example.coletai;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AccountEditFlowTest {
  @SuppressWarnings("unchecked") private void restore(SharedPreferences prefs, Map<String, ?> values) {
    SharedPreferences.Editor edit = prefs.edit().clear();
    for (Map.Entry<String, ?> entry : values.entrySet()) {
      Object v = entry.getValue(); String key = entry.getKey();
      if (v instanceof String) edit.putString(key, (String) v);
      else if (v instanceof Boolean) edit.putBoolean(key, (Boolean) v);
      else if (v instanceof Integer) edit.putInt(key, (Integer) v);
      else if (v instanceof Long) edit.putLong(key, (Long) v);
      else if (v instanceof Float) edit.putFloat(key, (Float) v);
      else if (v instanceof Set) edit.putStringSet(key, (Set<String>) v);
    }
    assertTrue(edit.commit());
  }

  @Test public void novaTelaPreservaDadosESenhasDiferentesImpedemSalvarEmTodosPerfis() {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assumeTrue(!PlatformGateway.configured());
    SharedPreferences prefs = PlatformStore.open(context);
    Map<String, ?> before = prefs.getAll();
    try {
      assertTrue(prefs.edit().clear().commit());
      ProfileRepository profiles = new ProfileRepository(prefs);
      for (String role : new String[]{"cliente", "ambulante", "empresa", "admin"}) {
        new PedidoRepository(prefs).selecionarConta(role, role);
        String originalName = profiles.current().optString("name");
        try (ActivityScenario<AccountEditActivity> scenario = ActivityScenario.launch(AccountEditActivity.class)) {
          onView(withText("Excluir conta")).perform(scrollTo(), click());
          onView(withText("Excluir sua conta?")).inRoot(isDialog()).check(matches(isDisplayed()));
          onView(withText("Manter minha conta")).inRoot(isDialog()).perform(click());
          assertTrue(profiles.available(role));
          onView(withHint("Nome completo")).perform(scrollTo(), replaceText("Nome não salvo"), closeSoftKeyboard());
          onView(withHint("Senha atual")).perform(scrollTo(), replaceText("12345"), closeSoftKeyboard());
          onView(withHint("Nova senha (8 a 128 caracteres)")).perform(scrollTo(), replaceText("nova-senha"), closeSoftKeyboard());
          onView(withHint("Confirmar nova senha")).perform(scrollTo(), replaceText("diferente"), closeSoftKeyboard());
          onView(withText("Salvar alterações")).perform(scrollTo(), click());
          onView(withHint("Confirmar nova senha")).check(matches(hasErrorText("As senhas não são iguais")));
          assertEquals(originalName, profiles.current().optString("name"));
          scenario.recreate();
          onView(withHint("Nome completo")).perform(scrollTo()).check(matches(withText("Nome não salvo")));
        }
      }
    } finally { restore(prefs, before); }
  }
}
