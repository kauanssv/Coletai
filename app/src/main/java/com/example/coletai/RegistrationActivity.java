package com.example.coletai;

import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;

public class RegistrationActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    ScrollView scroll = new ScrollView(this);
    LinearLayout form = PlatformUi.column(this);
    form.setPadding(
        PlatformUi.dp(this, 24),
        PlatformUi.dp(this, 32),
        PlatformUi.dp(this, 24),
        PlatformUi.dp(this, 24));
    scroll.addView(form);
    setContentView(scroll);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
        scroll,
        (v, i) -> {
          androidx.core.graphics.Insets b =
              i.getInsets(
                  androidx.core.view.WindowInsetsCompat.Type.systemBars()
                      | androidx.core.view.WindowInsetsCompat.Type.ime());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return i;
        });
    PlatformUi.text(form, "Crie sua conta", 26, true);
    PlatformUi.text(form, "Escolha como deseja participar da coleta de recicláveis.", 14, false);
    Spinner role = new Spinner(this);
    role.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"Cidadão", "Coletor independente", "Cooperativa"}));
    form.addView(role);
    TextInputEditText name =
        PlatformUi.field(
            form,
            "Nome / responsável",
            "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    TextInputEditText email =
        PlatformUi.field(
            form,
            "E-mail",
            "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    TextInputEditText phone = PlatformUi.field(form, "Telefone", "", InputType.TYPE_CLASS_PHONE);
    TextInputEditText company =
        PlatformUi.field(form, "Nome da cooperativa", "", InputType.TYPE_CLASS_TEXT);
    TextInputEditText cnpj = PlatformUi.field(form, "CNPJ", "", InputType.TYPE_CLASS_NUMBER);
    TextInputEditText password =
        PlatformUi.field(
            form,
            "Senha (mínimo 8 caracteres)",
            "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    TextInputEditText repeat =
        PlatformUi.field(
            form,
            "Confirme sua senha",
            "",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    Runnable visibility =
        () -> {
          int show =
              role.getSelectedItemPosition() == 2
                  ? android.view.View.VISIBLE
                  : android.view.View.GONE;
          ((android.view.View) company.getParent().getParent()).setVisibility(show);
          ((android.view.View) cnpj.getParent().getParent()).setVisibility(show);
        };
    role.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(
              android.widget.AdapterView<?> p, android.view.View v, int n, long id) {
            visibility.run();
          }
        });
    visibility.run();
    PlatformUi.button(
        form,
        "Cadastrar",
        () -> {
          if (!PlatformUi.value(password).equals(PlatformUi.value(repeat))) {
            repeat.setError("As senhas não coincidem");
            return;
          }
          JSONObject p = new JSONObject();
          LocalJson.put(p, "name", PlatformUi.value(name));
          LocalJson.put(p, "email", PlatformUi.value(email));
          LocalJson.put(p, "phone", PlatformUi.value(phone));
          LocalJson.put(p, "company", PlatformUi.value(company));
          LocalJson.put(p, "document", PlatformUi.value(cnpj));
          LocalJson.put(
              p,
              "role",
              new String[] {"cliente", "ambulante", "empresa"}[role.getSelectedItemPosition()]);
          PlatformTasks.run(
              this,
              () ->
                  new ProfileRepository(PlatformStore.open(this))
                      .register(p, PlatformUi.value(password)),
              () -> {
                android.widget.Toast.makeText(
                        this,
                        "Cadastro concluído. Entre com seu e-mail e senha.",
                        android.widget.Toast.LENGTH_LONG)
                    .show();
                finish();
              });
        });
    PlatformUi.button(form, "Voltar para o login", this::finish);
  }
}
