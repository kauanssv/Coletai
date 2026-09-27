package com.example.coletai;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class coleta_ativa_ambulante extends AppCompatActivity {

    private View layoutColetaAtiva;
    private View layoutFinalizada;

    private TextView btnVoltar;

    private TextView txtEnderecoCompleto;
    private TextView txtRegiao;
    private TextView txtObservacao;

    private TextView txtEmoji;
    private TextView txtTipoPeso;
    private TextView txtDescricao;

    private TextView txtValor;
    private TextView txtValorFinal;

    private MaterialButton btnAcaoColeta;
    private MaterialButton btnVoltarInicio;

    private SharedPreferences preferencias;

    private String etapaAtual;


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
                R.layout.activity_coleta_ativa_ambulante
        );


        configurarBarras();

        iniciarComponentes();


        preferencias =
                getSharedPreferences(
                        "coletai",
                        Context.MODE_PRIVATE
                );


        carregarDados();

        carregarEtapa();

        configurarEventos();
    }


    // =====================================================
    // BARRAS
    // =====================================================

    private void configurarBarras() {

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

        layoutColetaAtiva =
                findViewById(
                        R.id.layoutColetaAtiva
                );


        layoutFinalizada =
                findViewById(
                        R.id.layoutFinalizada
                );


        btnVoltar =
                findViewById(
                        R.id.btnVoltar
                );


        txtEnderecoCompleto =
                findViewById(
                        R.id.txtEnderecoCompleto
                );


        txtRegiao =
                findViewById(
                        R.id.txtRegiao
                );


        txtObservacao =
                findViewById(
                        R.id.txtObservacao
                );


        txtEmoji =
                findViewById(
                        R.id.txtEmoji
                );


        txtTipoPeso =
                findViewById(
                        R.id.txtTipoPeso
                );


        txtDescricao =
                findViewById(
                        R.id.txtDescricao
                );


        txtValor =
                findViewById(
                        R.id.txtValor
                );


        txtValorFinal =
                findViewById(
                        R.id.txtValorFinal
                );


        btnAcaoColeta =
                findViewById(
                        R.id.btnAcaoColeta
                );


        btnVoltarInicio =
                findViewById(
                        R.id.btnVoltarInicio
                );
    }


    // =====================================================
    // CARREGAR DADOS
    // =====================================================

    private void carregarDados() {

        String endereco =
                preferencias.getString(
                        "pedido_endereco_completo",
                        "Localização atual do cliente"
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


        String tipo =
                preferencias.getString(
                        "pedido_tipo",
                        "Outros"
                );


        String emoji =
                preferencias.getString(
                        "pedido_emoji",
                        "📦"
                );


        String peso =
                preferencias.getString(
                        "pedido_peso",
                        ""
                );


        String descricao =
                preferencias.getString(
                        "pedido_descricao",
                        ""
                );


        String preco =
                preferencias.getString(
                        "proposta_preco",
                        "0,00"
                );


        // ENDEREÇO

        txtEnderecoCompleto.setText(
                endereco
        );


        txtRegiao.setText(
                regiao
                        + ", "
                        + cidade
                        + " — SP"
        );


        // OBSERVAÇÃO

        if (
                observacao == null
                        || observacao
                        .trim()
                        .isEmpty()
        ) {

            txtObservacao.setVisibility(
                    View.GONE
            );

        } else {

            txtObservacao.setVisibility(
                    View.VISIBLE
            );


            txtObservacao.setText(
                    "💬 "
                            + observacao
            );
        }


        // PEDIDO

        txtEmoji.setText(
                emoji
        );


        txtTipoPeso.setText(
                tipo
                        + " · "
                        + peso
        );


        txtDescricao.setText(
                descricao
        );


        // VALOR

        txtValor.setText(
                "R$ "
                        + preco
        );


        txtValorFinal.setText(
                "R$ "
                        + preco
        );
    }


    // =====================================================
    // CARREGAR ETAPA
    // =====================================================

    private void carregarEtapa() {

        String statusPedido =
                preferencias.getString(
                        "pedido_status",
                        ""
                );


        etapaAtual =
                preferencias.getString(
                        "coleta_etapa",
                        "ready"
                );


        if (
                etapaAtual == null
                        || etapaAtual.trim().isEmpty()
        ) {

            etapaAtual =
                    "ready";
        }


        /*
         * PROTEÇÃO IMPORTANTE:
         *
         * Se o pedido está ativo, mas ficou salvo
         * "done" de uma coleta antiga,
         * significa que começou uma nova coleta.
         *
         * Portanto voltamos para ready.
         */
        if (
                "active".equals(
                        statusPedido
                )
                        &&
                        "done".equals(
                                etapaAtual
                        )
        ) {

            etapaAtual =
                    "ready";


            preferencias
                    .edit()

                    .putString(
                            "coleta_etapa",
                            "ready"
                    )

                    .apply();
        }


        atualizarTela();
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnVoltar.setOnClickListener(
                view -> finish()
        );


        btnAcaoColeta.setOnClickListener(
                view -> avancarEtapa()
        );


        btnVoltarInicio.setOnClickListener(
                view -> finish()
        );
    }


    // =====================================================
    // AVANÇAR ETAPA
    // =====================================================

    private void avancarEtapa() {

        switch (etapaAtual) {

            // =============================================
            // READY -> GOING
            // =============================================

            case "ready":

                etapaAtual =
                        "going";


                preferencias
                        .edit()

                        .putString(
                                "coleta_etapa",
                                "going"
                        )

                        .apply();


                Toast.makeText(
                        this,
                        "Deslocamento iniciado",
                        Toast.LENGTH_SHORT
                ).show();


                atualizarTela();

                break;


            // =============================================
            // GOING -> COLLECTING
            // =============================================

            case "going":

                etapaAtual =
                        "collecting";


                preferencias
                        .edit()

                        .putString(
                                "coleta_etapa",
                                "collecting"
                        )

                        .apply();


                Toast.makeText(
                        this,
                        "Você chegou ao local",
                        Toast.LENGTH_SHORT
                ).show();


                atualizarTela();

                break;


            // =============================================
            // COLLECTING -> DONE
            // =============================================

            case "collecting":

                finalizarColeta();

                break;
        }
    }


    // =====================================================
    // ATUALIZAR TELA
    // =====================================================

    private void atualizarTela() {

        if (
                "done".equals(
                        etapaAtual
                )
        ) {

            mostrarColetaFinalizada();

            return;
        }


        layoutColetaAtiva.setVisibility(
                View.VISIBLE
        );


        layoutFinalizada.setVisibility(
                View.GONE
        );


        switch (etapaAtual) {

            // =============================================
            // INICIAR DESLOCAMENTO
            // =============================================

            case "ready":

                btnAcaoColeta.setText(
                        "🚚  Iniciar deslocamento"
                );


                btnAcaoColeta.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor(
                                        "#1F7A4D"
                                )
                        )
                );

                break;


            // =============================================
            // CHEGUEI
            // =============================================

            case "going":

                btnAcaoColeta.setText(
                        "📍  Cheguei no local"
                );


                btnAcaoColeta.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor(
                                        "#D97706"
                                )
                        )
                );

                break;


            // =============================================
            // FINALIZAR
            // =============================================

            case "collecting":

                btnAcaoColeta.setText(
                        "✓  Finalizar coleta"
                );


                btnAcaoColeta.setBackgroundTintList(
                        ColorStateList.valueOf(
                                Color.parseColor(
                                        "#1F7A4D"
                                )
                        )
                );

                break;


            // =============================================
            // QUALQUER ESTADO INVÁLIDO
            // =============================================

            default:

                etapaAtual =
                        "ready";


                preferencias
                        .edit()

                        .putString(
                                "coleta_etapa",
                                "ready"
                        )

                        .apply();


                atualizarTela();

                break;
        }
    }


    // =====================================================
    // FINALIZAR
    // =====================================================

    private void finalizarColeta() {

        etapaAtual =
                "done";


        SharedPreferences.Editor editor =
                preferencias.edit();


        editor.putString(
                "pedido_status",
                "done"
        );


        editor.putString(
                "coleta_etapa",
                "done"
        );


        editor.apply();


        Toast.makeText(
                this,
                "Coleta finalizada!",
                Toast.LENGTH_SHORT
        ).show();


        mostrarColetaFinalizada();
    }


    // =====================================================
    // FINALIZADA
    // =====================================================

    private void mostrarColetaFinalizada() {

        layoutColetaAtiva.setVisibility(
                View.GONE
        );


        layoutFinalizada.setVisibility(
                View.VISIBLE
        );
    }
}