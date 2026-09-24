package com.example.coletai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private EditText edtEmail;
    private EditText edtSenha;

    private Button btnEntrar;

    private ImageButton btnMostrarSenha;

    private boolean senhaVisivel =
            false;


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        SplashScreen.installSplashScreen(
                this
        );


        super.onCreate(
                savedInstanceState
        );


        EdgeToEdge.enable(
                this
        );


        setContentView(
                R.layout.activity_main
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


        iniciarComponentes();

        configurarEventos();
    }


    private void iniciarComponentes() {

        edtEmail =
                findViewById(
                        R.id.edtEmail
                );


        edtSenha =
                findViewById(
                        R.id.edtSenha
                );


        btnEntrar =
                findViewById(
                        R.id.btnEntrar
                );


        btnMostrarSenha =
                findViewById(
                        R.id.btnMostrarSenha
                );
    }


    private void configurarEventos() {

        btnMostrarSenha.setOnClickListener(
                view -> alternarVisibilidadeSenha()
        );


        btnEntrar.setOnClickListener(
                view -> realizarLogin()
        );


        configurarContaDemonstracao(
                R.id.cardCliente,
                "cliente@gmail.com"
        );


        configurarContaDemonstracao(
                R.id.cardAmbulante,
                "ambulante@gmail.com"
        );


        configurarContaDemonstracao(
                R.id.cardEmpresa,
                "empresa@gmail.com"
        );
    }


    private void configurarContaDemonstracao(
            int viewId,
            String email
    ) {

        LinearLayout card =
                findViewById(
                        viewId
                );


        card.setOnClickListener(
                view -> {

                    edtEmail.setText(
                            email
                    );


                    edtSenha.setText(
                            "12345"
                    );


                    edtEmail.clearFocus();

                    edtSenha.clearFocus();
                }
        );
    }


    // =====================================================
    // SENHA
    // =====================================================

    private void alternarVisibilidadeSenha() {

        senhaVisivel =
                !senhaVisivel;


        if (senhaVisivel) {

            edtSenha.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            |
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            );


            btnMostrarSenha.setAlpha(
                    1f
            );

        } else {

            edtSenha.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            |
                            InputType.TYPE_TEXT_VARIATION_PASSWORD
            );


            btnMostrarSenha.setAlpha(
                    0.55f
            );
        }


        edtSenha.setTypeface(
                Typeface.DEFAULT
        );


        edtSenha.setSelection(
                edtSenha
                        .getText()
                        .length()
        );
    }


    // =====================================================
    // LOGIN
    // =====================================================

    private void realizarLogin() {

        String email =
                edtEmail
                        .getText()
                        .toString()
                        .trim()
                        .toLowerCase();


        String senha =
                edtSenha
                        .getText()
                        .toString()
                        .trim();


        if (email.isEmpty()) {

            edtEmail.setError(
                    "Informe seu e-mail"
            );


            edtEmail.requestFocus();

            return;
        }


        if (senha.isEmpty()) {

            edtSenha.setError(
                    "Informe sua senha"
            );


            edtSenha.requestFocus();

            return;
        }


        if (!senha.equals("12345")) {

            mostrarErroLogin();

            return;
        }


        if (
                email.equals(
                        "cliente@gmail.com"
                )
        ) {

            abrirTelaCliente();

            return;
        }


        if (
                email.equals(
                        "ambulante@gmail.com"
                )
        ) {

            abrirTelaAmbulante();

            return;
        }


        if (
                email.equals(
                        "empresa@gmail.com"
                )
        ) {

            abrirTelaEmpresa();

            return;
        }


        mostrarErroLogin();
    }


    // =====================================================
    // CLIENTE
    // =====================================================

    private void abrirTelaCliente() {

        salvarTipoUsuario(
                "cliente"
        );


        Toast.makeText(
                this,
                "Login realizado como Cliente",
                Toast.LENGTH_SHORT
        ).show();


        Intent intent =
                new Intent(
                        MainActivity.this,
                        tela_inicial_cliente.class
                );


        startActivity(
                intent
        );
    }


    // =====================================================
    // AMBULANTE
    // =====================================================

    private void abrirTelaAmbulante() {

        salvarTipoUsuario(
                "ambulante"
        );


        Toast.makeText(
                this,
                "Login realizado como Ambulante",
                Toast.LENGTH_SHORT
        ).show();


        Intent intent =
                new Intent(
                        MainActivity.this,
                        tela_inicial_ambulante.class
                );


        startActivity(
                intent
        );
    }


    // =====================================================
    // EMPRESA
    // =====================================================

    private void abrirTelaEmpresa() {

        salvarTipoUsuario(
                "empresa"
        );


        Toast.makeText(
                this,
                "Login realizado como Empresa",
                Toast.LENGTH_SHORT
        ).show();


        Intent intent =
                new Intent(
                        MainActivity.this,
                        tela_inicial_empresa.class
                );


        startActivity(
                intent
        );
    }


    // =====================================================
    // PERFIL ATUAL
    // =====================================================

    private void salvarTipoUsuario(
            String tipo
    ) {

        SharedPreferences preferencias =
                getSharedPreferences(
                        "coletai",
                        MODE_PRIVATE
                );


        preferencias
                .edit()

                .putString(
                        "usuario_tipo_atual",
                        tipo
                )

                .apply();
    }


    private void mostrarErroLogin() {

        Toast.makeText(
                this,
                "E-mail ou senha incorretos",
                Toast.LENGTH_SHORT
        ).show();
    }
}