package com.example.coletai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

  private DeviceLocation deviceLocation;
  private EditText edtEmail;
  private EditText edtSenha;

  private Button btnEntrar;
  private Button btnBluetoothDemo;

  private ImageButton btnMostrarSenha;

  private boolean senhaVisivel = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    SplashScreen.installSplashScreen(this);

    super.onCreate(savedInstanceState);

    SharedPreferences savedPrefs = PlatformStore.open(this);
    if (BluetoothPlatform.active()) BluetoothPlatform.get(this).resume();
    String savedAccount = savedPrefs.getString("logged_in_account", "");
    if (!savedAccount.isEmpty()
        && new ProfileRepository(savedPrefs).available(savedAccount)
        && (!PlatformGateway.configured() || !savedPrefs.getString("api_token", "").isEmpty())) {
      entrarNaConta(savedAccount);
      if (isFinishing()) return;
    }

    // A seleção de perfil pausa a sincronização sem apagar pedidos salvos.
    limparUsuarioAtual();

    EdgeToEdge.enable(this);

    setContentView(R.layout.activity_main);

    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (view, insets) -> {
          Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

          view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

          return insets;
        });

    iniciarComponentes();

    deviceLocation = new DeviceLocation(this, (location, status) -> {});
    deviceLocation.request(false);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (edtEmail != null) configurarEventos();
  }

  // =====================================================
  // USUÁRIO
  // =====================================================

  private void limparUsuarioAtual() {
    BluetoothColetai.getInstance(this).pausarSessao();
  }

  // =====================================================
  // COMPONENTES
  // =====================================================

  private void iniciarComponentes() {

    edtEmail = findViewById(R.id.edtEmail);

    edtSenha = findViewById(R.id.edtSenha);

    btnEntrar = findViewById(R.id.btnEntrar);

    btnBluetoothDemo = findViewById(R.id.btnBluetoothDemo);

    btnMostrarSenha = findViewById(R.id.btnMostrarSenha);
  }

  // =====================================================
  // EVENTOS
  // =====================================================

  private void configurarEventos() {
    findViewById(R.id.demoHeader).setVisibility(PlatformGateway.demoAllowed()?android.view.View.VISIBLE:android.view.View.GONE);
    findViewById(R.id.demoPasswordHint).setVisibility(PlatformGateway.demoAllowed()?android.view.View.VISIBLE:android.view.View.GONE);
    if(!PlatformGateway.demoAllowed()){((android.widget.TextView)findViewById(R.id.loginSubtitle)).setText("Entre para solicitar ou realizar coletas de recicláveis.");btnEntrar.setText("Entrar");}
    findViewById(R.id.btnAdminDemo)
        .setVisibility(
            PlatformGateway.demoAllowed() ? android.view.View.VISIBLE : android.view.View.GONE);
    findViewById(R.id.cardCliente)
        .setVisibility(
            PlatformGateway.demoAllowed() ? android.view.View.VISIBLE : android.view.View.GONE);
    findViewById(R.id.cardAmbulante)
        .setVisibility(
            PlatformGateway.demoAllowed() ? android.view.View.VISIBLE : android.view.View.GONE);
    findViewById(R.id.cardEmpresa)
        .setVisibility(
            PlatformGateway.demoAllowed() ? android.view.View.VISIBLE : android.view.View.GONE);
    findViewById(R.id.btnBluetoothDemo).setVisibility(android.view.View.VISIBLE);
    ((android.widget.Button) findViewById(R.id.btnBluetoothDemo)).setText("Apresentação offline por Bluetooth");
    findViewById(R.id.btnAdminDemo).setOnClickListener(view -> {
      if (BluetoothPlatform.active()) {
        preencherContaDemonstracao("admin", "admin@coletai.app");
        realizarLogin();
      } else entrarNaConta("admin");
    });

    btnMostrarSenha.setOnClickListener(view -> alternarVisibilidadeSenha());

    btnEntrar.setOnClickListener(view -> realizarLogin());

    btnBluetoothDemo.setOnClickListener(view -> abrirBluetooth());

    findViewById(R.id.btnRegister)
        .setOnClickListener(v -> startActivity(new Intent(this, RegistrationActivity.class)));

    configurarContaDemonstracao(R.id.cardCliente, "cliente@gmail.com");

    configurarContaDemonstracao(R.id.cardAmbulante, "ambulante@gmail.com");

    configurarContaDemonstracao(R.id.cardEmpresa, "empresa@gmail.com");
  }

  // =====================================================
  // BLUETOOTH
  // =====================================================

  private void abrirBluetooth() {

    Intent intent = new Intent(MainActivity.this, OfflineBluetoothActivity.class);

    startActivity(intent);
  }

  // =====================================================
  // CONTAS DEMONSTRAÇÃO
  // =====================================================

  private void configurarContaDemonstracao(int viewId, String email) {

    LinearLayout card = findViewById(viewId);

    card.setOnClickListener(view -> {
      String id =
          email.startsWith("empresa")
              ? "empresa"
              : email.startsWith("ambulante") ? "ambulante" : "cliente";
      preencherContaDemonstracao(id, email);
    });
  }

  private void preencherContaDemonstracao(String id, String defaultEmail) {
    ProfileRepository profiles = new ProfileRepository(PlatformStore.open(this));
    String email = defaultEmail;
    for (org.json.JSONObject profile : profiles.list()) {
      if (id.equals(profile.optString("id"))) {
        email = profile.optString("email", defaultEmail);
        break;
      }
    }
    edtEmail.setText(email);
    edtSenha.setText(R.string.texto_12345);
    edtEmail.clearFocus();
    edtSenha.clearFocus();
  }

  // =====================================================
  // SENHA
  // =====================================================

  private void alternarVisibilidadeSenha() {

    senhaVisivel = !senhaVisivel;

    if (senhaVisivel) {

      edtSenha.setInputType(
          InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

      btnMostrarSenha.setAlpha(1f);

    } else {

      edtSenha.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

      btnMostrarSenha.setAlpha(0.55f);
    }

    edtSenha.setTypeface(Typeface.DEFAULT);

    edtSenha.setSelection(edtSenha.getText().length());
  }

  // =====================================================
  // LOGIN
  // =====================================================

  private void realizarLogin() {
    String email = edtEmail.getText().toString().trim();
    if (email.isEmpty()) {
      edtEmail.setError("Informe seu e-mail");
      return;
    }
    String password = edtSenha.getText().toString();
    btnEntrar.setEnabled(false);
    String[] account = {""};
    PlatformTasks.run(
        this,
        () ->
            account[0] =
                new ProfileRepository(PlatformStore.open(this))
                    .authenticate(email, password)
                    .optString("id"),
        () -> entrarNaConta(account[0]),
        () -> btnEntrar.setEnabled(true));
  }

  private void entrarNaConta(String id) {
    try {
      ProfileRepository profiles = new ProfileRepository(PlatformStore.open(this));
      org.json.JSONObject profile = profiles.get(id);
      SharedPreferences prefs = PlatformStore.open(this);
      if (!profiles.available(id)) throw new IllegalArgumentException("Conta indisponivel");
      new PedidoRepository(prefs).selecionarConta(id, profile.optString("role"));
      SharedPreferences.Editor editor = prefs.edit();
      ProfileRepository.mirror(editor, profile);
      editor.putString("logged_in_account", id);
      LocalJson.commit(editor);
      String role = profile.optString("role");
      Class<?> screen = "admin".equals(role) ? AdminActivity.class : WorkspaceActivity.class;
      startActivity(new Intent(this, screen));
      finish();
    } catch (RuntimeException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  // =====================================================
  // CLIENTE
  // =====================================================

  // =====================================================
  // AMBULANTE
  // =====================================================

  // =====================================================
  // EMPRESA
  // =====================================================

  // =====================================================
  // ERRO
  // =====================================================

  private void mostrarErroLogin() {

    Toast.makeText(this, "E-mail ou senha incorretos", Toast.LENGTH_SHORT).show();
  }
}
