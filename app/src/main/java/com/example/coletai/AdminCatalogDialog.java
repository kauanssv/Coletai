package com.example.coletai;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Catalog and platform forms retain drafts and report save failures inline. */
public class AdminCatalogDialog extends DialogFragment {
  private final Map<String, TextInputEditText> fields = new LinkedHashMap<>();
  private CheckBox active;
  private TextView error;
  private RequestSubmission submission;
  private JSONObject original;
  private boolean information;

  static AdminCatalogDialog create(boolean information, JSONObject record) {
    AdminCatalogDialog dialog = new AdminCatalogDialog();
    Bundle args = new Bundle();
    args.putBoolean("information", information);
    args.putString("record", record.toString());
    dialog.setArguments(args);
    return dialog;
  }

  @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle state) {
    new ProfileRepository(PlatformStore.open(requireContext())).requireAdmin();
    information = requireArguments().getBoolean("information");
    original = LocalJson.object(requireArguments().getString("record", "{}"));
    ScrollView scroll = new ScrollView(requireContext());
    LinearLayout form = PlatformUi.column(requireContext());
    int pad = PlatformUi.dp(requireContext(), 20);
    form.setPadding(pad, 0, pad, pad);
    scroll.addView(form);
    add(form, "name", information ? "Nome da plataforma" : "Nome do material", InputType.TYPE_CLASS_TEXT, state);
    TextInputEditText description = add(form, "description", information ? "Apresentação" : "Descrição", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, state);
    description.setMinLines(3);
    description.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
    if (information) add(form, "support_email", "E-mail de suporte", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, state);
    else {
      active = new CheckBox(requireContext());
      active.setText("Disponível para novas solicitações");
      active.setChecked(state == null ? original.optBoolean("active", true) : state.getBoolean("active"));
      form.addView(active);
      PlatformUi.text(form, "Desativar mantém o material nos pedidos existentes e impede seu uso em novos pedidos.", 13, false).setTextColor(0xFF6F817D);
    }
    error = PlatformUi.text(form, "", 14, false);
    form.addView(error);
    error.setTextColor(0xFFB42318);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    error.setVisibility(View.GONE);
    submission = new androidx.lifecycle.ViewModelProvider(this).get(RequestSubmission.class);
    submission.outcome.observe(this, result -> {
      if (!isAdded()) return;
      updateState();
      if (result == null) return;
      submission.outcome.setValue(null);
      if (result.error == null) {
        getParentFragmentManager().setFragmentResult("admin_updated", new Bundle());
        android.widget.Toast.makeText(requireContext(), R.string.admin_saved, android.widget.Toast.LENGTH_SHORT).show();
        dismiss();
      } else if (result.error instanceof PlatformGateway.SessionExpired) {
        LocalJson.commit(PlatformStore.open(requireContext()).edit().clear());
        startActivity(new android.content.Intent(requireContext(), MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        dismiss();
      } else showError(result.error.getMessage());
    });
    return new MaterialAlertDialogBuilder(requireContext()).setTitle(information ? "Informações da plataforma" : original.optString("id").isEmpty() ? "Novo material" : "Editar material")
        .setView(scroll).setNegativeButton("Voltar", null).setPositiveButton("Salvar", null).create();
  }

  private TextInputEditText add(LinearLayout form, String key, String label, int input, Bundle state) {
    TextInputEditText field = PlatformUi.field(form, label, state == null ? original.optString(key) : state.getString(key, ""), input);
    field.setSaveEnabled(false);
    field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter("name".equals(key) ? information ? 100 : 80 : "support_email".equals(key) ? 254 : information ? 500 : 255)});
    fields.put(key, field);
    return field;
  }

  @Override public void onStart() {
    super.onStart();
    ((AlertDialog) requireDialog()).getButton(-1).setOnClickListener(v -> save());
    updateState();
  }

  private void save() {
    if (submission.running) return;
    JSONObject changed = LocalJson.object(original.toString());
    for (Map.Entry<String, TextInputEditText> entry : fields.entrySet()) LocalJson.put(changed, entry.getKey(), PlatformUi.value(entry.getValue()));
    if (changed.optString("name").isEmpty()) { showError("Informe o nome para continuar."); return; }
    String email = changed.optString("support_email");
    if (information && !email.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { showError("Informe um e-mail de suporte válido."); return; }
    if (active != null) LocalJson.put(changed, "active", active.isChecked());
    CollectionRepository repo = new CollectionRepository(PlatformStore.open(requireContext()));
    error.setVisibility(View.GONE);
    submission.start(() -> { if (information) repo.saveInformation(changed); else repo.saveMaterial(changed); });
    updateState();
  }

  private void showError(String message) {
    error.setText(message);
    error.setVisibility(View.VISIBLE);
    error.post(() -> error.requestRectangleOnScreen(new android.graphics.Rect(0, 0, error.getWidth(), error.getHeight()), true));
  }

  private void updateState() {
    boolean busy = submission.running;
    setCancelable(!busy);
    for (TextInputEditText field : fields.values()) field.setEnabled(!busy);
    if (active != null) active.setEnabled(!busy);
    if (getDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) getDialog();
      if (dialog.getButton(-1) != null) { dialog.getButton(-1).setEnabled(!busy); dialog.getButton(-1).setText(busy ? "Salvando…" : "Salvar"); }
      if (dialog.getButton(-2) != null) dialog.getButton(-2).setEnabled(!busy);
    }
  }

  @Override public void onSaveInstanceState(@NonNull Bundle state) {
    for (Map.Entry<String, TextInputEditText> entry : fields.entrySet()) state.putString(entry.getKey(), PlatformUi.value(entry.getValue()));
    if (active != null) state.putBoolean("active", active.isChecked());
    super.onSaveInstanceState(state);
  }
}
