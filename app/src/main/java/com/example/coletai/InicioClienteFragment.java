package com.example.coletai;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;

public class InicioClienteFragment extends Fragment {

    private View rootView;

    private Button btnFazerPedido;

    private LinearLayout painelCliente;
    private LinearLayout layoutStatusPedido;

    private MaterialCardView cardAguardandoPropostas;
    private MaterialCardView cardNovaProposta;
    private MaterialCardView cardEmColeta;

    private TextView txtPergunta;

    private TextView txtTipoAguardando;

    private TextView txtTipoProposta;
    private TextView txtAvatarColetor;
    private TextView txtNomeColetor;
    private TextView txtAvaliacaoColetor;
    private TextView txtPrecoProposta;
    private TextView txtMensagemProposta;

    private TextView txtColetorCaminho;
    private TextView txtTipoEmColeta;

    private TextView btnCancelarPedido;

    private View indicadorNotificacao;

    private String tipoResiduoSelecionado = null;
    private String localizacaoSelecionada = "atual";


    public InicioClienteFragment() {

        super(
                R.layout.fragment_inicio_cliente
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

        rootView = view;

        iniciarComponentes(view);

        esconderMenuAntigo(view);

        configurarEventos();

        atualizarEstadoPedido();
    }


    @Override
    public void onResume() {

        super.onResume();

        if (rootView != null) {

            atualizarEstadoPedido();
        }
    }


    // =====================================================
    // COMPONENTES
    // =====================================================

    private void iniciarComponentes(
            @NonNull View view
    ) {

        btnFazerPedido =
                view.findViewById(
                        R.id.btnFazerPedido
                );

        painelCliente =
                view.findViewById(
                        R.id.painelCliente
                );

        layoutStatusPedido =
                view.findViewById(
                        R.id.layoutStatusPedido
                );

        cardAguardandoPropostas =
                view.findViewById(
                        R.id.cardAguardandoPropostas
                );

        cardNovaProposta =
                view.findViewById(
                        R.id.cardNovaProposta
                );

        cardEmColeta =
                view.findViewById(
                        R.id.cardEmColeta
                );

        txtPergunta =
                view.findViewById(
                        R.id.txtPergunta
                );

        txtTipoAguardando =
                view.findViewById(
                        R.id.txtTipoAguardando
                );

        txtTipoProposta =
                view.findViewById(
                        R.id.txtTipoProposta
                );

        txtAvatarColetor =
                view.findViewById(
                        R.id.txtAvatarColetor
                );

        txtNomeColetor =
                view.findViewById(
                        R.id.txtNomeColetor
                );

        txtAvaliacaoColetor =
                view.findViewById(
                        R.id.txtAvaliacaoColetor
                );

        txtPrecoProposta =
                view.findViewById(
                        R.id.txtPrecoProposta
                );

        txtMensagemProposta =
                view.findViewById(
                        R.id.txtMensagemProposta
                );

        txtColetorCaminho =
                view.findViewById(
                        R.id.txtColetorCaminho
                );

        txtTipoEmColeta =
                view.findViewById(
                        R.id.txtTipoEmColeta
                );

        btnCancelarPedido =
                view.findViewById(
                        R.id.btnCancelarPedido
                );

        indicadorNotificacao =
                view.findViewById(
                        R.id.indicadorNotificacao
                );
    }


    // =====================================================
    // MENU ANTIGO
    // =====================================================

    private void esconderMenuAntigo(
            @NonNull View view
    ) {

        View menuAntigo =
                view.findViewById(
                        R.id.navegacaoInferior
                );

        if (menuAntigo != null) {

            menuAntigo.setVisibility(
                    View.GONE
            );
        }
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnFazerPedido.setOnClickListener(
                view -> abrirFormularioPedido()
        );


        rootView.findViewById(
                R.id.btnRecusarProposta
        ).setOnClickListener(
                view -> recusarProposta()
        );


        rootView.findViewById(
                R.id.btnAceitarProposta
        ).setOnClickListener(
                view -> aceitarProposta()
        );


        btnCancelarPedido.setOnClickListener(
                view -> cancelarPedido()
        );
    }


    // =====================================================
    // ATUALIZAR ESTADO DA HOME
    // =====================================================

    private void atualizarEstadoPedido() {

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


        esconderTodosOsEstados();


        if (
                status == null
                        || status.isEmpty()
        ) {

            mostrarEstadoSemPedido();

            return;
        }


        switch (status) {

            case "waiting":

                mostrarEstadoAguardando(
                        preferencias
                );

                break;


            case "proposal":

                mostrarEstadoProposta(
                        preferencias
                );

                break;


            case "active":

                mostrarEstadoEmColeta(
                        preferencias
                );

                break;


            default:

                mostrarEstadoSemPedido();

                break;
        }
    }


    // =====================================================
    // ESCONDER ESTADOS
    // =====================================================

    private void esconderTodosOsEstados() {

        layoutStatusPedido.setVisibility(
                View.GONE
        );

        cardAguardandoPropostas.setVisibility(
                View.GONE
        );

        cardNovaProposta.setVisibility(
                View.GONE
        );

        cardEmColeta.setVisibility(
                View.GONE
        );

        btnCancelarPedido.setVisibility(
                View.GONE
        );
    }


    // =====================================================
    // SEM PEDIDO
    // =====================================================

    private void mostrarEstadoSemPedido() {

        indicadorNotificacao.setVisibility(
                View.GONE
        );

        txtPergunta.setText(
                "O que deseja descartar hoje?"
        );

        btnFazerPedido.setText(
                "Fazer pedido"
        );

        alterarAlturaPainel(
                238
        );
    }


    // =====================================================
    // AGUARDANDO PROPOSTAS
    // =====================================================

    private void mostrarEstadoAguardando(
            SharedPreferences preferencias
    ) {

        layoutStatusPedido.setVisibility(
                View.VISIBLE
        );

        cardAguardandoPropostas.setVisibility(
                View.VISIBLE
        );

        btnCancelarPedido.setVisibility(
                View.VISIBLE
        );

        indicadorNotificacao.setVisibility(
                View.VISIBLE
        );


        String tipo =
                preferencias.getString(
                        "pedido_tipo",
                        "Pedido"
                );


        txtTipoAguardando.setText(
                tipo
        );


        txtPergunta.setText(
                "Deseja fazer outro pedido?"
        );

        btnFazerPedido.setText(
                "Fazer pedido"
        );


        alterarAlturaPainel(
                330
        );
    }


    // =====================================================
    // NOVA PROPOSTA
    // =====================================================

    private void mostrarEstadoProposta(
            SharedPreferences preferencias
    ) {

        layoutStatusPedido.setVisibility(
                View.VISIBLE
        );

        cardNovaProposta.setVisibility(
                View.VISIBLE
        );

        btnCancelarPedido.setVisibility(
                View.VISIBLE
        );

        indicadorNotificacao.setVisibility(
                View.VISIBLE
        );


        String tipo =
                preferencias.getString(
                        "pedido_tipo",
                        "Pedido"
                );


        String nome =
                preferencias.getString(
                        "proposta_coletor_nome",
                        "João Santos"
                );


        float avaliacao =
                preferencias.getFloat(
                        "proposta_coletor_avaliacao",
                        4.9f
                );


        int coletas =
                preferencias.getInt(
                        "proposta_coletor_coletas",
                        287
                );


        String preco =
                preferencias.getString(
                        "proposta_preco",
                        "0,00"
                );


        String mensagem =
                preferencias.getString(
                        "proposta_mensagem",
                        ""
                );


        txtTipoProposta.setText(
                tipo
        );


        txtNomeColetor.setText(
                nome
        );


        txtAvatarColetor.setText(
                obterIniciais(nome)
        );


        txtAvaliacaoColetor.setText(
                "★ "
                        + String.format(
                        java.util.Locale.US,
                        "%.1f",
                        avaliacao
                )
                        + " · "
                        + coletas
                        + " coletas"
        );


        txtPrecoProposta.setText(
                "R$ " + preco
        );


        if (
                mensagem == null
                        || mensagem.trim().isEmpty()
        ) {

            txtMensagemProposta.setVisibility(
                    View.GONE
            );

        } else {

            txtMensagemProposta.setVisibility(
                    View.VISIBLE
            );

            txtMensagemProposta.setText(
                    "\"" + mensagem + "\""
            );
        }


        txtPergunta.setText(
                "Deseja fazer outro pedido?"
        );


        btnFazerPedido.setText(
                "Fazer pedido"
        );


        alterarAlturaPainel(
                430
        );
    }


    // =====================================================
    // EM COLETA
    // =====================================================

    private void mostrarEstadoEmColeta(
            SharedPreferences preferencias
    ) {

        layoutStatusPedido.setVisibility(
                View.VISIBLE
        );

        cardEmColeta.setVisibility(
                View.VISIBLE
        );

        btnCancelarPedido.setVisibility(
                View.VISIBLE
        );

        indicadorNotificacao.setVisibility(
                View.VISIBLE
        );


        String tipo =
                preferencias.getString(
                        "pedido_tipo",
                        "Pedido"
                );


        String nomeColetor =
                preferencias.getString(
                        "proposta_coletor_nome",
                        "João Santos"
                );


        txtColetorCaminho.setText(
                nomeColetor
                        + " está a caminho"
        );


        txtTipoEmColeta.setText(
                tipo
        );


        txtPergunta.setText(
                "Deseja fazer outro pedido?"
        );


        btnFazerPedido.setText(
                "Fazer pedido"
        );


        alterarAlturaPainel(
                330
        );
    }


    // =====================================================
    // ACEITAR PROPOSTA
    // =====================================================

    private void aceitarProposta() {

        SharedPreferences preferencias =
                requireContext()
                        .getSharedPreferences(
                                "coletai",
                                Context.MODE_PRIVATE
                        );


        preferencias
                .edit()
                .putString(
                        "pedido_status",
                        "active"
                )
                .apply();


        Toast.makeText(
                requireContext(),
                "Proposta aceita!",
                Toast.LENGTH_SHORT
        ).show();


        atualizarEstadoPedido();
    }


    // =====================================================
    // RECUSAR PROPOSTA
    // =====================================================

    private void recusarProposta() {

        SharedPreferences preferencias =
                requireContext()
                        .getSharedPreferences(
                                "coletai",
                                Context.MODE_PRIVATE
                        );


        SharedPreferences.Editor editor =
                preferencias.edit();


        editor.putString(
                "pedido_status",
                "waiting"
        );


        editor.remove(
                "proposta_preco"
        );

        editor.remove(
                "proposta_mensagem"
        );

        editor.remove(
                "proposta_coletor_nome"
        );

        editor.remove(
                "proposta_coletor_avaliacao"
        );

        editor.remove(
                "proposta_coletor_coletas"
        );


        editor.apply();


        Toast.makeText(
                requireContext(),
                "Proposta recusada",
                Toast.LENGTH_SHORT
        ).show();


        atualizarEstadoPedido();
    }


    // =====================================================
    // CANCELAR PEDIDO
    // =====================================================

    private void cancelarPedido() {

        SharedPreferences preferencias =
                requireContext()
                        .getSharedPreferences(
                                "coletai",
                                Context.MODE_PRIVATE
                        );


        SharedPreferences.Editor editor =
                preferencias.edit();


        editor.remove("pedido_status");
        editor.remove("pedido_tipo");
        editor.remove("pedido_emoji");
        editor.remove("pedido_descricao");
        editor.remove("pedido_peso");
        editor.remove("pedido_regiao");
        editor.remove("pedido_cidade");
        editor.remove("pedido_observacao");
        editor.remove("pedido_endereco_completo");

        editor.remove("proposta_preco");
        editor.remove("proposta_mensagem");
        editor.remove("proposta_coletor_nome");
        editor.remove("proposta_coletor_avaliacao");
        editor.remove("proposta_coletor_coletas");


        editor.apply();


        Toast.makeText(
                requireContext(),
                "Pedido cancelado",
                Toast.LENGTH_SHORT
        ).show();


        atualizarEstadoPedido();
    }


    // =====================================================
    // ALTERAR ALTURA DO PAINEL
    // =====================================================

    private void alterarAlturaPainel(
            int alturaDp
    ) {

        ViewGroup.LayoutParams parametros =
                painelCliente.getLayoutParams();


        parametros.height =
                dpParaPx(
                        alturaDp
                );


        painelCliente.setLayoutParams(
                parametros
        );
    }


    private int dpParaPx(
            int dp
    ) {

        float densidade =
                getResources()
                        .getDisplayMetrics()
                        .density;


        return Math.round(
                dp * densidade
        );
    }


    // =====================================================
    // FORMULÁRIO DO PEDIDO
    // =====================================================

    private void abrirFormularioPedido() {

        tipoResiduoSelecionado = null;

        localizacaoSelecionada =
                "atual";


        BottomSheetDialog dialog =
                new BottomSheetDialog(
                        requireContext()
                );


        dialog.setContentView(
                R.layout.bottom_sheet_fazer_pedido
        );


        configurarTamanhoBottomSheet(
                dialog
        );


        configurarFormularioPedido(
                dialog
        );


        dialog.show();
    }


    private void configurarTamanhoBottomSheet(
            BottomSheetDialog dialog
    ) {

        dialog.setOnShowListener(
                evento -> {

                    FrameLayout painel =
                            dialog.findViewById(
                                    com.google.android.material.R.id.design_bottom_sheet
                            );


                    if (painel == null) {

                        return;
                    }


                    int alturaTela =
                            getResources()
                                    .getDisplayMetrics()
                                    .heightPixels;


                    int alturaPainel =
                            (int) (
                                    alturaTela * 0.88
                            );


                    painel.getLayoutParams().height =
                            alturaPainel;


                    painel.requestLayout();


                    painel.setBackgroundColor(
                            Color.TRANSPARENT
                    );


                    BottomSheetBehavior<FrameLayout> comportamento =
                            BottomSheetBehavior.from(
                                    painel
                            );


                    comportamento.setState(
                            BottomSheetBehavior.STATE_EXPANDED
                    );


                    comportamento.setSkipCollapsed(
                            true
                    );
                }
        );
    }


    private void configurarFormularioPedido(
            BottomSheetDialog dialog
    ) {

        TextView btnFechar =
                dialog.findViewById(
                        R.id.btnFecharPedido
                );


        EditText edtDescricao =
                dialog.findViewById(
                        R.id.edtDescricaoPedido
                );


        EditText edtPeso =
                dialog.findViewById(
                        R.id.edtPesoPedido
                );


        EditText edtObservacoes =
                dialog.findViewById(
                        R.id.edtObservacoesPedido
                );


        EditText edtOutroEndereco =
                dialog.findViewById(
                        R.id.edtOutroEndereco
                );


        Spinner spinnerEndereco =
                dialog.findViewById(
                        R.id.spinnerEnderecoSalvo
                );


        Button btnEnviar =
                dialog.findViewById(
                        R.id.btnEnviarPedido
                );


        if (btnFechar != null) {

            btnFechar.setOnClickListener(
                    view -> dialog.dismiss()
            );
        }


        configurarTiposDeResiduo(
                dialog
        );


        configurarFotos(
                dialog
        );


        configurarEnderecos(
                dialog,
                spinnerEndereco,
                edtOutroEndereco
        );


        if (btnEnviar != null) {

            btnEnviar.setOnClickListener(
                    view -> validarEEnviarPedido(
                            dialog,
                            edtDescricao,
                            edtPeso,
                            edtObservacoes,
                            edtOutroEndereco,
                            spinnerEndereco
                    )
            );
        }
    }


    // =====================================================
    // TIPOS DE RESÍDUO
    // =====================================================

    private void configurarTiposDeResiduo(
            BottomSheetDialog dialog
    ) {

        Button btnPilhas =
                dialog.findViewById(
                        R.id.btnPilhas
                );


        Button btnBaterias =
                dialog.findViewById(
                        R.id.btnBaterias
                );


        Button btnEletronicos =
                dialog.findViewById(
                        R.id.btnEletronicos
                );


        Button btnMedicamentos =
                dialog.findViewById(
                        R.id.btnMedicamentos
                );


        Button btnAgulhas =
                dialog.findViewById(
                        R.id.btnAgulhas
                );


        Button btnOleo =
                dialog.findViewById(
                        R.id.btnOleo
                );


        Button btnOutros =
                dialog.findViewById(
                        R.id.btnOutros
                );


        Button[] botoes = {

                btnPilhas,
                btnBaterias,
                btnEletronicos,
                btnMedicamentos,
                btnAgulhas,
                btnOleo,
                btnOutros
        };


        configurarBotaoResiduo(
                btnPilhas,
                "Pilhas",
                botoes
        );


        configurarBotaoResiduo(
                btnBaterias,
                "Baterias",
                botoes
        );


        configurarBotaoResiduo(
                btnEletronicos,
                "Eletrônicos",
                botoes
        );


        configurarBotaoResiduo(
                btnMedicamentos,
                "Medicamentos",
                botoes
        );


        configurarBotaoResiduo(
                btnAgulhas,
                "Agulhas",
                botoes
        );


        configurarBotaoResiduo(
                btnOleo,
                "Óleo",
                botoes
        );


        configurarBotaoResiduo(
                btnOutros,
                "Outros",
                botoes
        );
    }


    private void configurarBotaoResiduo(
            Button botao,
            String tipo,
            Button[] todosOsBotoes
    ) {

        if (botao == null) {

            return;
        }


        botao.setOnClickListener(
                view -> {

                    tipoResiduoSelecionado =
                            tipo;


                    for (Button item : todosOsBotoes) {

                        if (item != null) {

                            item.setSelected(
                                    false
                            );


                            item.setTextColor(
                                    ContextCompat.getColor(
                                            requireContext(),
                                            R.color.ink_700
                                    )
                            );
                        }
                    }


                    botao.setSelected(
                            true
                    );


                    botao.setTextColor(
                            Color.WHITE
                    );
                }
        );
    }


    // =====================================================
    // FOTOS
    // =====================================================

    private void configurarFotos(
            BottomSheetDialog dialog
    ) {

        configurarCampoFoto(
                dialog,
                R.id.fotoPedido1,
                R.id.imgFotoPedido1,
                R.id.txtFotoPedido1,
                "Foto 1"
        );


        configurarCampoFoto(
                dialog,
                R.id.fotoPedido2,
                R.id.imgFotoPedido2,
                R.id.txtFotoPedido2,
                "Foto 2"
        );


        configurarCampoFoto(
                dialog,
                R.id.fotoPedido3,
                R.id.imgFotoPedido3,
                R.id.txtFotoPedido3,
                "Foto 3"
        );
    }


    private void configurarCampoFoto(
            BottomSheetDialog dialog,
            int idCampo,
            int idImagem,
            int idTexto,
            String textoFoto
    ) {

        LinearLayout campo =
                dialog.findViewById(
                        idCampo
                );


        ImageView imagem =
                dialog.findViewById(
                        idImagem
                );


        TextView texto =
                dialog.findViewById(
                        idTexto
                );


        if (
                campo == null
                        || imagem == null
                        || texto == null
        ) {

            return;
        }


        campo.setOnClickListener(
                view -> {

                    boolean selecionado =
                            !campo.isSelected();


                    campo.setSelected(
                            selecionado
                    );


                    if (selecionado) {

                        imagem.setImageResource(
                                R.drawable.ic_check
                        );


                        imagem.setImageTintList(
                                ColorStateList.valueOf(
                                        ContextCompat.getColor(
                                                requireContext(),
                                                R.color.brand_700
                                        )
                                )
                        );


                        texto.setText(
                                textoFoto
                        );


                        texto.setTextColor(
                                ContextCompat.getColor(
                                        requireContext(),
                                        R.color.brand_700
                                )
                        );

                    } else {

                        imagem.setImageResource(
                                R.drawable.ic_camera
                        );


                        imagem.setImageTintList(
                                ColorStateList.valueOf(
                                        ContextCompat.getColor(
                                                requireContext(),
                                                R.color.ink_400
                                        )
                                )
                        );


                        texto.setText(
                                "Adicionar"
                        );


                        texto.setTextColor(
                                ContextCompat.getColor(
                                        requireContext(),
                                        R.color.ink_400
                                )
                        );
                    }
                }
        );
    }


    // =====================================================
    // ENDEREÇOS
    // =====================================================

    private void configurarEnderecos(
            BottomSheetDialog dialog,
            Spinner spinnerEndereco,
            EditText edtOutroEndereco
    ) {

        LinearLayout opcaoAtual =
                dialog.findViewById(
                        R.id.opcaoLocalizacaoAtual
                );


        LinearLayout opcaoSalvo =
                dialog.findViewById(
                        R.id.opcaoEnderecoSalvo
                );


        LinearLayout opcaoOutro =
                dialog.findViewById(
                        R.id.opcaoOutroEndereco
                );


        ImageView checkAtual =
                dialog.findViewById(
                        R.id.checkLocalizacaoAtual
                );


        ImageView checkSalvo =
                dialog.findViewById(
                        R.id.checkEnderecoSalvo
                );


        ImageView checkOutro =
                dialog.findViewById(
                        R.id.checkOutroEndereco
                );


        if (spinnerEndereco != null) {

            String[] enderecos = {

                    "Selecionar endereço",
                    "Casa — Rua das Flores, 234",
                    "Trabalho — Av. Paulista, 1578"
            };


            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_spinner_item,
                            enderecos
                    );


            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );


            spinnerEndereco.setAdapter(
                    adapter
            );
        }


        if (opcaoAtual != null) {

            opcaoAtual.setOnClickListener(
                    view -> selecionarLocalizacao(
                            "atual",
                            opcaoAtual,
                            opcaoSalvo,
                            opcaoOutro,
                            checkAtual,
                            checkSalvo,
                            checkOutro,
                            spinnerEndereco,
                            edtOutroEndereco
                    )
            );
        }


        if (opcaoSalvo != null) {

            opcaoSalvo.setOnClickListener(
                    view -> selecionarLocalizacao(
                            "salvo",
                            opcaoAtual,
                            opcaoSalvo,
                            opcaoOutro,
                            checkAtual,
                            checkSalvo,
                            checkOutro,
                            spinnerEndereco,
                            edtOutroEndereco
                    )
            );
        }


        if (opcaoOutro != null) {

            opcaoOutro.setOnClickListener(
                    view -> selecionarLocalizacao(
                            "outro",
                            opcaoAtual,
                            opcaoSalvo,
                            opcaoOutro,
                            checkAtual,
                            checkSalvo,
                            checkOutro,
                            spinnerEndereco,
                            edtOutroEndereco
                    )
            );
        }
    }


    private void selecionarLocalizacao(
            String tipo,
            LinearLayout opcaoAtual,
            LinearLayout opcaoSalvo,
            LinearLayout opcaoOutro,
            ImageView checkAtual,
            ImageView checkSalvo,
            ImageView checkOutro,
            Spinner spinnerEndereco,
            EditText edtOutroEndereco
    ) {

        localizacaoSelecionada =
                tipo;


        opcaoAtual.setBackgroundResource(
                tipo.equals("atual")
                        ? R.drawable.bg_location_selected
                        : R.drawable.bg_location_option
        );


        opcaoSalvo.setBackgroundResource(
                tipo.equals("salvo")
                        ? R.drawable.bg_location_selected
                        : R.drawable.bg_location_option
        );


        opcaoOutro.setBackgroundResource(
                tipo.equals("outro")
                        ? R.drawable.bg_location_selected
                        : R.drawable.bg_location_option
        );


        checkAtual.setVisibility(
                tipo.equals("atual")
                        ? View.VISIBLE
                        : View.INVISIBLE
        );


        checkSalvo.setVisibility(
                tipo.equals("salvo")
                        ? View.VISIBLE
                        : View.INVISIBLE
        );


        checkOutro.setVisibility(
                tipo.equals("outro")
                        ? View.VISIBLE
                        : View.INVISIBLE
        );


        spinnerEndereco.setVisibility(
                tipo.equals("salvo")
                        ? View.VISIBLE
                        : View.GONE
        );


        edtOutroEndereco.setVisibility(
                tipo.equals("outro")
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    // =====================================================
    // VALIDAR PEDIDO
    // =====================================================

    private void validarEEnviarPedido(
            BottomSheetDialog dialog,
            EditText edtDescricao,
            EditText edtPeso,
            EditText edtObservacoes,
            EditText edtOutroEndereco,
            Spinner spinnerEndereco
    ) {

        if (tipoResiduoSelecionado == null) {

            Toast.makeText(
                    requireContext(),
                    "Selecione o tipo de resíduo.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        String descricao =
                edtDescricao
                        .getText()
                        .toString()
                        .trim();


        String peso =
                edtPeso
                        .getText()
                        .toString()
                        .trim();


        String observacao =
                edtObservacoes
                        .getText()
                        .toString()
                        .trim();


        if (descricao.isEmpty()) {

            edtDescricao.setError(
                    "Descreva os itens que serão descartados"
            );

            edtDescricao.requestFocus();

            return;
        }


        if (peso.isEmpty()) {

            edtPeso.setError(
                    "Informe o peso estimado"
            );

            edtPeso.requestFocus();

            return;
        }


        if (
                localizacaoSelecionada.equals(
                        "salvo"
                )
                        && spinnerEndereco.getSelectedItemPosition() == 0
        ) {

            Toast.makeText(
                    requireContext(),
                    "Selecione um endereço salvo.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (
                localizacaoSelecionada.equals(
                        "outro"
                )
                        && edtOutroEndereco
                        .getText()
                        .toString()
                        .trim()
                        .isEmpty()
        ) {

            edtOutroEndereco.setError(
                    "Informe o endereço para coleta"
            );

            edtOutroEndereco.requestFocus();

            return;
        }


        String enderecoCompleto =
                obterEnderecoCompleto(
                        edtOutroEndereco,
                        spinnerEndereco
                );


        enviarPedido(
                dialog,
                descricao,
                peso,
                observacao,
                enderecoCompleto
        );
    }


    // =====================================================
    // ENVIAR PEDIDO
    // =====================================================

    private void enviarPedido(
            BottomSheetDialog dialog,
            String descricao,
            String peso,
            String observacao,
            String enderecoCompleto
    ) {

        SharedPreferences preferencias =
                requireContext()
                        .getSharedPreferences(
                                "coletai",
                                Context.MODE_PRIVATE
                        );


        SharedPreferences.Editor editor =
                preferencias.edit();


        // STATUS

        editor.putString(
                "pedido_status",
                "waiting"
        );


        // PEDIDO

        editor.putString(
                "pedido_tipo",
                tipoResiduoSelecionado
        );


        editor.putString(
                "pedido_emoji",
                obterEmoji(
                        tipoResiduoSelecionado
                )
        );


        editor.putString(
                "pedido_descricao",
                descricao
        );


        editor.putString(
                "pedido_peso",
                peso + " kg"
        );


        editor.putString(
                "pedido_regiao",
                "Consolação"
        );


        editor.putString(
                "pedido_cidade",
                "São Paulo"
        );


        editor.putString(
                "pedido_observacao",
                observacao
        );


        editor.putString(
                "pedido_endereco_completo",
                enderecoCompleto
        );


        // LIMPAR PROPOSTA ANTIGA

        editor.remove(
                "proposta_preco"
        );


        editor.remove(
                "proposta_mensagem"
        );


        editor.remove(
                "proposta_coletor_nome"
        );


        editor.remove(
                "proposta_coletor_avaliacao"
        );


        editor.remove(
                "proposta_coletor_coletas"
        );


        editor.apply();


        dialog.dismiss();


        Toast.makeText(
                requireContext(),
                "Pedido enviado com sucesso!",
                Toast.LENGTH_LONG
        ).show();


        atualizarEstadoPedido();
    }


    // =====================================================
    // ENDEREÇO COMPLETO
    // =====================================================

    private String obterEnderecoCompleto(
            EditText edtOutroEndereco,
            Spinner spinnerEndereco
    ) {

        if (
                localizacaoSelecionada.equals(
                        "salvo"
                )
        ) {

            Object endereco =
                    spinnerEndereco
                            .getSelectedItem();


            if (endereco != null) {

                return endereco.toString();
            }
        }


        if (
                localizacaoSelecionada.equals(
                        "outro"
                )
        ) {

            return edtOutroEndereco
                    .getText()
                    .toString()
                    .trim();
        }


        return "Localização atual do cliente";
    }


    // =====================================================
    // EMOJI
    // =====================================================

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


    // =====================================================
    // INICIAIS DO COLETOR
    // =====================================================

    private String obterIniciais(
            String nome
    ) {

        if (
                nome == null
                        || nome.trim().isEmpty()
        ) {

            return "JS";
        }


        String[] partes =
                nome.trim().split(
                        "\\s+"
                );


        if (partes.length == 1) {

            return partes[0]
                    .substring(
                            0,
                            1
                    )
                    .toUpperCase();
        }


        String primeira =
                partes[0]
                        .substring(
                                0,
                                1
                        );


        String ultima =
                partes[
                        partes.length - 1
                        ]
                        .substring(
                                0,
                                1
                        );


        return (
                primeira
                        + ultima
        ).toUpperCase();
    }
}