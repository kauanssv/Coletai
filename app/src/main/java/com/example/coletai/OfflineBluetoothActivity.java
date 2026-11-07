package com.example.coletai;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

/** Entry point for the current offline multi-phone platform, separate from the legacy demo. */
public class OfflineBluetoothActivity extends AppCompatActivity {
  private BluetoothPlatform platform;
  private TextView status;
  private Spinner devices;
  private final List<BluetoothDevice> paired = new ArrayList<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable update = new Runnable() {
    public void run() { if (status != null) status.setText(platform.status()); handler.postDelayed(this, 1000); }
  };
  private final ActivityResultLauncher<String> permission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
    if (granted) refresh(); else toast("Permita o acesso a dispositivos próximos para conectar os celulares.");
  });
  private final ActivityResultLauncher<Intent> enable = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> refresh());

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state); platform = BluetoothPlatform.get(this);
    ScrollView scroll = new ScrollView(this); LinearLayout form = PlatformUi.column(this);
    scroll.setBackgroundColor(0xFF0B3D37);
    int pad = PlatformUi.dp(this, 20); form.setPadding(pad, pad, pad, pad); scroll.addView(form); setContentView(scroll);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
      androidx.core.graphics.Insets b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
      v.setPadding(b.left, b.top, b.right, b.bottom); return insets;
    });
    PlatformUi.text(form, "Apresentação por Bluetooth", 25, true);
    PlatformUi.text(form, "Sem internet: escolha um celular como coordenador. Todos os demais se conectam a ele; o coordenador também pode usar qualquer perfil.", 15, false);
    status = PlatformUi.text(form, platform.status(), 16, true);
    PlatformUi.button(form, "Ativar Bluetooth / permitir acesso", this::prepare);
    if (Build.VERSION.SDK_INT >= 33) PlatformUi.button(form, "Permitir notificação da sessão", () -> permission.launch(Manifest.permission.POST_NOTIFICATIONS));
    PlatformUi.button(form, "Ser o coordenador", () -> confirmMode(true));
    devices = new Spinner(this); form.addView(devices);
    PlatformUi.button(form, "Atualizar celulares pareados", this::refresh);
    PlatformUi.button(form, "Conectar ao coordenador selecionado", () -> confirmMode(false));
    PlatformUi.button(form, "Retomar conexão", () -> { try { platform.resume(); } catch (RuntimeException error) { toast(error.getMessage()); } });
    PlatformUi.button(form, "Localização para a apresentação", () -> new MaterialAlertDialogBuilder(this)
        .setTitle("Escolha o ponto deste celular")
        .setItems(new String[]{"Ponto do cliente · São Paulo", "Ponto do coletor · São Paulo", "Usar GPS do aparelho"}, (dialog, choice) -> {
          SharedPreferences.Editor location = DeviceLocation.settings(this).edit().putBoolean("automatic", choice == 2);
          if (choice != 2) location.putLong("latitude", Double.doubleToLongBits(choice == 0 ? -23.538 : -23.542))
              .putLong("longitude", Double.doubleToLongBits(choice == 0 ? -46.56 : -46.57));
          LocalJson.commit(location); toast(choice == 2 ? "GPS selecionado" : "Ponto manual de apresentação selecionado");
        }).show());
    PlatformUi.button(form, "Entrar no sistema", () -> {
      if (!platform.ready()) { toast("Aguarde a conexão com o coordenador antes de entrar."); return; }
      startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
    });
    PlatformUi.text(form, "Como preparar", 19, true);
    PlatformUi.text(form, "1. Instale a mesma versão do APK em todos os celulares.\n2. Nas configurações do Android, pareie cada participante com o coordenador.\n3. No coordenador, toque em Ser o coordenador.\n4. Nos demais, selecione esse celular e conecte.\n5. Entre em uma conta diferente em cada aparelho.", 14, false);
    PlatformUi.text(form, "Contas iniciais da apresentação", 18, true);
    PlatformUi.text(form, "cliente@gmail.com\nambulante@gmail.com\nempresa@gmail.com\nadmin@coletai.app\nSenha inicial: 12345\n\nTambém é possível cadastrar novas contas. Todos usam a base do coordenador; os dados locais anteriores ficam separados.", 14, false);
    PlatformUi.text(form, "Mantenha o coordenador ligado e próximo aos participantes. Se ele sair, as alterações aguardam a reconexão. Mapas e rotas online não serão consultados nesta modalidade.", 14, false);
    PlatformUi.button(form, "Encerrar Bluetooth neste celular", () -> new MaterialAlertDialogBuilder(this)
        .setMessage("Sair da sessão offline? Se este celular for o coordenador, todos os participantes ficarão desconectados até ele retomar.")
        .setNegativeButton("Continuar", null).setPositiveButton("Encerrar", (d, n) -> {
          platform.leave(); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        }).show());
    PlatformUi.button(form, "Voltar", this::finish);
    whiteText(form);
    devices.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
    prepare();
  }
  private static void whiteText(android.view.View view) {
    if (view instanceof TextView) ((TextView) view).setTextColor(android.graphics.Color.WHITE);
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) whiteText(group.getChildAt(i));
    }
  }
  private void prepare() {
    if (!platform.supported()) { toast("Este dispositivo não possui Bluetooth."); return; }
    if (Build.VERSION.SDK_INT >= 31 && androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      permission.launch(Manifest.permission.BLUETOOTH_CONNECT); return;
    }
    if (!platform.enabled()) { enable.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return; }
    refresh();
  }
  @android.annotation.SuppressLint("MissingPermission") private void refresh() {
    try {
      paired.clear(); paired.addAll(platform.paired());
      paired.sort(Comparator.comparing(d -> d.getName() == null ? d.getAddress() : d.getName()));
      List<String> labels = new ArrayList<>();
      for (BluetoothDevice device : paired) labels.add((device.getName() == null ? "Celular" : device.getName()) + " · " + device.getAddress());
      if (labels.isEmpty()) labels.add("Pareie o coordenador nas configurações do Android");
      devices.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels) {
        @Override public android.view.View getView(int position, android.view.View recycled, android.view.ViewGroup parent) {
          android.view.View view = super.getView(position, recycled, parent);
          whiteText(view); return view;
        }
        @Override public android.view.View getDropDownView(int position, android.view.View recycled, android.view.ViewGroup parent) {
          android.view.View view = super.getDropDownView(position, recycled, parent);
          view.setBackgroundColor(0xFF0B3D37); whiteText(view); return view;
        }
      });
    } catch (SecurityException error) { toast("Permita o acesso a dispositivos próximos."); }
  }
  private void confirmMode(boolean host) {
    new MaterialAlertDialogBuilder(this).setMessage(host
        ? "Usar a base offline deste coordenador? Os participantes deverão selecionar este celular."
        : "Entrar na base do coordenador selecionado? A conta atual deste celular será desconectada.")
        .setNegativeButton("Voltar", null).setPositiveButton("Confirmar", (d, n) -> {
          try {
            if (host) platform.host();
            else {
              int index = devices.getSelectedItemPosition();
              if (index < 0 || index >= paired.size()) throw new IllegalArgumentException("Pareie e selecione o coordenador primeiro.");
              platform.join(paired.get(index));
            }
            getSharedPreferences("coletai_bluetooth", MODE_PRIVATE).edit().clear().commit();
          } catch (RuntimeException error) { toast(error.getMessage()); }
        }).show();
  }
  private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
  @Override protected void onStart() { super.onStart(); handler.post(update); }
  @Override protected void onStop() { handler.removeCallbacks(update); super.onStop(); }
}
