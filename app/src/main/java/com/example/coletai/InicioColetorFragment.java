package com.example.coletai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;

/** Fluxo comum a ambulantes e empresas. Exibe somente solicitações recebidas. */
public class InicioColetorFragment extends Fragment {
    private MaterialButton disponibilidade;
    private TextView pedido;
    private LinearLayout lista;
    private boolean disponivel;
    private SharedPreferences prefs;
    private BluetoothColetai bluetooth;
    private final BluetoothColetai.SyncListener atualizacao = this::atualizar;

    public InicioColetorFragment() { super(R.layout.fragment_inicio_ambulante); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        prefs = requireContext().getSharedPreferences("coletai", Context.MODE_PRIVATE);
        bluetooth = BluetoothColetai.getInstance(requireContext());
        disponibilidade = view.findViewById(R.id.btnDisponibilidade);
        lista = view.findViewById(R.id.layoutPedidos);
        pedido = view.findViewById(R.id.txtPedidoRecebido);
        disponivel = prefs.getBoolean("disponivel_" + LocalJson.account(prefs), true);
        disponibilidade.setOnClickListener(v -> {
            disponivel = !disponivel;
            prefs.edit().putBoolean("disponivel_" + LocalJson.account(prefs), disponivel).apply();
            atualizar();
        });
        pedido.setOnClickListener(v -> abrirPedido());
        atualizar();
    }

    @Override public void onStart() {
        super.onStart();
        bluetooth.addSyncListener(atualizacao);
        atualizar();
    }

    @Override public void onStop() {
        bluetooth.removeSyncListener(atualizacao);
        super.onStop();
    }

    private void atualizar() {
        if (getView() == null) return;
        disponibilidade.setText(disponivel ? R.string.disponivel : R.string.indisponivel);
        disponibilidade.setBackgroundTintList(ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(),
                        disponivel ? R.color.brand_700 : R.color.ink_500)));
        String status = prefs.getString("pedido_status", "");
        boolean ativo = "active".equals(status);
        lista.setVisibility(disponivel || ativo ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.layoutIndisponivel).setVisibility(disponivel || ativo ? View.GONE : View.VISIBLE);
        if (ativo) pedido.setText(getString(R.string.abrir_coleta, prefs.getString("pedido_tipo", "")));
        else if ("proposal".equals(status)) pedido.setText(R.string.proposta_aguardando);
        else if ("waiting".equals(status)) pedido.setText(getString(R.string.pedido_recebido,
                prefs.getString("pedido_tipo", ""), prefs.getString("pedido_descricao", "")));
        else pedido.setText(R.string.sem_pedidos);
        pedido.setEnabled(ativo || "waiting".equals(status));
    }

    private void abrirPedido() {
        String status = prefs.getString("pedido_status", "");
        if ("active".equals(status)) startActivity(new Intent(requireContext(), coleta_ativa_ambulante.class));
        else if (disponivel && "waiting".equals(status)) {
            startActivity(new Intent(requireContext(), detalhes_pedido_ambulante.class));
        }
    }
}
