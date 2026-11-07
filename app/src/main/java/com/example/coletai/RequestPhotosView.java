package com.example.coletai;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

final class RequestPhotosView {
  interface Select { void open(int position); }

  static void edit(LinearLayout parent, JSONArray photos, Select select) {
    parent.removeAllViews();
    PlatformUi.section(parent, "FOTOS (OPCIONAL)");
    LinearLayout row = PlatformUi.column(parent.getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    for (int i = 0; i < PhotoRefs.LIMIT; i++) {
      final int position = i;
      Context context = parent.getContext();
      LinearLayout slot = PlatformUi.column(context);
      slot.setGravity(Gravity.CENTER);
      slot.setMinimumHeight(PlatformUi.dp(context, 100));
      slot.setPadding(PlatformUi.dp(context, 6), PlatformUi.dp(context, 8), PlatformUi.dp(context, 6), 0);
      slot.setBackground(PlatformUi.surface(context, 0xFFF3F7F6, 12));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
      if (i > 0) params.leftMargin = PlatformUi.dp(context, 10);
      row.addView(slot, params);
      ImageView image = new ImageView(context);
      JSONObject ref = photos.optJSONObject(i);
      image.setScaleType(ref == null ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
      image.setImageResource(R.drawable.ic_camera);
      slot.addView(image, new LinearLayout.LayoutParams(-1, PlatformUi.dp(context, ref == null ? 36 : 70)));
      if (ref != null) RequestPhotoStore.bind(image, ref, false);
      TextView label = PlatformUi.text(slot, ref == null ? "Adicionar" : "Foto " + (i + 1) + " · Editar", 11, false);
      label.setGravity(Gravity.CENTER);
      label.setTextColor(0xFF6F817D);
      slot.setFocusable(true);
      slot.setContentDescription(ref == null ? "Adicionar foto " + (i + 1) : "Editar foto " + (i + 1));
      slot.setOnClickListener(v -> select.open(position));
    }
    PlatformUi.text(parent, photos.length() + "/3 fotos · Toque para adicionar, trocar ou remover.", 12, false).setTextColor(0xFF7B8B98);
  }

  static void display(LinearLayout row, JSONArray photos) {
    row.removeAllViews();
    if (photos == null || photos.length() == 0) {
      PlatformUi.text(row, "Nenhuma foto adicionada.", 13, false).setTextColor(0xFF7B8B98);
      return;
    }
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (int i = 0; i < Math.min(PhotoRefs.LIMIT, photos.length()); i++) {
      JSONObject ref = photos.optJSONObject(i);
      if (ref == null) continue;
      ImageView image = new ImageView(row.getContext());
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setContentDescription("Abrir foto " + (i + 1) + " do pedido");
      image.setFocusable(true);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, PlatformUi.dp(row.getContext(), 100), 1);
      if (i > 0) params.leftMargin = PlatformUi.dp(row.getContext(), 8);
      row.addView(image, params);
      RequestPhotoStore.bind(image, ref, false);
      image.setOnClickListener(v -> RequestPhotoStore.preview(row.getContext(), ref));
    }
    for (int i = photos.length(); i < PhotoRefs.LIMIT; i++) {
      LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(0, 1, 1);
      space.leftMargin = PlatformUi.dp(row.getContext(), 8);
      row.addView(new android.widget.Space(row.getContext()), space);
    }
  }

  static void section(LinearLayout parent, JSONArray photos) {
    PlatformUi.section(parent, "FOTOS DO PEDIDO");
    LinearLayout row = PlatformUi.column(parent.getContext());
    parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    display(row, photos);
  }
}
