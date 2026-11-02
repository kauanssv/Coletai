package com.example.coletai;

import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;

/** Entrada legada mantida para compatibilidade com a navegação existente. */
public class historico_cliente extends ContaActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.historyFragmentContainer);
        setContentView(container);
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(container.getId(), new HistoricoClienteFragment()).commit();
    }
}
