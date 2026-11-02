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

public class PerfilAmbulanteFragment extends Fragment {

    private View layoutPerfil;
    private View layoutEdicao;

    private TextView txtAvatar;

    private TextView txtNomePrincipal;
    private TextView txtNome;
    private TextView txtTelefone;

    private EditText edtNome;
    private EditText edtTelefone;

    private TextView btnFecharEdicao;
    private TextView btnSalvarPerfil;

    private SharedPreferences preferencias;

    public PerfilAmbulanteFragment() {

        super(
                R.layout.fragment_perfil_ambulante
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

    @Override
    public void onResume() {

        super.onResume();

        if (
                preferencias != null
        ) {

            carregarDados();
        }
    }

    // =====================================================
    // COMPONENTES
    // =====================================================

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
        org.json.JSONObject profile = new ProfileRepository(preferencias).current();
        android.widget.TextView header = requireView().findViewById(R.id.profileEmailHeader);
        android.widget.TextView emailBody = requireView().findViewById(R.id.profileEmailBody);
        android.widget.TextView document = requireView().findViewById(R.id.profileDocumentBody);
        header.setText(profile.optString("email")); emailBody.setText(profile.optString("email"));
        document.setText(profile.optString("document"));
        android.widget.TextView documentEdit = requireView().findViewById(R.id.edtCpf);
        if (documentEdit != null) documentEdit.setText(profile.optString("document"));

        String nome =
                preferencias.getString(
                        "ambulante_nome",
                        "João Santos"
                );

        String telefone =
                preferencias.getString(
                        "ambulante_telefone",
                        "(11) 98888-5678"
                );

        txtNomePrincipal.setText(
                nome
        );

        txtNome.setText(
                nome
        );

        txtTelefone.setText(
                telefone
        );

        txtAvatar.setText(
                obterIniciais(
                        nome
                )
        );

        edtNome.setText(
                nome
        );

        edtTelefone.setText(
                telefone
        );
    }

    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos(
            View view
    ) {

        /*
         * No MVP, tocar nos dados pessoais
         * leva à tela de edição.
         */

        view.findViewById(
                R.id.rowNome
        ).setOnClickListener(
                item -> abrirEdicao()
        );

        view.findViewById(
                R.id.rowCpf
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

        // FOTO

        view.findViewById(
                R.id.btnFotoPerfil
        ).setOnClickListener(
                item -> {

                    Toast.makeText(
                            requireContext(),
                            "Alteração de foto será adicionada depois.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        // FECHAR EDIÇÃO

        btnFecharEdicao.setOnClickListener(
                item -> fecharEdicao()
        );

        // SALVAR

        btnSalvarPerfil.setOnClickListener(
                item -> salvarPerfil()
        );

        // LOGOUT

        view.findViewById(
                R.id.btnSair
        ).setOnClickListener(
                item -> sairDaConta()
        );
    }

    // =====================================================
    // EDITAR
    // =====================================================

    private void abrirEdicao() {

        String nome =
                preferencias.getString(
                        "ambulante_nome",
                        "João Santos"
                );

        String telefone =
                preferencias.getString(
                        "ambulante_telefone",
                        "(11) 98888-5678"
                );

        edtNome.setText(
                nome
        );

        edtTelefone.setText(
                telefone
        );

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

    // =====================================================
    // SALVAR
    // =====================================================

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

        if (nome.isEmpty()) {

            edtNome.setError(
                    "Informe seu nome"
            );

            edtNome.requestFocus();

            return;
        }

        if (telefone.isEmpty()) {

            edtTelefone.setError(
                    "Informe seu telefone"
            );

            edtTelefone.requestFocus();

            return;
        }

        ProfileRepository repository = new ProfileRepository(preferencias);
        org.json.JSONObject profile = repository.current();
        LocalJson.put(profile, "name", nome); LocalJson.put(profile, "phone", telefone);
        try { repository.save(profile, false); }
        catch (RuntimeException e) { Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show(); return; }

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
        LocalJson.commit(PlatformStore.open(requireContext()).edit().remove("logged_in_account"));

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

    // =====================================================
    // INICIAIS
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
                nome
                        .trim()
                        .split(
                                "\\s+"
                        );

        if (
                partes.length == 1
        ) {

            return partes[0]
                    .substring(
                            0,
                            1
                    )
                    .toUpperCase(java.util.Locale.getDefault());
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
        ).toUpperCase(java.util.Locale.getDefault());
    }
}