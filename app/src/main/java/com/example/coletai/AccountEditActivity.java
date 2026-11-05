package com.example.coletai;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** One editor for citizen, collector and company accounts. */
public class AccountEditActivity extends AppCompatActivity {
  public static class EditorState extends androidx.lifecycle.ViewModel {
    final RequestSubmission task = new RequestSubmission();
    JSONObject original, photo;
    String operation = "", pendingUri = "";
  }
  private EditorState model;
  private final Map<String, TextInputEditText> fields = new LinkedHashMap<>();
  private TextInputEditText currentPassword, newPassword, confirmation;
  private MaterialButton save, choose, remove, delete;
  private LinearLayout photoArea;
  private ProfileRepository profiles;
  private final androidx.activity.result.ActivityResultLauncher<String[]> picker = registerForActivityResult(
      new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null || model.task.running) return;
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
        model.pendingUri = uri.toString();
        importPhoto();
      });

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    profiles = new ProfileRepository(PlatformStore.open(this));
    model = new androidx.lifecycle.ViewModelProvider(this).get(EditorState.class);
    if (model.original == null) {
      model.original = state == null ? LocalJson.object(profiles.current().toString()) : LocalJson.object(state.getString("original", "{}"));
      model.photo = state == null ? model.original.optJSONObject("profile_photo") : LocalJson.object(state.getString("photo", "{}"));
      if (model.photo != null && model.photo.optString("id").isEmpty()) model.photo = null;
      if (state != null) model.pendingUri = state.getString("pending_uri", "");
    }
    LinearLayout root = PlatformUi.column(this);
    root.setBackgroundColor(0xFFF4F7F6);
    setContentView(root);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
      androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.ime());
      v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
      return androidx.core.view.WindowInsetsCompat.CONSUMED;
    });
    com.google.android.material.appbar.MaterialToolbar toolbar = new com.google.android.material.appbar.MaterialToolbar(this);
    toolbar.setTitle("Editar conta");
    toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
    toolbar.setNavigationOnClickListener(v -> { if (!model.task.running) finish(); });
    root.addView(toolbar);
    ScrollView scroll = new ScrollView(this);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout body = PlatformUi.column(this);
    int pad = PlatformUi.dp(this, 20);
    body.setPadding(pad, 0, pad, pad);
    scroll.addView(body);
    LinearLayout photoCard = PlatformUi.card(body);
    photoCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
    photoArea = PlatformUi.column(this);
    photoArea.setGravity(android.view.Gravity.CENTER);
    photoCard.addView(photoArea, new LinearLayout.LayoutParams(-1, -2));
    choose = PlatformUi.button(photoCard, "Escolher foto", () -> picker.launch(new String[]{"image/*"}));
    remove = PlatformUi.button(photoCard, "Remover foto", () -> { model.photo = null; renderPhoto(); });
    remove.setTextColor(0xFFC62828);
    remove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
    renderPhoto();
    PlatformUi.section(body, "DADOS DA CONTA");
    LinearLayout personal = PlatformUi.card(body);
    field(personal, "name", "Nome completo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, state);
    field(personal, "document", "empresa".equals(model.original.optString("role")) ? "CNPJ" : "CPF", InputType.TYPE_CLASS_NUMBER, state);
    field(personal, "phone", "Telefone", InputType.TYPE_CLASS_PHONE, state);
    field(personal, "email", "E-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, state);
    if ("empresa".equals(model.original.optString("role"))) {
      field(personal, "legal_name", "Razão social", InputType.TYPE_CLASS_TEXT, state);
      field(personal, "company", "Nome fantasia", InputType.TYPE_CLASS_TEXT, state);
    }
    PlatformUi.section(body, "ALTERAR SENHA");
    LinearLayout passwords = PlatformUi.card(body);
    PlatformUi.text(passwords, "Preencha apenas se quiser trocar sua senha.", 13, false).setTextColor(0xFF6F817D);
    currentPassword = password(passwords, "Senha atual");
    newPassword = password(passwords, "Nova senha (8 a 128 caracteres)");
    confirmation = password(passwords, "Confirmar nova senha");
    save = PlatformUi.button(body, "Salvar alterações", this::save);
    delete = PlatformUi.button(body, "Excluir conta", this::confirmDeletion);
    delete.setTextColor(0xFFB91C1C);
    delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFEF2F2));
    delete.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFFECACA));
    delete.setStrokeWidth(PlatformUi.dp(this, 1));
    getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() { if (!model.task.running) finish(); }
    });
    model.task.outcome.observe(this, result -> {
      updateState();
      if (result == null) return;
      model.task.outcome.setValue(null);
      if (result.error instanceof PlatformGateway.SessionExpired) {
        LocalJson.commit(PlatformStore.open(this).edit().clear());
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
      } else if (result.error != null) {
        android.widget.Toast.makeText(this, result.error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
      } else if ("delete".equals(model.operation)) {
        android.widget.Toast.makeText(this, "Conta excluída. Um administrador poderá restaurá-la.", android.widget.Toast.LENGTH_LONG).show();
        returnToLogin();
      } else if ("save".equals(model.operation)) {
        android.widget.Toast.makeText(this, "Conta atualizada", android.widget.Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
      } else renderPhoto();
    });
    updateState();
    if (!model.pendingUri.isEmpty() && !model.task.running) importPhoto();
  }

  private void confirmDeletion() {
    if (model.task.running) return;
    androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        .setTitle("Excluir sua conta?")
        .setMessage("Sua conta ficará excluída e você será desconectado. Seus dados serão mantidos e um administrador poderá restaurar seu acesso.")
        .setNegativeButton("Manter minha conta", null)
        .setPositiveButton("Excluir conta", (d, which) -> {
          if (model.task.running) return;
          ProfileRepository repository = profiles;
          String id = model.original.optString("id");
          int revision = model.original.optInt("revision");
          model.operation = "delete";
          model.task.start(() -> repository.deleteAccount(id, revision));
          updateState();
        }).create();
    dialog.setOnShowListener(d -> dialog.getButton(-1).setTextColor(0xFFB91C1C));
    dialog.show();
  }

  private void returnToLogin() {
    android.content.SharedPreferences prefs = PlatformStore.open(this);
    // Local profiles are the database; remote preferences are only a session cache.
    android.content.SharedPreferences.Editor editor = prefs.edit();
    if (PlatformGateway.configured()) editor.clear();
    else editor.remove("logged_in_account").remove("conta_atual").remove("usuario_tipo_atual");
    LocalJson.commit(editor);
    startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    finish();
  }

  private void field(LinearLayout parent, String key, String label, int type, Bundle state) {
    fields.put(key, PlatformUi.field(parent, label, state == null ? model.original.optString(key) : state.getString("field_" + key, model.original.optString(key)), type));
  }

  private TextInputEditText password(LinearLayout parent, String hint) {
    TextInputEditText field = PlatformUi.field(parent, hint, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    field.setSaveEnabled(false);
    ((com.google.android.material.textfield.TextInputLayout) field.getParent().getParent())
        .setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
    return field;
  }

  private void renderPhoto() {
    photoArea.removeAllViews();
    JSONObject preview = LocalJson.object(model.original.toString());
    LocalJson.put(preview, "profile_photo", model.photo == null ? JSONObject.NULL : model.photo);
    photoArea.addView(ProfileAvatar.create(this, preview, 96));
    choose.setText(model.photo == null ? "Adicionar foto" : "Trocar foto");
    remove.setVisibility(model.photo == null ? View.GONE : View.VISIBLE);
  }

  private void importPhoto() {
    final android.content.Context context = getApplicationContext();
    final android.net.Uri uri = android.net.Uri.parse(model.pendingUri);
    EditorState retained = model;
    model.operation = "import";
    model.task.start(() -> {
      try { retained.photo = RequestPhotoStore.importImage(context, uri); }
      finally {
        retained.pendingUri = "";
        try { context.getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
      }
    });
    updateState();
  }

  private void save() {
    if (model.task.running) return;
    String current = currentPassword.getText().toString(), password = newPassword.getText().toString(), confirm = confirmation.getText().toString();
    confirmation.setError(null); newPassword.setError(null); currentPassword.setError(null);
    try { ProfileRepository.validatePasswordChange(current, password, confirm); }
    catch (IllegalArgumentException error) {
      TextInputEditText target = !password.equals(confirm) ? confirmation : password.length() < 8 || password.length() > 128 ? newPassword : currentPassword;
      target.setError(error.getMessage()); target.requestFocus(); return;
    }
    JSONObject changed = LocalJson.object(model.original.toString());
    for (Map.Entry<String, TextInputEditText> field : fields.entrySet()) LocalJson.put(changed, field.getKey(), PlatformUi.value(field.getValue()));
    JSONObject photo = model.photo == null ? null : LocalJson.object(model.photo.toString());
    final android.content.Context context = getApplicationContext();
    final ProfileRepository repository = profiles;
    model.operation = "save";
    model.task.start(() -> {
      JSONObject payload = new JSONObject();
      LocalJson.put(payload, "photos", photo == null ? new JSONArray() : new JSONArray().put(photo));
      JSONArray prepared = RequestPhotoStore.prepareOrder(context, payload).optJSONArray("photos");
      LocalJson.put(changed, "profile_photo", prepared.length() == 0 ? JSONObject.NULL : prepared.optJSONObject(0));
      repository.updateAccount(changed, current, password, confirm);
    });
    updateState();
  }

  private void updateState() {
    boolean busy = model.task.running;
    save.setEnabled(!busy); choose.setEnabled(!busy); remove.setEnabled(!busy);
    delete.setEnabled(!busy);
    delete.setText(busy && "delete".equals(model.operation) ? "Excluindo conta…" : "Excluir conta");
    save.setText(busy && !"delete".equals(model.operation) ? "import".equals(model.operation) ? "Preparando foto…" : "Salvando…" : "Salvar alterações");
    for (TextInputEditText field : fields.values()) field.setEnabled(!busy);
    currentPassword.setEnabled(!busy); newPassword.setEnabled(!busy); confirmation.setEnabled(!busy);
  }

  @Override protected void onResume() {
    super.onResume();
    if (model != null && "delete".equals(model.operation) && !model.task.running && model.task.outcome.getValue() != null && model.task.outcome.getValue().error == null) {
      returnToLogin();
      return;
    }
    if (model != null && (!model.original.optString("id").equals(LocalJson.account(PlatformStore.open(this))) || !profiles.available(model.original.optString("id")))) finish();
  }

  @Override protected void onSaveInstanceState(Bundle out) {
    out.putString("original", model.original.toString());
    out.putString("photo", model.photo == null ? "{}" : model.photo.toString());
    out.putString("pending_uri", model.pendingUri);
    for (Map.Entry<String, TextInputEditText> field : fields.entrySet()) out.putString("field_" + field.getKey(), PlatformUi.value(field.getValue()));
    super.onSaveInstanceState(out);
  }
}
