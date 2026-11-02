package com.example.coletai;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

public class tela_inicial_empresa extends ContaActivity {

    private ViewPager2 viewPager;

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
        startActivity(new android.content.Intent(this, WorkspaceActivity.class));
        finish();
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

        viewPager =
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

        EmpresaPagerAdapter adapter =
                new EmpresaPagerAdapter(
                        this
                );

        viewPager.setAdapter(
                adapter
        );

        viewPager.setOffscreenPageLimit(
                2
        );

        viewPager.setUserInputEnabled(
                true
        );

        viewPager.registerOnPageChangeCallback(
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
    // NAVEGAÇÃO
    // =====================================================

    private void configurarNavegacao() {

        navInicio.setOnClickListener(
                view -> {

                    viewPager.setCurrentItem(
                            0,
                            true
                    );
                }
        );

        navHistorico.setOnClickListener(
                view -> {

                    viewPager.setCurrentItem(
                            1,
                            true
                    );
                }
        );

        navPerfil.setOnClickListener(
                view -> {

                    viewPager.setCurrentItem(
                            2,
                            true
                    );
                }
        );
    }

    // =====================================================
    // CORES DO MENU
    // =====================================================

    private void atualizarMenu(
            int position
    ) {

        int selecionado =
                ContextCompat.getColor(
                        this,
                        R.color.brand_700
                );

        int iconeNormal =
                ContextCompat.getColor(
                        this,
                        R.color.ink_400
                );

        int textoNormal =
                ContextCompat.getColor(
                        this,
                        R.color.ink_500
                );

        imgNavInicio.setImageTintList(
                ColorStateList.valueOf(
                        position == 0
                                ? selecionado
                                : iconeNormal
                )
        );

        imgNavHistorico.setImageTintList(
                ColorStateList.valueOf(
                        position == 1
                                ? selecionado
                                : iconeNormal
                )
        );

        imgNavPerfil.setImageTintList(
                ColorStateList.valueOf(
                        position == 2
                                ? selecionado
                                : iconeNormal
                )
        );

        txtNavInicio.setTextColor(
                position == 0
                        ? selecionado
                        : textoNormal
        );

        txtNavHistorico.setTextColor(
                position == 1
                        ? selecionado
                        : textoNormal
        );

        txtNavPerfil.setTextColor(
                position == 2
                        ? selecionado
                        : textoNormal
        );

        txtNavInicio.setTypeface(
                null,
                position == 0
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL
        );

        txtNavHistorico.setTypeface(
                null,
                position == 1
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL
        );

        txtNavPerfil.setTypeface(
                null,
                position == 2
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL
        );
    }
}