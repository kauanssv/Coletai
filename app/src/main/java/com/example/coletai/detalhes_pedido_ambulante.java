package com.example.coletai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.card.MaterialCardView;

public class detalhes_pedido_ambulante extends PedidoActivity {
    @Override protected boolean aceitaStatus(String status) { return "waiting".equals(status) || "proposal".equals(status); }

    private TextView btnVoltar;

    private TextView txtTagTipo;
    private TextView txtEmojiTipo;
    private TextView txtTipoPedido;
    private TextView txtLocalizacaoCabecalho;

    private TextView txtDescricaoPedido;
    private TextView txtPesoPedido;
    private TextView txtRegiaoPedido;
    private TextView txtLocalizacaoPedido;

    private MaterialCardView cardObservacao;
    private TextView txtObservacaoPedido;

    private Button btnRecusar;
    private Button btnEnviarProposta;

    private SharedPreferences preferencias;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        EdgeToEdge.enable(
                this
        );

        setContentView(
                R.layout.activity_detalhes_pedido_ambulante
        );

        configurarBarrasDoSistema();

        preferencias =
                getSharedPreferences(
                        "coletai",
                        Context.MODE_PRIVATE
                );

        iniciarComponentes();

        carregarPedido();

        configurarEventos();
    }

    // =====================================================
    // BARRAS DO SISTEMA
    // =====================================================

    private void configurarBarrasDoSistema() {

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.setAppearanceLightStatusBars(
                true
        );

        controller.setAppearanceLightNavigationBars(
                true
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(
                        R.id.main
                ),
                (view, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    view.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );
    }

    // =====================================================
    // COMPONENTES
    // =====================================================

    private void iniciarComponentes() {

        btnVoltar =
                findViewById(
                        R.id.btnVoltarDetalhes
                );

        txtTagTipo =
                findViewById(
                        R.id.txtTagTipo
                );

        txtEmojiTipo =
                findViewById(
                        R.id.txtEmojiTipo
                );

        txtTipoPedido =
                findViewById(
                        R.id.txtTipoPedido
                );

        txtLocalizacaoCabecalho =
                findViewById(
                        R.id.txtLocalizacaoCabecalho
                );

        txtDescricaoPedido =
                findViewById(
                        R.id.txtDescricaoPedido
                );

        txtPesoPedido =
                findViewById(
                        R.id.txtPesoPedido
                );

        txtRegiaoPedido =
                findViewById(
                        R.id.txtRegiaoPedido
                );

        txtLocalizacaoPedido =
                findViewById(
                        R.id.txtLocalizacaoPedido
                );

        cardObservacao =
                findViewById(
                        R.id.cardObservacao
                );

        txtObservacaoPedido =
                findViewById(
                        R.id.txtObservacaoPedido
                );

        btnRecusar =
                findViewById(
                        R.id.btnRecusar
                );

        btnEnviarProposta =
                findViewById(
                        R.id.btnEnviarProposta
                );
    }

    // =====================================================
    // CARREGAR PEDIDO
    // =====================================================

    private void carregarPedido() {

        String tipo =
                preferencias.getString(
                        "pedido_tipo",
                        "Eletrônicos"
                );

        String emoji =
                preferencias.getString(
                        "pedido_emoji",
                        obterEmoji(tipo)
                );

        String descricao =
                preferencias.getString(
                        "pedido_descricao",
                        "TV de tubo 29\" e um rádio antigo. Estão na garagem."
                );

        String peso =
                preferencias.getString(
                        "pedido_peso",
                        "14 kg"
                );

        String regiao =
                preferencias.getString(
                        "pedido_regiao",
                        "Consolação"
                );

        String cidade =
                preferencias.getString(
                        "pedido_cidade",
                        "São Paulo"
                );

        String observacao =
                preferencias.getString(
                        "pedido_observacao",
                        ""
                );

        if (
                tipo == null
                        ||
                        tipo.trim().isEmpty()
        ) {

            tipo =
                    "Pedido";
        }

        if (
                emoji == null
                        ||
                        emoji.trim().isEmpty()
        ) {

            emoji =
                    obterEmoji(
                            tipo
                    );
        }

        if (
                descricao == null
                        ||
                        descricao.trim().isEmpty()
        ) {

            descricao =
                    "Sem descrição.";
        }

        if (
                peso == null
                        ||
                        peso.trim().isEmpty()
        ) {

            peso =
                    "Não informado";
        }

        if (
                regiao == null
                        ||
                        regiao.trim().isEmpty()
        ) {

            regiao =
                    "Região informada";
        }

        if (
                cidade == null
                        ||
                        cidade.trim().isEmpty()
        ) {

            cidade =
                    "São Paulo";
        }

        // =================================================
        // CABEÇALHO
        // =================================================

        txtTagTipo.setText(
                emoji + " " + tipo
        );

        txtEmojiTipo.setText(
                emoji
        );

        txtTipoPedido.setText(
                tipo
        );

        txtLocalizacaoCabecalho.setText(preferencias.getString("pedido_endereco_completo", ""));

        // =================================================
        // PEDIDO
        // =================================================

        txtDescricaoPedido.setText(
                descricao
        );

        txtPesoPedido.setText(
                peso
        );

        txtRegiaoPedido.setText(preferencias.getString("pedido_endereco_completo", ""));

        txtLocalizacaoPedido.setText(preferencias.getString("pedido_endereco_completo", ""));

        // =================================================
        // OBSERVAÇÃO
        // =================================================

        if (
                observacao == null
                        ||
                        observacao.trim().isEmpty()
        ) {

            cardObservacao.setVisibility(
                    View.GONE
            );

        } else {

            cardObservacao.setVisibility(
                    View.VISIBLE
            );

            txtObservacaoPedido.setText(
                    observacao
            );
        }
    }

    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnVoltar.setOnClickListener(
                view -> finish()
        );

        btnRecusar.setOnClickListener(
                view -> finish()
        );

        btnEnviarProposta.setOnClickListener(
                view -> {

                    Intent intent =
                            new Intent(
                                    detalhes_pedido_ambulante.this,
                                    enviar_proposta_ambulante.class
                            );

                    startActivity(
                            intent
                    );
                }
        );
    }

    // =====================================================
    // EMOJI
    // =====================================================

    private String obterEmoji(
            String tipo
    ) {

        if (tipo == null) {

            return "📦";
        }

        switch (tipo) {

            case "Pilhas":

                return "🔋";

            case "Baterias":

                return "⚡";

            case "Eletrônicos":

                return "💻";

            case "Medicamentos":

                return "💊";

            case "Agulhas":

                return "💉";

            case "Óleo":

                return "🫙";

            default:

                return "📦";
        }
    }
}