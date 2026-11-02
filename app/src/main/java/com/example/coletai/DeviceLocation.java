package com.example.coletai;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Device-wide foreground location preference, shared by every account. */
final class DeviceLocation implements DefaultLifecycleObserver, LocationListener {
  interface Changed { void update(Location location, String status); }
  private final FragmentActivity owner;
  private final Changed changed;
  private final LocationManager manager;
  private final ActivityResultLauncher<String[]> permission;
  private Location latest;
  private final boolean followPreference;
  static SharedPreferences settings(Context c) {
    return c.getSharedPreferences("device_location", Context.MODE_PRIVATE);
  }
  static boolean automatic(Context c) { return settings(c).getBoolean("automatic", true); }
  static Location manual(Context c) {
    SharedPreferences s = settings(c);
    if (!s.contains("latitude") || automatic(c)) return null;
    Location l = new Location("manual");
    l.setLatitude(Double.longBitsToDouble(s.getLong("latitude", 0)));
    l.setLongitude(Double.longBitsToDouble(s.getLong("longitude", 0)));
    return l;
  }
  DeviceLocation(FragmentActivity owner, Changed changed) { this(owner, changed, true); }
  DeviceLocation(FragmentActivity owner, Changed changed, boolean followPreference) {
    this.followPreference = followPreference;
    this.owner = owner;
    this.changed = changed;
    manager = (LocationManager) owner.getSystemService(Context.LOCATION_SERVICE);
    permission = owner.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> start());
    owner.getLifecycle().addObserver(this);
  }
  boolean allowed() {
    return ContextCompat.checkSelfPermission(owner, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(owner, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }
  void request(boolean explicit) {
    if (followPreference && !automatic(owner)) { start(); return; }
    if (allowed()) { start(); return; }
    if (explicit || !settings(owner).getBoolean("asked", false)) {
      settings(owner).edit().putBoolean("asked", true).apply();
      permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    } else changed.update(null, "Permitir localização");
  }
  Location current() { return !followPreference || automatic(owner) ? latest : manual(owner); }
  @SuppressWarnings("MissingPermission")
  void start() {
    stop();
    if (followPreference && !automatic(owner)) { changed.update(manual(owner), "Local escolhido"); return; }
    if (!allowed()) { changed.update(null, "Permitir localização"); return; }
    boolean active = false;
    if (manager != null) for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
      try {
        if (!manager.isProviderEnabled(p)) continue;
        manager.requestLocationUpdates(p, 5000, 10, this, Looper.getMainLooper());
        active = true;
        Location cached = manager.getLastKnownLocation(p);
        if (cached != null && android.os.SystemClock.elapsedRealtimeNanos() - cached.getElapsedRealtimeNanos() < 120000000000L) onLocationChanged(cached);
      } catch (SecurityException | IllegalArgumentException ignored) { }
    }
    if (latest == null) changed.update(null, active ? "Localizando…" : "Ative o GPS do celular");
  }
  private void stop() { if (manager != null) manager.removeUpdates(this); }
  @Override public void onLocationChanged(Location l) {
    if (latest != null && l.getElapsedRealtimeNanos() < latest.getElapsedRealtimeNanos()) return;
    latest = l;
    changed.update(l, "Localização atual");
  }
  @Override public void onProviderEnabled(String p) { start(); }
  @Override public void onProviderDisabled(String p) { changed.update(null, "Confira o GPS do celular"); }
  @Override public void onStatusChanged(String p, int status, Bundle b) { }
  @Override public void onStart(LifecycleOwner o) { start(); }
  @Override public void onStop(LifecycleOwner o) { stop(); latest = null; }
}
