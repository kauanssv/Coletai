package com.example.coletai;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;
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
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Executado apenas no emulador, com preservação do armazenamento anterior. */
@RunWith(AndroidJUnit4.class)
public class AdminFlowTest {
  private SharedPreferences prefs;
  private Map<String, ?> backup;
  private ActivityScenario<?> scenario;
  private SharedPreferences devicePrefs;
  private SnapshotPreferences deviceBackup;

  private final androidx.test.espresso.IdlingResource tasks =
      new androidx.test.espresso.IdlingResource() {
        public String getName() {
          return "Operações da plataforma";
        }

        public boolean isIdleNow() {
          return PlatformTasks.idle();
        }

        public void registerIdleTransitionCallback(ResourceCallback callback) {
          PlatformTasks.idleListener = callback::onTransitionToIdle;
        }
      };

  @Before
  public void prepare() {
    assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
    assumeTrue(!PlatformGateway.configured());
    android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    String targetPackage = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    automation.grantRuntimePermission(targetPackage, android.Manifest.permission.ACCESS_COARSE_LOCATION);
    automation.grantRuntimePermission(targetPackage, android.Manifest.permission.ACCESS_FINE_LOCATION);
    androidx.test.espresso.IdlingRegistry.getInstance().register(tasks);
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    devicePrefs = DeviceLocation.settings(context);
    deviceBackup = new SnapshotPreferences(devicePrefs);
    assertTrue(devicePrefs.edit().putBoolean("automatic", false)
        .putLong("latitude", Double.doubleToLongBits(-23.55))
        .putLong("longitude", Double.doubleToLongBits(-46.63)).commit());
    prefs = context.getSharedPreferences("coletai", Context.MODE_PRIVATE);
    backup = prefs.getAll();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> BluetoothColetai.getInstance(context).pausarSessao());
    assertTrue(prefs.edit().clear().commit());
    new ProfileRepository(prefs);
    new PedidoRepository(prefs).selecionarConta("admin", "admin");
  }

  @After
  public void restore() {
    if (scenario != null) scenario.close();
    if (deviceBackup != null) deviceBackup.persistTo(devicePrefs);
    androidx.test.espresso.IdlingRegistry.getInstance().unregister(tasks);
    PlatformTasks.idleListener = null;
    if (prefs == null || backup == null) return;
    SharedPreferences.Editor editor = prefs.edit().clear();
    for (Map.Entry<String, ?> e : backup.entrySet()) {
      Object value = e.getValue();
      String key = e.getKey();
      if (value instanceof String) editor.putString(key, (String) value);
      else if (value instanceof Integer) editor.putInt(key, (Integer) value);
      else if (value instanceof Long) editor.putLong(key, (Long) value);
      else if (value instanceof Float) editor.putFloat(key, (Float) value);
      else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
      else if (value instanceof Set) {
        @SuppressWarnings("unchecked")
        Set<String> set = (Set<String>) value;
        editor.putStringSet(key, set);
      }
    }
    assertTrue(editor.commit());
  }

  @Test
  public void criaContaPreservaRascunhoExcluiRestauraEAuditaPelaInterface() {
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withId(R.id.adminPageTitle)).check(matches(withText(R.string.admin_overview)));
    onView(withId(R.id.adminNavProfiles)).perform(click());
    onView(withId(R.id.adminNewProfile)).perform(click());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_name)))
        .perform(replaceText("Conta Teste"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_email)))
        .perform(replaceText("validacao@exemplo.com"), closeSoftKeyboard());
    scenario.recreate();
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_name)))
        .check(matches(withText("Conta Teste")));
    onView(
            allOf(
                isAssignableFrom(android.widget.EditText.class),
                withHint(R.string.platform_initial_password)))
        .perform(scrollTo(), replaceText("senha-segura"), closeSoftKeyboard());
    onView(withId(android.R.id.button1)).perform(click());
    onView(withId(R.id.adminSearch))
        .perform(replaceText("validacao@exemplo.com"), closeSoftKeyboard());
    settleSearch();
    onView(withText("Conta Teste")).perform(click());
    onView(withText(R.string.admin_delete)).perform(scrollTo(), click());
    onView(withText(R.string.admin_confirm)).perform(click());
    ProfileRepository profiles = new ProfileRepository(prefs);
    JSONObject created = null;
    for (JSONObject p : profiles.list())
      if ("validacao@exemplo.com".equals(p.optString("email"))) created = p;
    assertNotNull(created);
    assertEquals("deleted", created.optString("status"));
    onView(withText("Conta Teste")).perform(click());
    onView(withText(R.string.admin_restore)).perform(scrollTo(), click());
    onView(withText(R.string.admin_confirm)).perform(click());
    assertTrue(profiles.available(created.optString("id")));
    assertEquals(3, new AuditRepository(prefs).list().size());
  }

  private void settleSearch() {
    // A busca tem debounce de 150 ms e o DiffUtil calcula a lista fora da UI.
    onView(withId(R.id.adminList))
        .perform(
            new androidx.test.espresso.ViewAction() {
              @Override
              public org.hamcrest.Matcher<android.view.View> getConstraints() {
                return isDisplayed();
              }

              @Override
              public String getDescription() {
                return "aguardar busca e posicionamento da lista";
              }

              @Override
              public void perform(
                  androidx.test.espresso.UiController controller, android.view.View view) {
                controller.loopMainThreadForAtLeast(500);
              }
            });
  }

  @Test public void loginESaidaDosQuatroPerfisNaoMisturamSessoes() {
    for (String role : new String[]{"cliente", "ambulante", "empresa", "admin"}) {
      assertTrue(prefs.edit().remove("logged_in_account").commit());
      scenario = ActivityScenario.launch(MainActivity.class);
      String email = new ProfileRepository(prefs).get(role).optString("email");
      onView(withId(R.id.edtEmail)).perform(replaceText(email), closeSoftKeyboard());
      onView(withId(R.id.edtSenha)).perform(replaceText("12345"), closeSoftKeyboard());
      onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
      assertEquals(role, new ProfileRepository(prefs).current().optString("role"));
      assertEquals(role, prefs.getString("logged_in_account", ""));
      if ("admin".equals(role)) {
        onView(withId(R.id.adminPageTitle)).check(matches(isDisplayed()));
        onView(withId(R.id.adminExit)).perform(click());
      } else {
        onView(withId(R.id.workProfile)).perform(click());
        onView(withText("Sair da conta")).perform(scrollTo(), click());
      }
      onView(withId(R.id.edtEmail)).check(matches(isDisplayed()));
      assertFalse(prefs.contains("logged_in_account"));
      scenario.close(); scenario = null;
    }
  }

  @Test public void atividadeTraduzAcoesDestacaMudancasEPermiteBuscaEmPortugues() throws Exception {
    JSONObject before = LocalJson.object("{\"status\":\"coletando\",\"description\":\"Caixas separadas\",\"desired_date\":\"2026-11-02\"}");
    JSONObject after = LocalJson.object(before.toString());
    LocalJson.put(after, "status", "descartando");
    SharedPreferences.Editor editor = prefs.edit();
    new AuditRepository(prefs).append(editor, "Pedido: collect", "order", "pedido-auditoria", before, after);
    assertTrue(editor.commit());
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withText("Retirada dos materiais confirmada")).perform(scrollTo(), click());
    onView(withText("O QUE MUDOU")).check(matches(isDisplayed()));
    onView(allOf(withText("Situação"), isDisplayed())).check(matches(isDisplayed()));
    onView(allOf(withText(PlatformUi.status("descartando")), isDisplayed())).check(matches(isDisplayed()));
    screenshotAdmin("audit-changes");
    onView(withText("Ver dados completos")).perform(scrollTo(), click());
    onView(withText("Ocultar dados completos")).check(matches(isDisplayed()));
    androidx.test.espresso.Espresso.pressBack();
    onView(withId(R.id.adminNavAudit)).perform(click());
    onView(withId(R.id.adminSearch)).perform(replaceText("retirada"), closeSoftKeyboard());
    settleSearch();
    onView(allOf(withText("Retirada dos materiais confirmada"), isDisplayed())).check(matches(isDisplayed()));
    screenshotAdmin("audit-list");
  }

  @Test public void catalogoValidaSemFecharPreservaRascunhoESalvaMaterial() throws Exception {
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withId(R.id.adminNavCatalog)).perform(click());
    onView(withId(R.id.adminNewProfile)).perform(click());
    onView(withId(android.R.id.button1)).perform(click());
    onView(withText("Informe o nome para continuar.")).check(matches(isDisplayed()));
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Nome do material")))
        .perform(replaceText("Material de teste"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Descrição")))
        .perform(replaceText("Receber limpo e separado"), closeSoftKeyboard());
    scenario.recreate();
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Nome do material")))
        .check(matches(withText("Material de teste")));
    screenshotAdmin("material-editor");
    onView(withId(android.R.id.button1)).perform(click());
    long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
    while (new CollectionRepository(prefs).materials(true).stream().noneMatch(m -> "Material de teste".equals(m.optString("name")))
        && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50);
    assertTrue(new CollectionRepository(prefs).materials(true).stream().anyMatch(m -> "Material de teste".equals(m.optString("name"))));
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    onView(withId(R.id.adminSearch)).perform(replaceText("Material de teste"), closeSoftKeyboard());
    settleSearch();
    onView(allOf(withId(R.id.adminRowTitle), withText("Material de teste"))).check(matches(isDisplayed()));
    onView(withId(R.id.adminInfo)).perform(click());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Nome da plataforma")))
        .perform(replaceText("Coletaí"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("E-mail de suporte")))
        .perform(scrollTo(), replaceText("invalido"), closeSoftKeyboard());
    onView(withId(android.R.id.button1)).perform(click());
    onView(withText("Informe um e-mail de suporte válido.")).check(matches(isDisplayed()));
  }

  private void screenshotAdmin(String name) throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    java.io.File directory = new java.io.File(context.getExternalFilesDir(null), "admin-review");
    assertTrue(directory.isDirectory() || directory.mkdirs());
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
      assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
    } finally { bitmap.recycle(); }
  }

  @Test public void resumoEFiltrosPreservamEtapasDosPedidosESeparamLixeira() throws Exception {
    org.json.JSONArray orders = new org.json.JSONArray();
    String[] statuses = {"descartando", "concluida", "cancelada", "concluida"};
    for (int i = 0; i < statuses.length; i++) orders.put(new JSONObject()
        .put("id", "pedido-" + i).put("status", statuses[i]).put("citizen_id", "cliente")
        .put("citizen_name", "Cliente de teste").put("responsible_name", "Coletor de teste")
        .put("revision", 1).put("deleted", i == 3).put("items", new org.json.JSONArray())
        .put("created_at", 1000 + i));
    assertTrue(prefs.edit().putString(CollectionRepository.ORDERS, orders.toString()).commit());
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withId(R.id.adminStatOpen)).check(matches(withText("1")));
    onView(withId(R.id.adminStatDone)).check(matches(withText("1")));
    onView(withId(R.id.adminStatTrash)).check(matches(withText("1")));
    onView(withId(R.id.adminCardOpen)).perform(click());
    settleSearch();
    onView(withText(PlatformUi.status("descartando"))).check(matches(isDisplayed()));
    onView(withText("Pedido #pedido-0")).perform(click());
    onView(withText("Consultar dados")).perform(click());
    onView(withText("Coletor de teste")).check(matches(isDisplayed()));
    screenshotAdmin("order-details");
    androidx.test.espresso.Espresso.pressBack();
    onView(allOf(withText(R.string.admin_cancelled), isAssignableFrom(com.google.android.material.chip.Chip.class))).perform(scrollTo(), click());
    settleSearch();
    onView(withText("Pedido #pedido-2")).check(matches(isDisplayed()));
    onView(allOf(withText(R.string.admin_trash), isAssignableFrom(com.google.android.material.chip.Chip.class))).perform(scrollTo(), click());
    settleSearch();
    onView(withText("Pedido #pedido-3")).check(matches(isDisplayed()));
  }

  @Test public void erroDeCadastroFicaNoFormularioSemPerderOsDados() {
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withId(R.id.adminNavProfiles)).perform(click());
    onView(withId(R.id.adminSearch)).perform(replaceText("cliente@gmail.com"), closeSoftKeyboard());
    settleSearch();
    onView(withId(R.id.adminRowTitle)).perform(click());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_email)))
        .perform(scrollTo(), replaceText("invalido"), closeSoftKeyboard());
    onView(withId(android.R.id.button1)).perform(click());
    onView(withText("Informe um e-mail válido")).check(matches(isDisplayed()));
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_email)))
        .perform(scrollTo()).check(matches(withText("invalido")));
  }

  @Test
  public void edicaoAdministrativaApareceNoPerfilComumEEstePersisteNovasEdicoes() {
    ProfileRepository profiles = new ProfileRepository(prefs);
    JSONObject profile = profiles.get("cliente");
    LocalJson.put(profile, "name", "Nome do Administrador");
    profiles.save(profile, true);
    scenario = ActivityScenario.launch(MainActivity.class);
    onView(withId(R.id.edtEmail)).perform(replaceText("cliente@gmail.com"), closeSoftKeyboard());
    onView(withId(R.id.edtSenha)).perform(replaceText("12345"), closeSoftKeyboard());
    onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
    onView(withId(R.id.workProfile)).perform(click());
    onView(withContentDescription("Editar Nome completo: Nome do Administrador")).perform(scrollTo(), click());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Nome completo")))
        .perform(replaceText("Cliente no app"), closeSoftKeyboard());
    onView(withId(android.R.id.button1)).perform(click());
    assertEquals("Cliente no app", profiles.current().optString("name"));
  }

  @Test
  public void editaCancelaExcluiERestauraPedidoExistentePelaInterface() {
    JSONObject order = new JSONObject();
    LocalJson.put(order, "pedido_id", "p-ui");
    LocalJson.put(order, "pedido_status", "waiting");
    LocalJson.put(order, "pedido_tipo", "Óleo");
    LocalJson.put(order, "pedido_descricao", "Descrição original");
    LocalJson.put(order, "pedido_peso", "2 kg");
    LocalJson.put(order, "pedido_endereco_completo", "Rua Teste, 10");
    assertTrue(prefs.edit().putString("perfil_pedido_cliente", order.toString()).commit());
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withId(R.id.adminNavOrders)).perform(click());
    onView(withText(containsString("#p-ui"))).perform(click());
    onView(
            allOf(
                isAssignableFrom(android.widget.EditText.class),
                withHint(R.string.admin_description)))
        .perform(replaceText("Pedido editado"), closeSoftKeyboard());
    onView(withId(android.R.id.button1)).perform(click());
    onView(withText(containsString("#p-ui"))).perform(click());
    onView(withText(R.string.admin_cancel_order)).perform(scrollTo(), click());
    onView(withText(R.string.admin_confirm)).perform(click());
    onView(withText(containsString("#p-ui"))).perform(click());
    onView(withText(R.string.admin_delete)).perform(scrollTo(), click());
    onView(withText(R.string.admin_confirm)).perform(click());
    assertTrue(new AdminRepository(prefs).order("cliente", "p-ui").optBoolean("deleted"));
    onView(allOf(withText(R.string.admin_trash), isAssignableFrom(com.google.android.material.chip.Chip.class))).perform(scrollTo(), click());
    settleSearch();
    onView(withText(containsString("#p-ui"))).perform(click());
    onView(withText(R.string.admin_restore)).perform(scrollTo(), click());
    onView(withText(R.string.admin_confirm)).perform(click());
    assertEquals(4, new AuditRepository(prefs).list().size());
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    assertEquals(
        "Pedido editado",
        new PedidoRepository(prefs).historico().get(0).optString("pedido_descricao"));
  }

  @Test
  public void novaEmpresaPermiteEscolherTipoEPreencherDadosEmpresariais() {
    scenario = ActivityScenario.launch(AdminActivity.class);
    onView(withId(R.id.adminNavProfiles)).perform(click());
    onView(withId(R.id.adminNewProfile)).perform(click());
    onView(withId(R.id.adminRoleInput)).perform(click());
    onData(anything())
        .inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup())
        .atPosition(2)
        .perform(click());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_name)))
        .perform(scrollTo(), replaceText("Responsavel Teste"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_email)))
        .perform(scrollTo(), replaceText("empresa.teste@exemplo.com"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_company)))
        .perform(scrollTo(), replaceText("Empresa Teste"), closeSoftKeyboard());
    onView(
            allOf(
                isAssignableFrom(android.widget.EditText.class),
                withHint(R.string.platform_initial_password)))
        .perform(scrollTo(), replaceText("senha-segura"), closeSoftKeyboard());
    onView(
            allOf(
                isAssignableFrom(android.widget.EditText.class), withHint(R.string.admin_document)))
        .perform(scrollTo(), replaceText("12000345000199"), closeSoftKeyboard());
    onView(withId(android.R.id.button1)).perform(click());
    JSONObject created = new ProfileRepository(prefs).login("empresa.teste@exemplo.com");
    assertEquals("empresa", created.optString("role"));
    assertEquals("Empresa Teste", created.optString("company"));
  }

  @Test
  public void cidadaoCriaSolicitacaoComMaterialPesoDataEEnderecoSemPreco() {
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    CollectionRepository repo = new CollectionRepository(prefs);
    JSONObject address = new JSONObject();
    for (String[] pair :
        new String[][] {
          {"nickname", "Casa"},
          {"street", "Rua Um"},
          {"number", "10"},
          {"district", "Centro"},
          {"city", "São Paulo"},
          {"state", "SP"},
          {"zip", "01001000"}
        }) LocalJson.put(address, pair[0], pair[1]);
    repo.saveAddress(address);
    scenario = ActivityScenario.launch(WorkspaceActivity.class);
    onView(withId(R.id.workAction)).perform(click());
    onView(withText("Papel")).perform(scrollTo(), click());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Peso de Papel (kg)")))
        .perform(scrollTo(), replaceText("2,50"), closeSoftKeyboard());
    onView(withText("Enviar pedido")).perform(click());
    assertEquals(1, repo.orders().size());
    assertEquals("aberta", repo.orders().get(0).optString("status"));
    assertEquals(
        "2.50", repo.orders().get(0).optJSONArray("items").optJSONObject(0).optString("weight"));
  }

  @Test
  public void mapaAbrePreservaDestinoAoRecriarEVoltaParaInicio() {
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    scenario = ActivityScenario.launch(WorkspaceActivity.class);
    onView(withId(R.id.workMapCard)).check(matches(isDisplayed()));
    onView(withId(R.id.workExplore)).perform(click());
    onView(withText("Calcular rota")).check(matches(isDisplayed()));
    androidx.test.espresso.Espresso.pressBack();
    onView(withId(R.id.workAction)).check(matches(isDisplayed()));
    scenario.close();
    android.content.Context context =
        InstrumentationRegistry.getInstrumentation().getTargetContext();
    scenario =
        ActivityScenario.launch(
            new android.content.Intent(context, LocationActivity.class)
                .putExtra("pick", true)
                .putExtra("latitude", -23.55)
                .putExtra("longitude", -46.63));
    scenario.recreate();
    onView(withText("Confirmar local da coleta")).check(matches(isEnabled()));
  }

  @Test
  public void renderizadorNativoCarregaEstiloLocalSemDependerDeChaves() throws Exception {
    new PedidoRepository(prefs).selecionarConta("cliente", "cliente");
    java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
    org.maplibre.android.maps.MapView[] nativeView = new org.maplibre.android.maps.MapView[1];
    scenario = ActivityScenario.launch(WorkspaceActivity.class);
    scenario.onActivity(activity -> {
      org.maplibre.android.MapLibre.getInstance(activity);
      nativeView[0] = new org.maplibre.android.maps.MapView(activity);
      nativeView[0].onCreate(null);
      ((android.widget.FrameLayout) activity.findViewById(R.id.workMapContainer))
          .addView(nativeView[0], new android.widget.FrameLayout.LayoutParams(-1, -1));
      nativeView[0].onStart();
      nativeView[0].onResume();
      nativeView[0].getMapAsync(map ->
          map.setStyle(
              new org.maplibre.android.maps.Style.Builder()
                  .fromJson(
                      "{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#EBF8F3\"}}]}"),
              style -> loaded.countDown()));
    });
    try {
      assertTrue(
          "Native renderer must load a valid style",
          loaded.await(20, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      scenario.onActivity(activity -> {
        nativeView[0].onPause();
        nativeView[0].onStop();
        nativeView[0].onDestroy();
      });
    }
  }

  @Test
  public void cadastroPublicoPermiteSenhaPropria() {
    scenario = ActivityScenario.launch(RegistrationActivity.class);
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Nome / responsável")))
        .perform(scrollTo(), replaceText("Pessoa Nova"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("E-mail")))
        .perform(scrollTo(), replaceText("pessoa.nova@example.com"), closeSoftKeyboard());
    onView(
            allOf(
                isAssignableFrom(android.widget.EditText.class),
                withHint("Senha (mínimo 8 caracteres)")))
        .perform(scrollTo(), replaceText("senha-segura"), closeSoftKeyboard());
    onView(allOf(isAssignableFrom(android.widget.EditText.class), withHint("Confirme sua senha")))
        .perform(scrollTo(), replaceText("senha-segura"), closeSoftKeyboard());
    onView(withText("Cadastrar")).perform(scrollTo(), click());
    assertEquals(
        "Pessoa Nova",
        new ProfileRepository(prefs)
            .authenticate("pessoa.nova@example.com", "senha-segura")
            .optString("name"));
  }
}
