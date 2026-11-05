package com.example.coletai;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Foreground updates for local account switching and authenticated remote sessions. */
final class OrderUpdates implements DefaultLifecycleObserver {
  private final FragmentActivity owner;
  private final SharedPreferences prefs;
  private final Runnable changed;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean active, busy;
  private final Runnable refresh = this::refresh;
  private final SharedPreferences.OnSharedPreferenceChangeListener listener;

  OrderUpdates(FragmentActivity owner, SharedPreferences prefs, Runnable changed) {
    this.owner = owner;
    this.prefs = prefs;
    this.changed = () -> {
      changed.run();
      ReviewDialog.promptPending(owner, prefs);
    };
    listener = (p, key) -> {
      if (CollectionRepository.ORDERS.equals(key)) {
        handler.removeCallbacks(this.changed);
        if (active) handler.post(this.changed);
      }
    };
    owner.getLifecycle().addObserver(this);
  }

  private void refresh() {
    if (!active || busy || !PlatformGateway.configured()) return;
    busy = true;
    PlatformTasks.run(owner, () -> PlatformGateway.refresh(prefs), changed, () -> {
      busy = false;
      if (active) handler.postDelayed(refresh, 5000);
    });
  }

  @Override public void onStart(LifecycleOwner owner) {
    active = true;
    prefs.registerOnSharedPreferenceChangeListener(listener);
    handler.post(changed);
    handler.post(refresh);
  }

  @Override public void onStop(LifecycleOwner owner) {
    active = false;
    prefs.unregisterOnSharedPreferenceChangeListener(listener);
    handler.removeCallbacks(changed);
    handler.removeCallbacks(refresh);
  }

  @Override public void onResume(LifecycleOwner owner) {
    handler.removeCallbacks(changed);
    handler.post(changed);
  }
}
