package com.example.coletai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class enviar_proposta_ambulante extends PedidoActivity {
    @Override protected boolean aceitaStatus(String status) { return "waiting".equals(status); }

    private TextView btnVoltar;

    private TextView txtEmoji;
    private TextView txtResumoPedido;
    private TextView txtLocalizacao;

    private EditText edtPreco;
    private EditText edtMensagem;

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
                R.layout.activity_enviar_proposta_ambulante
        );

        configurarBarras();

        iniciarComponentes();

        receberDados();

        configurarEventos();
    }

    // =====================================================
    // BARRAS
    // =====================================================

    private void configurarBarras() {

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

        btnVoltar =
                findViewById(
                        R.id.btnVoltar
                );

        txtEmoji =
                findViewById(
                        R.id.txtEmoji
                );

        txtResumoPedido =
                findViewById(
                        R.id.txtResumoPedido
                );

        txtLocalizacao =
                findViewById(
                        R.id.txtLocalizacao
                );

        edtPreco =
                findViewById(
                        R.id.edtPreco
                );

        edtMensagem =
                findViewById(
                        R.id.edtMensagem
                );
    }

    // =====================================================
    // DADOS DO PEDIDO
    // =====================================================

    private void receberDados() {

        tipo =
                getIntent().getStringExtra(
                        "tipo"
                );

        emoji =
                getIntent().getStringExtra(
                        "emoji"
                );

        descricao =
                getIntent().getStringExtra(
                        "descricao"
                );

        peso =
                getIntent().getStringExtra(
                        "peso"
                );

        regiao =
                getIntent().getStringExtra(
                        "regiao"
                );

        cidade =
                getIntent().getStringExtra(
                        "cidade"
                );

        observacao =
                getIntent().getStringExtra(
                        "observacao"
                );

        if (emoji != null) {

            txtEmoji.setText(
                    emoji
            );
        }

        if (
                tipo != null
                        && peso != null
        ) {

            txtResumoPedido.setText(
                    tipo
                            + " · "
                            + peso
            );
        }

        if (
                regiao != null
                        && cidade != null
        ) {

            txtLocalizacao.setText(getSharedPreferences("coletai", MODE_PRIVATE).getString("pedido_endereco_completo", ""));
        }
    }

    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnVoltar.setOnClickListener(
                view -> finish()
        );

        findViewById(
                R.id.btnEnviarProposta
        ).setOnClickListener(
                view -> enviarProposta()
        );
    }

    // =====================================================
    // ENVIAR PROPOSTA
    // =====================================================

    private void enviarProposta() {

        String preco =
                edtPreco
                        .getText()
                        .toString()
                        .trim();

        String mensagem =
                edtMensagem
                        .getText()
                        .toString()
                        .trim();

        try {
            preco = DecimalValues.positive(preco, 2).setScale(2).toPlainString();
        } catch (IllegalArgumentException e) {
            edtPreco.setError("Informe um valor positivo com até 2 casas decimais");
            edtPreco.requestFocus();
            return;
        }
        if (preco.isEmpty()) {

            edtPreco.setError(
                    "Informe o valor da proposta"
            );

            edtPreco.requestFocus();

            return;
        }

        if (mensagem.isEmpty()) {

            mensagem =
                    "Posso ir em até 30 minutos. Fico no bairro!";
        }

        SharedPreferences preferencias =
                getSharedPreferences(
                        "coletai",
                        Context.MODE_PRIVATE
                );

        if (!verificarPedido()) return;

        String usuarioAtual =
                preferencias.getString(
                        "usuario_tipo_atual",
                        "ambulante"
                );

        String nomeColetor;

        float avaliacao;

        int totalColetas;

        String email;

        // =================================================
        // EMPRESA
        // =================================================

        if (
                "empresa".equals(
                        usuarioAtual
                )
        ) {

            nomeColetor =
                    preferencias.getString(
                            "empresa_nome_fantasia",
                            "EcoColeta"
                    );

            avaliacao =
                    4.8f;

            totalColetas =
                    1450;

            email =
                    preferencias.getString("empresa_email", "empresa@gmail.com");

        }

        // =================================================
        // AMBULANTE
        // =================================================

        else {

            usuarioAtual =
                    "ambulante";

            nomeColetor =
                    preferencias.getString(
                            "ambulante_nome",
                            "João Santos"
                    );

            avaliacao =
                    4.9f;

            totalColetas =
                    287;

            email =
                    preferencias.getString("ambulante_email", "ambulante@gmail.com");
        }

        SharedPreferences.Editor editor =
                preferencias.edit();

        // =================================================
        // STATUS DA PROPOSTA
        // =================================================

        editor.putString(
                "pedido_status",
                "proposal"
        );

        // =================================================
        // MUITO IMPORTANTE:
        // NOVA COLETA SEMPRE COMEÇA EM READY
        // =================================================

        editor.putString(
                "coleta_etapa",
                "ready"
        );

        // =================================================
        // DADOS DA PROPOSTA
        // =================================================

        editor.putString(
                "proposta_preco",
                preco
        );

        editor.putString(
                "proposta_mensagem",
                mensagem
        );

        editor.putString(
                "proposta_coletor_nome",
                nomeColetor
        );

        editor.putFloat(
                "proposta_coletor_avaliacao",
                avaliacao
        );

        editor.putInt(
                "proposta_coletor_coletas",
                totalColetas
        );

        editor.putString(
                "proposta_coletor_tipo",
                usuarioAtual
        );

        editor.putString(
                "proposta_coletor_email",
                email
        );

        // =================================================
        // MANTER DADOS DO PEDIDO
        // =================================================

        editor.putString(
                "pedido_tipo",
                tipo
        );

        editor.putString(
                "pedido_emoji",
                emoji
        );

        editor.putString(
                "pedido_descricao",
                descricao
        );

        editor.putString(
                "pedido_peso",
                peso
        );

        editor.putString(
                "pedido_regiao",
                regiao
        );

        editor.putString(
                "pedido_cidade",
                cidade
        );

        editor.putString(
                "pedido_observacao",
                observacao
        );

        editor.apply();

        Toast.makeText(
                this,
                "Proposta salva. Aguardando confirmação pelo Bluetooth.",
                Toast.LENGTH_SHORT
        ).show();

        // =================================================
        // VOLTAR PARA HOME CORRETA
        // =================================================

        Intent intent;

        if (
                "empresa".equals(
                        usuarioAtual
                )
        ) {

            intent =
                    new Intent(
                            enviar_proposta_ambulante.this,
                            tela_inicial_empresa.class
                    );

        } else {

            intent =
                    new Intent(
                            enviar_proposta_ambulante.this,
                            tela_inicial_ambulante.class
                    );
        }

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        startActivity(
                intent
        );

        finish();
    }
}
