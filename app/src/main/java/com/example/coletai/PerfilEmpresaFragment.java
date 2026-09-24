package com.example.coletai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PerfilEmpresaFragment extends Fragment {

    private View layoutPerfil;
    private View layoutEdicao;

    private TextView txtAvatar;
    private TextView txtNomePrincipal;

    private TextView txtNome;
    private TextView txtRazaoSocial;
    private TextView txtNomeFantasia;
    private TextView txtTelefone;

    private EditText edtNome;
    private EditText edtTelefone;
    private EditText edtNomeFantasia;

    private TextView btnFecharEdicao;
    private TextView btnSalvarPerfil;

    private SharedPreferences preferencias;


    public PerfilEmpresaFragment() {

        super(
                R.layout.fragment_perfil_empresa
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


        preferencias =
                requireContext()
                        .getSharedPreferences(
                                "coletai",
                                Context.MODE_PRIVATE
                        );


        iniciarComponentes(
                view
        );


        carregarDados();


        configurarEventos(
                view
        );
    }


    private void iniciarComponentes(
            View view
    ) {

        layoutPerfil =
                view.findViewById(
                        R.id.layoutPerfil
                );

        layoutEdicao =
                view.findViewById(
                        R.id.layoutEdicao
                );


        txtAvatar =
                view.findViewById(
                        R.id.txtAvatar
                );

        txtNomePrincipal =
                view.findViewById(
                        R.id.txtNomePrincipal
                );

        txtNome =
                view.findViewById(
                        R.id.txtNome
                );

        txtRazaoSocial =
                view.findViewById(
                        R.id.txtRazaoSocial
                );

        txtNomeFantasia =
                view.findViewById(
                        R.id.txtNomeFantasia
                );

        txtTelefone =
                view.findViewById(
                        R.id.txtTelefone
                );


        edtNome =
                view.findViewById(
                        R.id.edtNome
                );

        edtTelefone =
                view.findViewById(
                        R.id.edtTelefone
                );

        edtNomeFantasia =
                view.findViewById(
                        R.id.edtNomeFantasia
                );


        btnFecharEdicao =
                view.findViewById(
                        R.id.btnFecharEdicao
                );

        btnSalvarPerfil =
                view.findViewById(
                        R.id.btnSalvarPerfil
                );
    }


    // =====================================================
    // DADOS
    // =====================================================

    private void carregarDados() {

        String nome =
                preferencias.getString(
                        "empresa_nome",
                        "EcoColeta"
                );


        String nomeFantasia =
                preferencias.getString(
                        "empresa_nome_fantasia",
                        "EcoColeta"
                );


        String razaoSocial =
                preferencias.getString(
                        "empresa_razao_social",
                        "EcoColeta Soluções Ambientais LTDA"
                );


        String telefone =
                preferencias.getString(
                        "empresa_telefone",
                        "(11) 3333-4444"
                );


        txtNomePrincipal.setText(
                nomeFantasia
        );


        txtNome.setText(
                nome
        );


        txtRazaoSocial.setText(
                razaoSocial
        );


        txtNomeFantasia.setText(
                nomeFantasia
        );


        txtTelefone.setText(
                telefone
        );


        txtAvatar.setText(
                obterIniciais(
                        nomeFantasia
                )
        );


        edtNome.setText(
                nome
        );


        edtTelefone.setText(
                telefone
        );


        edtNomeFantasia.setText(
                nomeFantasia
        );
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos(
            View view
    ) {

        view.findViewById(
                R.id.rowNome
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.rowCnpj
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.rowRazaoSocial
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.rowNomeFantasia
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.rowTelefone
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.rowEmail
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.rowSenha
        ).setOnClickListener(
                item -> abrirEdicao()
        );


        view.findViewById(
                R.id.btnFotoPerfil
        ).setOnClickListener(
                item -> Toast.makeText(
                        requireContext(),
                        "Alteração de foto será adicionada depois.",
                        Toast.LENGTH_SHORT
                ).show()
        );


        btnFecharEdicao.setOnClickListener(
                item -> fecharEdicao()
        );


        btnSalvarPerfil.setOnClickListener(
                item -> salvarPerfil()
        );


        view.findViewById(
                R.id.btnSair
        ).setOnClickListener(
                item -> sairDaConta()
        );
    }


    // =====================================================
    // EDIÇÃO
    // =====================================================

    private void abrirEdicao() {

        carregarDados();


        layoutPerfil.setVisibility(
                View.GONE
        );


        layoutEdicao.setVisibility(
                View.VISIBLE
        );
    }


    private void fecharEdicao() {

        layoutEdicao.setVisibility(
                View.GONE
        );


        layoutPerfil.setVisibility(
                View.VISIBLE
        );
    }


    private void salvarPerfil() {

        String nome =
                edtNome
                        .getText()
                        .toString()
                        .trim();


        String telefone =
                edtTelefone
                        .getText()
                        .toString()
                        .trim();


        String nomeFantasia =
                edtNomeFantasia
                        .getText()
                        .toString()
                        .trim();


        if (nome.isEmpty()) {

            edtNome.setError(
                    "Informe o nome"
            );

            edtNome.requestFocus();

            return;
        }


        if (telefone.isEmpty()) {

            edtTelefone.setError(
                    "Informe o telefone"
            );

            edtTelefone.requestFocus();

            return;
        }


        if (nomeFantasia.isEmpty()) {

            edtNomeFantasia.setError(
                    "Informe o nome fantasia"
            );

            edtNomeFantasia.requestFocus();

            return;
        }


        preferencias
                .edit()

                .putString(
                        "empresa_nome",
                        nome
                )

                .putString(
                        "empresa_telefone",
                        telefone
                )

                .putString(
                        "empresa_nome_fantasia",
                        nomeFantasia
                )

                .apply();


        carregarDados();


        Toast.makeText(
                requireContext(),
                "Perfil atualizado",
                Toast.LENGTH_SHORT
        ).show();


        fecharEdicao();
    }


    // =====================================================
    // LOGOUT
    // =====================================================

    private void sairDaConta() {

        Intent intent =
                new Intent(
                        requireContext(),
                        MainActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        requireActivity().finish();
    }


    private String obterIniciais(
            String nome
    ) {

        if (
                nome == null
                        || nome.trim().isEmpty()
        ) {

            return "EC";
        }


        String[] partes =
                nome
                        .trim()
                        .split(
                                "\\s+"
                        );


        if (partes.length == 1) {

            if (partes[0].length() >= 2) {

                return partes[0]
                        .substring(
                                0,
                                2
                        )
                        .toUpperCase();
            }


            return partes[0]
                    .substring(
                            0,
                            1
                    )
                    .toUpperCase();
        }


        return (
                partes[0].substring(0, 1)
                        +
                        partes[partes.length - 1]
                                .substring(0, 1)
        ).toUpperCase();
    }
}