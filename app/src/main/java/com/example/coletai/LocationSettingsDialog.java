package com.example.coletai;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;

/** A single place for device location preferences and the current account's addresses. */
public class LocationSettingsDialog extends BottomSheetDialogFragment {
  private LinearLayout content;
  private CollectionRepository repo;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged =
      (prefs, key) -> { if (isAdded() && content != null) render(); };

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle state) {
    repo = new CollectionRepository(PlatformStore.open(requireContext()));
    BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
    LinearLayout root = PlatformUi.column(requireContext());
    root.setBackgroundResource(R.drawable.bg_bottom_sheet);
    int pad = dp(24);
    View handle = new View(requireContext());
    handle.setBackgroundResource(R.drawable.bg_sheet_handle);
    LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
    handleParams.gravity = Gravity.CENTER_HORIZONTAL;
    handleParams.topMargin = dp(12);
    root.addView(handle, handleParams);

    LinearLayout header = PlatformUi.column(requireContext());
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(pad, dp(16), pad, dp(8));
    TextView title = PlatformUi.text(header, "Localização", 25, true);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton close = new ImageButton(requireContext());
    close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
    close.setPadding(dp(12), dp(12), dp(12), dp(12));
    close.setImageTintList(ColorStateList.valueOf(0xFF6F817D));
    close.setBackgroundResource(R.drawable.bg_location_option);
    close.setContentDescription("Fechar configurações de localização");
    close.setOnClickListener(v -> dismiss());
    header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);

    ScrollView scroll = new ScrollView(requireContext());
    scroll.setClipToPadding(false);
    content = PlatformUi.column(requireContext());
    content.setPadding(pad, 0, pad, pad);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    dialog.setContentView(root);
    getChildFragmentManager().setFragmentResultListener(
        "platform_updated", this, (key, result) -> render());
    render();
    return dialog;
  }

  @Override
  public void onStart() {
    super.onStart();
    View sheet = requireDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
    if (sheet != null) {
      sheet.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * .86);
      BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
      behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
      behavior.setSkipCollapsed(true);
    }
    DeviceLocation.settings(requireContext()).registerOnSharedPreferenceChangeListener(preferenceChanged);
  }

  @Override
  public void onResume() {
    super.onResume();
    render();
  }

  @Override
  public void onStop() {
    DeviceLocation.settings(requireContext()).unregisterOnSharedPreferenceChangeListener(preferenceChanged);
    super.onStop();
  }

  private int dp(int value) { return PlatformUi.dp(requireContext(), value); }

  private void render() {
    if (content == null) return;
    content.removeAllViews();
    TextView intro = PlatformUi.text(content, "Escolha como o Coletaí encontra você e organize seus endereços de coleta.", 14, false);
    intro.setTextColor(0xFF6F817D);
    label("LOCAL PADRÃO");
    boolean automatic = DeviceLocation.automatic(requireContext());
    option("Usar minha localização atual", "O GPS acompanha sua posição enquanto você usa o app.",
        R.drawable.ic_location_small, automatic, true, () -> ((WorkspaceActivity) requireActivity()).useGpsLocation());
    option("Escolher no mapa", "Defina outro local para usar como referência.",
        R.drawable.ic_map, !automatic, true, () -> ((WorkspaceActivity) requireActivity()).chooseLocationOnMap());
    TextView shared = PlatformUi.text(content, "Essa escolha vale para todos os perfis neste celular.", 12, false);
    shared.setTextColor(0xFF6F817D);

    if ("cliente".equals(new ProfileRepository(PlatformStore.open(requireContext())).current().optString("role"))) {
      label("MEUS ENDEREÇOS");
      java.util.List<JSONObject> addresses = repo.addresses();
      if (addresses.isEmpty()) {
        TextView empty = PlatformUi.text(content, "Casa, trabalho ou outro lugar: salve um endereço para facilitar seus próximos pedidos.", 14, false);
        empty.setTextColor(0xFF6F817D);
      }
      for (JSONObject address : addresses) addressCard(address);
      MaterialButton add = PlatformUi.button(content, "Adicionar endereço", () ->
          AddressEditorDialog.create(new JSONObject()).show(getChildFragmentManager(), "address_editor"));
      add.setCornerRadius(dp(16));
      add.setIconResource(R.drawable.ic_location_small);
      add.setMinHeight(dp(52));
    }

    label("ACESSO À LOCALIZAÇÃO");
    option("Permissões do celular", "Permita ou revise o acesso à localização nas configurações do Android.",
        R.drawable.ic_location_small, false, false, () -> startActivity(new Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:" + requireContext().getPackageName()))));
    option("Configurações do GPS", "Ative ou desative a localização do aparelho.",
        R.drawable.ic_map, false, false,
        () -> startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
  }

  private void label(String text) {
    TextView label = PlatformUi.text(content, text, 12, true);
    label.setTextColor(0xFF0F7667);
    label.setLetterSpacing(.08f);
    label.setPadding(0, dp(24), 0, dp(10));
  }

  private MaterialCardView card(boolean selected) {
    MaterialCardView card = new MaterialCardView(requireContext());
    card.setRadius(dp(18));
    card.setCardElevation(0);
    card.setStrokeWidth(dp(1));
    card.setStrokeColor(selected ? 0xFF0F7667 : 0xFFDCE7E3);
    card.setCardBackgroundColor(selected ? 0xFFEBF8F3 : Color.WHITE);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.bottomMargin = dp(10);
    content.addView(card, params);
    return card;
  }

  private void option(String title, String description, int icon, boolean selected, boolean choice, Runnable action) {
    MaterialCardView card = card(selected);
    LinearLayout row = PlatformUi.column(requireContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(16), dp(16), dp(16), dp(16));
    ImageView image = new ImageView(requireContext());
    image.setImageResource(icon);
    image.setImageTintList(ColorStateList.valueOf(0xFF0F7667));
    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(image, new LinearLayout.LayoutParams(dp(26), dp(26)));
    LinearLayout text = PlatformUi.column(requireContext());
    text.setPadding(dp(14), 0, dp(10), 0);
    row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    TextView heading = PlatformUi.text(text, title, 16, true);
    heading.setPadding(0, 0, 0, dp(4));
    TextView hint = PlatformUi.text(text, description, 13, false);
    hint.setTextColor(0xFF6F817D);
    hint.setPadding(0, 0, 0, 0);
    TextView indicator = new TextView(requireContext());
    indicator.setText(choice ? selected ? "●" : "○" : "›");
    indicator.setTextColor(selected ? 0xFF0F7667 : 0xFF97A6A1);
    indicator.setTextSize(24);
    indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(indicator);
    card.addView(row);
    card.setClickable(true);
    card.setFocusable(true);
    card.setSelected(selected);
    card.setContentDescription(title + (selected ? ", selecionado. " : ". ") + description);
    row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    card.setOnClickListener(v -> action.run());
  }

  private void addressCard(JSONObject address) {
    MaterialCardView card = card(false);
    LinearLayout details = PlatformUi.column(requireContext());
    details.setPadding(dp(16), dp(10), dp(16), dp(12));
    card.addView(details);
    PlatformUi.text(details, address.optString("nickname"), 16, true);
    TextView addressText = PlatformUi.text(details, CollectionRepository.addressText(address), 13, false);
    addressText.setTextColor(0xFF6F817D);
    LinearLayout actions = PlatformUi.column(requireContext());
    actions.setOrientation(LinearLayout.HORIZONTAL);
    details.addView(actions);
    MaterialButton edit = PlatformUi.button(actions, "Editar", () ->
        AddressEditorDialog.create(address).show(getChildFragmentManager(), "address_editor"));
    edit.setCornerRadius(dp(14));
    edit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    MaterialButton delete = PlatformUi.button(actions, "Excluir", () ->
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Excluir endereço?")
            .setMessage("Os pedidos anteriores manterão o endereço original.")
            .setNegativeButton("Voltar", null)
            .setPositiveButton("Excluir", (d, n) -> PlatformTasks.mutate(requireActivity(),
                () -> repo.deleteAddress(address.optString("id")), () -> { if (isAdded()) render(); }))
            .show());
    delete.setCornerRadius(dp(14));
    delete.setBackgroundTintList(ColorStateList.valueOf(0xFFF4F7F6));
    delete.setTextColor(0xFF3C504C);
    LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, -2, 1);
    deleteParams.leftMargin = dp(8);
    delete.setLayoutParams(deleteParams);
  }
}
