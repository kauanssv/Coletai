package com.example.coletai;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONObject;

final class ProfileAvatar {
  static android.view.View create(Context context, JSONObject profile, int size) {
    FrameLayout frame = new FrameLayout(context);
    int pixels = PlatformUi.dp(context, size);
    frame.setLayoutParams(new android.widget.LinearLayout.LayoutParams(pixels, pixels));
    frame.setBackground(PlatformUi.surface(context, 0xFFEAF8F3, size / 2));
    JSONObject photo = profile.optJSONObject("profile_photo");
    if (photo != null) {
      com.google.android.material.imageview.ShapeableImageView image = new com.google.android.material.imageview.ShapeableImageView(context);
      image.setShapeAppearanceModel(image.getShapeAppearanceModel().toBuilder()
          .setAllCornerSizes(new com.google.android.material.shape.RelativeCornerSize(.5f)).build());
      image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
      image.setContentDescription("Foto de perfil");
      frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
      RequestPhotoStore.bind(image, photo, size > 80);
    } else {
      TextView initials = new TextView(context);
      StringBuilder text = new StringBuilder();
      for (String part : profile.optString("name").trim().split("\\s+"))
        if (!part.isEmpty() && text.length() < 2) text.append(part.charAt(0));
      initials.setText(text.toString().toUpperCase(java.util.Locale.forLanguageTag("pt-BR")));
      initials.setTextSize(size / 3f);
      initials.setTextColor(0xFF0F7667);
      initials.setGravity(Gravity.CENTER);
      frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));
    }
    return frame;
  }
}
