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
    private Button btnBluetoothDemo;

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


        /*
         * Estamos na tela de Login.
         *
         * Portanto, ainda não existe um perfil ativo.
         *
         * Isso evita que o Bluetooth considere uma conta
         * antiga como ainda conectada.
         */
        limparUsuarioAtual();


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


    // =====================================================
    // USUÁRIO
    // =====================================================

    private void limparUsuarioAtual() {

        SharedPreferences preferencias =
                getSharedPreferences(
                        "coletai",
                        MODE_PRIVATE
                );


        preferencias
                .edit()
                .remove(
                        "usuario_tipo_atual"
                )
                .apply();
    }


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


    // =====================================================
    // COMPONENTES
    // =====================================================

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


        btnBluetoothDemo =
                findViewById(
                        R.id.btnBluetoothDemo
                );


        btnMostrarSenha =
                findViewById(
                        R.id.btnMostrarSenha
                );
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnMostrarSenha.setOnClickListener(
                view -> alternarVisibilidadeSenha()
        );


        btnEntrar.setOnClickListener(
                view -> realizarLogin()
        );


        btnBluetoothDemo.setOnClickListener(
                view -> abrirBluetooth()
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


    // =====================================================
    // BLUETOOTH
    // =====================================================

    private void abrirBluetooth() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        BluetoothActivity.class
                );


        startActivity(
                intent
        );
    }


    // =====================================================
    // CONTAS DEMONSTRAÇÃO
    // =====================================================

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
    // ERRO
    // =====================================================

    private void mostrarErroLogin() {

        Toast.makeText(
                this,
                "E-mail ou senha incorretos",
                Toast.LENGTH_SHORT
        ).show();
    }
}