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
                  if (locationStatus != null)
                    locationStatus.setText(
                        "Localização marcada. Confira os dados do endereço e salve.");
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
    original = LocalJson.object(requireArguments().getString("address", "{}"));
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
      {"district", "Bairro"},
      {"city", "Cidade"},
      {"state", "UF"}
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
              InputType.TYPE_CLASS_TEXT));
    return new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Endereço de coleta")
        .setView(scroll)
        .setNegativeButton("Voltar", null)
        .setPositiveButton("Salvar", null)
        .create();
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
          d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
          PlatformTasks.mutate(
              requireActivity(),
              () ->
                  new CollectionRepository(PlatformStore.open(requireContext()))
                      .saveAddress(changed),
              () -> {
                getParentFragmentManager().setFragmentResult("platform_updated", new Bundle());
                dismiss();
              },
              () -> d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
        });
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle out) {
    requireArguments().putString("address", original.toString());
    for (Map.Entry<String, TextInputEditText> f : fields.entrySet())
      out.putString(f.getKey(), PlatformUi.value(f.getValue()));
    super.onSaveInstanceState(out);
  }
}
