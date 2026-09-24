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

public class tela_inicial_cliente extends AppCompatActivity {

    private ViewPager2 viewPagerCliente;

    private LinearLayout navInicio;
    private LinearLayout navHistorico;
    private LinearLayout navPerfil;

    private ImageView imgNavInicio;
    private ImageView imgNavHistorico;
    private ImageView imgNavPerfil;

    private TextView txtNavInicio;
    private TextView txtNavHistorico;
    private TextView txtNavPerfil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(
                R.layout.activity_tela_inicial_cliente
        );

        configurarBarrasDoSistema();
        iniciarComponentes();
        configurarViewPager();
        configurarMenuInferior();

        // Começa na tela Início
        selecionarPagina(
                0,
                false
        );
    }

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

    private void iniciarComponentes() {

        viewPagerCliente =
                findViewById(
                        R.id.viewPagerCliente
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

    private void configurarViewPager() {

        ClientePagerAdapter adapter =
                new ClientePagerAdapter(
                        this
                );

        viewPagerCliente.setAdapter(
                adapter
        );

        /*
         * Mantém as três telas carregadas.
         */
        viewPagerCliente.setOffscreenPageLimit(
                2
        );

        /*
         * Permite arrastar com o dedo.
         */
        viewPagerCliente.setUserInputEnabled(
                true
        );

        /*
         * Detecta quando o usuário troca
         * de página arrastando.
         */
        viewPagerCliente.registerOnPageChangeCallback(
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

        viewPagerCliente.setCurrentItem(
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
}