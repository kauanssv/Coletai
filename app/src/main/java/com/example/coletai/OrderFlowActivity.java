package com.example.coletai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;

/** Restores the original full-screen request and proposal screens on the current account model. */
public class OrderFlowActivity extends ContaActivity {
  private SharedPreferences prefs;
  private CollectionRepository repo;
  private JSONObject order;
  private String id, rendered = "";
  private boolean busy;
  private CollectionMapView mapView;
  private org.maplibre.android.maps.MapLibreMap map;
  private Bundle mapState;
  private DeviceLocation disposalGps;
  private android.location.Location collectorLocation;
  private DisposalPoint suggestedDisposal;
  private TextView disposalInfo;
  private MaterialButton disposalRoute;
  private String disposalStatus = "Buscando sua localização…", mapFocus = "";
  private long lastDisposalRequest;
  private boolean findingDisposal;
  private String disposalTile = "";
  private final java.util.concurrent.ExecutorService roadWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
  private StreetRoute streetRoute;
  private android.location.Location routeOrigin;
  private String routeKey = "", routeMessage = "", routeProfile = "driving-car";
  private long routeRequestedAt;
  private volatile int routeGeneration;
  private boolean routeFetching, routeForeground;
  private final ActivityResultLauncher<Intent> disposalPicker = registerForActivityResult(
      new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        JSONObject point = new JSONObject();
        LocalJson.put(point, "latitude", result.getData().getDoubleExtra("latitude", 0));
        LocalJson.put(point, "longitude", result.getData().getDoubleExtra("longitude", 0));
        LocalJson.put(point, "automatic", false);
        prefs.edit().putString(disposalKey(), point.toString()).apply();
        rendered = "";
        render();
        route(point);
      });

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    prefs = PlatformStore.open(this);
    repo = new CollectionRepository(prefs);
    id = getIntent().getStringExtra("request_id");
    mapState = state == null ? null : state.getBundle("flow_map");
    if (PedidoRules.coletor(new ProfileRepository(prefs).current().optString("role")))
      disposalGps = new DeviceLocation(this, (location, status) -> {
        collectorLocation = location;
        if (location == null) { disposalStatus = status + ". A busca de pontos usa sua posição atual."; updateDisposalUi(); }
        else findNearestDisposal();
        updateMap();
      }, BluetoothPlatform.active());
    render();
    getSupportFragmentManager().setFragmentResultListener("platform_updated", this, (key, result) -> { rendered = ""; render(); });
    new OrderUpdates(this, prefs, this::render);
    getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() { back(); }
    });
  }

  private void back() {
    if (busy) return;
    if (order != null && assigned() && "negociando".equals(order.optString("status")))
      new MaterialAlertDialogBuilder(this).setMessage("Sair sem enviar a proposta? O pedido voltar\u00e1 a procurar um coletor.")
          .setNegativeButton("Continuar escrevendo", null).setPositiveButton("Sair", (d, n) -> change("release", true)).show();
    else finish();
  }

  private String disposalKey() { return "disposal_" + LocalJson.account(prefs) + "_" + id; }

  private void render() {
    if (isFinishing() || busy) return;
    try { order = repo.order(id); }
    catch (RuntimeException e) {
      android.widget.Toast.makeText(this, "Este pedido não está mais disponível.", android.widget.Toast.LENGTH_LONG).show();
      finish();
      return;
    }
    String version = id + ":" + order.optInt("revision");
    if (version.equals(rendered)) return;
    rendered = version;
    disposalInfo = null; disposalRoute = null;
    boolean citizen = "cliente".equals(new ProfileRepository(prefs).current().optString("role"));
    String status = order.optString("status");
    if (!citizen && "aberta".equals(status)) details();
    else if (!citizen && "negociando".equals(status) && assigned()) proposal();
    else progress(citizen);
  }

  private boolean assigned() { return LocalJson.account(prefs).equals(order.optString("responsible_id")); }

  private void bars(int root) {
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(root), (v, insets) -> {
      androidx.core.graphics.Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
      v.setPadding(b.left, b.top, b.right, b.bottom);
      return WindowInsetsCompat.CONSUMED;
    });
    androidx.core.view.WindowInsetsControllerCompat controller = new androidx.core.view.WindowInsetsControllerCompat(getWindow(), findViewById(root));
    controller.setAppearanceLightStatusBars(true);
    controller.setAppearanceLightNavigationBars(true);
    ViewCompat.requestApplyInsets(findViewById(root));
  }

  private void text(int view, String value) { ((TextView) findViewById(view)).setText(value); }

  static String region(JSONObject order) {
    JSONObject a = order.optJSONObject("address");
    if (a == null) return "Região não informada";
    return a.optString("district") + " · " + a.optString("city") + "/" + a.optString("state");
  }

  private void details() {
    setContentView(R.layout.activity_detalhes_pedido_ambulante);
    bars(R.id.main);
    text(R.id.txtTagTipo, "NOVO PEDIDO");
    text(R.id.txtEmojiTipo, "♻");
    text(R.id.txtTipoPedido, CollectionRepository.itemSummary(order));
    text(R.id.txtLocalizacaoCabecalho, region(order));
    text(R.id.txtDescricaoPedido, order.optString("description"));
    RequestPhotosView.display(findViewById(R.id.orderPhotos), order.optJSONArray("photos"));
    text(R.id.txtPesoPedido, totalWeight());
    text(R.id.txtRegiaoPedido, region(order));
    text(R.id.txtLocalizacaoPedido, "O endereço completo será liberado após o cliente aceitar sua proposta.");
    text(R.id.txtObservacaoPedido, order.optString("notes") + "\nData desejada: " + order.optString("desired_date") + "\nCliente: " + order.optString("citizen_name"));
    findViewById(R.id.cardObservacao).setVisibility(View.VISIBLE);
    findViewById(R.id.btnVoltarDetalhes).setOnClickListener(v -> finish());
    text(R.id.btnEnviarProposta, "Aceitar pedido");
    findViewById(R.id.btnEnviarProposta).setOnClickListener(v -> change("accept", false));
    findViewById(R.id.btnRecusar).setOnClickListener(v -> confirm("Recusar este pedido?", "reject", true));
  }

  private String totalWeight() {
    java.math.BigDecimal total = java.math.BigDecimal.ZERO;
    org.json.JSONArray items = order.optJSONArray("items");
    if (items != null) for (int i = 0; i < items.length(); i++) {
      try { total = total.add(new java.math.BigDecimal(items.optJSONObject(i).optString("weight"))); }
      catch (NumberFormatException ignored) { }
    }
    return total.stripTrailingZeros().toPlainString() + " kg";
  }

  private void proposal() {
    setContentView(R.layout.activity_enviar_proposta_ambulante);
    bars(R.id.main);
    text(R.id.txtEmoji, "♻");
    text(R.id.txtResumoPedido, CollectionRepository.itemSummary(order));
    text(R.id.txtLocalizacao, region(order));
    findViewById(R.id.btnVoltar).setOnClickListener(v -> back());
    EditText price = findViewById(R.id.edtPreco), message = findViewById(R.id.edtMensagem);
    findViewById(R.id.btnEnviarProposta).setOnClickListener(v -> {
      try { DecimalValues.positive(price.getText().toString(), 2); }
      catch (IllegalArgumentException e) { price.setError("Informe um valor positivo com até duas casas decimais"); return; }
      busy = true;
      findViewById(R.id.btnEnviarProposta).setEnabled(false);
      String value = price.getText().toString(), body = message.getText().toString();
      PlatformTasks.mutate(this, () -> repo.propose(id, order.optInt("revision"), value, body),
          () -> { busy = false; render(); },
          () -> { busy = false; View button = findViewById(R.id.btnEnviarProposta); if (button != null) button.setEnabled(true); });
    });
  }

  private void progress(boolean citizen) {
    setContentView(R.layout.activity_order_flow);
    bars(R.id.orderFlowRoot);
    String status = order.optString("status");
    MaterialToolbar toolbar = findViewById(R.id.orderFlowToolbar);
    toolbar.setTitle("proposta".equals(status) ? citizen ? "Nova proposta" : "Proposta enviada" : "Acompanhar pedido");
    toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    toolbar.setNavigationOnClickListener(v -> finish());
    LinearLayout body = findViewById(R.id.orderFlowBody);
    String title = PlatformUi.status(status), instruction = "";
    int step = 0;
    if ("proposta".equals(status)) {
      title = citizen ? "Confira a proposta" : "Proposta enviada";
      instruction = citizen ? "Confira o valor e a mensagem do coletor antes de aceitar."
          : "Aguarde a resposta do cliente para iniciar a coleta.";
    } else if (assigned() && !citizen) {
      switch (status) {
        case "aceita": step = 1; title = "Coleta confirmada";
          instruction = "O cliente aceitou. Quando sair para a coleta, toque em Ir até o cliente."; break;
        case "a_caminho": step = 2; title = "A caminho do cliente";
          instruction = "Siga até o endereço da coleta e avise quando chegar."; break;
        case "coletando": step = 3; title = "No local da coleta";
          instruction = "Confira os materiais com o cliente e confirme a retirada."; break;
        case "descartando": step = 4; title = "Hora de descartar";
          instruction = "Leve os materiais a um ponto de descarte. Finalize a coleta após entregá-los."; break;
      }
    }
    if (step > 0) PlatformUi.section(body, "ETAPA " + step + " DE 4");
    PlatformUi.text(body, title, 22, true);
    if (!instruction.isEmpty()) PlatformUi.text(body, instruction, 14, false).setTextColor(0xFF6F817D);
    PlatformUi.section(body, "MATERIAIS");
    LinearLayout summary = PlatformUi.card(body);
    PlatformUi.text(summary, CollectionRepository.itemSummary(order), 17, true);
    PlatformUi.text(summary, order.optString("description"), 15, false);
    RequestPhotosView.section(body, order.optJSONArray("photos"));
    String date = order.optString("desired_date");
    if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) date = date.substring(8) + "/" + date.substring(5, 7) + "/" + date.substring(0, 4);
    PlatformUi.text(summary, "Cliente: " + order.optString("citizen_name") + "\nData da coleta: " + date, 14, false);
    if (citizen && !order.optString("responsible_name").isEmpty()) PlatformUi.text(summary, "Coletor: " + order.optString("responsible_name"), 15, true);
    if (!order.optString("proposal_price").isEmpty()) {
      PlatformUi.section(body, "proposta".equals(status) ? "VALOR DA PROPOSTA" : "VALOR COMBINADO");
      LinearLayout proposal = PlatformUi.card(body);
      PlatformUi.text(proposal, "R$ " + order.optString("proposal_price").replace('.', ','), 24, true);
      if (!order.optString("proposal_message").trim().isEmpty()) {
        PlatformUi.text(proposal, "Mensagem do coletor", 12, true).setTextColor(0xFF6F817D);
        PlatformUi.text(proposal, order.optString("proposal_message"), 15, false);
      }
    }
    if (!order.optString("notes").trim().isEmpty()) {
      PlatformUi.section(body, "OBSERVAÇÕES DO CLIENTE");
      PlatformUi.text(PlatformUi.card(body), order.optString("notes"), 14, false);
    }
    if (CollectionRepository.approved(order) || citizen) {
      PlatformUi.section(body, "LOCAL DA COLETA");
      LinearLayout location = PlatformUi.card(body);
      PlatformUi.text(location, CollectionRepository.addressText(order.optJSONObject("address")), 14, false);
      if (CollectionRepository.approved(order)) {
        attachMap();
        if (assigned() && ("aceita".equals(status) || "a_caminho".equals(status)))
          routeButton(location, "Ver rota até o cliente", () -> route(order.optJSONObject("address")));
      }
    } else PlatformUi.text(body, "O endereço do cliente será liberado quando ele aceitar sua proposta.", 14, false);
    MaterialButton primary = findViewById(R.id.orderFlowPrimary), secondary = findViewById(R.id.orderFlowSecondary);
    primary.setVisibility(View.GONE);
    secondary.setVisibility(View.GONE);
    if (citizen && "proposta".equals(status)) {
      action(primary, "Aceitar proposta", () -> confirm("Aceitar esta proposta pelo valor informado?", "approve", false));
      action(secondary, "Recusar proposta", () -> confirm("Recusar e continuar procurando outro coletor?", "decline", false));
    } else if (citizen && !CollectionRepository.ended(order)) {
      action(secondary, "Cancelar pedido", () -> confirm("Cancelar este pedido?", "cancel", false));
    } else if (assigned() && !CollectionRepository.ended(order)) {
      if ("aceita".equals(status)) action(primary, "Ir até o cliente", () -> change("going", false));
      if ("a_caminho".equals(status)) action(primary, "Cheguei ao local", () -> change("arrive", false));
      if ("coletando".equals(status)) action(primary, "Confirmar coleta", () -> confirm("Você já retirou os materiais com o cliente?", "collect", false));
      if ("descartando".equals(status)) {
        action(primary, "Finalizar coleta", () -> confirm("Você já entregou os materiais no ponto de descarte?", "complete", false));
      }
      action(secondary, "Sair desta coleta", () -> confirm("Esta coleta ficará disponível para outro coletor. Deseja sair?", "release", true));
      if ("descartando".equals(status)) {
        PlatformUi.section(body, "LOCAL DE DESCARTE");
        LinearLayout disposalCard = PlatformUi.card(body);
        disposalInfo = PlatformUi.text(disposalCard, disposalStatus, 14, false);
        disposalRoute = routeButton(disposalCard, "Ver rota até o descarte", () -> {
          JSONObject destination = LocalJson.object(prefs.getString(disposalKey(), "{}"));
          if (destination.has("latitude")) route(destination);
        });
        routeButton(disposalCard, "Escolher outro ponto", () -> disposalPicker.launch(new Intent(this, LocationActivity.class).putExtra("pick", true)));
        updateDisposalUi();
        findNearestDisposal();
      }
    }
    if (CollectionRepository.ended(order)) {
      PlatformUi.text(body, "concluida".equals(status) ? "Coleta e descarte finalizados. Obrigado por ajudar o meio ambiente!" : "Este pedido foi cancelado.", 16, false);
      JSONObject review = order.optJSONObject("review");
      if (review != null) {
        PlatformUi.section(body, citizen ? "SUA AVALIAÇÃO" : "AVALIAÇÃO DO CLIENTE");
        LinearLayout rating = PlatformUi.card(body);
        PlatformUi.text(rating, ReviewStats.stars(review.optInt("score")) + " · " + review.optInt("score") + " de 5", 20, true).setTextColor(0xFFB77910);
        PlatformUi.text(rating, review.optString("comment"), 14, false);
      }
      if (citizen && ReviewDialog.pending(order)) {
        action(primary, "Avaliar coleta", () -> ReviewDialog.create(id).show(getSupportFragmentManager(), ReviewDialog.TAG));
        action(secondary, "Voltar ao início", this::finish);
        secondary.setTextColor(0xFF0F7667);
        ReviewDialog.promptOnce(this, order);
      } else action(primary, "Voltar ao início", this::finish);
    }

  }

  private MaterialButton routeButton(LinearLayout parent, String title, Runnable action) {
    MaterialButton button = PlatformUi.button(parent, title, action);
    button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
    button.setTextColor(new android.content.res.ColorStateList(
        new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
        new int[] {0xFF97A6A1, 0xFF0F7667}));
    button.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFDCE7E3));
    button.setStrokeWidth(PlatformUi.dp(this, 1));
    return button;
  }

  private void action(MaterialButton button, String title, Runnable action) {
    button.setVisibility(View.VISIBLE);
    button.setText(title);
    button.setOnClickListener(v -> action.run());
  }

  private void confirm(String message, String action, boolean close) {
    new MaterialAlertDialogBuilder(this).setMessage(message).setNegativeButton("Voltar", null)
        .setPositiveButton("Confirmar", (d, n) -> change(action, close)).show();
  }

  private void change(String action, boolean close) {
    if (busy) return;
    busy = true;
    PlatformTasks.mutate(this, () -> repo.transition(id, action, order.optInt("revision")),
        () -> {
          busy = false;
          if (close) finish();
          else {
            if ("going".equals(action) || "collect".equals(action)) lastDisposalRequest = 0;
            render();
            if (("going".equals(action) || "collect".equals(action)) && disposalGps != null && !disposalGps.allowed()) disposalGps.request(true);
          }
        }, () -> busy = false);
  }

  private void route(JSONObject point) {
    if (point == null) return;
    Intent intent = new Intent(this, LocationActivity.class);
    if (assigned()) intent.putExtra("force_gps", true);
    boolean disposal = point.has("automatic") || (point.has("source") && !"device_location".equals(point.optString("source")));
    intent.putExtra("destination_name", disposal ? point.optString("name", "Local de descarte") : "Local da coleta");
    if (point.has("latitude") && point.has("longitude")) intent.putExtra("latitude", point.optDouble("latitude")).putExtra("longitude", point.optDouble("longitude"));
    else intent.putExtra("address", CollectionRepository.addressText(point));
    startActivity(intent);
  }

  private boolean disposalStage() {
    return order != null && assigned() && java.util.Arrays.asList("a_caminho", "coletando", "descartando").contains(order.optString("status"));
  }

  private void findNearestDisposal() {
    if (!disposalStage() || collectorLocation == null || isFinishing()) return;
    double lat = collectorLocation.getLatitude(), lng = collectorLocation.getLongitude();
    long now = System.currentTimeMillis();
    DisposalRepository.Result saved = DisposalMapLayer.repository(this).cached(lat, lng, now);
    if (saved.updated != 0) selectNearest(saved);
    String tile = DisposalRepository.tile(lat, lng);
    if (!saved.stale || findingDisposal || (tile.equals(disposalTile) && now - lastDisposalRequest < 60000)) return;
    findingDisposal = true; lastDisposalRequest = now; disposalTile = tile;
    disposalStatus = "Buscando um ponto para estes materiais perto de você…";
    updateDisposalUi();
    DisposalMapLayer.nearby(this, lat, lng, result -> {
      findingDisposal = false;
      if (isFinishing() || isDestroyed() || !disposalStage() || collectorLocation == null) return;
      if (!tile.equals(DisposalRepository.tile(collectorLocation.getLatitude(), collectorLocation.getLongitude()))) { findNearestDisposal(); return; }
      selectNearest(result);
    });
  }

  private void selectNearest(DisposalRepository.Result result) {
    if (collectorLocation == null || !disposalStage()) return;
    suggestedDisposal = DisposalPoint.nearest(result.points, order, collectorLocation.getLatitude(), collectorLocation.getLongitude());
    JSONObject previous = LocalJson.object(prefs.getString(disposalKey(), "{}"));
    boolean manual = previous.has("latitude") && !previous.optBoolean("automatic", false);
    if (suggestedDisposal != null) {
      double distance = DisposalPoint.distance(collectorLocation.getLatitude(), collectorLocation.getLongitude(), suggestedDisposal.latitude, suggestedDisposal.longitude);
      disposalStatus = suggestedDisposal.name + String.format(java.util.Locale.forLanguageTag("pt-BR"), "\nA %.1f km de você (em linha reta).", distance / 1000)
          + (result.stale ? "\nInformações salvas. Confirme os horários e os materiais aceitos." : "\nConfira os horários e os materiais aceitos antes de ir.");
      if (!manual) {
        JSONObject point = suggestedDisposal.json();
        LocalJson.put(point, "automatic", true);
        prefs.edit().putString(disposalKey(), point.toString()).apply();
      }
    } else {
      disposalStatus = result.error.isEmpty()
          ? "Não encontramos um ponto para estes materiais perto de você. Escolha um ponto no mapa."
          : "Não foi possível buscar um ponto. Confira sua conexão ou escolha no mapa.";
      if (previous.optBoolean("automatic", false)) prefs.edit().remove(disposalKey()).apply();
    }
    if (manual) disposalStatus = "Ponto escolhido no mapa.\nConfira os horários e os materiais aceitos antes de ir.";
    updateDisposalUi();
    updateMap();
  }

  private void updateDisposalUi() {
    JSONObject destination = LocalJson.object(prefs.getString(disposalKey(), "{}"));
    if (disposalInfo != null) disposalInfo.setText(destination.has("latitude") && !destination.optBoolean("automatic")
        ? "Ponto escolhido no mapa.\nConfira os horários e os materiais aceitos antes de ir." : disposalStatus);
    if (disposalRoute != null) {
      JSONObject selected = LocalJson.object(prefs.getString(disposalKey(), "{}"));
      disposalRoute.setEnabled(selected.has("latitude") && (!selected.optBoolean("automatic") || collectorLocation != null));
    }
  }

  private void attachMap() {
    FrameLayout container = findViewById(R.id.orderFlowMap);
    container.setVisibility(View.VISIBLE);
    container.getLayoutParams().height = PlatformUi.dp(this, Math.min(220, getResources().getConfiguration().screenHeightDp / 4));
    if (mapView == null) mapView = new CollectionMapView(this, mapState, loaded -> { map = loaded; updateMap(); });
    if (mapView.getParent() != null) ((android.view.ViewGroup) mapView.getParent()).removeView(mapView);
    container.addView(mapView);
    updateMap();
  }

  private void updateMap() {
    if (map == null || order == null) return;
    JSONObject point = order.optJSONObject("address");
    boolean disposalStage = "descartando".equals(order.optString("status"));
    JSONObject disposal = LocalJson.object(prefs.getString(disposalKey(), "{}"));
    if (disposalStage && disposal.has("latitude")) point = disposal;
    map.clear();
    java.util.List<org.maplibre.android.geometry.LatLng> positions = new java.util.ArrayList<>();
    if (point != null) {
      double lat = point.optDouble("latitude", Double.NaN), lng = point.optDouble("longitude", Double.NaN);
      try {
        MapServices.validPoint(lat, lng);
        org.maplibre.android.geometry.LatLng location = new org.maplibre.android.geometry.LatLng(lat, lng);
        positions.add(location);
        map.addMarker(new org.maplibre.android.annotations.MarkerOptions().position(location).icon(orderIcon()).title(disposalStage && disposal.has("latitude") ? "Local de descarte" : "Local da coleta"));
      } catch (IllegalArgumentException ignored) { }
    }
    if (disposalStage && suggestedDisposal != null) {
      org.maplibre.android.geometry.LatLng destination = new org.maplibre.android.geometry.LatLng(suggestedDisposal.latitude, suggestedDisposal.longitude);
      positions.add(destination);
      map.addMarker(new org.maplibre.android.annotations.MarkerOptions().position(destination).icon(positionIcon(true)).title("Descarte mais próximo · " + suggestedDisposal.name));
    }
    if (assigned() && collectorLocation != null && CollectionRepository.approved(order) && !CollectionRepository.ended(order)) {
      org.maplibre.android.geometry.LatLng here = new org.maplibre.android.geometry.LatLng(collectorLocation.getLatitude(), collectorLocation.getLongitude());
      positions.add(here);
      map.addMarker(new org.maplibre.android.annotations.MarkerOptions().position(here).icon(positionIcon(false)).title("GPS atual do coletor"));
    }
    previewRoadRoute(disposalStage ? (disposal.has("latitude") ? disposal : null) : point, positions);
    String focus = order.optString("status") + ":" + (suggestedDisposal == null ? "" : suggestedDisposal.id) + ":" + disposal.optDouble("latitude", 0) + ":" + disposal.optDouble("longitude", 0);
    if (positions.isEmpty() || focus.equals(mapFocus)) return;
    mapFocus = focus;
    if (positions.size() == 1) map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(positions.get(0), 16));
    else map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(new org.maplibre.android.geometry.LatLngBounds.Builder().includes(positions).build(), PlatformUi.dp(this, 32)));
  }

  private boolean routeActive() {
    return order != null && assigned() && CollectionRepository.approved(order) && !CollectionRepository.ended(order);
  }

  private void previewRoadRoute(JSONObject destination, java.util.List<org.maplibre.android.geometry.LatLng> positions) {
    TextView info = findViewById(R.id.orderFlowRouteInfo);
    if (info == null) return;
    info.setVisibility(routeActive() ? View.VISIBLE : View.GONE);
    if (!routeActive()) { clearStreetRoute(); return; }
    info.setOnClickListener(v -> {
      if (collectorLocation == null) { if (disposalGps != null) disposalGps.request(true); return; }
      if (routeFetching) return;
      if (!routeFetching && routeMessage.startsWith("Não foi possível")) { clearStreetRoute(); updateMap(); return; }
      clearStreetRoute();
      mapFocus = "";
      updateMap();
    });
    if (collectorLocation == null) {
      clearStreetRoute();
      info.setText("Ative sua localização para ver a rota. Toque aqui.");
      return;
    }
    double lat = destination == null ? Double.NaN : destination.optDouble("latitude", Double.NaN);
    double lng = destination == null ? Double.NaN : destination.optDouble("longitude", Double.NaN);
    try { MapServices.validPoint(lat, lng); }
    catch (IllegalArgumentException error) {
      clearStreetRoute();
      info.setText("descartando".equals(order.optString("status"))
          ? "Escolha um ponto de descarte para ver a rota."
          : "Use Ver rota até o cliente para localizar o endereço.");
      info.setOnClickListener(v -> {
        if ("descartando".equals(order.optString("status")))
          disposalPicker.launch(new Intent(this, LocationActivity.class).putExtra("pick", true));
        else route(order.optJSONObject("address"));
      });
      return;
    }
    if (!BluetoothPlatform.active() && BuildConfig.ORS_API_KEY.trim().isEmpty()) {
      clearStreetRoute();
      info.setText("O cálculo de rotas não está disponível nesta versão.");
      return;
    }
    String key = ("descartando".equals(order.optString("status")) ? "descarte" : "cliente") + ":" + lat + ":" + lng + ":" + routeProfile;
    if (!key.equals(routeKey)) { clearStreetRoute(); routeKey = key; }
    if (streetRoute != null) {
      java.util.List<org.maplibre.android.geometry.LatLng> road = new java.util.ArrayList<>();
      for (StreetRoute.Point vertex : streetRoute.points)
        road.add(new org.maplibre.android.geometry.LatLng(vertex.latitude, vertex.longitude));
      map.addPolyline(new org.maplibre.android.annotations.PolylineOptions().addAll(road).color(0xFFFFFFFF).width(9));
      map.addPolyline(new org.maplibre.android.annotations.PolylineOptions().addAll(road).color(0xFF0F7667).width(5));
      positions.addAll(road);
    }
    info.setText(routeMessage.isEmpty() ? "Calculando rota pelas ruas…" : routeMessage);
    long now = android.os.SystemClock.elapsedRealtime();
    boolean moved = routeOrigin == null || collectorLocation.distanceTo(routeOrigin) >= 75;
    if (!routeForeground || routeFetching || (routeRequestedAt != 0 && now - routeRequestedAt < 30000)
        || (streetRoute != null && !moved)) return;
    routeFetching = true;
    routeRequestedAt = now;
    routeOrigin = new android.location.Location(collectorLocation);
    android.location.Location start = new android.location.Location(collectorLocation);
    String transport = routeProfile;
    int generation = routeGeneration;
    routeMessage = streetRoute == null ? "Calculando rota pelas ruas…" : "Atualizando o percurso…";
    info.setText(routeMessage);
    final double targetLat = lat, targetLng = lng;
    roadWorker.execute(() -> {
      if (generation != routeGeneration || Thread.currentThread().isInterrupted()) return;
      StreetRoute result = null;
      String failure = null;
      try { result = StreetRoute.parse(MapServices.route(start.getLatitude(), start.getLongitude(), targetLat, targetLng, transport)); }
      catch (RuntimeException error) { failure = "Não foi possível traçar a rota. Toque para tentar novamente."; }
      final StreetRoute completed = result;
      final String error = failure;
      runOnUiThread(() -> {
        if (isDestroyed() || isFinishing() || !routeForeground || generation != routeGeneration) return;
        routeFetching = false;
        streetRoute = completed;
        if (error == null) {
          String mode = "driving-car".equals(transport) ? "Carro" : "cycling-regular".equals(transport) ? "Bicicleta" : "A pé";
          routeMessage = String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f km · %d min estimados · %s",
              completed.meters / 1000, Math.max(1, Math.round(completed.seconds / 60)), mode)
              + (BluetoothPlatform.active() ? " · Offline (demonstração)" : "");
        } else routeMessage = error;
        mapFocus = "";
        updateMap();
      });
    });
  }

  private void clearStreetRoute() {
    routeGeneration++;
    routeKey = "";
    streetRoute = null;
    routeOrigin = null;
    routeRequestedAt = 0;
    routeFetching = false;
    routeMessage = "";
  }

  private org.maplibre.android.annotations.Icon requestIcon;
  private org.maplibre.android.annotations.Icon disposalIcon, gpsIcon;
  private org.maplibre.android.annotations.Icon positionIcon(boolean destination) {
    if (destination && disposalIcon != null) return disposalIcon;
    if (!destination && gpsIcon != null) return gpsIcon;
    int size = PlatformUi.dp(this, 36);
    android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
    android.graphics.Canvas canvas = new android.graphics.Canvas(image);
    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    paint.setColor(destination ? 0x55F0B94B : 0x55276A9A);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    paint.setColor(android.graphics.Color.WHITE);
    canvas.drawCircle(size / 2f, size / 2f, size * .35f, paint);
    paint.setColor(destination ? 0xFFB77910 : 0xFF276A9A);
    canvas.drawCircle(size / 2f, size / 2f, size * .27f, paint);
    paint.setColor(android.graphics.Color.WHITE);
    canvas.drawCircle(size / 2f, size / 2f, size * .10f, paint);
    org.maplibre.android.annotations.Icon icon = org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(image);
    if (destination) disposalIcon = icon; else gpsIcon = icon;
    return icon;
  }
  private org.maplibre.android.annotations.Icon orderIcon() {
    if (requestIcon != null) return requestIcon;
    android.graphics.drawable.Drawable drawable = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_map_pin);
    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(PlatformUi.dp(this, 30), PlatformUi.dp(this, 38), android.graphics.Bitmap.Config.ARGB_8888);
    drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
    drawable.draw(new android.graphics.Canvas(bitmap));
    requestIcon = org.maplibre.android.annotations.IconFactory.getInstance(this).fromBitmap(bitmap);
    return requestIcon;
  }

  @Override protected void onStart() { super.onStart(); routeForeground = true; updateMap(); }
  @Override protected void onStop() {
    routeForeground = false;
    collectorLocation = null;
    clearStreetRoute();
    super.onStop();
  }

  @Override protected void onDestroy() { roadWorker.shutdownNow(); super.onDestroy(); }

  @Override protected void onSaveInstanceState(Bundle out) {
    if (mapView != null) { Bundle saved = new Bundle(); mapView.save(saved); out.putBundle("flow_map", saved); }
    super.onSaveInstanceState(out);
  }

  @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.lowMemory(); }
}
