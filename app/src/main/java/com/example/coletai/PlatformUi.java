package com.example.coletai;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

final class PlatformUi {
  static int dp(Context context, int dp) {
    return Math.round(dp * context.getResources().getDisplayMetrics().density);
  }

  static LinearLayout column(Context context) {
    LinearLayout layout = new LinearLayout(context);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  static TextInputEditText field(LinearLayout form, String label, String value, int type) {
    Context context = form.getContext();
    TextInputLayout box = new TextInputLayout(context);
    box.setHint(label);
    box.setExpandedHintEnabled(false);
    box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
    float radius = dp(context, 12);
    box.setBoxCornerRadii(radius, radius, radius, radius);
    box.setBoxBackgroundColor(0xFFF5F8F7);
    box.setBoxStrokeColor(0xFFDCE7E3);
    box.setPlaceholderTextColor(android.content.res.ColorStateList.valueOf(0xFF7B8B98));
    TextInputEditText input = new TextInputEditText(context);
    input.setInputType(type);
    input.setTextSize(15);
    input.setTextColor(0xFF102522);
    input.setMinHeight(dp(context, 52));
    input.setPadding(dp(context, 14), dp(context, 16), dp(context, 14), dp(context, 16));
    input.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
    input.setBackground(null);
    input.setText(value);
    input.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
    if (android.os.Build.VERSION.SDK_INT >= 26)
      input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
    box.addView(input, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.topMargin = dp(context, 12);
    form.addView(box, params);
    return input;
  }

  static TextView text(LinearLayout form, String text, int size, boolean bold) {
    TextView view = new TextView(form.getContext());
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(0xFF102522);
    if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
    view.setPadding(0, dp(form.getContext(), 8), 0, dp(form.getContext(), 8));
    if (!text.trim().isEmpty()) form.addView(view);
    return view;
  }

  static MaterialButton button(LinearLayout form, String text, Runnable action) {
    MaterialButton button = new MaterialButton(form.getContext());
    button.setText(text);
    button.setAllCaps(false);
    button.setTextColor(androidx.appcompat.content.res.AppCompatResources.getColorStateList(form.getContext(), R.color.button_primary_text));
    button.setIconTint(androidx.appcompat.content.res.AppCompatResources.getColorStateList(form.getContext(), R.color.button_primary_text));
    button.setBackgroundTintList(androidx.appcompat.content.res.AppCompatResources.getColorStateList(form.getContext(), R.color.button_primary_background));
    button.setMinHeight(dp(form.getContext(), 48));
    button.setCornerRadius(dp(form.getContext(), 12));
    button.setGravity(android.view.Gravity.CENTER);
    button.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
    button.setOnClickListener(v -> action.run());
    form.addView(button, new LinearLayout.LayoutParams(-1, -2));
    return button;
  }

  static String value(TextInputEditText input) {
    return input.getText() == null ? "" : input.getText().toString().trim();
  }

  static android.graphics.drawable.GradientDrawable surface(Context context, int color, int radius) {
    android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
    background.setColor(color);
    background.setCornerRadius(dp(context, radius));
    background.setStroke(dp(context, 1), 0xFFE0EAE6);
    return background;
  }

  static LinearLayout card(LinearLayout parent) {
    Context context = parent.getContext();
    LinearLayout card = column(context);
    card.setBackground(surface(context, 0xFFFFFFFF, 20));
    int pad = dp(context, 16);
    card.setPadding(pad, pad / 2, pad, pad / 2);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.bottomMargin = dp(context, 14);
    parent.addView(card, params);
    return card;
  }

  static void section(LinearLayout parent, String title) {
    TextView label = text(parent, title, 11, false);
    label.setTextColor(0xFF6F817D);
    label.setLetterSpacing(.06f);
    label.setPadding(0, dp(parent.getContext(), 18), 0, dp(parent.getContext(), 10));
  }

  static String status(String status) {
    switch (status) {
      case "aberta":
        return "Procurando um coletor";
      case "negociando":
        return "Proposta em preparação";
      case "proposta":
        return "Aguardando confirmação do cliente";
      case "coletando":
        return "Coletor no local";
      case "descartando":
        return "Materiais a caminho do descarte";
      case "aceita":
        return "Coleta confirmada";
      case "a_caminho":
        return "Coletor a caminho";
      case "concluida":
        return "Concluída";
      case "cancelada":
        return "Cancelada";
      default:
        return status;
    }
  }
}
