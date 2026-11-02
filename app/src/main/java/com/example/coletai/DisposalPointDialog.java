package com.example.coletai;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import org.json.JSONArray;

public class DisposalPointDialog extends BottomSheetDialogFragment {
  static final String TAG = "disposal_point_details";

  static DisposalPointDialog create(DisposalPoint point) {
    DisposalPointDialog dialog = new DisposalPointDialog();
    Bundle args = new Bundle(); args.putString("point", point.json().toString()); dialog.setArguments(args);
    return dialog;
  }

  @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle state) {
    DisposalPoint point = DisposalPoint.read(new JSONArray().put(LocalJson.object(requireArguments().getString("point", "{}")))).get(0);
    BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
    LinearLayout root = PlatformUi.column(requireContext());
    root.setBackgroundResource(R.drawable.bg_bottom_sheet);
    View handle = new View(requireContext());
    handle.setBackgroundResource(R.drawable.bg_sheet_handle);
    LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
    handleParams.gravity = Gravity.CENTER_HORIZONTAL; handleParams.topMargin = dp(12);
    root.addView(handle, handleParams);

    LinearLayout header = PlatformUi.column(requireContext());
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(dp(20), dp(16), dp(20), dp(16));
    ImageView icon = new ImageView(requireContext());
    icon.setImageResource(R.drawable.ic_leaf_small);
    icon.setImageTintList(ColorStateList.valueOf(0xFF0F7667));
    icon.setBackground(PlatformUi.surface(requireContext(), 0xFFEAF8F3, 24));
    icon.setPadding(dp(12), dp(12), dp(12), dp(12));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    header.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
    LinearLayout titles = PlatformUi.column(requireContext());
    titles.setPadding(dp(12), 0, dp(8), 0);
    header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
    TextView name = PlatformUi.text(titles, point.name, 19, true);
    name.setPadding(0, 0, 0, dp(4));
    TextView category = PlatformUi.text(titles, DisposalPoint.categoryName(point.category), 12, false);
    category.setTextColor(0xFF0F7667); category.setPadding(0, 0, 0, 0);
    ImageButton close = new ImageButton(requireContext());
    close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
    close.setImageTintList(ColorStateList.valueOf(0xFF6F817D));
    close.setPadding(dp(12), dp(12), dp(12), dp(12));
    close.setBackgroundResource(R.drawable.bg_location_option);
    close.setContentDescription("Fechar detalhes do ponto");
    close.setOnClickListener(v -> dismiss());
    header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    root.addView(header);

    ScrollView scroll = new ScrollView(requireContext());
    scroll.setClipToPadding(false);
    LinearLayout content = PlatformUi.column(requireContext());
    content.setPadding(dp(20), dp(12), dp(20), dp(16));
    content.setBackgroundColor(0xFFF4F7F6);
    scroll.addView(content);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

    information(content, "ENDEREÇO", point.address.isEmpty() ? "Endereço não informado. Veja a posição no mapa." : point.address, R.drawable.ic_location_small);
    PlatformUi.section(content, "MATERIAIS INFORMADOS");
    LinearLayout materials = PlatformUi.card(content);
    if (point.materials.isEmpty()) PlatformUi.text(materials, "O local não informou os materiais recebidos. Consulte antes de ir.", 14, false);
    else {
      ChipGroup chips = new ChipGroup(requireContext());
      chips.setSingleLine(false);
      chips.setChipSpacingHorizontal(dp(6)); chips.setChipSpacingVertical(dp(2));
      materials.addView(chips, new LinearLayout.LayoutParams(-1, -2));
      for (String material : point.materials) {
        Chip chip = new Chip(requireContext());
        chip.setText(material); chip.setTextSize(12); chip.setTextColor(0xFF0F7667);
        chip.setCheckable(false); chip.setClickable(false); chip.setFocusable(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf(0xFFEAF8F3));
        chip.setChipStrokeColor(ColorStateList.valueOf(0xFFD6F0E8)); chip.setChipStrokeWidth(dp(1));
        chips.addView(chip);
      }
    }
    information(content, "HORÁRIO DE FUNCIONAMENTO", point.hours.isEmpty() ? "Horário não informado. Confirme com o local." : point.hours,
        android.R.drawable.ic_menu_recent_history);
    if (!point.notes.isEmpty() || !point.publicAccess) {
      LinearLayout note = PlatformUi.card(content);
      note.setBackground(PlatformUi.surface(requireContext(), 0xFFFFF8E6, 16));
      PlatformUi.text(note, "ANTES DE IR", 11, true).setTextColor(0xFF91651C);
      if (!point.publicAccess) PlatformUi.text(note, "Acesso restrito. Confirme as condições de entrada.", 14, true);
      if (!point.notes.isEmpty()) PlatformUi.text(note, point.notes, 14, false);
    }
    TextView notice = PlatformUi.text(content, "Confirme os horários e os materiais recebidos antes de sair.", 12, false);
    notice.setTextColor(0xFF6F817D);
    String date = point.verifiedOn;
    if (date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) date = date.substring(8) + "/" + date.substring(5, 7) + "/" + date.substring(0, 4);
    TextView source = PlatformUi.text(content, "Fonte: " + point.sourceLabel + (date.isEmpty() ? "" : "\nConsultada em " + date), 11, false);
    source.setTextColor(0xFF7B8B98);
    if (!point.sourceUrl.isEmpty()) {
      MaterialButton link = PlatformUi.button(content, "Consultar fonte", () -> {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(point.sourceUrl))); }
        catch (android.content.ActivityNotFoundException ignored) { }
      });
      link.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF)); link.setTextColor(0xFF0F7667);
      link.setStrokeColor(ColorStateList.valueOf(0xFFDCE7E3)); link.setStrokeWidth(dp(1));
    }

    LinearLayout footer = PlatformUi.column(requireContext());
    footer.setPadding(dp(20), dp(10), dp(20), dp(20));
    boolean picking = requireActivity() instanceof LocationActivity && requireActivity().getIntent().getBooleanExtra("pick", false);
    MaterialButton route = PlatformUi.button(footer, picking ? "Selecionar este ponto" : "Traçar rota", () -> {
      if (picking) ((LocationActivity) requireActivity()).selectReferencePoint(point);
      else startActivity(new Intent(requireContext(), LocationActivity.class).putExtra("latitude", point.latitude)
          .putExtra("longitude", point.longitude).putExtra("force_gps", true).putExtra("destination_name", point.name));
      dismiss();
    });
    route.setIconResource(R.drawable.ic_map);
    route.setMinHeight(dp(52));
    root.addView(footer);
    dialog.setContentView(root);
    return dialog;
  }

  private int dp(int value) { return PlatformUi.dp(requireContext(), value); }

  private void information(LinearLayout parent, String title, String value, int iconResource) {
    LinearLayout card = PlatformUi.card(parent);
    card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.TOP);
    ImageView icon = new ImageView(requireContext());
    icon.setImageResource(iconResource); icon.setImageTintList(ColorStateList.valueOf(0xFF0F7667));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(22), dp(22));
    imageParams.topMargin = dp(10); imageParams.rightMargin = dp(12);
    card.addView(icon, imageParams);
    LinearLayout text = PlatformUi.column(requireContext());
    card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    PlatformUi.text(text, title, 11, true).setTextColor(0xFF6F817D);
    TextView description = PlatformUi.text(text, value, 14, false);
    description.setLineSpacing(dp(3), 1);
    description.setPadding(0, 0, 0, dp(8));
  }

  @Override public void onStart() {
    super.onStart();
    View sheet = requireDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
    if (sheet != null) {
      sheet.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * .88);
      BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
      behavior.setSkipCollapsed(true); behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
  }
}
