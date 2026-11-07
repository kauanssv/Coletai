package com.example.coletai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** Encerra telas de contas bloqueadas, excluídas ou substituídas por outra sessão. */
public abstract class ContaActivity extends AppCompatActivity {
    private String account;
    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = PlatformStore.open(this);
        account = state == null ? LocalJson.account(prefs) : state.getString("account_screen", "");
    }
    @Override protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PlatformStore.open(this);
        if (!account.equals(LocalJson.account(prefs)) || !new ProfileRepository(prefs).available(account)
                || "admin".equals(prefs.getString("usuario_tipo_atual", ""))) {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("account_screen", account); super.onSaveInstanceState(out);
    }
}
