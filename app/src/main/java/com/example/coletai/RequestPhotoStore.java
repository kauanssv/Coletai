package com.example.coletai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class RequestPhotoStore {
  static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newFixedThreadPool(2);

  static File file(Context context, JSONObject photo) {
    JSONArray checked = PhotoRefs.copy(new JSONArray().put(photo));
    JSONObject ref = checked.optJSONObject(0);
    File folder = new File("remote".equals(ref.optString("source")) ? context.getCacheDir() : context.getFilesDir(), "request_photos");
    if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Não foi possível salvar a foto");
    return new File(folder, ref.optString("source") + "_" + ref.optString("id") + ".jpg");
  }

  static JSONObject importImage(Context context, Uri uri) {
    File temporary = null;
    Bitmap bitmap = null;
    try {
      temporary = File.createTempFile("photo_import_", ".tmp", context.getCacheDir());
      try (InputStream input = context.getContentResolver().openInputStream(uri);
          FileOutputStream output = new FileOutputStream(temporary)) {
        if (input == null) throw new IllegalArgumentException();
        byte[] buffer = new byte[8192];
        int total = 0, count;
        while ((count = input.read(buffer)) != -1) {
          total += count;
          if (total > 50 * 1024 * 1024) throw new IllegalArgumentException("Escolha uma imagem de até 50 MB");
          output.write(buffer, 0, count);
        }
      }
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(temporary.getAbsolutePath(), options);
      if (options.outWidth <= 0 || options.outHeight <= 0 || (long) options.outWidth * options.outHeight > 150000000)
        throw new IllegalArgumentException("Esta imagem não pode ser aberta. Escolha outra foto.");
      int longest = Math.max(options.outWidth, options.outHeight);
      options.inSampleSize = 1;
      while (longest / options.inSampleSize > 1600) options.inSampleSize *= 2;
      options.inJustDecodeBounds = false;
      bitmap = BitmapFactory.decodeFile(temporary.getAbsolutePath(), options);
      if (bitmap == null) throw new IllegalArgumentException("Esta imagem não pode ser aberta. Escolha outra foto.");
      int orientation = android.media.ExifInterface.ORIENTATION_NORMAL;
      try { orientation = new android.media.ExifInterface(temporary.getAbsolutePath())
          .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL); }
      catch (java.io.IOException ignored) { }
      Matrix matrix = new Matrix();
      switch (orientation) {
        case 2: matrix.setScale(-1, 1); break;
        case 3: matrix.setRotate(180); break;
        case 4: matrix.setScale(1, -1); break;
        case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
        case 6: matrix.setRotate(90); break;
        case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
        case 8: matrix.setRotate(270); break;
      }
      if (!matrix.isIdentity()) {
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated; }
      }
      byte[] bytes;
      while (true) {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 78, encoded);
        bytes = encoded.toByteArray();
        if (bytes.length <= PhotoRefs.MAX_BYTES) break;
        int width = Math.max(1, bitmap.getWidth() * 3 / 4), height = Math.max(1, bitmap.getHeight() * 3 / 4);
        if (width < 100 || height < 100) throw new IllegalArgumentException("Não foi possível preparar esta foto");
        Bitmap smaller = Bitmap.createScaledBitmap(bitmap, width, height, true);
        bitmap.recycle(); bitmap = smaller;
      }
      JSONObject photo = new JSONObject();
      LocalJson.put(photo, "id", UUID.randomUUID().toString());
      LocalJson.put(photo, "source", "local");
      try (FileOutputStream output = new FileOutputStream(file(context, photo))) { output.write(bytes); }
      return photo;
    } catch (java.io.IOException | SecurityException error) {
      throw new IllegalArgumentException("Não foi possível ler a foto. Escolha a imagem novamente.");
    } finally {
      if (bitmap != null) bitmap.recycle();
      if (temporary != null) temporary.delete();
    }
  }

  private static byte[] bytes(File file) {
    try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; int count;
      while ((count = input.read(buffer)) != -1) {
        if (output.size() + count > PhotoRefs.MAX_BYTES) throw new IllegalArgumentException("Foto muito grande");
        output.write(buffer, 0, count);
      }
      return output.toByteArray();
    } catch (java.io.IOException error) { throw new IllegalArgumentException("Foto não encontrada. Escolha a imagem novamente."); }
  }

  static JSONObject prepareOrder(Context context, JSONObject order) {
    JSONObject payload = LocalJson.object(order.toString());
    if (!payload.has("photos")) return payload;
    JSONArray photos = PhotoRefs.copy(payload.optJSONArray("photos"));
    for (int i = 0; i < photos.length(); i++) {
      JSONObject photo = photos.optJSONObject(i);
      if ("local".equals(photo.optString("source"))) {
        File local = file(context, photo);
        if (PlatformGateway.configured()) {
          // Cache upload identity so retrying an order does not upload the same photo again.
          android.content.SharedPreferences prefs = PlatformStore.open(context);
          String key = "uploaded_photo_" + LocalJson.account(prefs) + "_" + photo.optString("id");
          String uploaded = prefs.getString(key, "");
          JSONObject remote = PhotoRefs.validId(uploaded)
              ? LocalJson.object("{\"source\":\"remote\",\"id\":\"" + uploaded + "\"}")
              : PlatformGateway.uploadPhoto(prefs, bytes(local));
          LocalJson.commit(prefs.edit().putString(key, remote.optString("id")));
          File cache = file(context, remote);
          if (!cache.exists()) try (FileOutputStream output = new FileOutputStream(cache)) { output.write(bytes(local)); }
          catch (java.io.IOException ignored) { }
          try { photos.put(i, remote); } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        } else bytes(local); // Confirm a selected local image is still readable before saving.
      }
    }
    LocalJson.put(payload, "photos", photos);
    return payload;
  }

  static void bind(ImageView view, JSONObject photo, boolean fullSize) {
    Context context = view.getContext().getApplicationContext();
    String identity = photo.optString("source") + photo.optString("id");
    view.setTag(identity);
    view.setImageResource(R.drawable.ic_camera);
    IO.execute(() -> {
      Bitmap bitmap = null;
      try {
        File local = file(context, photo);
        if (!local.exists() && "remote".equals(photo.optString("source"))) {
          byte[] content = PlatformGateway.downloadPhoto(PlatformStore.open(context), photo.optString("id"));
          File temporary = File.createTempFile("download_", ".jpg", local.getParentFile());
          try {
            try (FileOutputStream output = new FileOutputStream(temporary)) { output.write(content); }
            if (!temporary.renameTo(local) && !local.exists()) throw new java.io.IOException();
          } finally { temporary.delete(); }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = fullSize ? 1 : 4;
        bitmap = BitmapFactory.decodeFile(local.getAbsolutePath(), options);
        if (bitmap == null && "remote".equals(photo.optString("source"))) local.delete();
      } catch (RuntimeException | java.io.IOException ignored) { }
      final Bitmap ready = bitmap;
      view.post(() -> {
        if (!identity.equals(view.getTag()) || !view.isAttachedToWindow()) { if (ready != null) ready.recycle(); return; }
        if (ready != null) view.setImageBitmap(ready);
        else view.setContentDescription("Não foi possível abrir a foto. Toque para tentar novamente.");
      });
    });
  }

  static void preview(Context context, JSONObject photo) {
    ImageView image = new ImageView(context);
    image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setMinimumHeight(PlatformUi.dp(context, 240));
    new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle("Foto do pedido").setView(image).setPositiveButton("Fechar", null).show();
    bind(image, photo, true);
  }
}
