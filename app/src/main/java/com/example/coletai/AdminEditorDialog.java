package com.example.coletai;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Editor com validação, confirmação de exclusão e preservação de rascunhos na rotação. */
public class AdminEditorDialog extends DialogFragment {
  private final Map<String, TextInputEditText> fields = new LinkedHashMap<>();
  private final Map<String, String> initial = new LinkedHashMap<>();
  private JSONObject original;
  private ProfileRepository profiles;
  private AdminRepository admin;
  private LinearLayout form;
  private TextView error;
  private MaterialAutoCompleteTextView roles;
  private String kind, account, id;
  private boolean newProfile, deleted;
  private boolean saving;
  private Bundle draft;

  static AdminEditorDialog profile(String id) {
    return create("profile", "", id);
  }

  static AdminEditorDialog order(String account, String id) {
    return create("order", account, id);
  }

  private static AdminEditorDialog create(String kind, String account, String id) {
    AdminEditorDialog dialog = new AdminEditorDialog();
    Bundle args = new Bundle();
    args.putString("kind", kind);
    args.putString("account", account);
    args.putString("id", id);
    dialog.setArguments(args);
    return dialog;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle state) {
    kind = requireArguments().getString("kind");
    account = requireArguments().getString("account", "");
    id = requireArguments().getString("id", "");
    newProfile = "profile".equals(kind) && id.isEmpty();
    SharedPreferences prefs =
        PlatformStore.open(requireContext());
    profiles = new ProfileRepository(prefs);
    admin = new AdminRepository(prefs);
    profiles.requireAdmin();
    original =
        state != null
            ? LocalJson.object(state.getString("original", "{}"))
            : newProfile
                ? new JSONObject()
                : "profile".equals(kind) ? profiles.get(id) : admin.order(account, id);
    draft = state == null ? null : state.getBundle("draft");
    deleted =
        "profile".equals(kind)
            ? "deleted".equals(original.optString("status"))
            : original.optBoolean("deleted");
    ScrollView scroll = new ScrollView(requireContext());
    scroll.setFillViewport(false);
    form = new LinearLayout(requireContext());
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(dp(24), dp(8), dp(24), dp(16));
    scroll.addView(form);
    if ("profile".equals(kind)) buildProfile();
    else buildOrder();
    error = new TextView(requireContext());
    error.setTextColor(0xFFB42318);
    error.setTextSize(14);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    error.setVisibility(View.GONE);
    form.addView(error);
    int title =
        newProfile
            ? R.string.admin_new_profile
            : "profile".equals(kind) ? R.string.admin_edit_profile : R.string.admin_edit_order;
    AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton(R.string.admin_back, null)
            .setPositiveButton(newProfile ? R.string.admin_create : R.string.admin_save, null)
            .create();
    dialog.setCanceledOnTouchOutside(false);
    dialog.setOnKeyListener((d, code, event) -> {
      if (code == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
        close();
        return true;
      }
      return false;
    });
    return dialog;
  }

  private void buildProfile() {
    if (!newProfile) {
      note(
          getString(
              R.string.admin_account_detail,
              AdminText.role(requireContext(), original.optString("role")),
              AdminText.profileStatus(requireContext(), original.optString("status"))));
      note(getString(R.string.admin_record_id, id));
    }
    if (newProfile) {
      note("Defina uma senha inicial de pelo menos 8 caracteres para a nova conta.");
      field(
          "password",
          R.string.platform_initial_password,
          InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
          false);
      TextInputLayout layout = new TextInputLayout(requireContext());
      layout.setHint(R.string.admin_role);
      layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
      layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
      roles = new MaterialAutoCompleteTextView(requireContext());
      roles.setId(R.id.adminRoleInput);
      roles.setInputType(InputType.TYPE_NULL);
      roles.setThreshold(0);
      String[] names = {
        getString(R.string.admin_role_client),
        getString(R.string.admin_role_collector),
        getString(R.string.admin_role_company)
      };
      roles.setAdapter(
          new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names));
      roles.setText(draft == null ? names[0] : draft.getString("role_name", names[0]), false);
      layout.addView(roles);
      form.addView(layout, params());
      roles.setOnClickListener(v -> roles.showDropDown());
      layout.setEndIconOnClickListener(v -> roles.showDropDown());
    }
    field(
        "name",
        R.string.admin_name,
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
        false);
    field(
        "email",
        R.string.admin_email,
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        false);
    field("phone", R.string.admin_phone, InputType.TYPE_CLASS_PHONE, false);
    if (!"admin".equals(original.optString("role"))) {
      field("document", R.string.admin_document, InputType.TYPE_CLASS_TEXT, false);
      field(
          "address",
          R.string.admin_address,
          InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
          false);
    }
    if (newProfile || "empresa".equals(original.optString("role"))) {
      field(
          "company",
          R.string.admin_company,
          InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
          false);
      field(
          "legal_name",
          R.string.admin_legal_name,
          InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
          false);
      if (roles != null) {
        roles.setOnItemClickListener((parent, view, position, rowId) -> companyVisibility());
        companyVisibility();
      }
    }
    if (deleted) {
      note(getString(R.string.admin_deleted_profile_info));
      action(
          R.string.admin_restore,
          R.string.admin_restore_confirm,
          () -> profiles.setStatus(id, "active"),
          false);
    } else if (!newProfile && !"admin".equals(original.optString("role"))) {
      boolean blocked = "blocked".equals(original.optString("status"));
      action(
          blocked ? R.string.admin_unblock : R.string.admin_block,
          blocked ? R.string.admin_restore_confirm : R.string.admin_block_confirm,
          () -> profiles.setStatus(id, blocked ? "active" : "blocked"),
          false);
      action(
          R.string.admin_delete,
          R.string.admin_delete_profile_confirm,
          () -> profiles.setStatus(id, "deleted"),
          true);
    }
  }

  private void buildOrder() {
    note(
        original.optString("account_name")
            + " · "
            + AdminText.orderStatus(requireContext(), original));
    note(getString(R.string.admin_record_id, id));
    note(getString(R.string.admin_order_local_info));
    field(
        "pedido_tipo",
        R.string.admin_residue,
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS,
        false);
    field(
        "pedido_descricao",
        R.string.admin_description,
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        true);
    field(
        "pedido_peso",
        R.string.admin_weight,
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
        false);
    field(
        "pedido_endereco_completo",
        R.string.admin_address,
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
        false);
    field(
        "pedido_observacao",
        R.string.admin_notes,
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        true);

    if (deleted)
      action(
          R.string.admin_restore,
          R.string.admin_restore_confirm,
          () -> admin.setDeleted(account, id, false),
          false);
    else if (AdminText.open(original))
      action(
          R.string.admin_cancel_order,
          R.string.admin_cancel_confirm,
          () -> admin.cancelOrder(account, id),
          true);
    else
      action(
          R.string.admin_delete,
          R.string.admin_delete_order_confirm,
          () -> admin.setDeleted(account, id, true),
          true);
  }

  private void field(String key, int label, int input, boolean multiline) {
    TextInputLayout layout = new TextInputLayout(requireContext());
    layout.setHint(label);
    layout.setExpandedHintEnabled(false);
    layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    TextInputEditText value = new TextInputEditText(requireContext());
    value.setId(View.generateViewId());
    value.setInputType(input);
    value.setTextSize(15);
    value.setMinHeight(dp(52));
    value.setPadding(dp(14), dp(16), dp(14), dp(16));
    value.setGravity(android.view.Gravity.START | (multiline ? android.view.Gravity.TOP : android.view.Gravity.CENTER_VERTICAL));
    if (android.os.Build.VERSION.SDK_INT >= 26)
      value.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    value.setSaveEnabled(false);
    value.setBackground(null);
    value.setFilters(
        new android.text.InputFilter[] {
          new android.text.InputFilter.LengthFilter(multiline ? 500 : 240)
        });
    value.setMinLines(multiline ? 2 : 1);
    value.setMaxLines(multiline ? 4 : 2);
    String initialValue = original.optString(key);
    if ("pedido_peso".equals(key)) initialValue = initialValue.replace(" kg", "");
    initial.put(key, initialValue);
    value.setText(draft == null ? initialValue : draft.getString(key, initialValue));
    value.setEnabled(!deleted);
    fields.put(key, value);
    layout.addView(value);
    form.addView(layout, params());
  }

  private void companyVisibility() {
    int visibility =
        getString(R.string.admin_role_company).equals(roles.getText().toString())
            ? View.VISIBLE
            : View.GONE;
    ((View) fields.get("company").getParent().getParent()).setVisibility(visibility);
    ((View) fields.get("legal_name").getParent().getParent()).setVisibility(visibility);
  }

  private void note(String message) {
    TextView view = new TextView(requireContext());
    view.setText(message);
    view.setTextColor(0xFF6F817D);
    view.setTextSize(13);
    view.setPadding(0, dp(4), 0, dp(10));
    view.setTextIsSelectable(true);
    form.addView(view);
  }

  private void action(int title, int message, Runnable work, boolean destructive) {
    MaterialButton button =
        new MaterialButton(requireContext(), null, androidx.appcompat.R.attr.borderlessButtonStyle);
    button.setText(title);
    button.setMinHeight(dp(48));
    if (destructive) button.setTextColor(0xFFB42318);
    button.setOnClickListener(v ->
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                R.string.admin_confirm,
                (d, which) -> {
                  submit(work);
                })
            .show());
    form.addView(button, params());
  }

  @Override
  public void onStart() {
    super.onStart();
    AlertDialog dialog = (AlertDialog) requireDialog();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(deleted ? View.GONE : View.VISIBLE);
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save());
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> close());
    if (dialog.getWindow() != null) {
      int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(600));
      dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
  }

  private void save() {
    if (saving) return;
    JSONObject changed = LocalJson.object(original.toString());
    for (Map.Entry<String, TextInputEditText> entry : fields.entrySet())
      LocalJson.put(
          changed,
          entry.getKey(),
          entry.getValue().getText() == null ? "" : entry.getValue().getText().toString());
    if (newProfile) {
      String role =
          getString(R.string.admin_role_company).equals(roles.getText().toString())
              ? "empresa"
              : getString(R.string.admin_role_collector).equals(roles.getText().toString())
                  ? "ambulante"
                  : "cliente";
      LocalJson.put(changed, "role", role);
    }
    try {
      Runnable work =
          () -> {
            if ("profile".equals(kind)) {
              if (newProfile) profiles.createByAdmin(changed, changed.optString("password"));
              else profiles.save(changed, true);
            } else admin.editOrder(account, id, changed, original.optInt("revision"));
          };
      submit(work);
    } catch (RuntimeException e) {
      showError(e);
    }
  }

  private void showError(RuntimeException e) {
    if (!isAdded()) return;
    error.setText(e.getMessage());
    error.setVisibility(View.VISIBLE);
    error.post(() ->
        error.requestRectangleOnScreen(
            new android.graphics.Rect(0, 0, error.getWidth(), error.getHeight()), true));
  }

  private void updated() {
    if (!isAdded()) return;
    getParentFragmentManager().setFragmentResult("admin_updated", new Bundle());
    Toast.makeText(requireContext(), R.string.admin_saved, Toast.LENGTH_SHORT).show();
    dismiss();
  }

  private boolean dirty() {
    for (Map.Entry<String, TextInputEditText> entry : fields.entrySet())
      if (!initial
          .get(entry.getKey())
          .equals(entry.getValue().getText() == null ? "" : entry.getValue().getText().toString()))
        return true;
    if (roles != null && !getString(R.string.admin_role_client).equals(roles.getText().toString()))
      return true;
    return false;
  }

  private void close() {
    if (saving) return;
    if (!dirty()) {
      dismiss();
      return;
    }
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.admin_discard_title)
        .setMessage(R.string.admin_discard_hint)
        .setNegativeButton(R.string.admin_keep_editing, null)
        .setPositiveButton(R.string.admin_discard, (dialog, which) -> dismiss())
        .show();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle out) {
    out.putString("original", original.toString());
    Bundle values = new Bundle();
    for (Map.Entry<String, TextInputEditText> entry : fields.entrySet())
      values.putString(
          entry.getKey(),
          entry.getValue().getText() == null ? "" : entry.getValue().getText().toString());
    if (roles != null) values.putString("role_name", roles.getText().toString());
    out.putBundle("draft", values);
    super.onSaveInstanceState(out);
  }

  private LinearLayout.LayoutParams params() {
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    p.topMargin = dp(12);
    return p;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void submit(Runnable work) {
    if (saving) return;
    saving = true;
    setCancelable(false);
    AlertDialog dialog = (AlertDialog) requireDialog();
    dialog.getButton(-1).setEnabled(false);
    dialog.getButton(-1).setText("Salvando…");
    dialog.getButton(-2).setEnabled(false);
    PlatformTasks.run(requireActivity(), work, this::updated, () -> {
      saving = false;
      if (!isAdded() || getDialog() == null) return;
      setCancelable(true);
      dialog.getButton(-1).setEnabled(true);
      dialog.getButton(-1).setText(newProfile ? R.string.admin_create : R.string.admin_save);
      dialog.getButton(-2).setEnabled(true);
    }, this::showError);
  }
}
