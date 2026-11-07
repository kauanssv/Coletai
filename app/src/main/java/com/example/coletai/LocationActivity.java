package com.example.coletai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;
import org.json.*;
import org.maplibre.android.annotations.*;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.*;
import org.maplibre.android.maps.MapLibreMap;

/** Real map selection and road routing; all positions come from the user or GPS. */
public class LocationActivity extends ContaActivity {
  private CollectionMapView mapView;
  private MapLibreMap map;
  private LatLng selected, origin;
  private TextView summary;
  private MaterialButton confirm, gps;
  private boolean pick, busy, routeAfterLocation;
  private boolean autoRouteStarted;
  private String profile = "driving-car";
  private LocationManager manager;
  private LocationListener listener;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable timeout =
      () -> {
        stopLocation();
        gps.setEnabled(true);
        summary.setText("Não foi possível obter sua posição. Confira o GPS e tente novamente.");
      };
  private final ActivityResultLauncher<String[]> permission =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(), result -> locate());

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    pick = getIntent().getBooleanExtra("pick", false);
    double lat =
        state != null
            ? state.getDouble("lat", Double.NaN)
            : getIntent().getDoubleExtra("latitude", Double.NaN);
    double lng =
        state != null
            ? state.getDouble("lng", Double.NaN)
            : getIntent().getDoubleExtra("longitude", Double.NaN);
    try {
      MapServices.validPoint(lat, lng);
      selected = new LatLng(lat, lng);
    } catch (IllegalArgumentException ignored) {
    }
    LinearLayout root = PlatformUi.column(this);
    root.setBackgroundColor(0xFFF4F7F6);
    setContentView(root);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, i) -> {
          androidx.core.graphics.Insets b =
              i.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
    MaterialToolbar toolbar = new MaterialToolbar(this);
    toolbar.setTitle(pick ? "Selecionar local" : "Trajeto");
    toolbar.setSubtitle(pick ? "Busque um endereço ou toque no mapa" : getIntent().getStringExtra("destination_name"));
    toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    toolbar.setNavigationOnClickListener(v -> finish());
    root.addView(toolbar);
    mapView =
        new CollectionMapView(
            this,
            state == null ? null : state.getBundle("map"),
            m -> {
              map = m;
              map.moveCamera(
                  CameraUpdateFactory.newLatLngZoom(
                      selected == null ? new LatLng(-23.538, -46.56) : selected,
                      selected == null ? 13 : 16));
              if (selected != null) {
                marker();
                if (!pick && getIntent().hasExtra("latitude") && !autoRouteStarted) {
                  autoRouteStarted = true;
                  root.post(this::requestRoute);
                }
              }
              map.addOnMapClickListener(point -> {
                if (busy) return false;
                selected = point;
                marker();
                return true;
              });
              String address = getIntent().getStringExtra("address");
              if (selected == null && address != null && !address.trim().isEmpty()) search(address);
            });
    root.addView(mapView, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout panel = PlatformUi.column(this);
    int pad = PlatformUi.dp(this, 20);
    panel.setPadding(pad, pad / 2, pad, pad / 2);
    panel.setBackgroundResource(R.drawable.bg_bottom_sheet);
    ScrollView controls = new ScrollView(this);
    controls.setFillViewport(true);
    controls.addView(panel);
    root.addView(
        controls,
        new LinearLayout.LayoutParams(
            -1,
            Math.min(
                PlatformUi.dp(this, 300), getResources().getDisplayMetrics().heightPixels / 2)));
    PlatformUi.button(panel, "Buscar endereço", this::searchDialog);
    summary =
        PlatformUi.text(
            panel,
            selected == null
                ? "Selecione o local para continuar."
                : "Local da coleta marcado no mapa.",
            14,
            false);
    gps =
        PlatformUi.button(
            panel,
            "Minha localização",
            () -> {
              routeAfterLocation = false;
              askLocation();
            });
    gps.setIconResource(R.drawable.ic_location_small);
    confirm =
        PlatformUi.button(
            panel,
            pick ? "Confirmar local da coleta" : "Calcular rota",
            () -> {
              if (selected == null) {
                summary.setText("Busque um endereço ou marque o destino no mapa.");
                return;
              }
              if (pick) {
                setResult(
                    RESULT_OK,
                    new Intent()
                        .putExtra("latitude", selected.getLatitude())
                        .putExtra("longitude", selected.getLongitude()));
                finish();
              } else requestRoute();
            });
    confirm.setEnabled(selected != null && (pick || MapServices.configured()));
    if (selected == null) gps.post(() -> {
      android.location.Location preferred = DeviceLocation.manual(this);
      if (preferred != null) {
        selected = new LatLng(preferred.getLatitude(), preferred.getLongitude());
        marker();
        if (map != null) map.moveCamera(CameraUpdateFactory.newLatLngZoom(selected, 16));
      } else if (DeviceLocation.automatic(this)) askLocation();
    });
  }

  private void marker() {
    if (selected == null) return;
    if (map != null) {
      map.clear();
      map.addMarker(new MarkerOptions().position(selected).title("Local da coleta"));
    }
    confirm.setEnabled(!busy && (pick || map != null));
    summary.setText(
        String.format(
            Locale.forLanguageTag("pt-BR"),
            "Local marcado: %.5f, %.5f. Confira a posição antes de continuar.",
            selected.getLatitude(),
            selected.getLongitude()));
  }

  void selectReferencePoint(DisposalPoint point) {
    if (busy) return;
    selected = new LatLng(point.latitude, point.longitude);
    marker();
    summary.setText(point.name + " · " + DisposalPoint.categoryName(point.category));
    if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(selected, 16));
  }

  private void searchDialog() {
    LinearLayout form = PlatformUi.column(this);
    int pad = PlatformUi.dp(this, 20);
    form.setPadding(pad, 0, pad, pad);
    TextInputEditText query =
        PlatformUi.field(form, "Rua, número e cidade", "", android.text.InputType.TYPE_CLASS_TEXT);
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(this)
            .setTitle("Buscar endereço")
            .setView(form)
            .setNegativeButton("Voltar", null)
            .setPositiveButton("Buscar", null)
            .create();
    dialog.setOnShowListener(d ->
        dialog
            .getButton(-1)
            .setOnClickListener(v -> {
              String value = PlatformUi.value(query);
              if (value.length() < 3) {
                query.setError("Digite pelo menos 3 caracteres");
                return;
              }
              dialog.dismiss();
              search(value);
            }));
    dialog.show();
  }

  private void search(String query) {
    if (busy) return;
    busy = true;
    summary.setText("Buscando endereço…");
    final JSONArray[] found = new JSONArray[1];
    PlatformTasks.run(
        this,
        () -> found[0] = MapServices.search(query),
        () -> {
          JSONArray values = found[0];
          if (values == null || values.length() == 0) {
            summary.setText("Endereço não encontrado. Informe a cidade e o número.");
            return;
          }
          String[] names = new String[values.length()];
          for (int i = 0; i < names.length; i++)
            names[i] = values.optJSONObject(i).optString("place_name");
          new MaterialAlertDialogBuilder(this)
              .setTitle("Escolha o endereço")
              .setItems(
                  names,
                  (d, n) -> {
                    JSONArray center = values.optJSONObject(n).optJSONArray("center");
                    if (center == null) {
                      summary.setText("Endereço sem coordenadas disponíveis.");
                      return;
                    }
                    double a = center.optDouble(1, Double.NaN), b = center.optDouble(0, Double.NaN);
                    try {
                      MapServices.validPoint(a, b);
                    } catch (IllegalArgumentException e) {
                      summary.setText(e.getMessage());
                      return;
                    }
                    selected = new LatLng(a, b);
                    marker();
                    summary.setText(names[n]);
                    if (map != null)
                      map.animateCamera(CameraUpdateFactory.newLatLngZoom(selected, 16));
                    if (!pick && getIntent().hasExtra("address")) requestRoute();
                  })
              .setNegativeButton("Voltar", null)
              .show();
        },
        () -> {
          busy = false;
          if (summary.getText().toString().equals("Buscando endereço…"))
            summary.setText("Busca encerrada. Você pode tentar novamente.");
        });
  }

  private void requestRoute() {
    if (busy) return;
    if (map == null || selected == null) {
      summary.setText("Aguarde o mapa carregar e selecione o destino.");
      return;
    }
    if (!BluetoothPlatform.active() && BuildConfig.ORS_API_KEY.trim().isEmpty()) {
      summary.setText("O cálculo de rotas ainda não está disponível nesta instalação.");
      return;
    }
    profile = "driving-car";
    routeAfterLocation = true;
    askLocation();
  }

  private void askLocation() {
    if (routeAfterLocation) {
      android.location.Location preferred = !BluetoothPlatform.active() && getIntent().getBooleanExtra("force_gps", false) ? null : DeviceLocation.manual(this);
      if (preferred != null) {
        origin = new LatLng(preferred.getLatitude(), preferred.getLongitude());
        routeAfterLocation = false;
        calculateRoute();
        return;
      }
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
      permission.launch(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          });
    else locate();
  }

  @SuppressWarnings("MissingPermission")
  private void locate() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
      summary.setText("Permita o acesso à localização para usar o GPS e calcular o trajeto.");
      return;
    }
    stopLocation();
    manager = (LocationManager) getSystemService(LOCATION_SERVICE);
    listener =
        new LocationListener() {
          @Override
          public void onLocationChanged(Location location) {
            if (System.currentTimeMillis() - location.getTime() > 120000) return;
            origin = new LatLng(location.getLatitude(), location.getLongitude());
            stopLocation();
            gps.setEnabled(true);
            if (routeAfterLocation) {
              routeAfterLocation = false;
              calculateRoute();
            } else {
              if (pick) {
                selected = origin;
                marker();
              }
              if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 16));
              summary.setText(
                  pick
                      ? "Sua posição foi marcada. Confira o local e confirme."
                      : "Sua posição foi localizada. Toque em calcular rota.");
            }
          }

          @Override
          public void onProviderEnabled(String provider) {}

          @Override
          public void onProviderDisabled(String provider) {}

          @Override
          public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
    boolean requested = false;
    for (String provider :
        new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
      try {
        if (manager.isProviderEnabled(provider)) {
          manager.requestLocationUpdates(provider, 1000, 0, listener, Looper.getMainLooper());
          requested = true;
        }
      } catch (SecurityException | IllegalArgumentException ignored) {
      }
    }
    if (!requested) {
      summary.setText("Ative a localização do celular para continuar.");
      stopLocation();
      return;
    }
    gps.setEnabled(false);
    summary.setText("Obtendo sua localização…");
    handler.postDelayed(timeout, 25000);
  }

  private void calculateRoute() {
    if (busy || selected == null || origin == null || map == null) return;
    busy = true;
    confirm.setEnabled(false);
    summary.setText("Calculando trajeto…");
    LatLng destination = selected, start = origin;
    String transport = profile;
    final JSONObject[] route = new JSONObject[1];
    PlatformTasks.run(
        this,
        () ->
            route[0] =
                MapServices.route(
                    start.getLatitude(),
                    start.getLongitude(),
                    destination.getLatitude(),
                    destination.getLongitude(),
                    transport),
        () -> {
          try {
            StreetRoute road = StreetRoute.parse(route[0]);
            List<LatLng> points = new ArrayList<>();
            for (StreetRoute.Point point : road.points) points.add(new LatLng(point.latitude, point.longitude));
            marker();
            map.addMarker(new MarkerOptions().position(start).title("Sua posição"));
            map.addPolyline(new PolylineOptions().addAll(points).color(0xFFFFFFFF).width(10));
            map.addPolyline(new PolylineOptions().addAll(points).color(0xFF0F7667).width(6));
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    new LatLngBounds.Builder().includes(points).build(), PlatformUi.dp(this, 40)));
            summary.setText(
                String.format(
                    Locale.forLanguageTag("pt-BR"),
                    BluetoothPlatform.active() ? "%.1f km • %d min estimados%nRota offline de demonstração · © OpenStreetMap"
                        : "%.1f km • %d min estimados%nRota: OpenRouteService · © OpenStreetMap contributors",
                    road.meters / 1000,
                    Math.max(1, Math.round(road.seconds / 60))));
          } catch (IllegalArgumentException e) {
            summary.setText("Não foi possível interpretar o trajeto. Tente outro destino.");
          }
        },
        () -> {
          busy = false;
          confirm.setEnabled(true);
          if (summary.getText().toString().equals("Calculando trajeto…"))
            summary.setText("Não foi possível calcular o trajeto. Tente novamente.");
        });
  }

  private void stopLocation() {
    handler.removeCallbacks(timeout);
    if (manager != null && listener != null) manager.removeUpdates(listener);
    listener = null;
  }

  @Override
  protected void onStop() {
    stopLocation();
    if (gps != null) gps.setEnabled(true);
    super.onStop();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    if (mapView != null) mapView.lowMemory();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    if (selected != null) {
      out.putDouble("lat", selected.getLatitude());
      out.putDouble("lng", selected.getLongitude());
    }
    Bundle mapState = new Bundle();
    if (mapView != null) mapView.save(mapState);
    out.putBundle("map", mapState);
    super.onSaveInstanceState(out);
  }
}
