package com.example.coletai;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class HistoricoAmbulanteFragment extends Fragment {

    private LinearLayout layoutHistorico;
    private LinearLayout layoutDetalhe;
    private LinearLayout listaHistorico;

    private TextView txtTotalColetas;
    private TextView txtGanhos;

    private TextView btnVoltarDetalhe;

    private TextView txtDetalheResiduo;
    private TextView txtDetalheStatus;
    private TextView txtDetalheDescricao;
    private TextView txtDetalheData;
    private TextView txtDetalheEndereco;
    private TextView txtDetalhePeso;
    private TextView txtDetalheValor;

    private final List<ColetaHistorico> coletas =
            new ArrayList<>();


    public HistoricoAmbulanteFragment() {

        super(
                R.layout.fragment_historico_ambulante
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

        iniciarComponentes(view);

        criarHistoricoMvp();

        atualizarResumo();

        carregarLista();

        configurarEventos();
    }


    // =====================================================
    // COMPONENTES
    // =====================================================

    private void iniciarComponentes(
            View view
    ) {

        layoutHistorico =
                view.findViewById(
                        R.id.layoutHistorico
                );

        layoutDetalhe =
                view.findViewById(
                        R.id.layoutDetalhe
                );

        listaHistorico =
                view.findViewById(
                        R.id.listaHistorico
                );


        txtTotalColetas =
                view.findViewById(
                        R.id.txtTotalColetas
                );

        txtGanhos =
                view.findViewById(
                        R.id.txtGanhos
                );


        btnVoltarDetalhe =
                view.findViewById(
                        R.id.btnVoltarDetalhe
                );


        txtDetalheResiduo =
                view.findViewById(
                        R.id.txtDetalheResiduo
                );

        txtDetalheStatus =
                view.findViewById(
                        R.id.txtDetalheStatus
                );

        txtDetalheDescricao =
                view.findViewById(
                        R.id.txtDetalheDescricao
                );

        txtDetalheData =
                view.findViewById(
                        R.id.txtDetalheData
                );

        txtDetalheEndereco =
                view.findViewById(
                        R.id.txtDetalheEndereco
                );

        txtDetalhePeso =
                view.findViewById(
                        R.id.txtDetalhePeso
                );

        txtDetalheValor =
                view.findViewById(
                        R.id.txtDetalheValor
                );
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnVoltarDetalhe.setOnClickListener(
                view -> fecharDetalhe()
        );
    }


    // =====================================================
    // DADOS EXATOS DO MVP
    // =====================================================

    private void criarHistoricoMvp() {

        coletas.clear();


        coletas.add(
                new ColetaHistorico(
                        "Baterias",
                        "Bateria de moto e 2 baterias de nobreak",
                        "8 kg",
                        "Rua das Flores, 234 — Jardim Paulista, São Paulo",
                        "18 ago. 2025",
                        "concluido",
                        35.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Eletrônicos",
                        "Impressora HP e scanner antigo",
                        "6 kg",
                        "Rua Oscar Freire, 620 — Jardins, São Paulo",
                        "10 ago. 2025",
                        "concluido",
                        55.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Óleo",
                        "Óleo de motor e óleo de cozinha",
                        "7 kg",
                        "Av. Brasil, 1200 — Água Branca, São Paulo",
                        "28 jul. 2025",
                        "concluido",
                        40.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Medicamentos",
                        "Medicamentos e pomadas vencidas",
                        "1.2 kg",
                        "Rua Pamplona, 410 — Bela Vista, São Paulo",
                        "12 jul. 2025",
                        "concluido",
                        26.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Eletrônicos",
                        "Computador antigo completo",
                        "12 kg",
                        "Rua da Consolação, 980 — Consolação, São Paulo",
                        "30 jun. 2025",
                        "cancelado",
                        0
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Agulhas",
                        "Recipiente com agulhas descartáveis",
                        "0.7 kg",
                        "Alameda Campinas, 700 — Jardins, São Paulo",
                        "17 jun. 2025",
                        "concluido",
                        32.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Pilhas",
                        "Caixa com pilhas e baterias pequenas",
                        "2.1 kg",
                        "Rua Bela Cintra, 350 — Consolação, São Paulo",
                        "25 mai. 2025",
                        "concluido",
                        24.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Baterias",
                        "Bateria automotiva sem uso",
                        "18 kg",
                        "Alameda Lorena, 820 — Jardim Paulista, São Paulo",
                        "08 mai. 2025",
                        "cancelado",
                        0
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Óleo",
                        "Óleo de cozinha em cinco garrafas PET",
                        "5 kg",
                        "Rua Frei Caneca, 540 — Consolação, São Paulo",
                        "19 abr. 2025",
                        "concluido",
                        30.00
                )
        );


        coletas.add(
                new ColetaHistorico(
                        "Outros",
                        "Lâminas protegidas em recipiente rígido",
                        "0.4 kg",
                        "Rua Haddock Lobo, 290 — Cerqueira César, São Paulo",
                        "27 mar. 2025",
                        "concluido",
                        20.00
                )
        );
    }


    // =====================================================
    // RESUMO
    // =====================================================

    private void atualizarResumo() {

        int concluidas =
                0;

        double ganhos =
                0;


        for (ColetaHistorico coleta : coletas) {

            if (
                    coleta.status.equals(
                            "concluido"
                    )
            ) {

                concluidas++;

                ganhos +=
                        coleta.valor;
            }
        }


        txtTotalColetas.setText(
                String.valueOf(
                        concluidas
                )
        );


        txtGanhos.setText(
                "R$ "
                        + String.format(
                        java.util.Locale.US,
                        "%.0f",
                        ganhos
                )
        );
    }


    // =====================================================
    // LISTA
    // =====================================================

    private void carregarLista() {

        listaHistorico.removeAllViews();


        for (ColetaHistorico coleta : coletas) {

            adicionarCard(
                    coleta
            );
        }
    }


    private void adicionarCard(
            ColetaHistorico coleta
    ) {

        MaterialCardView card =
                new MaterialCardView(
                        requireContext()
                );


        LinearLayout.LayoutParams parametrosCard =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        parametrosCard.bottomMargin =
                dp(10);


        card.setLayoutParams(
                parametrosCard
        );


        card.setCardBackgroundColor(
                Color.WHITE
        );


        card.setRadius(
                dp(16)
        );


        card.setCardElevation(
                0
        );


        card.setStrokeWidth(
                dp(1)
        );


        card.setStrokeColor(
                Color.parseColor(
                        "#E5E7EB"
                )
        );


        card.setClickable(
                true
        );


        card.setFocusable(
                true
        );


        LinearLayout conteudo =
                new LinearLayout(
                        requireContext()
                );


        conteudo.setOrientation(
                LinearLayout.VERTICAL
        );


        conteudo.setPadding(
                dp(15),
                dp(14),
                dp(15),
                dp(14)
        );


        // =================================================
        // CABEÇALHO
        // =================================================

        LinearLayout cabecalho =
                new LinearLayout(
                        requireContext()
                );


        cabecalho.setOrientation(
                LinearLayout.HORIZONTAL
        );


        cabecalho.setGravity(
                Gravity.CENTER_VERTICAL
        );


        TextView badgeResiduo =
                new TextView(
                        requireContext()
                );


        badgeResiduo.setText(
                obterEmoji(
                        coleta.tipo
                )
                        + "  "
                        + coleta.tipo
        );


        badgeResiduo.setTextSize(
                11
        );


        badgeResiduo.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );


        configurarBadgeResiduo(
                badgeResiduo,
                coleta.tipo
        );


        cabecalho.addView(
                badgeResiduo
        );


        View espaco =
                new View(
                        requireContext()
                );


        LinearLayout.LayoutParams parametrosEspaco =
                new LinearLayout.LayoutParams(
                        0,
                        1,
                        1
                );


        espaco.setLayoutParams(
                parametrosEspaco
        );


        cabecalho.addView(
                espaco
        );


        TextView valorStatus =
                new TextView(
                        requireContext()
                );


        if (
                coleta.status.equals(
                        "cancelado"
                )
        ) {

            valorStatus.setText(
                    "Cancelado"
            );


            valorStatus.setTextColor(
                    Color.parseColor(
                            "#DC2626"
                    )
            );


            valorStatus.setBackground(
                    criarFundo(
                            "#FEE2E2",
                            20
                    )
            );


            valorStatus.setPadding(
                    dp(9),
                    dp(5),
                    dp(9),
                    dp(5)
            );


            valorStatus.setTextSize(
                    11
            );

        } else {

            valorStatus.setText(
                    "R$ "
                            + formatarValor(
                            coleta.valor
                    )
            );


            valorStatus.setTextColor(
                    Color.parseColor(
                            "#1F7A4D"
                    )
            );


            valorStatus.setTextSize(
                    14
            );


            valorStatus.setTypeface(
                    null,
                    android.graphics.Typeface.BOLD
            );
        }


        cabecalho.addView(
                valorStatus
        );


        conteudo.addView(
                cabecalho
        );


        // =================================================
        // DESCRIÇÃO
        // =================================================

        TextView descricao =
                new TextView(
                        requireContext()
                );


        descricao.setText(
                coleta.descricao
        );


        descricao.setTextColor(
                Color.parseColor(
                        "#374151"
                )
        );


        descricao.setTextSize(
                13
        );


        descricao.setMaxLines(
                1
        );


        LinearLayout.LayoutParams parametrosDescricao =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        parametrosDescricao.topMargin =
                dp(10);


        descricao.setLayoutParams(
                parametrosDescricao
        );


        conteudo.addView(
                descricao
        );


        // =================================================
        // RODAPÉ
        // =================================================

        LinearLayout rodape =
                new LinearLayout(
                        requireContext()
                );


        rodape.setOrientation(
                LinearLayout.HORIZONTAL
        );


        rodape.setGravity(
                Gravity.CENTER_VERTICAL
        );


        LinearLayout.LayoutParams parametrosRodape =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );


        parametrosRodape.topMargin =
                dp(12);


        rodape.setLayoutParams(
                parametrosRodape
        );


        TextView local =
                new TextView(
                        requireContext()
                );


        local.setText(
                obterRegiao(
                        coleta.endereco
                )
        );


        local.setTextColor(
                Color.parseColor(
                        "#6B7280"
                )
        );


        local.setTextSize(
                12
        );


        LinearLayout.LayoutParams parametrosLocal =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );


        local.setLayoutParams(
                parametrosLocal
        );


        rodape.addView(
                local
        );


        TextView data =
                new TextView(
                        requireContext()
                );


        data.setText(
                coleta.data
        );


        data.setTextColor(
                Color.parseColor(
                        "#9CA3AF"
                )
        );


        data.setTextSize(
                11
        );


        rodape.addView(
                data
        );


        conteudo.addView(
                rodape
        );


        card.addView(
                conteudo
        );


        card.setOnClickListener(
                view -> abrirDetalhe(
                        coleta
                )
        );


        listaHistorico.addView(
                card
        );
    }


    // =====================================================
    // DETALHE
    // =====================================================

    private void abrirDetalhe(
            ColetaHistorico coleta
    ) {

        layoutHistorico.setVisibility(
                View.GONE
        );


        layoutDetalhe.setVisibility(
                View.VISIBLE
        );


        txtDetalheResiduo.setText(
                obterEmoji(
                        coleta.tipo
                )
                        + " "
                        + coleta.tipo
        );


        configurarBadgeResiduo(
                txtDetalheResiduo,
                coleta.tipo
        );


        boolean concluido =
                coleta.status.equals(
                        "concluido"
                );


        txtDetalheStatus.setText(
                concluido
                        ? "Concluído"
                        : "Cancelado"
        );


        txtDetalheStatus.setTextColor(
                Color.parseColor(
                        concluido
                                ? "#6B7280"
                                : "#DC2626"
                )
        );


        txtDetalheStatus.setBackground(
                criarFundo(
                        concluido
                                ? "#F3F4F6"
                                : "#FEE2E2",
                        20
                )
        );


        txtDetalheDescricao.setText(
                coleta.descricao
        );


        txtDetalheData.setText(
                coleta.data
        );


        txtDetalheEndereco.setText(
                coleta.endereco
        );


        txtDetalhePeso.setText(
                coleta.peso
        );


        txtDetalheValor.setText(
                concluido
                        ? "R$ "
                          + formatarValor(
                        coleta.valor
                )
                        : "—"
        );
    }


    private void fecharDetalhe() {

        layoutDetalhe.setVisibility(
                View.GONE
        );


        layoutHistorico.setVisibility(
                View.VISIBLE
        );
    }


    // =====================================================
    // VISUAL
    // =====================================================

    private void configurarBadgeResiduo(
            TextView badge,
            String tipo
    ) {

        String fundo =
                "#F3F4F6";

        String texto =
                "#6B7280";


        switch (tipo) {

            case "Pilhas":

                fundo = "#FEF3C7";
                texto = "#D97706";

                break;


            case "Baterias":

                fundo = "#FEE2E2";
                texto = "#DC2626";

                break;


            case "Eletrônicos":

                fundo = "#EEF2FF";
                texto = "#4F46E5";

                break;


            case "Medicamentos":

                fundo = "#E0F2FE";
                texto = "#0891B2";

                break;


            case "Agulhas":

                fundo = "#FCE7F3";
                texto = "#BE185D";

                break;


            case "Óleo":

                fundo = "#FEF3C7";
                texto = "#92400E";

                break;
        }


        badge.setTextColor(
                Color.parseColor(
                        texto
                )
        );


        badge.setBackground(
                criarFundo(
                        fundo,
                        20
                )
        );


        badge.setPadding(
                dp(9),
                dp(5),
                dp(9),
                dp(5)
        );
    }


    private GradientDrawable criarFundo(
            String cor,
            int raio
    ) {

        GradientDrawable drawable =
                new GradientDrawable();


        drawable.setColor(
                Color.parseColor(
                        cor
                )
        );


        drawable.setCornerRadius(
                dp(raio)
        );


        return drawable;
    }


    // =====================================================
    // UTILIDADES
    // =====================================================

    private int dp(
            int valor
    ) {

        return Math.round(
                valor
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }


    private String formatarValor(
            double valor
    ) {

        return String.format(
                java.util.Locale.US,
                "%.2f",
                valor
        ).replace(
                ".",
                ","
        );
    }


    private String obterRegiao(
            String endereco
    ) {

        if (
                endereco.contains(
                        "Jardim Paulista"
                )
        ) {

            return "Jardim Paulista, São Paulo";
        }


        if (
                endereco.contains(
                        "Jardins"
                )
        ) {

            return "Jardins, São Paulo";
        }


        if (
                endereco.contains(
                        "Água Branca"
                )
        ) {

            return "Água Branca, São Paulo";
        }


        if (
                endereco.contains(
                        "Bela Vista"
                )
        ) {

            return "Bela Vista, São Paulo";
        }


        if (
                endereco.contains(
                        "Cerqueira César"
                )
        ) {

            return "Cerqueira César, São Paulo";
        }


        return "Consolação, São Paulo";
    }


    private String obterEmoji(
            String tipo
    ) {

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


    // =====================================================
    // MODELO
    // =====================================================

    private static class ColetaHistorico {

        String tipo;
        String descricao;
        String peso;
        String endereco;
        String data;
        String status;

        double valor;


        ColetaHistorico(
                String tipo,
                String descricao,
                String peso,
                String endereco,
                String data,
                String status,
                double valor
        ) {

            this.tipo =
                    tipo;

            this.descricao =
                    descricao;

            this.peso =
                    peso;

            this.endereco =
                    endereco;

            this.data =
                    data;

            this.status =
                    status;

            this.valor =
                    valor;
        }
    }
}