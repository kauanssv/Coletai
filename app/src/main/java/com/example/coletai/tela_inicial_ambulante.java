package com.example.coletai;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;

public class tela_inicial_ambulante extends AppCompatActivity {

    private ViewPager2 viewPagerAmbulante;

    private LinearLayout navegacaoInferior;

    private LinearLayout navInicio;
    private LinearLayout navHistorico;
    private LinearLayout navPerfil;

    private ImageView imgNavInicio;
    private ImageView imgNavHistorico;
    private ImageView imgNavPerfil;

    private TextView txtNavInicio;
    private TextView txtNavHistorico;
    private TextView txtNavPerfil;


    // =====================================================
    // DETALHES DO PEDIDO
    // =====================================================

    private View telaDetalhesPedido;

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

    private boolean detalhesAberto = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(
                R.layout.activity_tela_inicial_ambulante
        );

        configurarBarrasDoSistema();

        iniciarComponentes();

        configurarViewPager();

        configurarMenuInferior();

        configurarTelaDetalhes();

        configurarBotaoVoltarSistema();

        selecionarPagina(
                0,
                false
        );
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

        controller.setAppearanceLightStatusBars(true);

        controller.setAppearanceLightNavigationBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
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

        viewPagerAmbulante =
                findViewById(
                        R.id.viewPagerAmbulante
                );

        navegacaoInferior =
                findViewById(
                        R.id.navegacaoInferior
                );

        navInicio =
                findViewById(
                        R.id.navInicio
                );

        navHistorico =
                findViewById(
                        R.id.navHistorico
                );

        navPerfil =
                findViewById(
                        R.id.navPerfil
                );

        imgNavInicio =
                findViewById(
                        R.id.imgNavInicio
                );

        imgNavHistorico =
                findViewById(
                        R.id.imgNavHistorico
                );

        imgNavPerfil =
                findViewById(
                        R.id.imgNavPerfil
                );

        txtNavInicio =
                findViewById(
                        R.id.txtNavInicio
                );

        txtNavHistorico =
                findViewById(
                        R.id.txtNavHistorico
                );

        txtNavPerfil =
                findViewById(
                        R.id.txtNavPerfil
                );


        // Tela de detalhes

        telaDetalhesPedido =
                findViewById(
                        R.id.telaDetalhesPedido
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
    // VIEWPAGER
    // =====================================================

    private void configurarViewPager() {

        AmbulantePagerAdapter adapter =
                new AmbulantePagerAdapter(
                        this
                );

        viewPagerAmbulante.setAdapter(
                adapter
        );

        viewPagerAmbulante.setOffscreenPageLimit(
                2
        );

        viewPagerAmbulante.setUserInputEnabled(
                true
        );

        viewPagerAmbulante.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {

                    @Override
                    public void onPageSelected(
                            int position
                    ) {

                        super.onPageSelected(
                                position
                        );

                        atualizarMenu(
                                position
                        );
                    }
                }
        );
    }


    // =====================================================
    // MENU INFERIOR
    // =====================================================

    private void configurarMenuInferior() {

        navInicio.setOnClickListener(
                view ->
                        selecionarPagina(
                                0,
                                true
                        )
        );

        navHistorico.setOnClickListener(
                view ->
                        selecionarPagina(
                                1,
                                true
                        )
        );

        navPerfil.setOnClickListener(
                view ->
                        selecionarPagina(
                                2,
                                true
                        )
        );
    }


    private void selecionarPagina(
            int pagina,
            boolean animar
    ) {

        viewPagerAmbulante.setCurrentItem(
                pagina,
                animar
        );

        atualizarMenu(
                pagina
        );
    }


    private void atualizarMenu(
            int paginaSelecionada
    ) {

        int corSelecionada =
                ContextCompat.getColor(
                        this,
                        R.color.brand_700
                );

        int corIconeNormal =
                ContextCompat.getColor(
                        this,
                        R.color.ink_400
                );

        int corTextoNormal =
                ContextCompat.getColor(
                        this,
                        R.color.ink_500
                );

        atualizarItemMenu(
                imgNavInicio,
                txtNavInicio,
                paginaSelecionada == 0,
                corSelecionada,
                corIconeNormal,
                corTextoNormal
        );

        atualizarItemMenu(
                imgNavHistorico,
                txtNavHistorico,
                paginaSelecionada == 1,
                corSelecionada,
                corIconeNormal,
                corTextoNormal
        );

        atualizarItemMenu(
                imgNavPerfil,
                txtNavPerfil,
                paginaSelecionada == 2,
                corSelecionada,
                corIconeNormal,
                corTextoNormal
        );
    }


    private void atualizarItemMenu(
            @NonNull ImageView imagem,
            @NonNull TextView texto,
            boolean selecionado,
            int corSelecionada,
            int corIconeNormal,
            int corTextoNormal
    ) {

        imagem.setImageTintList(
                ColorStateList.valueOf(
                        selecionado
                                ? corSelecionada
                                : corIconeNormal
                )
        );

        texto.setTextColor(
                selecionado
                        ? corSelecionada
                        : corTextoNormal
        );

        texto.setTypeface(
                null,
                selecionado
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL
        );
    }


    // =====================================================
    // DETALHES DO PEDIDO
    // =====================================================

    private void configurarTelaDetalhes() {

        findViewById(
                R.id.btnVoltar
        ).setOnClickListener(
                view ->
                        fecharDetalhesPedido()
        );


        findViewById(
                R.id.btnRecusar
        ).setOnClickListener(
                view ->
                        fecharDetalhesPedido()
        );


        findViewById(
                R.id.btnEnviarProposta
        ).setOnClickListener(
                view -> {

                    // Próxima etapa:
                    // abrir o formulário de proposta.

                }
        );
    }


    public void abrirDetalhesPedido(
            String tipo,
            String emoji,
            String descricao,
            String peso,
            String regiao,
            String cidade,
            String observacao
    ) {

        txtTagTipo.setText(
                emoji + " " + tipo
        );

        txtEmoji.setText(
                emoji
        );

        txtTipoResiduo.setText(
                tipo
        );

        txtDescricao.setText(
                descricao
        );

        txtPeso.setText(
                peso
        );

        txtRegiao.setText(
                regiao
        );

        String localizacao =
                regiao + ", " + cidade;

        txtLocalizacaoTopo.setText(
                localizacao
        );

        txtLocalizacao.setText(
                localizacao
        );


        // OBSERVAÇÃO

        if (
                observacao == null
                        || observacao.trim().isEmpty()
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


        // ESCONDE HOME + MENU

        viewPagerAmbulante.setVisibility(
                View.GONE
        );

        navegacaoInferior.setVisibility(
                View.GONE
        );


        // MOSTRA DETALHES

        telaDetalhesPedido.setVisibility(
                View.VISIBLE
        );

        detalhesAberto = true;
    }


    public void fecharDetalhesPedido() {

        telaDetalhesPedido.setVisibility(
                View.GONE
        );


        viewPagerAmbulante.setVisibility(
                View.VISIBLE
        );

        navegacaoInferior.setVisibility(
                View.VISIBLE
        );

        detalhesAberto = false;
    }


    // =====================================================
    // BOTÃO VOLTAR DO ANDROID
    // =====================================================

    private void configurarBotaoVoltarSistema() {

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (detalhesAberto) {

                            fecharDetalhesPedido();

                        } else {

                            setEnabled(false);

                            getOnBackPressedDispatcher()
                                    .onBackPressed();
                        }
                    }
                }
        );
    }
}