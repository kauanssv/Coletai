package com.example.coletai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Console local independente do fluxo visual de clientes e coletores. */
public class AdminActivity extends AppCompatActivity {
  private SharedPreferences prefs;
  private ProfileRepository profiles;
  private AdminRepository admin;
  private AdminRecordAdapter adapter;
  private int page = R.id.adminNavOverview;
  private String filter = "all";
  private TextInputEditText search;
  private List<JSONObject> accounts = new ArrayList<>(),
      orders = new ArrayList<>(),
      audits = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable searchTask = this::renderList;

  @Override
  protected void onCreate(@Nullable Bundle state) {
    super.onCreate(state);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    prefs = PlatformStore.open(this);
    profiles = new ProfileRepository(prefs);
    admin = new AdminRepository(prefs);
    try {
      profiles.requireAdmin();
    } catch (RuntimeException e) {
      exit();
      return;
    }
    BluetoothColetai.getInstance(this).pausarSessao();
    setContentView(R.layout.activity_admin);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.adminRoot),
        (view, insets) -> {
          androidx.core.graphics.Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
          view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    search = findViewById(R.id.adminSearch);
    adapter = new AdminRecordAdapter(this::open);
    RecyclerView list = findViewById(R.id.adminList);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
    findViewById(R.id.adminExit).setOnClickListener(v -> exit());
    findViewById(R.id.adminBluetooth).setOnClickListener(v -> startActivity(new android.content.Intent(this, OfflineBluetoothActivity.class)));
    findViewById(R.id.adminNewProfile)
        .setOnClickListener(v -> {
          if (page == R.id.adminNavCatalog) editMaterial(new JSONObject());
          else AdminEditorDialog.profile("").show(getSupportFragmentManager(), "admin_editor");
        });
    findViewById(R.id.adminInfo).setOnClickListener(v -> editInformation());
    findViewById(R.id.adminCardProfiles)
        .setOnClickListener(v -> navigate(R.id.adminNavProfiles, "active"));
    findViewById(R.id.adminCardOpen).setOnClickListener(v -> navigate(R.id.adminNavOrders, "open"));
    findViewById(R.id.adminCardDone).setOnClickListener(v -> navigate(R.id.adminNavOrders, "done"));
    findViewById(R.id.adminCardTrash)
        .setOnClickListener(v ->
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_trash)
                .setItems(
                    new String[] {
                      getString(R.string.admin_profiles), getString(R.string.admin_orders)
                    },
                    (dialog, which) ->
                        navigate(
                            which == 0 ? R.id.adminNavProfiles : R.id.adminNavOrders, "deleted"))
                .show());
    BottomNavigationView nav = findViewById(R.id.adminNavigation);
    nav.setOnItemSelectedListener(item -> {
      if (page != item.getItemId()) {
        page = item.getItemId();
        filter = "all";
        search.setText("");
      }
      renderPage();
      return true;
    });
    search.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            handler.removeCallbacks(searchTask);
            handler.postDelayed(searchTask, 150);
          }

          @Override
          public void afterTextChanged(Editable value) {}
        });
    getSupportFragmentManager()
        .setFragmentResultListener("admin_updated", this, (key, result) -> reload());
    if (state != null) {
      page = state.getInt("page", R.id.adminNavOverview);
      filter = state.getString("filter", "all");
      search.setText(state.getString("query", ""));
    }
    nav.setSelectedItemId(page);
    getSupportFragmentManager()
        .setFragmentResultListener("platform_updated", this, (key, result) -> reload());
    new OrderUpdates(this, prefs, this::reload);
    reload();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (adapter != null) {
      if (PlatformGateway.configured())
        PlatformTasks.run(this, () -> PlatformGateway.refresh(prefs), this::reload);
      else reload();
    }
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacks(searchTask);
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putInt("page", page);
    out.putString("filter", filter);
    out.putString(
        "query", search == null || search.getText() == null ? "" : search.getText().toString());
    super.onSaveInstanceState(out);
  }

  private void navigate(int target, String wanted) {
    ((BottomNavigationView) findViewById(R.id.adminNavigation)).setSelectedItemId(target);
    filter = wanted;
    renderPage();
  }

  private void reload() {
    try {
      profiles.requireAdmin();
      accounts = profiles.list();
      orders = admin.orders();
      audits = new AuditRepository(prefs).list();
      renderPage();
    } catch (RuntimeException e) {
      new MaterialAlertDialogBuilder(this)
          .setTitle(R.string.admin_error)
          .setMessage(e.getMessage())
          .setPositiveButton(android.R.string.ok, null)
          .show();
    }
  }

  private void renderPage() {
    ((com.google.android.material.textfield.TextInputLayout) findViewById(R.id.adminSearchLayout))
        .setHint(
            page == R.id.adminNavCatalog
                ? "Buscar material"
                : page == R.id.adminNavProfiles
                    ? "Buscar nome ou e-mail"
                    : page == R.id.adminNavOrders
                        ? "Buscar material, cidadão ou ID"
                        : "Buscar ação ou responsável");
    boolean overview = page == R.id.adminNavOverview;
    findViewById(R.id.adminOverview).setVisibility(overview ? View.VISIBLE : View.GONE);
    findViewById(R.id.adminListPanel).setVisibility(overview ? View.GONE : View.VISIBLE);
    findViewById(R.id.adminNewProfile)
        .setVisibility(
            page == R.id.adminNavProfiles || page == R.id.adminNavCatalog
                ? View.VISIBLE
                : View.GONE);
    ((TextView) findViewById(R.id.adminNewProfile))
        .setText(
            page == R.id.adminNavCatalog ? "Novo material" : getString(R.string.admin_new_profile));
    findViewById(R.id.adminInfo)
        .setVisibility(page == R.id.adminNavCatalog ? View.VISIBLE : View.GONE);
    ((TextView) findViewById(R.id.adminPageTitle))
        .setText(
            overview
                ? R.string.admin_overview
                : page == R.id.adminNavCatalog
                    ? R.string.platform_catalog
                    : page == R.id.adminNavProfiles
                        ? R.string.admin_profiles
                        : page == R.id.adminNavOrders
                            ? R.string.admin_orders
                            : R.string.admin_audit);
    ((TextView) findViewById(R.id.adminPageHint))
        .setText(
            overview
                ? R.string.admin_overview_hint
                : page == R.id.adminNavCatalog
                    ? R.string.platform_catalog_hint
                    : page == R.id.adminNavProfiles
                        ? R.string.admin_profiles_hint
                        : page == R.id.adminNavOrders
                            ? R.string.admin_orders_hint
                            : R.string.admin_audit_hint);
    if (overview) {
      renderOverview();
      return;
    }
    ChipGroup group = findViewById(R.id.adminFilters);
    group.removeAllViews();
    String[][] options =
        page == R.id.adminNavCatalog
            ? new String[][] {{"all", "Todos"}, {"active", "Ativos"}, {"inactive", "Inativos"}}
            : page == R.id.adminNavProfiles
                ? new String[][] {
                  {"all", getString(R.string.admin_all)},
                  {"active", getString(R.string.admin_active)},
                  {"blocked", getString(R.string.admin_blocked)},
                  {"deleted", getString(R.string.admin_trash)}
                }
                : page == R.id.adminNavOrders
                    ? new String[][] {
                      {"all", getString(R.string.admin_all)},
                      {"open", getString(R.string.admin_open_orders)},
                      {"done", getString(R.string.admin_completed)},
                      {"cancelado", getString(R.string.admin_cancelled)},
                      {"deleted", getString(R.string.admin_trash)}
                    }
                    : new String[][] {
                      {"all", getString(R.string.admin_all)},
                      {"profile", getString(R.string.admin_profiles)},
                      {"order", getString(R.string.admin_orders)},
                      {"material", "Materiais"},
                      {"information", "Plataforma"}
                    };
    for (String[] option : options) {
      Chip chip = new Chip(this);
      chip.setId(View.generateViewId());
      chip.setText(option[1]);
      chip.setCheckable(true);
      group.addView(chip);
      chip.setChecked(filter.equals(option[0]));
      chip.setOnClickListener(v -> {
        filter = option[0];
        renderList();
      });
    }
    renderList();
  }

  private void renderOverview() {
    int active = 0, open = 0, done = 0, trash = 0;
    for (JSONObject p : accounts) {
      if ("active".equals(p.optString("status"))) active++;
      if ("deleted".equals(p.optString("status"))) trash++;
    }
    for (JSONObject order : orders) {
      if (order.optBoolean("deleted")) {
        trash++;
        continue;
      }
      if (AdminText.open(order)) open++;
      if (AdminText.completed(order))
        done++;
    }
    ((TextView) findViewById(R.id.adminStatProfiles)).setText(String.valueOf(active));
    ((TextView) findViewById(R.id.adminStatOpen)).setText(String.valueOf(open));
    ((TextView) findViewById(R.id.adminStatDone)).setText(String.valueOf(done));
    ((TextView) findViewById(R.id.adminStatTrash)).setText(String.valueOf(trash));
    LinearLayout recent = findViewById(R.id.adminRecent);
    recent.removeAllViews();
    if (audits.isEmpty()) {
      TextView empty = new TextView(this);
      empty.setPadding(12, 16, 12, 16);
      empty.setText(R.string.admin_recent_empty);
      recent.addView(empty);
    }
    for (int i = 0; i < Math.min(4, audits.size()); i++) {
      JSONObject item = audits.get(i);
      View card = getLayoutInflater().inflate(R.layout.item_admin_record, recent, false);
      ((TextView) card.findViewById(R.id.adminRowTitle)).setText(AdminText.action(item));
      ((TextView) card.findViewById(R.id.adminRowStatus))
          .setText(AdminText.date(item.optLong("data")));
      ((TextView) card.findViewById(R.id.adminRowSubtitle)).setText(auditSubtitle(item));
      card.setOnClickListener(v -> openAudit(item));
      recent.addView(card);
    }
  }

  private void renderList() {
    if (adapter == null || page == R.id.adminNavOverview) return;
    String query = normalized(search.getText() == null ? "" : search.getText().toString());
    List<AdminRecordAdapter.Row> rows = new ArrayList<>();
    List<JSONObject> data =
        page == R.id.adminNavCatalog
            ? new CollectionRepository(prefs).materials(true)
            : page == R.id.adminNavProfiles
                ? accounts
                : page == R.id.adminNavOrders ? orders : audits;
    for (JSONObject item : data) {
      if (!matches(item)) continue;
      if (page == R.id.adminNavCatalog)
        rows.add(
            new AdminRecordAdapter.Row(
                "material",
                item,
                item.optString("name"),
                item.optString("description"),
                item.optBoolean("active") ? "Ativo" : "Inativo"));
      else if (page == R.id.adminNavProfiles)
        rows.add(
            new AdminRecordAdapter.Row(
                "profile",
                item,
                item.optString("name"),
                item.optString("email"),
                getString(
                    R.string.admin_account_detail,
                    AdminText.role(this, item.optString("role")),
                    AdminText.profileStatus(this, item.optString("status")))));
      else if (page == R.id.adminNavOrders)
        rows.add(
            new AdminRecordAdapter.Row(
                "order",
                item,
                "Pedido #" + AdminText.shortId(item.optString("id")),
                item.optString("account_name") + "\n" + item.optString("pedido_tipo"),
                AdminText.orderStatus(this, item)));
      else
        rows.add(
            new AdminRecordAdapter.Row(
                "audit",
                item,
                AdminText.action(item),
                auditSubtitle(item),
                AdminText.date(item.optLong("data"))));
    }
    rows.removeIf(row -> !normalized(row.title + " " + row.subtitle + " " + row.status + " " + row.data.toString()).contains(query));
    adapter.submitList(
        rows, () -> ((RecyclerView) findViewById(R.id.adminList)).scrollToPosition(0));
    ((TextView) findViewById(R.id.adminCount))
        .setText(getResources().getQuantityString(R.plurals.admin_count, rows.size(), rows.size()));
    findViewById(R.id.adminEmpty).setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
  }

  private boolean matches(JSONObject item) {
    if ("all".equals(filter)) return page != R.id.adminNavOrders || !item.optBoolean("deleted");
    if (page == R.id.adminNavCatalog) return item.optBoolean("active") == "active".equals(filter);
    if (page == R.id.adminNavProfiles) return filter.equals(item.optString("status"));
    if (page == R.id.adminNavAudit) {
      String entity = item.optString("entity");
      return filter.equals(entity) || ("material".equals(filter) && CollectionRepository.MATERIALS.equals(entity))
          || ("information".equals(filter) && "platform_information".equals(entity));
    }
    if ("deleted".equals(filter)) return item.optBoolean("deleted");
    if (item.optBoolean("deleted")) return false;
    if ("open".equals(filter)) return AdminText.open(item);
    if ("done".equals(filter)) return AdminText.completed(item);
    if ("cancelado".equals(filter)) return AdminText.cancelled(item);
    return filter.equals(item.optString("status"));
  }

  private void open(AdminRecordAdapter.Row row) {
    if ("material".equals(row.kind)) {
      editMaterial(row.data);
      return;
    }
    if (row.data.optBoolean("v14")) {
      openPlatformOrder(row.data.optString("id"));
      return;
    }
    if ("audit".equals(row.kind)) {
      openAudit(row.data);
      return;
    }
    AdminEditorDialog dialog =
        "profile".equals(row.kind)
            ? AdminEditorDialog.profile(row.data.optString("id"))
            : AdminEditorDialog.order(row.data.optString("account_id"), row.data.optString("id"));
    dialog.show(getSupportFragmentManager(), "admin_editor");
  }

  private void openPlatformOrder(String id) {
    CollectionRepository repo = new CollectionRepository(prefs);
    JSONObject request = repo.order(id);
    String status = request.optString("status");
    java.util.List<String> options = new java.util.ArrayList<>();
    options.add("Consultar dados");
    if (!request.optBoolean("deleted")) options.add("Editar solicitação");
    options.add(
        request.optBoolean("deleted")
            ? "Restaurar solicitação"
            : ("concluida".equals(status) || "cancelada".equals(status))
                ? "Excluir solicitação"
                : "Cancelar solicitação");
    new MaterialAlertDialogBuilder(this)
        .setTitle("Solicitação #" + AdminText.shortId(id))
        .setItems(
            options.toArray(new String[0]),
            (dialog, which) -> {
              if (which == 0) {
                AdminAuditDetails.showOrder(this, request);
                return;
              }
              if (which == 1 && !request.optBoolean("deleted")) {
                RequestFormDialog.create(id).show(getSupportFragmentManager(), "request_form");
                return;
              }
              new MaterialAlertDialogBuilder(this)
                  .setTitle(options.get(which) + "?")
                  .setMessage(
                      "Pedido #" + AdminText.shortId(id) + ". Os dados anteriores serão preservados na atividade"
                          + " administrativa.")
                  .setNegativeButton("Voltar", null)
                  .setPositiveButton(
                      "Confirmar",
                      (d, n) -> {
                        PlatformTasks.mutate(
                            this,
                            () -> {
                              if (request.optBoolean("deleted")) repo.setDeleted(id, false);
                              else if ("concluida".equals(status) || "cancelada".equals(status))
                                repo.setDeleted(id, true);
                              else repo.transition(id, "cancel", request.optInt("revision"));
                            },
                            this::reload);
                      })
                  .show();
            })
        .show();
  }

  private void openAudit(JSONObject item) {
    AdminAuditDetails.show(this, item, actor(item.optString("actor")));
  }

  private String auditSubtitle(JSONObject item) {
    return actor(item.optString("actor")) + " · " + AdminText.entity(item.optString("entity"))
        + " #" + AdminText.shortId(item.optString("target"));
  }

  private String actor(String id) {
    for (JSONObject p : accounts) if (id.equals(p.optString("id"))) return p.optString("name");
    return id.isEmpty() ? getString(R.string.admin_role_admin) : id;
  }

  private static String normalized(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  private void exit() {
    LocalJson.commit(PlatformStore.open(this).edit().remove("logged_in_account"));
    startActivity(
        new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    finish();
  }

  private void editMaterial(JSONObject material) {
    AdminCatalogDialog.create(false, material).show(getSupportFragmentManager(), "admin_catalog_editor");
  }

  private void editInformation() {
    AdminCatalogDialog.create(true, new CollectionRepository(prefs).information()).show(getSupportFragmentManager(), "admin_catalog_editor");
  }
}
