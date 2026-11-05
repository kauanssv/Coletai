package com.example.coletai;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

public class AddressEditorDialog extends DialogFragment {
  private final Map<String, TextInputEditText> fields = new LinkedHashMap<>();
  private JSONObject original;
  private android.widget.TextView locationStatus;
  private static final java.util.concurrent.ExecutorService LOOKUPS = java.util.concurrent.Executors.newFixedThreadPool(2);
  private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
  private int lookupGeneration;
  private boolean filling, lookingUp, saving, resumeReverse;
  private Runnable postalSearch;
  private final androidx.activity.result.ActivityResultLauncher<android.content.Intent>
      mapLauncher =
          registerForActivityResult(
              new androidx.activity.result.contract.ActivityResultContracts
                  .StartActivityForResult(),
              result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK
                    && result.getData() != null) {
                  LocalJson.put(
                      original, "latitude", result.getData().getDoubleExtra("latitude", 0));
                  LocalJson.put(
                      original, "longitude", result.getData().getDoubleExtra("longitude", 0));
                  filling = true;
                  try {
                    for (String key : new String[]{"zip", "street", "number", "district"}) fields.get(key).setText("");
                  } finally { filling = false; }
                  lookupMap();
                }
              });

  public static AddressEditorDialog create(JSONObject address) {
    AddressEditorDialog d = new AddressEditorDialog();
    Bundle b = new Bundle();
    b.putString("address", address.toString());
    d.setArguments(b);
    return d;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle state) {
    fields.clear();
    lookingUp = false;
    original = LocalJson.object(state == null ? requireArguments().getString("address", "{}") : state.getString("original", "{}"));
    resumeReverse = state != null && state.getBoolean("reverse_pending");
    ScrollView scroll = new ScrollView(requireContext());
    LinearLayout form = PlatformUi.column(requireContext());
    int pad = PlatformUi.dp(requireContext(), 20);
    form.setPadding(pad, 0, pad, pad);
    scroll.addView(form);
    String[][] names = {
      {"nickname", "Apelido (casa, trabalho…)"},
      {"zip", "CEP"},
      {"street", "Rua / avenida"},
      {"number", "Número"},
      {"complement", "Complemento (opcional)"},
      {"district", "Bairro"}
    };
    PlatformUi.button(
        form, "Marcar localização no mapa (opcional)", () -> mapLauncher.launch(mapIntent()));
    locationStatus =
        PlatformUi.text(
            form,
            original.has("latitude")
                ? "Localização marcada no mapa."
                : "Marcar a posição ajuda o coletor a chegar até você.",
            13,
            false);
    for (String[] f : names)
      fields.put(
          f[0],
          PlatformUi.field(
              form,
              f[1],
              state == null ? original.optString(f[0]) : state.getString(f[0], ""),
              "zip".equals(f[0]) ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT));
    fields.get("zip").setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(9)});
    fields.get("zip").addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (filling) return;
        lookupGeneration++;
        lookingUp = false;
        resumeReverse = false;
        if (postalSearch != null) handler.removeCallbacks(postalSearch);
        original.remove("city"); original.remove("state");
        original.remove("latitude"); original.remove("longitude");
        locationStatus.setText("Informe o CEP para buscar o endereço automaticamente.");
        updateSave();
        if (zip().length() == 8) {
          postalSearch = () -> lookupPostal();
          handler.postDelayed(postalSearch, 450);
        }
      }
      public void afterTextChanged(android.text.Editable s) { }
    });
    com.google.android.material.textfield.TextInputLayout postalBox =
        (com.google.android.material.textfield.TextInputLayout) fields.get("zip").getParent().getParent();
    postalBox.setHelperText("Rua e bairro preenchidos automaticamente");
    postalBox.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM);
    postalBox.setEndIconDrawable(android.R.drawable.ic_popup_sync);
    postalBox.setEndIconContentDescription("Buscar endereço pelo CEP");
    postalBox.setEndIconOnClickListener(v -> lookupPostal());
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Endereço de coleta")
        .setView(scroll)
        .setNegativeButton("Voltar", null)
        .setPositiveButton("Salvar", null)
        .create();
  }

  private String zip() { return PlatformUi.value(fields.get("zip")).replaceAll("\\D", ""); }

  private void lookupPostal() {
    if (saving) return;
    String postal = zip();
    if (!postal.matches("[0-9]{8}")) { fields.get("zip").setError("Informe um CEP com 8 dígitos"); return; }
    if (postalSearch != null) handler.removeCallbacks(postalSearch);
    startLookup(false, () -> AddressLookup.postal(postal));
  }

  private void lookupMap() {
    if (postalSearch != null) handler.removeCallbacks(postalSearch);
    final android.content.Context context = requireContext().getApplicationContext();
    double lat = original.optDouble("latitude"), lng = original.optDouble("longitude");
    original.remove("city"); original.remove("state");
    startLookup(true, () -> AddressLookup.reverse(context, lat, lng));
  }

  private interface Lookup { JSONObject run(); }

  private void startLookup(boolean fromMap, Lookup lookup) {
    int generation = ++lookupGeneration;
    lookingUp = true;
    resumeReverse = fromMap;
    Map<String, String> baseline = new LinkedHashMap<>();
    for (Map.Entry<String, TextInputEditText> f : fields.entrySet()) baseline.put(f.getKey(), PlatformUi.value(f.getValue()));
    locationStatus.setText(fromMap ? "Buscando endereço do local marcado…" : "Buscando endereço pelo CEP…");
    updateSave();
    LOOKUPS.execute(() -> {
      JSONObject found = null;
      String error = null;
      try { found = lookup.run(); } catch (RuntimeException e) { error = e.getMessage(); }
      JSONObject result = found;
      String failure = error;
      handler.post(() -> {
        if (generation != lookupGeneration || !isAdded() || getDialog() == null) return;
        lookingUp = false;
        resumeReverse = false;
        if (result == null) locationStatus.setText(failure);
        else {
          filling = true;
          try {
            for (String key : new String[]{"street", "district", "zip", "number"}) {
              if (!result.has(key)) continue;
              TextInputEditText field = fields.get(key);
              // Preserve corrections typed while the suggestion was being fetched.
              if (PlatformUi.value(field).equals(baseline.get(key))) field.setText(result.optString(key));
            }
            LocalJson.put(original, "city", result.optString("city"));
            LocalJson.put(original, "state", result.optString("state"));
            fields.get("zip").setError(null);
          } finally { filling = false; }
          boolean complete = !result.optString("city").isEmpty() && !result.optString("state").isEmpty() && zip().length() == 8;
          locationStatus.setText(complete ? "Endereço preenchido. Confira o número e complete o que faltar."
              : "Alguns dados não foram encontrados. Informe o CEP para completar o endereço.");
        }
        updateSave();
      });
    });
  }

  private void updateSave() {
    if (getDialog() instanceof AlertDialog) {
      android.widget.Button save = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);
      if (save != null) save.setEnabled(!lookingUp && !saving);
    }
  }

  private android.content.Intent mapIntent() {
    JSONObject draft = LocalJson.object(original.toString());
    for (Map.Entry<String, TextInputEditText> field : fields.entrySet())
      LocalJson.put(draft, field.getKey(), PlatformUi.value(field.getValue()));
    android.content.Intent intent =
        new android.content.Intent(requireContext(), LocationActivity.class).putExtra("pick", true);
    if (!draft.optString("street").isEmpty() || !draft.optString("city").isEmpty())
      intent.putExtra("address", CollectionRepository.addressText(draft));
    if (original.has("latitude") && original.has("longitude"))
      intent
          .putExtra("latitude", original.optDouble("latitude"))
          .putExtra("longitude", original.optDouble("longitude"));
    return intent;
  }

  @Override
  public void onStart() {
    super.onStart();
    AlertDialog d = (AlertDialog) requireDialog();
    d.getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(v -> {
          JSONObject changed = LocalJson.object(original.toString());
          for (Map.Entry<String, TextInputEditText> f : fields.entrySet())
            LocalJson.put(changed, f.getKey(), PlatformUi.value(f.getValue()));
          LocalJson.put(changed, "zip", zip());
          if (original.optString("city").isEmpty() || original.optString("state").isEmpty()) {
            locationStatus.setText("Busque um CEP válido ou marque o endereço no mapa antes de salvar.");
            lookupPostal();
            return;
          }
          saving = true;
          updateSave();
          final CollectionRepository repo = new CollectionRepository(PlatformStore.open(requireContext()));
          PlatformTasks.mutate(
              requireActivity(),
              () ->
                  repo.saveAddress(changed),
              () -> {
                getParentFragmentManager().setFragmentResult("platform_updated", new Bundle());
                dismiss();
              },
              () -> { saving = false; updateSave(); });
        });
    updateSave();
    if (!lookingUp) {
      if (resumeReverse && original.has("latitude")) lookupMap();
      else if (zip().length() == 8 && (original.optString("city").isEmpty() || original.optString("state").isEmpty())) lookupPostal();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle out) {
    out.putString("original", original.toString());
    out.putBoolean("reverse_pending", resumeReverse);
    for (Map.Entry<String, TextInputEditText> f : fields.entrySet())
      out.putString(f.getKey(), PlatformUi.value(f.getValue()));
    super.onSaveInstanceState(out);
  }

  @Override public void onDestroyView() {
    lookupGeneration++;
    if (postalSearch != null) handler.removeCallbacks(postalSearch);
    super.onDestroyView();
  }
}
