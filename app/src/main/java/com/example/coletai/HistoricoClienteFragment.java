package com.example.coletai;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class HistoricoClienteFragment extends Fragment {
    private final BluetoothColetai.SyncListener atualizacao = () -> {
        if (getView() != null) { carregarPedidos(); carregarLista(); }
    };

    @Override public void onStart() {
        super.onStart();
        BluetoothColetai.getInstance(requireContext()).addSyncListener(atualizacao);
    }

    @Override public void onStop() {
        BluetoothColetai.getInstance(requireContext()).removeSyncListener(atualizacao);
        super.onStop();
    }


    private TextView btnTodos;
    private TextView btnConcluidos;
    private TextView btnCancelados;
    private TextView txtListaVazia;

    private LinearLayout listaHistorico;

    private final List<PedidoHistorico> pedidos =
            new ArrayList<>();

    private String filtroAtual = "todos";

    public HistoricoClienteFragment() {
        super(R.layout.fragment_historico_cliente);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        /*
         * O XML é uma cópia do antigo activity_historico_cliente.xml.
         * A barra inferior antiga fica escondida porque agora existe
         * somente uma barra, controlada pela Activity principal.
         */
        View menuAntigo =
                view.findViewById(R.id.navegacaoInferior);

        if (menuAntigo != null) {
            menuAntigo.setVisibility(View.GONE);
        }

        iniciarComponentes(view);
        carregarPedidos();
        configurarEventos();
        selecionarFiltro("todos");
    }

    private void iniciarComponentes(
            @NonNull View view
    ) {

        btnTodos =
                view.findViewById(R.id.btnTodos);

        btnConcluidos =
                view.findViewById(R.id.btnConcluidos);

        btnCancelados =
                view.findViewById(R.id.btnCancelados);

        txtListaVazia =
                view.findViewById(R.id.txtListaVazia);

        listaHistorico =
                view.findViewById(R.id.listaHistorico);
    }

    private void configurarEventos() {

        btnTodos.setOnClickListener(
                view -> selecionarFiltro("todos")
        );

        btnConcluidos.setOnClickListener(
                view -> selecionarFiltro("concluido")
        );

        btnCancelados.setOnClickListener(
                view -> selecionarFiltro("cancelado")
        );
    }

    private void carregarPedidos() {
        pedidos.clear();
        PedidoRepository repository = new PedidoRepository(requireContext()
                .getSharedPreferences("coletai", android.content.Context.MODE_PRIVATE));
        for (org.json.JSONObject item : repository.historico()) {
            pedidos.add(new PedidoHistorico(item.optString("pedido_tipo"), item.optString("pedido_descricao"),
                    item.optString("proposta_coletor_nome"), item.optString("proposta_preco"),
                    new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date(item.optLong("data"))), item.optString("status")));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        carregarPedidos();
        carregarLista();
    }

    private void selecionarFiltro(
            String filtro
    ) {

        filtroAtual = filtro;

        atualizarBotaoFiltro(
                btnTodos,
                filtro.equals("todos")
        );

        atualizarBotaoFiltro(
                btnConcluidos,
                filtro.equals("concluido")
        );

        atualizarBotaoFiltro(
                btnCancelados,
                filtro.equals("cancelado")
        );

        carregarLista();
    }

    private void atualizarBotaoFiltro(
            TextView botao,
            boolean selecionado
    ) {

        int fundo =
                selecionado
                        ? Color.parseColor("#0F7667")
                        : Color.WHITE;

        int borda =
                selecionado
                        ? Color.parseColor("#0F7667")
                        : Color.parseColor("#DCE7E3");

        int texto =
                selecionado
                        ? Color.WHITE
                        : Color.parseColor("#6F817D");

        botao.setBackground(
                criarFundo(
                        fundo,
                        borda,
                        50
                )
        );

        botao.setTextColor(texto);
    }

    private void carregarLista() {

        listaHistorico.removeAllViews();

        int quantidadeVisivel = 0;

        for (PedidoHistorico pedido : pedidos) {

            if (!filtroAtual.equals("todos")
                    && !pedido.status.equals(filtroAtual)) {
                continue;
            }

            adicionarPedidoNaLista(pedido);
            quantidadeVisivel++;
        }

        txtListaVazia.setVisibility(
                quantidadeVisivel == 0
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void adicionarPedidoNaLista(
            PedidoHistorico pedido
    ) {

        View item =
                LayoutInflater.from(
                        requireContext()
                ).inflate(
                        R.layout.item_historico_pedido,
                        listaHistorico,
                        false
                );

        TextView txtResiduo =
                item.findViewById(R.id.txtResiduo);

        TextView txtStatus =
                item.findViewById(R.id.txtStatus);

        TextView txtDescricao =
                item.findViewById(R.id.txtDescricao);

        TextView txtColetor =
                item.findViewById(R.id.txtColetor);

        TextView txtPreco =
                item.findViewById(R.id.txtPreco);

        TextView txtData =
                item.findViewById(R.id.txtData);

        txtResiduo.setText(pedido.tipoResiduo);
        txtDescricao.setText(pedido.descricao);
        txtColetor.setText(pedido.coletor);
        txtPreco.setText(pedido.preco);
        txtData.setText(pedido.data);

        configurarBadgeResiduo(
                txtResiduo,
                pedido.tipoResiduo
        );

        configurarBadgeStatus(
                txtStatus,
                pedido.status
        );

        item.setOnClickListener(view ->
                Toast.makeText(
                        requireContext(),
                        "Detalhes: " + pedido.tipoResiduo,
                        Toast.LENGTH_SHORT
                ).show()
        );

        listaHistorico.addView(item);
    }

    private void configurarBadgeStatus(
            TextView badge,
            String status
    ) {

        boolean concluido =
                status.equals("concluido");

        badge.setText(
                concluido
                        ? "Concluído"
                        : "Cancelado"
        );

        badge.setTextColor(
                Color.parseColor(
                        concluido
                                ? "#0F7667"
                                : "#DC2626"
                )
        );

        badge.setBackground(
                criarFundo(
                        Color.parseColor(
                                concluido
                                        ? "#EBF8F3"
                                        : "#FEE2E2"
                        ),
                        Color.TRANSPARENT,
                        50
                )
        );
    }

    private void configurarBadgeResiduo(
            TextView badge,
            String tipo
    ) {

        String corFundo = "#F3F4F6";
        String corTexto = "#6B7280";

        switch (tipo) {

            case "Pilhas":
            case "Óleo":
                corFundo = "#FEF3C7";
                corTexto = "#92400E";
                break;

            case "Baterias":
                corFundo = "#FEE2E2";
                corTexto = "#DC2626";
                break;

            case "Eletrônicos":
                corFundo = "#EEF2FF";
                corTexto = "#4F46E5";
                break;

            case "Medicamentos":
                corFundo = "#E0F2FE";
                corTexto = "#0891B2";
                break;

            case "Agulhas":
                corFundo = "#FCE7F3";
                corTexto = "#BE185D";
                break;
        }

        badge.setTextColor(
                Color.parseColor(corTexto)
        );

        badge.setBackground(
                criarFundo(
                        Color.parseColor(corFundo),
                        Color.TRANSPARENT,
                        50
                )
        );
    }

    private GradientDrawable criarFundo(
            int cor,
            int corBorda,
            int raioDp
    ) {

        GradientDrawable fundo =
                new GradientDrawable();

        fundo.setColor(cor);
        fundo.setCornerRadius(dp(raioDp));

        if (corBorda != Color.TRANSPARENT) {
            fundo.setStroke(
                    dp(1),
                    corBorda
            );
        }

        return fundo;
    }

    private int dp(int valor) {

        return Math.round(
                valor
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private static class PedidoHistorico {

        final String tipoResiduo;
        final String descricao;
        final String coletor;
        final String preco;
        final String data;
        final String status;

        PedidoHistorico(
                String tipoResiduo,
                String descricao,
                String coletor,
                String preco,
                String data,
                String status
        ) {

            this.tipoResiduo = tipoResiduo;
            this.descricao = descricao;
            this.coletor = coletor;
            this.preco = preco;
            this.data = data;
            this.status = status;
        }
    }
}
