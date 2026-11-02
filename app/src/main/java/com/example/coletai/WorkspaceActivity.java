package com.example.coletai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Solicitação gratuita e aceite direto conforme a documentação 1.4. */
public class WorkspaceActivity extends ContaActivity {
  private final java.util.Set<String> presentedOrders = new java.util.HashSet<>();
  private android.widget.Chronometer searchTimer;
  private DeviceLocation deviceLocation;
  private android.location.Location currentLocation;
  private boolean centered;
  private org.maplibre.android.annotations.Icon requestIcon, positionIcon;
  private final androidx.activity.result.ActivityResultLauncher<Intent> locationPicker =
      registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        DeviceLocation.settings(this).edit().putBoolean("automatic", false)
            .putLong("latitude", Double.doubleToLongBits(result.getData().getDoubleExtra("latitude", 0)))
            .putLong("longitude", Double.doubleToLongBits(result.getData().getDoubleExtra("longitude", 0))).apply();
        centered = false;
        deviceLocation.start();
      });
  private SharedPreferences prefs;
  private CollectionRepository repo;
  private ProfileRepository profiles;
  private AdminRecordAdapter adapter;
  private int page = R.id.workHome;
  private LinearLayout body;
  private CollectionMapView homeMap;
  private org.maplibre.android.maps.MapLibreMap nativeMap;
  private final java.util.Map<Long, String> markerRequests = new java.util.HashMap<>();

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    prefs = PlatformStore.open(this);
    profiles = new ProfileRepository(prefs);
    repo = new CollectionRepository(prefs);
    repo.migrateLegacy();
    BluetoothColetai.getInstance(this).pausarSessao();
    setContentView(R.layout.activity_workspace);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.workRoot),
        (v, i) -> {
          androidx.core.graphics.Insets b =
              i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    androidx.core.view.WindowInsetsControllerCompat systemBars = new androidx.core.view.WindowInsetsControllerCompat(getWindow(), findViewById(R.id.workRoot));
    systemBars.setAppearanceLightStatusBars(true);
    systemBars.setAppearanceLightNavigationBars(true);
    ((BottomNavigationView) findViewById(R.id.workNavigation)).setItemActiveIndicatorEnabled(true);
    ((BottomNavigationView) findViewById(R.id.workNavigation)).setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
    body = findViewById(R.id.workBody);
    homeMap =
        new CollectionMapView(
            this,
            state == null ? null : state.getBundle("homeMap"),
            map -> {
              nativeMap = map;
              map.moveCamera(
                  org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                      new org.maplibre.android.geometry.LatLng(-23.538, -46.56), 13));
              map.setOnMarkerClickListener(marker -> {
                String id = markerRequests.get(marker.getId());
                if (id != null) openRequest(id);
                return true;
              });
              updateMap();
            });
    ((android.widget.FrameLayout) findViewById(R.id.workMapContainer)).addView(homeMap);
    findViewById(R.id.workExplore)
        .setOnClickListener(v -> startActivity(new Intent(this, LocationActivity.class)));
    findViewById(R.id.workNotices).setOnClickListener(v -> { page = R.id.workNotifications; render(); });
    findViewById(R.id.workLocation).setOnClickListener(v -> locationSettings());
    findViewById(R.id.workMapContainer).addOnLayoutChangeListener((v, left, top, right, bottom,
        oldLeft, oldTop, oldRight, oldBottom) ->
        ((MaterialButton) findViewById(R.id.workLocation)).setMaxWidth(
            Math.max(PlatformUi.dp(this, 48), right - left - PlatformUi.dp(this, 100))));
    deviceLocation = new DeviceLocation(this, (location, status) -> {
      currentLocation = location;
      ((MaterialButton) findViewById(R.id.workLocation)).setText(status);
      updateMap();
    });
    deviceLocation.request(false);
    adapter =
        new AdminRecordAdapter(row -> {
          if ("notification".equals(row.kind)) {
            PlatformTasks.mutate(
                this,
                () -> repo.markRead(row.data.optString("id")),
                () -> openRequest(row.data.optString("request_id")));
          } else openRequest(row.data.optString("id"));
        });
    RecyclerView list = findViewById(R.id.workList);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
    ((BottomNavigationView) findViewById(R.id.workNavigation))
        .setOnItemSelectedListener(item -> {
          page = item.getItemId();
          if (PlatformGateway.configured())
            PlatformTasks.run(this, () -> PlatformGateway.refresh(prefs), this::render);
          else render();
          return true;
        });
    getSupportFragmentManager()
        .setFragmentResultListener("platform_updated", this, (key, b) -> render());
    if (state != null) {
      page = state.getInt("page", R.id.workHome);
      historyFilter = state.getInt("history_filter", 0);
      presentedOrders.addAll(state.getStringArrayList("presented_orders") == null ? new ArrayList<>() : state.getStringArrayList("presented_orders"));
    }
    new OrderUpdates(this, prefs, this::render);
    ((BottomNavigationView) findViewById(R.id.workNavigation)).setSelectedItemId(page);
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (adapter != null) {
      if (PlatformGateway.configured())
        PlatformTasks.run(this, () -> PlatformGateway.refresh(prefs), this::render);
      else render();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putStringArrayList("presented_orders", new ArrayList<>(presentedOrders));
    out.putInt("page", page);
    out.putInt("history_filter", historyFilter);
    Bundle mapState = new Bundle();
    if (homeMap != null) homeMap.save(mapState);
    out.putBundle("homeMap", mapState);
    super.onSaveInstanceState(out);
  }

  private void render() {
    JSONObject who = profiles.current();
    boolean citizen = "cliente".equals(who.optString("role")), profile = page == R.id.workProfile;
    ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.workToolbar))
        .setSubtitle(AdminText.role(this, who.optString("role")));
    findViewById(R.id.workMapCard).setVisibility(page == R.id.workHome ? View.VISIBLE : View.GONE);
    boolean home = page == R.id.workHome;
    findViewById(R.id.workToolbar).setVisibility(home || profile || page == R.id.workHistory ? View.GONE : View.VISIBLE);
    findViewById(R.id.workContent).setVisibility(home ? View.GONE : View.VISIBLE);
    ((TextView) findViewById(R.id.workGreeting)).setText("OLÁ, " + who.optString("name").split(" ")[0].toUpperCase(java.util.Locale.forLanguageTag("pt-BR")));
    ((TextView) findViewById(R.id.workQuestion)).setText(citizen ? "O que deseja descartar hoje?" : "Vamos fazer a diferença hoje?");
    updateMap();
    findViewById(R.id.workScroll).setVisibility(profile ? View.VISIBLE : View.GONE);
    findViewById(R.id.workList).setVisibility(profile ? View.GONE : View.VISIBLE);
    findViewById(R.id.workEmpty).setVisibility(View.GONE);
    MaterialButton action = findViewById(R.id.workAction);
    action.setVisibility(page == R.id.workHome ? View.VISIBLE : View.GONE);
    action.setText(citizen ? "Fazer pedido" : "Ver coletas disponíveis");
    action.setOnClickListener(v -> {
      if (citizen) newRequest();
      else showHomeOrders();
    });
    ((TextView) findViewById(R.id.workTitle))
        .setText(
            profile
                ? "Perfil"
                : page == R.id.workNotifications
                    ? "Notificações"
                    : page == R.id.workHistory
                        ? "Histórico"
                        : citizen ? "Minhas solicitações" : "Solicitações disponíveis");
    ((TextView) findViewById(R.id.workHint))
        .setText(
            profile
                ? (citizen ? "SUA CONTA" : "CONTA PROFISSIONAL")
                : page == R.id.workNotifications
                    ? "Acompanhe as mudanças nas suas solicitações."
                    : page == R.id.workHistory
                        ? "Consulte o que já foi concluído ou cancelado."
                        : citizen
                            ? "Dê um novo destino aos seus recicláveis."
                            : "Selecione os materiais atendidos e aceite uma solicitação"
                                + " diretamente.");
    renderHomeOrder(who);
    com.google.android.material.chip.ChipGroup historyChips = findViewById(R.id.workHistoryFilters);
    historyChips.setVisibility(page == R.id.workHistory ? View.VISIBLE : View.GONE);
    if (historyChips.getChildCount() == 0) {
      String[] labels = {"Todos", "Concluídos", "Cancelados"};
      for (int i = 0; i < labels.length; i++) {
        final int filter = i;
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(labels[i]);
        chip.setCheckable(true);
        chip.setChecked(i == historyFilter);
        historyChips.addView(chip);
        chip.setOnClickListener(v -> { historyFilter = filter; render(); });
      }
    }
    if (profile) {
      profile(who);
      return;
    }
    List<AdminRecordAdapter.Row> rows = new ArrayList<>();
    if (page == R.id.workNotifications) {
      for (JSONObject n : repo.notifications())
        rows.add(
            new AdminRecordAdapter.Row(
                "notification",
                n,
                n.optString("message"),
                AdminText.date(n.optLong("created_at")),
                n.optBoolean("read") ? "Lida" : "Nova notificação"));
    } else
      for (JSONObject order : repo.orders()) {
        String status = order.optString("status");
        boolean ended = "concluida".equals(status) || "cancelada".equals(status);
        boolean released =
            !citizen
                && !who.optString("id").equals(order.optString("responsible_id"))
                && !"aberta".equals(status);
        if (page == R.id.workHistory && !(ended || released)) continue;
        if (page == R.id.workHistory && historyFilter == 1 && !"concluida".equals(status)) continue;
        if (page == R.id.workHistory && historyFilter == 2 && !"cancelada".equals(status)) continue;
        if (page == R.id.workHome && (ended || released)) continue;
        if (page == R.id.workHome
            && !citizen
            && "aberta".equals(status)
            && rejected(order, who.optString("id"))) continue;
        rows.add(
            new AdminRecordAdapter.Row(
                "request",
                order,
                CollectionRepository.itemSummary(order),
                "Data desejada: "
                    + order.optString("desired_date")
                    + "\n"
                    + order.optString("description"),
                PlatformUi.status(status)));
      }
    adapter.submitList(rows);
    LinearLayout opportunities = findViewById(R.id.workOpportunities);
    opportunities.removeAllViews();
    opportunities.setVisibility(home && !citizen ? View.VISIBLE : View.GONE);
    if (home && !citizen) {
      findViewById(R.id.workGreeting).setVisibility(View.GONE);
      findViewById(R.id.workQuestion).setVisibility(View.GONE);
      action.setVisibility(View.GONE);
      for (int i = 0; i < Math.min(2, rows.size()); i++) {
        AdminRecordAdapter.Row row = rows.get(i);
        LinearLayout card = PlatformUi.card(opportunities);
        card.setBackground(PlatformUi.surface(this, 0xFFF3F7F5, 16));
        PlatformUi.text(card, row.title, 14, true).setTextColor(0xFF0F7667);
        TextView preview = PlatformUi.text(card, row.data.optString("description"), 12, false);
        preview.setMaxLines(1);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        preview.setTextColor(0xFF7B8B98);
        card.setFocusable(true);
        card.setOnClickListener(v -> openRequest(row.data.optString("id")));
      }
    } else {
      findViewById(R.id.workGreeting).setVisibility(View.VISIBLE);
      findViewById(R.id.workQuestion).setVisibility(View.VISIBLE);
    }
    ((TextView) findViewById(R.id.workOrders)).setText(rows.isEmpty() ? "Suas próximas coletas começam aqui" : "●  " + rows.size() + (citizen ? " solicitações em andamento · Ver" : " coletas disponíveis · Ver"));
    findViewById(R.id.workOrders).setVisibility(citizen ? View.GONE : View.VISIBLE);
    findViewById(R.id.workOrders).setOnClickListener(v -> showHomeOrders());
    if (rows.isEmpty()) {
      TextView empty = findViewById(R.id.workEmpty);
      empty.setText(
          page == R.id.workNotifications
              ? "Você ainda não possui notificações."
              : citizen
                  ? "Nenhuma solicitação nesta lista. Suas coletas aparecerão aqui."
                  : "Nenhuma solicitação compatível. Confira os materiais que você aceita"
                      + " coletar.");
      empty.setVisibility(View.VISIBLE);
    }
  }

  private int historyFilter;

  @Override protected void onStop() {
    if (searchTimer != null) searchTimer.stop();
    super.onStop();
  }

  private void renderHomeOrder(JSONObject who) {
    if (searchTimer != null) { searchTimer.stop(); searchTimer = null; }
    android.widget.FrameLayout panel = findViewById(R.id.workOrderPanel);
    panel.removeAllViews();
    boolean home = page == R.id.workHome, citizen = "cliente".equals(who.optString("role"));
    JSONObject current = null, offered = null;
    for (JSONObject candidate : repo.orders()) {
      if (CollectionRepository.ended(candidate)) continue;
      if (citizen || who.optString("id").equals(candidate.optString("responsible_id"))) {
        if (current == null) current = candidate;
      } else if ("aberta".equals(candidate.optString("status")) && !rejected(candidate, who.optString("id")) && offered == null) offered = candidate;
    }
    boolean show = home && current != null;
    panel.setVisibility(show ? View.VISIBLE : View.GONE);
    for (int view : new int[]{R.id.workGreeting, R.id.workQuestion, R.id.workAction})
      findViewById(view).setVisibility(show ? View.GONE : View.VISIBLE);
    if (show) {
      JSONObject selected = current;
      getLayoutInflater().inflate(R.layout.view_home_order, panel, true);
      String status = current.optString("status");
      boolean searching = "aberta".equals(status) || "negociando".equals(status);
      ((TextView) panel.findViewById(R.id.orderHomeStatus)).setText(citizen && searching ? "Procurando um coletor..." : PlatformUi.status(status));
      String summary = CollectionRepository.itemSummary(current);
      if ("proposta".equals(status)) summary = current.optString("responsible_name") + " - R$ " + current.optString("proposal_price").replace('.', ',');
      ((TextView) panel.findViewById(R.id.orderHomeSummary)).setText(summary);
      searchTimer = panel.findViewById(R.id.orderHomeTimer);
      searchTimer.setVisibility(citizen && searching ? View.VISIBLE : View.GONE);
      if (citizen && searching) {
        long elapsed = Math.max(0, System.currentTimeMillis() - current.optLong("created_at", System.currentTimeMillis()));
        searchTimer.setBase(android.os.SystemClock.elapsedRealtime() - elapsed);
        searchTimer.start();
      }
      MaterialButton open = panel.findViewById(R.id.orderHomeOpen);
      open.setText("proposta".equals(status) && citizen ? "Ver proposta" : "negociando".equals(status) && !citizen ? "Escrever proposta" : "Acompanhar pedido");
      open.setOnClickListener(v -> openRequest(selected.optString("id")));
      MaterialButton cancel = panel.findViewById(R.id.orderHomeCancel);
      cancel.setVisibility(citizen ? View.VISIBLE : View.GONE);
      cancel.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setMessage("Cancelar este pedido?")
          .setNegativeButton("Voltar", null).setPositiveButton("Cancelar pedido", (d, n) ->
              PlatformTasks.mutate(this, () -> repo.transition(selected.optString("id"), "cancel", selected.optInt("revision")), this::render)).show());
    }
    JSONObject automatic = !citizen ? current != null ? current : offered : current != null && "proposta".equals(current.optString("status")) ? current : null;
    if (home && automatic != null) {
      String requestId = automatic.optString("id");
      String key = requestId + (citizen ? ":proposal:" + automatic.optLong("proposed_at") : "");
      findViewById(R.id.workRoot).post(() -> {
        if (page != R.id.workHome || isFinishing() || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            || getSupportFragmentManager().getFragments().stream().anyMatch(f -> f instanceof androidx.fragment.app.DialogFragment && f.isAdded())
            || !presentedOrders.add(key)) return;
        openRequest(requestId);
      });
    }
  }

  private org.maplibre.android.annotations.Icon mapIcon(boolean current) {
    if (current && positionIcon != null) return positionIcon;
    if (!current && requestIcon != null) return requestIcon;
    int width = PlatformUi.dp(this, current ? 40 : 30), height = PlatformUi.dp(this, current ? 40 : 38);
    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
    if (current) {
      android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      paint.setColor(0x29115E52);
      canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint);
      paint.setColor(android.graphics.Color.WHITE);
      canvas.drawCircle(width / 2f, height / 2f, width * .29f, paint);
      paint.setColor(0xFF0B3D37);
      canvas.drawCircle(width / 2f, height / 2f, width * .23f, paint);
      paint.setColor(android.graphics.Color.WHITE);
      canvas.drawCircle(width / 2f, height / 2f, width * .09f, paint);
    } else {
      android.graphics.drawable.Drawable pin = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_map_pin);
      pin.setBounds(0, 0, width, height);
      pin.draw(canvas);
    }
    org.maplibre.android.annotations.Icon icon = org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(bitmap);
    if (current) positionIcon = icon;
    else requestIcon = icon;
    return icon;
  }

  private void updateMap() {
    if (nativeMap == null || repo == null) return;
    nativeMap.clear();
    markerRequests.clear();
    org.maplibre.android.annotations.Icon pinIcon = mapIcon(false);
    java.util.List<org.maplibre.android.geometry.LatLng> points = new ArrayList<>();
    for (JSONObject order : repo.orders()) {
      String status = order.optString("status");
      if ("concluida".equals(status) || "cancelada".equals(status)) continue;
      JSONObject a = order.optJSONObject("address");
      if (a == null) continue;
      double lat = a.optDouble("latitude", Double.NaN), lng = a.optDouble("longitude", Double.NaN);
      try {
        MapServices.validPoint(lat, lng);
      } catch (IllegalArgumentException ignored) {
        continue;
      }
      org.maplibre.android.geometry.LatLng point =
          new org.maplibre.android.geometry.LatLng(lat, lng);
      points.add(point);
      org.maplibre.android.annotations.Marker marker =
          nativeMap.addMarker(
              new org.maplibre.android.annotations.MarkerOptions()
                  .position(point)
                  .icon(pinIcon)
                  .title(CollectionRepository.itemSummary(order)));
      markerRequests.put(marker.getId(), order.optString("id"));
    }
    if (currentLocation != null) {
      org.maplibre.android.geometry.LatLng here = new org.maplibre.android.geometry.LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
      nativeMap.addMarker(new org.maplibre.android.annotations.MarkerOptions().position(here).icon(mapIcon(true)).title(DeviceLocation.automatic(this) ? "Você está aqui" : "Local escolhido"));
      if (!centered) {
        nativeMap.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(here, 15));
        centered = true;
      }
      return;
    }
    if (points.size() == 1)
      nativeMap.moveCamera(
          org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(points.get(0), 14));
    else if (points.size() > 1)
      nativeMap.moveCamera(
          org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
              new org.maplibre.android.geometry.LatLngBounds.Builder().includes(points).build(),
              PlatformUi.dp(this, 32)));
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    if (homeMap != null) homeMap.lowMemory();
  }

  private boolean rejected(JSONObject o, String account) {
    JSONArray values = o.optJSONArray("rejected_by");
    if (values == null) return false;
    for (int i = 0; i < values.length(); i++) if (account.equals(values.optString(i))) return true;
    return false;
  }

  private void materials() {
    List<JSONObject> materials = repo.materials(false);
    String[] names = new String[materials.size()];
    boolean[] selected = new boolean[names.length];
    JSONArray old = repo.acceptedMaterials();
    for (int i = 0; i < names.length; i++) {
      names[i] = materials.get(i).optString("name");
      for (int j = 0; j < old.length(); j++)
        if (materials.get(i).optString("id").equals(old.optString(j))) selected[i] = true;
    }
    new MaterialAlertDialogBuilder(this)
        .setTitle("Materiais atendidos")
        .setMultiChoiceItems(names, selected, (d, n, on) -> selected[n] = on)
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Salvar",
            (d, n) -> {
              JSONArray ids = new JSONArray();
              for (int i = 0; i < selected.length; i++)
                if (selected[i]) ids.put(materials.get(i).optString("id"));
              PlatformTasks.mutate(this, () -> repo.acceptedMaterials(ids), this::render);
            })
        .show();
  }

  private void openRequest(String id) {
    try {
      if (!CollectionRepository.ended(repo.order(id))) {
        startActivity(new Intent(this, OrderFlowActivity.class).putExtra("request_id", id));
        return;
      }
      JSONObject request = repo.order(id), who = profiles.current();
      String status = request.optString("status");
      boolean citizen = "cliente".equals(who.optString("role")),
          assigned = who.optString("id").equals(request.optString("responsible_id"));
      ScrollView scroll = new ScrollView(this);
      LinearLayout form = PlatformUi.column(this);
      int pad = PlatformUi.dp(this, 20);
      form.setPadding(pad, 0, pad, pad);
      scroll.addView(form);
      PlatformUi.text(form, PlatformUi.status(status), 19, true);
      PlatformUi.text(form, "Data desejada: " + request.optString("desired_date"), 14, false);
      PlatformUi.text(form, CollectionRepository.itemSummary(request), 17, true);
      PlatformUi.text(form, request.optString("description"), 15, false);
      RequestPhotosView.section(form, request.optJSONArray("photos"));
      PlatformUi.text(form, request.optString("notes"), 14, false);
      PlatformUi.text(
          form, CollectionRepository.addressText(request.optJSONObject("address")), 14, false);
      if (!request.optString("responsible_name").isEmpty())
        PlatformUi.text(form, "Responsável: " + request.optString("responsible_name"), 14, false);
      if (request.has("review"))
        PlatformUi.text(
            form,
            "Avaliação: "
                + request.optJSONObject("review").optInt("score")
                + "/5\n"
                + request.optJSONObject("review").optString("comment"),
            14,
            false);
      androidx.appcompat.app.AlertDialog dialog =
          new MaterialAlertDialogBuilder(this)
              .setTitle("Solicitação #" + AdminText.shortId(id))
              .setView(scroll)
              .setNegativeButton("Voltar", null)
              .create();
      if (citizen && "aberta".equals(status))
        PlatformUi.button(
            form,
            "Editar solicitação",
            () -> {
              dialog.dismiss();
              RequestFormDialog.create(id).show(getSupportFragmentManager(), "request_form");
            });
      if (citizen && !("concluida".equals(status) || "cancelada".equals(status)))
        PlatformUi.button(
            form,
            "Cancelar solicitação",
            () -> confirm(dialog, request, "cancel", "Cancelar esta solicitação?"));
      if (!citizen && "aberta".equals(status)) {
        PlatformUi.button(
            form,
            "Aceitar coleta",
            () ->
                confirm(
                    dialog,
                    request,
                    "accept",
                    "Você assumirá a responsabilidade por esta coleta."));
        PlatformUi.button(
            form,
            "Recusar solicitação",
            () ->
                confirm(
                    dialog,
                    request,
                    "reject",
                    "A solicitação continuará disponível para outros responsáveis."));
      }
      if (assigned && "aceita".equals(status))
        PlatformUi.button(
            form,
            "Estou a caminho",
            () -> confirm(dialog, request, "going", "Confirmar início do deslocamento?"));
      if (assigned && "a_caminho".equals(status))
        PlatformUi.button(
            form,
            "Confirmar coleta realizada",
            () ->
                confirm(
                    dialog,
                    request,
                    "complete",
                    "Confirma que os materiais foram coletados? Essa ação conclui a solicitação."));
      if (assigned && ("aceita".equals(status) || "a_caminho".equals(status)))
        PlatformUi.button(
            form,
            "Cancelar meu atendimento",
            () ->
                confirm(
                    dialog,
                    request,
                    "release",
                    "O pedido será reaberto para outros coletores. A solicitação do cidadão não"
                        + " será cancelada."));
      if (citizen && "concluida".equals(status) && !request.has("review"))
        PlatformUi.button(
            form,
            "Avaliar coleta",
            () -> {
              dialog.dismiss();
              review(request);
            });
      PlatformUi.button(
          form,
          "Ver localização / abrir rota",
          () -> {
            JSONObject a = request.optJSONObject("address");
            Intent intent =
                new Intent(this, LocationActivity.class)
                    .putExtra("address", CollectionRepository.addressText(a));
            if (a != null && a.has("latitude") && a.has("longitude")) {
              intent.putExtra("latitude", a.optDouble("latitude"));
              intent.putExtra("longitude", a.optDouble("longitude"));
            }
            startActivity(intent);
          });
      dialog.show();
      render();
    } catch (RuntimeException e) {
      android.widget.Toast.makeText(this, e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
      render();
    }
  }

  private void confirm(
      androidx.appcompat.app.AlertDialog parent, JSONObject request, String action, String text) {
    new MaterialAlertDialogBuilder(this)
        .setMessage(text)
        .setNegativeButton("Voltar", null)
        .setPositiveButton(
            "Confirmar",
            (d, n) -> {
              PlatformTasks.mutate(
                  this,
                  () ->
                      repo.transition(request.optString("id"), action, request.optInt("revision")),
                  () -> {
                    parent.dismiss();
                    render();
                  });
            })
        .show();
  }

  private void review(JSONObject request) {
    ReviewDialog.create(request.optString("id")).show(getSupportFragmentManager(), ReviewDialog.TAG);
  }

  private void newRequest() {
    if (currentLocation != null) {
      RequestFormDialog.createAt(currentLocation).show(getSupportFragmentManager(), "request_form");
      return;
    }
    new MaterialAlertDialogBuilder(this).setTitle("Local da coleta")
        .setMessage("Sua posi\u00e7\u00e3o ainda n\u00e3o est\u00e1 dispon\u00edvel. Ative o GPS e permita a localiza\u00e7\u00e3o ou escolha um endere\u00e7o cadastrado.")
        .setPositiveButton("Usar GPS", (d, n) -> deviceLocation.request(true))
        .setNeutralButton("Usar endere\u00e7o cadastrado", (d, n) -> RequestFormDialog.create("").show(getSupportFragmentManager(), "request_form"))
        .setNegativeButton("Voltar", null).show();
  }

  private void locationSettings() {
    if (getSupportFragmentManager().findFragmentByTag("location_settings") == null)
      new LocationSettingsDialog().show(getSupportFragmentManager(), "location_settings");
  }

  void useGpsLocation() {
    DeviceLocation.settings(this).edit().putBoolean("automatic", true).apply();
    centered = false;
    deviceLocation.request(true);
  }

  void chooseLocationOnMap() {
    Intent intent = new Intent(this, LocationActivity.class).putExtra("pick", true);
    if (currentLocation != null) {
      intent.putExtra("latitude", currentLocation.getLatitude());
      intent.putExtra("longitude", currentLocation.getLongitude());
    }
    locationPicker.launch(intent);
  }

  private void showHomeOrders() {
    List<JSONObject> orders = new ArrayList<>();
    for (JSONObject order : repo.orders()) {
      String status = order.optString("status");
      boolean citizen = "cliente".equals(profiles.current().optString("role"));
      String account = profiles.current().optString("id");
      if (!citizen && !"aberta".equals(status) && !account.equals(order.optString("responsible_id"))) continue;
      if (!"concluida".equals(status) && !"cancelada".equals(status)
          && !("aberta".equals(status) && !citizen && rejected(order, account))) orders.add(order);
    }
    String[] names = new String[orders.size()];
    for (int i = 0; i < names.length; i++) names[i] = CollectionRepository.itemSummary(orders.get(i)) + " · " + PlatformUi.status(orders.get(i).optString("status"));
    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this).setTitle("Coletas em andamento").setNegativeButton("Fechar", null);
    if (orders.isEmpty()) dialog.setMessage("Nenhuma coleta disponível no momento.");
    else dialog.setItems(names, (d, i) -> openRequest(orders.get(i).optString("id")));
    dialog.show();
  }

  private void profile(JSONObject who) {
    body.removeAllViews();
    boolean citizen = "cliente".equals(who.optString("role"));
    LinearLayout identity = PlatformUi.card(body);
    identity.setOrientation(LinearLayout.HORIZONTAL);
    identity.setGravity(android.view.Gravity.CENTER_VERTICAL);
    TextView avatar = new TextView(this);
    StringBuilder initials = new StringBuilder();
    for (String part : who.optString("name").trim().split("\\s+"))
      if (!part.isEmpty() && initials.length() < 2) initials.append(part.charAt(0));
    avatar.setText(initials.toString().toUpperCase(java.util.Locale.forLanguageTag("pt-BR")));
    avatar.setTextSize(22);
    avatar.setTextColor(0xFF0F7667);
    avatar.setGravity(android.view.Gravity.CENTER);
    avatar.setBackground(PlatformUi.surface(this, 0xFFEAF8F3, 32));
    identity.addView(avatar, new LinearLayout.LayoutParams(PlatformUi.dp(this, 60), PlatformUi.dp(this, 60)));
    LinearLayout identityText = PlatformUi.column(this);
    identityText.setPadding(PlatformUi.dp(this, 14), 0, 0, 0);
    identity.addView(identityText, new LinearLayout.LayoutParams(0, -2, 1));
    PlatformUi.text(identityText, who.optString("name"), 18, true);
    TextView email = PlatformUi.text(identityText, who.optString("email"), 12, false);
    email.setTextColor(0xFF7B8B98);
    PlatformUi.text(identityText, AdminText.role(this, who.optString("role")), 11, false).setTextColor(0xFF0F7667);
    if (!citizen) {
      int completed = 0;
      int ratings = who.optInt("rating_count");
      double average = who.optDouble("rating_average", 0);
      java.math.BigDecimal earnings = java.math.BigDecimal.ZERO;
      for (JSONObject order : repo.orders()) {
        if ("concluida".equals(order.optString("status"))
            && who.optString("id").equals(order.optString("responsible_id"))) {
          completed++;
          try { earnings = earnings.add(new java.math.BigDecimal(order.optString("proposal_price", "0"))); }
          catch (NumberFormatException ignored) { }
        }
      }
      LinearLayout stats = PlatformUi.column(this);
      stats.setOrientation(LinearLayout.HORIZONTAL);
      body.addView(stats, new LinearLayout.LayoutParams(-1, -2));
      profileStat(stats, ratings == 0 ? "—" : String.format(java.util.Locale.forLanguageTag("pt-BR"), "★ %.1f", average), "AVALIAÇÃO", true);
      profileStat(stats, String.valueOf(completed), "COLETAS", false);
      profileStat(stats, java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(earnings), "GANHOS", false);
      PlatformUi.text(body, ratings == 0 ? "Ainda não há avaliações." : "Média de " + ratings + (ratings == 1 ? " avaliação" : " avaliações"), 12, false).setTextColor(0xFF6F817D);
    }
    PlatformUi.section(body, "DADOS PESSOAIS");
    LinearLayout personal = PlatformUi.card(body);
    profileRow(personal, who, "name", citizen ? "Nome completo" : "Nome", InputType.TYPE_CLASS_TEXT);
    profileRow(personal, who, "document", "empresa".equals(who.optString("role")) ? "CNPJ" : "CPF", InputType.TYPE_CLASS_TEXT);
    if ("empresa".equals(who.optString("role"))) {
      profileRow(personal, who, "legal_name", "Razão social", InputType.TYPE_CLASS_TEXT);
      profileRow(personal, who, "company", "Nome fantasia", InputType.TYPE_CLASS_TEXT);
    }
    profileRow(personal, who, "phone", "Telefone", InputType.TYPE_CLASS_PHONE);
    profileRow(personal, who, "email", "E-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    PlatformUi.button(
        personal,
        "Alterar senha",
        () -> {
          LinearLayout form = PlatformUi.column(this);
          int pad = PlatformUi.dp(this, 20);
          form.setPadding(pad, 0, pad, pad);
          TextInputEditText
              current =
                  PlatformUi.field(
                      form,
                      "Senha atual",
                      "",
                      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD),
              next =
                  PlatformUi.field(
                      form,
                      "Nova senha (mínimo 8 caracteres)",
                      "",
                      InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
          androidx.appcompat.app.AlertDialog dialog =
              new MaterialAlertDialogBuilder(this)
                  .setTitle("Alterar senha")
                  .setView(form)
                  .setNegativeButton("Voltar", null)
                  .setPositiveButton("Salvar", null)
                  .create();
          dialog.setOnShowListener(d ->
              dialog
                  .getButton(-1)
                  .setOnClickListener(v ->
                      PlatformTasks.run(
                          this,
                          () ->
                              profiles.changePassword(
                                  PlatformUi.value(current), PlatformUi.value(next)),
                          dialog::dismiss)));
          dialog.show();
        });

    PlatformUi.section(body, citizen ? "ENDEREÇOS" : "PREFERÊNCIAS DE COLETA");
    LinearLayout preferences = PlatformUi.card(body);
    if (citizen) {
      for (JSONObject saved : repo.addresses()) {
        MaterialButton addressButton = PlatformUi.button(preferences,
            saved.optString("nickname") + " · " + CollectionRepository.addressText(saved),
            () -> AddressEditorDialog.create(saved).show(getSupportFragmentManager(), "address_editor"));
        addressButton.setTextColor(0xFF102522);
        addressButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        addressButton.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
      }
      PlatformUi.button(preferences, "+ Adicionar endereço", () ->
          AddressEditorDialog.create(new JSONObject()).show(getSupportFragmentManager(), "address_editor"));
    } else PlatformUi.button(preferences, "Materiais que aceito coletar", this::materials);
    PlatformUi.button(preferences, "Configurações de localização", this::locationSettings);
    PlatformUi.button(preferences, "Apresentação offline por Bluetooth", () -> startActivity(new Intent(this, OfflineBluetoothActivity.class)));
    MaterialButton logout = PlatformUi.button(
        body,
        "Sair da conta",
        () -> {
          LocalJson.commit(prefs.edit().remove("logged_in_account"));
          Runnable exit =
              () ->
                  startActivity(
                      new Intent(this, MainActivity.class)
                          .addFlags(
                              Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
          if (PlatformGateway.configured())
            PlatformTasks.run(this, () -> PlatformGateway.logout(prefs), exit);
          else exit.run();
        });
    logout.setTextColor(0xFFDC5B67);
    logout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFF1F2));
    logout.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFFFDEE2));
    logout.setStrokeWidth(PlatformUi.dp(this, 1));
  }

  private void profileRow(LinearLayout card, JSONObject who, String field, String label, int inputType) {
    LinearLayout row = PlatformUi.column(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setMinimumHeight(PlatformUi.dp(this, 54));
    card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    TextView title = PlatformUi.text(row, label, 13, false);
    title.setTextColor(0xFF7B8B98);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    TextView value = PlatformUi.text(row, who.optString(field).isEmpty() ? "Adicionar  ›" : who.optString(field) + "  ›", 13, false);
    value.setMaxLines(2);
    value.setEllipsize(android.text.TextUtils.TruncateAt.END);
    value.setGravity(android.view.Gravity.END);
    value.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.5f));
    row.setBackgroundResource(android.R.drawable.list_selector_background);
    row.setFocusable(true);
    row.setContentDescription("Editar " + label + ": " + who.optString(field));
    row.setOnClickListener(v -> {
      LinearLayout form = PlatformUi.column(this);
      int pad = PlatformUi.dp(this, 20);
      form.setPadding(pad, 0, pad, pad);
      TextInputEditText input = PlatformUi.field(form, label, who.optString(field), inputType);
      androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
          .setTitle("Editar " + label.toLowerCase(java.util.Locale.forLanguageTag("pt-BR")))
          .setView(form).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
      dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(button -> {
        JSONObject changed = LocalJson.object(who.toString());
        LocalJson.put(changed, field, PlatformUi.value(input));
        PlatformTasks.mutate(this, () -> profiles.save(changed, false), () -> { dialog.dismiss(); render(); });
      }));
      dialog.show();
    });
    View divider = new View(this);
    divider.setBackgroundColor(0xFFF0F4F2);
    card.addView(divider, new LinearLayout.LayoutParams(-1, PlatformUi.dp(this, 1)));
  }

  private void profileStat(LinearLayout parent, String value, String label, boolean highlighted) {
    LinearLayout card = PlatformUi.column(this);
    card.setGravity(android.view.Gravity.CENTER);
    card.setPadding(PlatformUi.dp(this, 4), PlatformUi.dp(this, 10), PlatformUi.dp(this, 4), PlatformUi.dp(this, 10));
    card.setBackground(PlatformUi.surface(this, highlighted ? 0xFF0F7667 : 0xFFFFFFFF, 12));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
    params.setMargins(PlatformUi.dp(this, 3), 0, PlatformUi.dp(this, 3), PlatformUi.dp(this, 10));
    parent.addView(card, params);
    TextView amount = PlatformUi.text(card, value, 16, true);
    amount.setTextColor(highlighted ? 0xFFFFFFFF : 0xFF102522);
    amount.setGravity(android.view.Gravity.CENTER);
    TextView caption = PlatformUi.text(card, label, 9, false);
    caption.setTextSize(11);
    caption.setGravity(android.view.Gravity.CENTER);
    caption.setTextColor(highlighted ? 0xFFCDEDE4 : 0xFF7B8B98);
  }
}
