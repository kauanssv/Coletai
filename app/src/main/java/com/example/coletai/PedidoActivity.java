package com.example.coletai;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** Impede ações de uma tela cujo pedido já foi cancelado ou substituído. */
public abstract class PedidoActivity extends ContaActivity {
    private SharedPreferences estadoPedido;
    private String pedidoExibido;
    private final SharedPreferences.OnSharedPreferenceChangeListener observer = (prefs, key) -> {
        if ("pedido_status".equals(key) || "pedido_id".equals(key)) verificarPedido();
    };

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        estadoPedido = getSharedPreferences("coletai", MODE_PRIVATE);
        pedidoExibido = state == null ? estadoPedido.getString("pedido_id", "")
                : state.getString("pedido_exibido", "");
        BluetoothColetai.getInstance(this);
    }

    @Override protected void onStart() {
        super.onStart();
        estadoPedido.registerOnSharedPreferenceChangeListener(observer);
        verificarPedido();
    }

    @Override protected void onStop() {
        estadoPedido.unregisterOnSharedPreferenceChangeListener(observer);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("pedido_exibido", pedidoExibido);
        super.onSaveInstanceState(out);
    }

    protected abstract boolean aceitaStatus(String status);

    protected final boolean verificarPedido() {
        boolean valido = !pedidoExibido.isEmpty()
                && pedidoExibido.equals(estadoPedido.getString("pedido_id", ""))
                && aceitaStatus(estadoPedido.getString("pedido_status", ""));
        if (!valido) finish();
        return valido;
    }
}
