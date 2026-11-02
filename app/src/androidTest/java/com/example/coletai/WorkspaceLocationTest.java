package com.example.coletai;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses only the emulator and restores its existing account and device preferences. */
@RunWith(AndroidJUnit4.class)
public class WorkspaceLocationTest {
  @SuppressWarnings("unchecked")
  private void restore(SharedPreferences prefs, Map<String, ?> values) {
    SharedPreferences.Editor editor = prefs.edit().clear();
    for (Map.Entry<String, ?> entry : values.entrySet()) {
      String key = entry.getKey(); Object v = entry.getValue();
      if (v instanceof String) editor.putString(key, (String) v);
      else if (v instanceof Boolean) editor.putBoolean(key, (Boolean) v);
      else if (v instanceof Integer) editor.putInt(key, (Integer) v);
      else if (v instanceof Long) editor.putLong(key, (Long) v);
      else if (v instanceof Float) editor.putFloat(key, (Float) v);
      else if (v instanceof Set) editor.putStringSet(key, (Set<String>) v);
    }
    assertTrue(editor.commit());
  }

  @Test public void sharesLocationAcrossProfilesAndOpensRequestWithSelectedPosition() {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured());
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    SharedPreferences prefs = PlatformStore.open(context), device = DeviceLocation.settings(context);
    Map<String, ?> oldPrefs = prefs.getAll(), oldDevice = device.getAll();
    try {
      assertTrue(device.edit().clear().commit());
      assertTrue(DeviceLocation.automatic(context));
      assertNull(DeviceLocation.manual(context));
      assertTrue(device.edit().putBoolean("automatic", false)
          .putLong("latitude", Double.doubleToLongBits(-23.55))
          .putLong("longitude", Double.doubleToLongBits(-46.63)).commit());
      assertTrue(prefs.edit().clear().commit());
      new ProfileRepository(prefs);
      for (String role : new String[]{"cliente", "ambulante", "empresa"}) {
        new PedidoRepository(prefs).selecionarConta(role, role);
        assertFalse(DeviceLocation.automatic(context));
        assertEquals(-23.55, DeviceLocation.manual(context).getLatitude(), 0.000001);
        try (ActivityScenario<WorkspaceActivity> scenario = ActivityScenario.launch(WorkspaceActivity.class)) {
          InstrumentationRegistry.getInstrumentation().waitForIdleSync();
          scenario.onActivity(activity -> {
            View map = activity.findViewById(R.id.workMapContainer);
            assertEquals(View.VISIBLE, activity.findViewById(R.id.workMapCard).getVisibility());
            assertTrue("Map must dominate the home screen", map.getHeight() > activity.findViewById(R.id.workRoot).getHeight() / 2);
            assertEquals(3, ((BottomNavigationView) activity.findViewById(R.id.workNavigation)).getMenu().size());
            assertEquals(View.GONE, activity.findViewById(R.id.workContent).getVisibility());
            if ("cliente".equals(role)) {
              assertEquals("Fazer pedido", ((TextView) activity.findViewById(R.id.workAction)).getText().toString());
              activity.findViewById(R.id.workAction).performClick();
              activity.getSupportFragmentManager().executePendingTransactions();
              RequestFormDialog form = (RequestFormDialog) activity.getSupportFragmentManager().findFragmentByTag("request_form");
              assertNotNull(form);
              assertEquals(-23.55, form.requireArguments().getDouble("latitude"), 0.000001);
              assertTrue(form.requireDialog().isShowing());
            }
          });
        }
      }
    } finally {
      restore(prefs, oldPrefs);
      restore(device, oldDevice);
    }
  }
}
