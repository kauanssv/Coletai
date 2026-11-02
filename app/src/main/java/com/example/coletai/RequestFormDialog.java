package com.example.coletai;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class RequestFormDialog extends com.google.android.material.bottomsheet.BottomSheetDialogFragment {
  private com.google.android.material.button.MaterialButton save;
  private CollectionRepository repo;
  public static RequestFormDialog createAt(android.location.Location location) {
    RequestFormDialog d = create("");
    if (location != null) {
      d.requireArguments().putBoolean("gps", !"manual".equals(location.getProvider()));
      d.requireArguments().putDouble("latitude", location.getLatitude());
      d.requireArguments().putDouble("longitude", location.getLongitude());
    }
    return d;
  }

  private JSONObject original;
  private TextInputEditText desired, description, notes;
  private Spinner address;
  private LinearLayout addressOptions;
  private List<JSONObject> addresses = new ArrayList<>();
  private final Map<String, TextInputEditText> weights = new LinkedHashMap<>();
  private final Map<String, CheckBox> selected = new LinkedHashMap<>();
  private boolean admin;
  private String selectedAddressId = "";
  private JSONArray photos = new JSONArray();
  private LinearLayout photosArea;
  private int photoTarget;
  private String pendingPhoto = "";
  private boolean preparingPhoto;
  private RequestSubmission submission;
  private android.widget.ImageButton closeButton;
  private final androidx.activity.result.ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
      new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        try { requireContext().getContentResolver().takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
        pendingPhoto = uri.toString();
        preparePhoto();
      });

  public static RequestFormDialog create(String id) {
    RequestFormDialog d = new RequestFormDialog();
    Bundle b = new Bundle();
    b.putString("id", id);
    d.setArguments(b);
    return d;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle state) {
    repo = new CollectionRepository(PlatformStore.open(requireContext()));
    String id = requireArguments().getString("id", "");
    original =
        state != null
            ? LocalJson.object(state.getString("original", "{}"))
            : id.isEmpty() ? new JSONObject() : repo.order(id);
    photos = PhotoRefs.copy(state != null ? LocalJson.array(state.getString("photos", "[]"))
        : original.optJSONArray("photos") == null ? new JSONArray() : original.optJSONArray("photos"));
    photoTarget = state == null ? 0 : state.getInt("photo_target", 0);
    pendingPhoto = state == null ? "" : state.getString("pending_photo", "");
    submission = new androidx.lifecycle.ViewModelProvider(this).get(RequestSubmission.class);
    submission.outcome.observe(this, outcome -> {
      updateSaveState();
      if (outcome == null || !isAdded()) return;
      submission.outcome.setValue(null);
      if (outcome.error == null) {
        getParentFragmentManager().setFragmentResult("platform_updated", new Bundle());
        dismiss();
      } else if (outcome.error instanceof PlatformGateway.SessionExpired) {
        LocalJson.commit(PlatformStore.open(requireContext()).edit().clear());
        startActivity(new android.content.Intent(requireContext(), MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        dismiss();
      } else android.widget.Toast.makeText(requireContext(), outcome.error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
    });
    selectedAddressId =
        state == null ? original.optString("address_id") : state.getString("selected_address", "");
    admin =
        "admin"
            .equals(
                new ProfileRepository(PlatformStore.open(requireContext()))
                    .current()
                    .optString("role"));
    ScrollView scroll = new ScrollView(requireContext());
    LinearLayout form = PlatformUi.column(requireContext());
    int pad = PlatformUi.dp(requireContext(), 20);
    form.setPadding(pad, 0, pad, pad);
    scroll.addView(form);
    PlatformUi.text(
        form,
        "Informe os materiais e confira o local. Você poderá aceitar ou recusar a proposta do coletor.",
        14,
        false);
    desired =
        PlatformUi.field(
            form,
            "Data desejada",
            state == null
                ? original.optString("desired_date", CollectionRepository.today())
                : state.getString("date"),
            InputType.TYPE_CLASS_DATETIME);
    desired.setFocusable(false);
    desired.setOnClickListener(v -> {
      java.util.Calendar c = java.util.Calendar.getInstance();
      new DatePickerDialog(
              requireContext(),
              (picker, y, m, d) ->
                  desired.setText(String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)),
              c.get(java.util.Calendar.YEAR),
              c.get(java.util.Calendar.MONTH),
              c.get(java.util.Calendar.DAY_OF_MONTH))
          .show();
    });
    if (!admin) {
      PlatformUi.text(form, "LOCALIZA\u00c7\u00c3O PARA COLETA", 14, true);
      address = new Spinner(requireContext());
      address.setVisibility(android.view.View.GONE);
      form.addView(address);
      addressOptions = PlatformUi.column(requireContext());
      form.addView(addressOptions);
      loadAddresses();
      PlatformUi.button(
          form,
          "Cadastrar endereço",
          () ->
              AddressEditorDialog.create(new JSONObject())
                  .show(getParentFragmentManager(), "address_editor"));
      getParentFragmentManager()
          .setFragmentResultListener("platform_updated", this, (key, b) -> {
            if (requireArguments().containsKey("latitude") && (selectedAddressId.isEmpty() || "__current_location".equals(selectedAddressId))) {
              for (JSONObject saved : repo.addresses()) {
                if (Math.abs(saved.optDouble("latitude", 999) - requireArguments().getDouble("latitude")) < 0.000001
                    && Math.abs(saved.optDouble("longitude", 999) - requireArguments().getDouble("longitude")) < 0.000001) {
                  selectedAddressId = saved.optString("id");
                }
              }
              address.setAdapter(null);
            }
            loadAddresses();
          });
    } else
      PlatformUi.text(
          form, CollectionRepository.addressText(original.optJSONObject("address")), 14, false);
    PlatformUi.section(form, "TIPO DE RES\u00cdDUO *");
    android.widget.HorizontalScrollView materialScroll = new android.widget.HorizontalScrollView(requireContext());
    materialScroll.setHorizontalScrollBarEnabled(false);
    com.google.android.material.chip.ChipGroup chips = new com.google.android.material.chip.ChipGroup(requireContext());
    chips.setSingleLine(true);
    chips.setChipSpacingHorizontal(PlatformUi.dp(requireContext(), 8));
    materialScroll.addView(chips);
    form.addView(materialScroll);
    JSONArray old = original.optJSONArray("items");
    List<JSONObject> availableMaterials = new ArrayList<>(repo.materials(false));
    if (old != null) for (int i = 0; i < old.length(); i++) {
      JSONObject previous = old.optJSONObject(i);
      if (previous == null) continue;
      String previousId = previous.optString("material_id");
      if (availableMaterials.stream().noneMatch(m -> previousId.equals(m.optString("id")))) {
        JSONObject retained = new JSONObject();
        LocalJson.put(retained, "id", previousId);
        LocalJson.put(retained, "name", previous.optString("name") + " (desativado)");
        availableMaterials.add(retained);
      }
    }
    for (JSONObject material : availableMaterials) {
      String materialId = material.optString("id"), oldWeight = "";
      if (old != null)
        for (int i = 0; i < old.length(); i++)
          if (materialId.equals(old.optJSONObject(i).optString("material_id")))
            oldWeight = old.optJSONObject(i).optString("weight");
      com.google.android.material.chip.Chip check = new com.google.android.material.chip.Chip(requireContext());
      check.setCheckable(true);
      check.setEnsureMinTouchTargetSize(true);
      check.setChipCornerRadius(PlatformUi.dp(requireContext(), 24));
      check.setText(material.optString("name"));
      check.setChecked(
          state == null ? !oldWeight.isEmpty() : state.getBoolean("checked_" + materialId));
      chips.addView(check);
      selected.put(materialId, check);
      TextInputEditText weight =
          PlatformUi.field(
              form,
              "Peso de " + material.optString("name") + " (kg)",
              state == null ? oldWeight : state.getString("weight_" + materialId, ""),
              InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
      weights.put(materialId, weight);
      android.view.View box = (android.view.View) weight.getParent().getParent();
      box.setVisibility(check.isChecked() ? android.view.View.VISIBLE : android.view.View.GONE);
      check.setOnCheckedChangeListener((button, on) ->
          box.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE));
    }
    description =
        PlatformUi.field(
            form,
            "Descrição dos materiais",
            state == null ? original.optString("description") : state.getString("description"),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    notes =
        PlatformUi.field(
            form,
            "Observações para coleta (opcional)",
            state == null ? original.optString("notes") : state.getString("notes"),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    description.setMinLines(3);
    description.setGravity(android.view.Gravity.TOP);
    notes.setMinLines(3);
    notes.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
    ((com.google.android.material.textfield.TextInputLayout) description.getParent().getParent())
        .setPlaceholderText("Ex.: 2 celulares quebrados, notebook antigo com carregador…");
    ((com.google.android.material.textfield.TextInputLayout) notes.getParent().getParent())
        .setPlaceholderText("Ex.: Interfone com defeito, usar campainha…");
    int materialTitle = form.indexOfChild(materialScroll) - 1;
    java.util.List<android.view.View> locationViews = new ArrayList<>();
    for (int i = 2; i < materialTitle; i++) locationViews.add(form.getChildAt(i));
    android.view.View typeTitle = form.getChildAt(materialTitle);
    android.view.View dateBox = (android.view.View) desired.getParent().getParent();
    android.view.View descriptionBox = (android.view.View) description.getParent().getParent();
    android.view.View notesBox = (android.view.View) notes.getParent().getParent();
    java.util.List<android.view.View> weightBoxes = new ArrayList<>();
    for (TextInputEditText weight : weights.values())
      weightBoxes.add((android.view.View) weight.getParent().getParent());
    form.removeAllViews();
    form.addView(typeTitle);
    form.addView(materialScroll);
    form.addView(descriptionBox);
    for (android.view.View weightBox : weightBoxes) form.addView(weightBox);
    photosArea = PlatformUi.column(requireContext());
    form.addView(photosArea);
    renderPhotos();
    for (android.view.View locationView : locationViews) form.addView(locationView);
    form.addView(dateBox);
    form.addView(notesBox);
    for (int i = 0; i < form.getChildCount(); i++) {
      if (form.getChildAt(i) instanceof com.google.android.material.textfield.TextInputLayout) {
        com.google.android.material.textfield.TextInputLayout field = (com.google.android.material.textfield.TextInputLayout) form.getChildAt(i);
        float radius = PlatformUi.dp(requireContext(), 12);
        field.setBoxCornerRadii(radius, radius, radius, radius);
        field.setBoxBackgroundColor(0xFFF7FAF9);
      }
    }
    com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
    LinearLayout panel = PlatformUi.column(requireContext());
    panel.setBackgroundResource(R.drawable.bg_bottom_sheet);
    android.view.View handle = new android.view.View(requireContext());
    handle.setBackgroundResource(R.drawable.bg_sheet_handle);
    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(PlatformUi.dp(requireContext(), 40), PlatformUi.dp(requireContext(), 4));
    hp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
    hp.topMargin = PlatformUi.dp(requireContext(), 12);
    panel.addView(handle, hp);
    LinearLayout header = PlatformUi.column(requireContext());
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(android.view.Gravity.CENTER_VERTICAL);
    header.setPadding(pad, pad / 2, pad, pad / 2);
    android.widget.TextView title = PlatformUi.text(header, id.isEmpty() ? "Novo pedido" : "Editar solicita\u00e7\u00e3o", 20, true);
    title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    title.setPadding(0, pad / 2, 0, pad / 2);
    android.widget.ImageButton close = new android.widget.ImageButton(requireContext());
    closeButton = close;
    close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
    close.setPadding(PlatformUi.dp(requireContext(), 12), PlatformUi.dp(requireContext(), 12),
        PlatformUi.dp(requireContext(), 12), PlatformUi.dp(requireContext(), 12));
    close.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF6F817D));
    close.setContentDescription("Fechar");
    close.setBackgroundResource(R.drawable.bg_location_option);
    close.setOnClickListener(v -> { if (!submission.running) dismiss(); });
    header.addView(close, new LinearLayout.LayoutParams(PlatformUi.dp(requireContext(), 48), PlatformUi.dp(requireContext(), 48)));
    panel.addView(header);
    panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout footer = PlatformUi.column(requireContext());
    footer.setPadding(pad, 0, pad, pad);
    save = PlatformUi.button(footer, id.isEmpty() ? "Enviar pedido" : "Salvar altera\u00e7\u00f5es", () -> {});
    save.setCornerRadius(PlatformUi.dp(requireContext(), 12));
    save.setMinHeight(PlatformUi.dp(requireContext(), 56));
    panel.addView(footer);
    dialog.setContentView(panel);
    return dialog;
  }

  private void loadAddresses() {
    if (address.getAdapter() != null
        && !addresses.isEmpty()
        && address.getSelectedItemPosition() >= 0)
      selectedAddressId = addresses.get(address.getSelectedItemPosition()).optString("id");
    addresses = repo.addresses();
    if (!original.optString("id").isEmpty() && original.optJSONObject("address") != null) {
      JSONObject preserved = LocalJson.object(original.optJSONObject("address").toString());
      LocalJson.put(preserved, "id", original.optString("address_id"));
      LocalJson.put(preserved, "nickname", "Local deste pedido");
      LocalJson.put(preserved, "source", "request_snapshot");
      addresses.removeIf(a -> original.optString("address_id").equals(a.optString("id")));
      addresses.add(0, preserved);
    }
    JSONObject orderLocation = original.optJSONObject("address");
    if (!original.optString("id").isEmpty() && orderLocation != null
        && "device_location".equals(orderLocation.optString("source"))) {
      JSONObject existing = LocalJson.object(orderLocation.toString());
      LocalJson.put(existing, "id", original.optString("address_id"));
      addresses.add(0, existing);
    }
    List<String> names = new ArrayList<>();
    JSONObject savedCurrent = null;
    if (requireArguments().containsKey("latitude") && original.optString("id").isEmpty()) {
      for (JSONObject saved : addresses) {
        if (Math.abs(saved.optDouble("latitude", Double.NaN) - requireArguments().getDouble("latitude")) < 0.000001
            && Math.abs(saved.optDouble("longitude", Double.NaN) - requireArguments().getDouble("longitude")) < 0.000001) {
          savedCurrent = saved;
          break;
        }
      }
      if (savedCurrent != null
          && (selectedAddressId.isEmpty() || "__current_location".equals(selectedAddressId)))
        selectedAddressId = savedCurrent.optString("id");
      // GPS readings vary between requests. Saved GPS entries must not become
      // additional "current location" choices when their coordinates differ.
      for (java.util.Iterator<JSONObject> it = addresses.iterator(); it.hasNext();) {
        JSONObject saved = it.next();
        if (requireArguments().getBoolean("gps", true)
            && "Localiza\u00e7\u00e3o atual".equalsIgnoreCase(saved.optString("nickname").trim())
            && saved != savedCurrent) {
          if (selectedAddressId.equals(saved.optString("id")))
            selectedAddressId = savedCurrent == null ? "__current_location" : savedCurrent.optString("id");
          it.remove();
        }
      }
    }
    if (requireArguments().containsKey("latitude") && original.optString("id").isEmpty() && savedCurrent == null) {
      JSONObject current = new JSONObject();
      LocalJson.put(current, "id", "__current_location");
      LocalJson.put(current, "nickname", "Localiza\u00e7\u00e3o atual");
      LocalJson.put(current, "latitude", requireArguments().getDouble("latitude"));
      LocalJson.put(current, "longitude", requireArguments().getDouble("longitude"));
      LocalJson.put(current, "source", "device_location");
      addresses.add(0, current);
      if (selectedAddressId.isEmpty()) selectedAddressId = "__current_location";
    }
    for (JSONObject a : addresses)
      names.add("__current_location".equals(a.optString("id")) ? (requireArguments().getBoolean("gps", true) ? "Localiza\u00e7\u00e3o atual" : "Local escolhido no mapa") : a.optString("nickname") + " \u00b7 " + CollectionRepository.addressText(a));
    if (names.isEmpty()) names.add("Cadastre um endereço para continuar");
    address.setAdapter(
        new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, names));
    for (int i = 0; i < addresses.size(); i++)
      if (selectedAddressId.equals(addresses.get(i).optString("id"))) address.setSelection(i);
    renderAddressOptions(names);
  }

  private void renderAddressOptions(List<String> names) {
    addressOptions.removeAllViews();
    if (addresses.isEmpty()) {
      PlatformUi.text(addressOptions, "Cadastre um endereço para continuar", 14, false);
      return;
    }
    for (int i = 0; i < addresses.size(); i++) {
      final int position = i;
      boolean active = i == address.getSelectedItemPosition();
      LinearLayout option = PlatformUi.column(requireContext());
      option.setBackground(PlatformUi.surface(requireContext(), active ? 0xFFEAF8F3 : 0xFFFFFFFF, 12));
      int pad = PlatformUi.dp(requireContext(), 14);
      option.setPadding(pad, pad / 2, pad, pad / 2);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
      params.topMargin = PlatformUi.dp(requireContext(), 8);
      addressOptions.addView(option, params);
      TextView title = PlatformUi.text(option, names.get(i) + (active ? "  ✓" : ""), 14, active);
      title.setTextColor(active ? 0xFF0F7667 : 0xFF102522);
      option.setSelected(active);
      option.setFocusable(true);
      option.setContentDescription(names.get(i) + (active ? ", selecionado" : ""));
      option.setOnClickListener(v -> {
        selectedAddressId = addresses.get(position).optString("id");
        address.setSelection(position);
        renderAddressOptions(names);
      });
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    android.view.View sheet = requireDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
    if (sheet != null) {
      sheet.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.90);
      com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet);
      behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
      behavior.setSkipCollapsed(true);
    }
    save.setOnClickListener(v -> {
          if (preparingPhoto || submission.running) return;
          final android.content.Context photoContext = requireContext().getApplicationContext();
          JSONObject changed = LocalJson.object(original.toString());
          LocalJson.put(changed, "desired_date", PlatformUi.value(desired));
          LocalJson.put(changed, "description", PlatformUi.value(description));
          LocalJson.put(changed, "notes", PlatformUi.value(notes));
          LocalJson.put(changed, "photos", PhotoRefs.copy(photos));
          if (!admin) {
            if (addresses.isEmpty()) {
              android.widget.Toast.makeText(
                      requireContext(), "Cadastre um endereço", android.widget.Toast.LENGTH_LONG)
                  .show();
              return;
            }
            JSONObject chosen = addresses.get(address.getSelectedItemPosition());
            if ("device_location".equals(chosen.optString("source"))) {
              changed.remove("address_id");
              LocalJson.put(changed, "current_location", LocalJson.object(chosen.toString()));
            } else {
              changed.remove("current_location");
              LocalJson.put(changed, "address_id", chosen.optString("id"));
            }
          } else changed.remove("address_id");
          JSONArray items = new JSONArray();
          for (String id : selected.keySet())
            if (selected.get(id).isChecked()) {
              try { DecimalValues.positive(PlatformUi.value(weights.get(id)), 2); }
              catch (IllegalArgumentException error) { weights.get(id).setError(error.getMessage()); return; }
              JSONObject item = new JSONObject();
              LocalJson.put(item, "material_id", id);
              LocalJson.put(item, "weight", PlatformUi.value(weights.get(id)));
              items.put(item);
            }
          LocalJson.put(changed, "items", items);
          if (items.length() == 0) {
            android.widget.Toast.makeText(requireContext(), "Selecione um material e informe o peso", android.widget.Toast.LENGTH_LONG).show();
            return;
          }
          if (changed.has("current_location")) {
            submitCurrentLocation(changed);
            return;
          }
          submission.start(() -> repo.saveOrder(RequestPhotoStore.prepareOrder(photoContext, changed)));
        });
    updateSaveState();
    if (!pendingPhoto.isEmpty() && !preparingPhoto) preparePhoto();
  }

  private void renderPhotos() {
    if (photosArea == null) return;
    if (admin) { photosArea.removeAllViews(); RequestPhotosView.section(photosArea, photos); return; }
    RequestPhotosView.edit(photosArea, photos, position -> {
      if (preparingPhoto || (submission != null && submission.running)) return;
      JSONObject existing = photos.optJSONObject(position);
      if (existing == null) { choosePhoto(position); return; }
      new MaterialAlertDialogBuilder(requireContext()).setTitle("Foto " + (position + 1))
          .setItems(new String[] {"Ver foto", "Trocar foto", "Remover foto"}, (dialog, action) -> {
            if (action == 0) RequestPhotoStore.preview(requireContext(), existing);
            else if (action == 1) choosePhoto(position);
            else { photos.remove(position); renderPhotos(); }
          }).show();
    });
    if (preparingPhoto) PlatformUi.text(photosArea, "Preparando foto…", 13, false);
  }

  private void choosePhoto(int position) {
    photoTarget = Math.min(position, photos.length());
    photoPicker.launch(new String[] {"image/*"});
  }

  private void preparePhoto() {
    if (!isAdded() || pendingPhoto.isEmpty() || preparingPhoto) return;
    preparingPhoto = true;
    updateSaveState();
    renderPhotos();
    final android.content.Context context = requireContext().getApplicationContext();
    final android.net.Uri uri = android.net.Uri.parse(pendingPhoto);
    final int position = photoTarget;
    RequestPhotoStore.IO.execute(() -> {
      JSONObject imported = null;
      String failure = null;
      try { imported = RequestPhotoStore.importImage(context, uri); }
      catch (RuntimeException error) { failure = error.getMessage(); }
      final JSONObject result = imported;
      final String error = failure;
      new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
        if (!isAdded() || getDialog() == null || !getDialog().isShowing()) {
          if (result != null) RequestPhotoStore.file(context, result).delete();
          return;
        }
        preparingPhoto = false;
        pendingPhoto = "";
        try { context.getContentResolver().releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { }
        if (result != null) {
          try { if (position < photos.length()) photos.put(position, result); else if (photos.length() < PhotoRefs.LIMIT) photos.put(result); }
          catch (org.json.JSONException ignored) { }
        } else android.widget.Toast.makeText(context, error == null ? "Não foi possível abrir a foto" : error, android.widget.Toast.LENGTH_LONG).show();
        renderPhotos();
        updateSaveState();
      });
    });
  }

  private void updateSaveState() {
    boolean running = submission != null && submission.running;
    setCancelable(!running);
    if (closeButton != null) { closeButton.setEnabled(!running); closeButton.setAlpha(running ? .4f : 1f); }
    if (save == null || original == null) return;
    save.setEnabled(!running && !preparingPhoto);
    save.setText(running ? "Enviando pedido…" : original.optString("id").isEmpty() ? "Enviar pedido" : "Salvar alterações");
  }

  private void submitCurrentLocation(JSONObject changed) {
    JSONObject draft = changed.optJSONObject("current_location");
    double lat = draft.optDouble("latitude", Double.NaN), lng = draft.optDouble("longitude", Double.NaN);
    android.content.Context context = requireContext().getApplicationContext();
    LocalJson.put(draft, "nickname", requireArguments().getBoolean("gps", true) ? "Localiza\u00e7\u00e3o atual" : "Local escolhido");
    LocalJson.put(draft, "latitude", lat);
    LocalJson.put(draft, "longitude", lng);
    submission.start(() -> {
      if (BluetoothPlatform.active()) {
        if (lat >= -23.575 && lat <= -23.515 && lng >= -46.610 && lng <= -46.515) {
          LocalJson.put(draft, "city", "São Paulo"); LocalJson.put(draft, "state", "SP");
        }
        repo.saveOrder(RequestPhotoStore.prepareOrder(context, changed));
        return;
      }
      try {
        java.util.List<android.location.Address> found = new android.location.Geocoder(context, new Locale("pt", "BR")).getFromLocation(lat, lng, 1);
        if (found != null && !found.isEmpty()) {
          android.location.Address a = found.get(0);
          LocalJson.put(draft, "street", a.getThoroughfare() == null ? "" : a.getThoroughfare());
          LocalJson.put(draft, "number", a.getSubThoroughfare() == null ? "" : a.getSubThoroughfare());
          LocalJson.put(draft, "district", a.getSubLocality() == null ? "" : a.getSubLocality());
          LocalJson.put(draft, "city", a.getSubAdminArea() == null ? "" : a.getSubAdminArea());
          LocalJson.put(draft, "zip", a.getPostalCode() == null ? "" : a.getPostalCode());
          String region = a.getAdminArea() == null ? "" : a.getAdminArea();
          String[] regions = {"Acre", "Alagoas", "Amapa", "Amazonas", "Bahia", "Ceara", "Distrito Federal", "Espirito Santo", "Goias", "Maranhao", "Mato Grosso", "Mato Grosso do Sul", "Minas Gerais", "Para", "Paraiba", "Parana", "Pernambuco", "Piaui", "Rio de Janeiro", "Rio Grande do Norte", "Rio Grande do Sul", "Rondonia", "Roraima", "Santa Catarina", "Sao Paulo", "Sergipe", "Tocantins"};
          String[] codes = {"AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"};
          String plain = java.text.Normalizer.normalize(region, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
          for (int i = 0; i < regions.length; i++)
            if (plain.equalsIgnoreCase(regions[i]) || plain.equalsIgnoreCase(codes[i])) LocalJson.put(draft, "state", codes[i]);
        }
      } catch (java.io.IOException | IllegalArgumentException ignored) { }
      repo.saveOrder(RequestPhotoStore.prepareOrder(context, changed));
    });
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle out) {
    out.putString("photos", photos.toString());
    out.putInt("photo_target", photoTarget);
    out.putString("pending_photo", pendingPhoto);
    out.putString("original", original.toString());
    if (address != null && !addresses.isEmpty())
      out.putString(
          "selected_address", addresses.get(address.getSelectedItemPosition()).optString("id"));
    out.putString("date", PlatformUi.value(desired));
    out.putString("description", PlatformUi.value(description));
    out.putString("notes", PlatformUi.value(notes));
    for (String id : selected.keySet()) {
      out.putBoolean("checked_" + id, selected.get(id).isChecked());
      out.putString("weight_" + id, PlatformUi.value(weights.get(id)));
    }
    super.onSaveInstanceState(out);
  }
}
