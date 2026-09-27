package com.example.coletai;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

public class tela_inicial_ambulante extends AppCompatActivity {

    // =====================================================
    // VIEWPAGER
    // =====================================================

    private ViewPager2 viewPagerAmbulante;


    // =====================================================
    // MENU INFERIOR
    // =====================================================

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
    // ON CREATE
    // =====================================================

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
                R.layout.activity_tela_inicial_ambulante
        );


        configurarBarrasDoSistema();

        iniciarComponentes();

        configurarViewPager();

        configurarMenuInferior();


        /*
         * O ambulante começa sempre
         * na tela inicial.
         */
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

        viewPagerAmbulante =
                findViewById(
                        R.id.viewPagerAmbulante
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


        /*
         * Mantém as três páginas carregadas.
         */
        viewPagerAmbulante.setOffscreenPageLimit(
                2
        );


        /*
         * Permite trocar de tela
         * arrastando para os lados.
         */
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


    // =====================================================
    // SELECIONAR PÁGINA
    // =====================================================

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


    // =====================================================
    // ATUALIZAR MENU
    // =====================================================

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


    // =====================================================
    // ITEM DO MENU
    // =====================================================

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
}