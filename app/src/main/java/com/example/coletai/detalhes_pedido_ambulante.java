package com.example.coletai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;

public class detalhes_pedido_ambulante extends AppCompatActivity {

    private TextView btnVoltar;

    private TextView txtTagTipo;
    private TextView txtEmoji;
    private TextView txtTipoResiduo;
    private TextView txtLocalizacaoTopo;

    private TextView txtDescricao;
    private TextView txtPeso;
    private TextView txtRegiao;
    private TextView txtLocalizacao;
    private TextView txtObservacao;

    private MaterialCardView cardObservacao;


    // =====================================================
    // DADOS DO PEDIDO
    // =====================================================

    private String tipo;
    private String emoji;
    private String descricao;
    private String peso;
    private String regiao;
    private String cidade;
    private String observacao;


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


        configurarBarras();

        iniciarComponentes();

        receberDados();

        configurarEventos();
    }


    // =====================================================
    // BARRAS DO SISTEMA
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

        btnVoltar =
                findViewById(
                        R.id.btnVoltar
                );


        txtTagTipo =
                findViewById(
                        R.id.txtTagTipo
                );


        txtEmoji =
                findViewById(
                        R.id.txtEmoji
                );


        txtTipoResiduo =
                findViewById(
                        R.id.txtTipoResiduo
                );


        txtLocalizacaoTopo =
                findViewById(
                        R.id.txtLocalizacaoTopo
                );


        txtDescricao =
                findViewById(
                        R.id.txtDescricao
                );


        txtPeso =
                findViewById(
                        R.id.txtPeso
                );


        txtRegiao =
                findViewById(
                        R.id.txtRegiao
                );


        txtLocalizacao =
                findViewById(
                        R.id.txtLocalizacao
                );


        txtObservacao =
                findViewById(
                        R.id.txtObservacao
                );


        cardObservacao =
                findViewById(
                        R.id.cardObservacao
                );
    }


    // =====================================================
    // RECEBER DADOS
    // =====================================================

    private void receberDados() {

        tipo =
                getIntent()
                        .getStringExtra(
                                "tipo"
                        );


        emoji =
                getIntent()
                        .getStringExtra(
                                "emoji"
                        );


        descricao =
                getIntent()
                        .getStringExtra(
                                "descricao"
                        );


        peso =
                getIntent()
                        .getStringExtra(
                                "peso"
                        );


        regiao =
                getIntent()
                        .getStringExtra(
                                "regiao"
                        );


        cidade =
                getIntent()
                        .getStringExtra(
                                "cidade"
                        );


        observacao =
                getIntent()
                        .getStringExtra(
                                "observacao"
                        );


        // =================================================
        // TIPO
        // =================================================

        if (tipo != null) {

            txtTipoResiduo.setText(
                    tipo
            );


            if (emoji != null) {

                txtTagTipo.setText(
                        emoji
                                + " "
                                + tipo
                );

            } else {

                txtTagTipo.setText(
                        tipo
                );
            }
        }


        // =================================================
        // EMOJI
        // =================================================

        if (emoji != null) {

            txtEmoji.setText(
                    emoji
            );
        }


        // =================================================
        // DESCRIÇÃO
        // =================================================

        if (descricao != null) {

            txtDescricao.setText(
                    descricao
            );
        }


        // =================================================
        // PESO
        // =================================================

        if (peso != null) {

            txtPeso.setText(
                    peso
            );
        }


        // =================================================
        // LOCALIZAÇÃO
        // =================================================

        if (regiao != null) {

            txtRegiao.setText(
                    regiao
            );


            String localizacao;


            if (
                    cidade != null
                            && !cidade
                            .trim()
                            .isEmpty()
            ) {

                localizacao =
                        regiao
                                + ", "
                                + cidade;

            } else {

                localizacao =
                        regiao;
            }


            txtLocalizacao.setText(
                    localizacao
            );


            txtLocalizacaoTopo.setText(
                    localizacao
            );
        }


        // =================================================
        // OBSERVAÇÃO
        // =================================================

        if (
                observacao == null
                        || observacao
                        .trim()
                        .isEmpty()
        ) {

            cardObservacao.setVisibility(
                    View.GONE
            );

        } else {

            cardObservacao.setVisibility(
                    View.VISIBLE
            );


            txtObservacao.setText(
                    observacao
            );
        }
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        // VOLTAR

        btnVoltar.setOnClickListener(
                view -> finish()
        );


        // RECUSAR

        findViewById(
                R.id.btnRecusar
        ).setOnClickListener(
                view -> recusarPedido()
        );


        // ENVIAR PROPOSTA

        findViewById(
                R.id.btnEnviarProposta
        ).setOnClickListener(
                view -> abrirTelaProposta()
        );
    }


    // =====================================================
    // RECUSAR PEDIDO
    // =====================================================

    private void recusarPedido() {

        /*
         * Não mudamos o status global para cancelado.
         *
         * O pedido continua como "waiting" para poder
         * continuar procurando outros coletores.
         *
         * Nesta sessão, a Home já sabe que a solicitação
         * foi exibida e não abrirá imediatamente de novo.
         */

        finish();
    }


    // =====================================================
    // ABRIR TELA DE PROPOSTA
    // =====================================================

    private void abrirTelaProposta() {

        Intent intent =
                new Intent(
                        detalhes_pedido_ambulante.this,
                        enviar_proposta_ambulante.class
                );


        intent.putExtra(
                "tipo",
                tipo
        );


        intent.putExtra(
                "emoji",
                emoji
        );


        intent.putExtra(
                "descricao",
                descricao
        );


        intent.putExtra(
                "peso",
                peso
        );


        intent.putExtra(
                "regiao",
                regiao
        );


        intent.putExtra(
                "cidade",
                cidade
        );


        intent.putExtra(
                "observacao",
                observacao
        );


        startActivity(
                intent
        );
    }
}