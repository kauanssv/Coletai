package com.example.coletai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class InicioAmbulanteFragment extends Fragment {

    private MaterialButton btnDisponibilidade;

    private LinearLayout layoutPedidos;
    private LinearLayout layoutIndisponivel;

    private boolean disponivel = true;

    private boolean solicitacaoJaExibida = false;

    private boolean coletaAtivaJaExibida = false;


    public InicioAmbulanteFragment() {

        super(
                R.layout.fragment_inicio_ambulante
        );
    }


    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );


        btnDisponibilidade =
                view.findViewById(
                        R.id.btnDisponibilidade
                );


        layoutPedidos =
                view.findViewById(
                        R.id.layoutPedidos
                );


        layoutIndisponivel =
                view.findViewById(
                        R.id.layoutIndisponivel
                );


        configurarDisponibilidade();

        configurarPedidos(
                view
        );


        atualizarDisponibilidade();
    }


    @Override
    public void onResume() {

        super.onResume();


        verificarEstadoPedido();
    }


    private void configurarDisponibilidade() {

        btnDisponibilidade.setOnClickListener(
                view -> {

                    disponivel =
                            !disponivel;


                    atualizarDisponibilidade();


                    if (disponivel) {

                        verificarEstadoPedido();
                    }
                }
        );
    }


    private void atualizarDisponibilidade() {

        if (disponivel) {

            btnDisponibilidade.setText(
                    "●  Disponível"
            );


            btnDisponibilidade.setTextColor(
                    Color.WHITE
            );


            btnDisponibilidade.setBackgroundTintList(
                    ColorStateList.valueOf(
                            Color.parseColor(
                                    "#1F7A4D"
                            )
                    )
            );


            btnDisponibilidade.setStrokeWidth(
                    0
            );


            layoutPedidos.setVisibility(
                    View.VISIBLE
            );


            layoutIndisponivel.setVisibility(
                    View.GONE
            );

        } else {

            btnDisponibilidade.setText(
                    "●  Indisponível"
            );


            btnDisponibilidade.setTextColor(
                    Color.parseColor(
                            "#6B7280"
                    )
            );


            btnDisponibilidade.setBackgroundTintList(
                    ColorStateList.valueOf(
                            Color.WHITE
                    )
            );


            btnDisponibilidade.setStrokeColor(
                    ColorStateList.valueOf(
                            Color.parseColor(
                                    "#E5E7EB"
                            )
                    )
            );


            btnDisponibilidade.setStrokeWidth(
                    1
            );


            layoutPedidos.setVisibility(
                    View.GONE
            );


            layoutIndisponivel.setVisibility(
                    View.VISIBLE
            );
        }
    }


    // =====================================================
    // ESTADO DO PEDIDO
    // =====================================================

    private void verificarEstadoPedido() {

        if (!isAdded()) {

            return;
        }


        SharedPreferences preferencias =
                requireContext()
                        .getSharedPreferences(
                                "coletai",
                                Context.MODE_PRIVATE
                        );


        String status =
                preferencias.getString(
                        "pedido_status",
                        ""
                );


        String tipoColetor =
                preferencias.getString(
                        "proposta_coletor_tipo",
                        ""
                );


        // NOVO PEDIDO

        if (
                "waiting".equals(status)
                        && disponivel
                        && !solicitacaoJaExibida
        ) {

            solicitacaoJaExibida =
                    true;


            abrirPedidoDoCliente(
                    preferencias
            );


            return;
        }


        // CLIENTE ACEITOU A PROPOSTA DE JOÃO

        if (
                "active".equals(status)
                        &&
                        (
                                "ambulante".equals(tipoColetor)
                                        || tipoColetor.isEmpty()
                        )
                        && !coletaAtivaJaExibida
        ) {

            coletaAtivaJaExibida =
                    true;


            abrirColetaAtiva();


            return;
        }


        if (!"waiting".equals(status)) {

            solicitacaoJaExibida =
                    false;
        }


        if (
                !"active".equals(status)
                        || "empresa".equals(tipoColetor)
        ) {

            coletaAtivaJaExibida =
                    false;
        }
    }


    private void abrirPedidoDoCliente(
            SharedPreferences preferencias
    ) {

        String tipo =
                preferencias.getString(
                        "pedido_tipo",
                        "Outros"
                );


        String emoji =
                preferencias.getString(
                        "pedido_emoji",
                        obterEmoji(tipo)
                );


        String descricao =
                preferencias.getString(
                        "pedido_descricao",
                        ""
                );


        String peso =
                preferencias.getString(
                        "pedido_peso",
                        ""
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


        Intent intent =
                new Intent(
                        requireContext(),
                        detalhes_pedido_ambulante.class
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


    private void abrirColetaAtiva() {

        Intent intent =
                new Intent(
                        requireContext(),
                        coleta_ativa_ambulante.class
                );


        startActivity(
                intent
        );
    }


    private void configurarPedidos(
            @NonNull View view
    ) {

        View cardPedido1 =
                view.findViewById(
                        R.id.cardPedido1
                );


        if (cardPedido1 != null) {

            cardPedido1.setOnClickListener(
                    v -> abrirDetalhesPedido(
                            "Eletrônicos",
                            "💻",
                            "TV de tubo 29\" e um rádio antigo. Estão na garagem.",
                            "14 kg",
                            "Consolação",
                            "São Paulo",
                            "Ligar antes de ir"
                    )
            );
        }


        View cardPedido2 =
                view.findViewById(
                        R.id.cardPedido2
                );


        if (cardPedido2 != null) {

            cardPedido2.setOnClickListener(
                    v -> abrirDetalhesPedido(
                            "Medicamentos",
                            "💊",
                            "3 caixas de remédios vencidos e xaropes",
                            "0,8 kg",
                            "Jardim Paulista",
                            "São Paulo",
                            ""
                    )
            );
        }


        View cardPedido3 =
                view.findViewById(
                        R.id.cardPedido3
                );


        if (cardPedido3 != null) {

            cardPedido3.setOnClickListener(
                    v -> abrirDetalhesPedido(
                            "Pilhas",
                            "🔋",
                            "Pilhas e baterias de controles remotos e mouse",
                            "0,4 kg",
                            "Cerqueira César",
                            "São Paulo",
                            ""
                    )
            );
        }


        View cardPedido4 =
                view.findViewById(
                        R.id.cardPedido4
                );


        if (cardPedido4 != null) {

            cardPedido4.setOnClickListener(
                    v -> abrirDetalhesPedido(
                            "Baterias",
                            "⚡",
                            "Bateria de carro 60Ah e 2 baterias de nobreak",
                            "22 kg",
                            "Jardim Paulista",
                            "São Paulo",
                            "Produto pesado, trazer carrinho"
                    )
            );
        }
    }


    private void abrirDetalhesPedido(
            String tipo,
            String emoji,
            String descricao,
            String peso,
            String regiao,
            String cidade,
            String observacao
    ) {

        Intent intent =
                new Intent(
                        requireContext(),
                        detalhes_pedido_ambulante.class
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