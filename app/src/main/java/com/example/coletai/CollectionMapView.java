package com.example.coletai;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import org.maplibre.android.MapLibre;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;

/** Owns the map renderer lifecycle. */
final class CollectionMapView extends FrameLayout implements DefaultLifecycleObserver {
  interface Ready {
    void loaded(MapLibreMap map);
  }

  private MapView view;
  private TextView message;
  private TextView pointStatus;
  private DisposalMapLayer disposalLayer;

  CollectionMapView(FragmentActivity owner, Bundle state, Ready ready) {
    super(owner);
    setBackgroundColor(0xFFEBF8F3);
    message = new TextView(owner);
    message.setGravity(Gravity.CENTER);
    message.setTextColor(0xFF3C504C);
    int pad = PlatformUi.dp(owner, 24);
    message.setPadding(pad, pad, pad, pad);
    if (!MapServices.configured()) {
      message.setText("O mapa estará disponível assim que o serviço for configurado.");
      addView(message, new LayoutParams(-1, -1));
      return;
    }
    MapLibre.getInstance(owner);
    view = new MapView(owner, new org.maplibre.android.maps.MapLibreMapOptions()
        .compassEnabled(true)
        .compassGravity(Gravity.TOP | Gravity.END)
        .compassMargins(new int[] {0, PlatformUi.dp(owner, 84), PlatformUi.dp(owner, 22), 0}));
    view.onCreate(state);
    addView(view, new LayoutParams(-1, -1));
    pointStatus = new TextView(owner);
    pointStatus.setTextSize(11);
    pointStatus.setTextColor(0xFF0B3D37);
    pointStatus.setBackgroundResource(R.drawable.bg_floating_control);
    pointStatus.setPadding(PlatformUi.dp(owner, 10), PlatformUi.dp(owner, 4), PlatformUi.dp(owner, 10), PlatformUi.dp(owner, 4));
    pointStatus.setGravity(Gravity.CENTER);
    pointStatus.setMinHeight(PlatformUi.dp(owner, 48));
    pointStatus.setMaxWidth(PlatformUi.dp(owner, 230));
    LayoutParams pointsParams = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
    pointsParams.rightMargin = PlatformUi.dp(owner, 6);
    pointsParams.bottomMargin = PlatformUi.dp(owner, 18);
    addView(pointStatus, pointsParams);
    message.setText("Carregando mapa…");
    addView(message, new LayoutParams(-1, -1));
    message.setBackgroundColor(0xDDEBF8F3);
    message.setOnClickListener(v -> load(ready));
    view.addOnDidFailLoadingMapListener(error -> {
      message.setText("Não foi possível carregar o mapa. Toque para tentar novamente.");
      message.setVisibility(VISIBLE);
    });
    load(ready);
    owner.getLifecycle().addObserver(this);
  }

  private void load(Ready ready) {
    message.setText("Carregando mapa…");
    view.getMapAsync(map -> {
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setCompassGravity(Gravity.TOP | Gravity.END);
        map.getUiSettings().setCompassMargins(0, PlatformUi.dp(getContext(), 84),
            PlatformUi.dp(getContext(), 22), 0);
        map.getUiSettings().setLogoEnabled(false);
        map.getUiSettings().setAttributionEnabled(false);
        map.setStyle(
            MapServices.styleUrl(),
            style -> {
              message.setVisibility(GONE);
              if (BluetoothPlatform.active()) {
                TextView attribution = new TextView(getContext());
                attribution.setText("Mapa offline · © OpenStreetMap · ODbL");
                attribution.setTextSize(10); attribution.setTextColor(0xFF294A3A); attribution.setBackgroundColor(0xEEFFFFFF);
                LayoutParams credits = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
                credits.bottomMargin = PlatformUi.dp(getContext(), 4); addView(attribution, credits);
              }
              if (disposalLayer != null) disposalLayer.dispose();
              disposalLayer = new DisposalMapLayer((FragmentActivity) getContext(), map, style, pointStatus);
              ready.loaded(map);
            });
    });
  }

  void save(Bundle out) {
    if (view != null) view.onSaveInstanceState(out);
  }

  void lowMemory() {
    if (view != null) view.onLowMemory();
  }

  @Override
  public void onStart(LifecycleOwner owner) {
    if (view != null) view.onStart();
  }

  @Override
  public void onResume(LifecycleOwner owner) {
    if (view != null) view.onResume();
  }

  @Override
  public void onPause(LifecycleOwner owner) {
    if (view != null) view.onPause();
  }

  @Override
  public void onStop(LifecycleOwner owner) {
    if (view != null) view.onStop();
  }

  @Override
  public void onDestroy(LifecycleOwner owner) {
    if (view != null) view.onDestroy();
    owner.getLifecycle().removeObserver(this);
  }
}
