package com.example.coletai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PerfilClienteFragment extends Fragment {

    private View scrollPerfil;
    private View scrollEditarPerfil;

    private TextView txtAvatar;
    private TextView txtNomeCabecalho;
    private TextView txtEmailCabecalho;

    private TextView txtNomePerfil;
    private TextView txtCpfPerfil;
    private TextView txtTelefonePerfil;
    private TextView txtEmailPerfil;

    private TextView txtEnderecoCasa;
    private TextView txtEnderecoTrabalho;

    private LinearLayout rowNome;
    private LinearLayout rowCpf;
    private LinearLayout rowTelefone;
    private LinearLayout rowEmail;
    private LinearLayout rowSenha;
    private LinearLayout rowEnderecoCasa;
    private LinearLayout rowEnderecoTrabalho;
    private LinearLayout rowAdicionarEndereco;

    private TextView btnFotoPerfil;
    private Button btnSairConta;

    private TextView btnVoltarEdicao;
    private TextView btnSalvarPerfil;
    private EditText edtNomePerfil;
    private EditText edtTelefonePerfil;
    private EditText edtCpfPerfil;
    private EditText edtEmailPerfil;

    private OnBackPressedCallback callbackVoltarEdicao;

    /*
     * Dados de demonstração do cliente.
     * Mais para frente eles podem vir do banco/API.
     */
    private String nome = "Lucas Ferreira";
    private String cpf = "123.456.789-00";
    private String telefone = "(11) 99999-1234";
    private String email = "cliente@gmail.com";

    private String enderecoCasa =
            "Rua das Flores, 234";

    private String enderecoTrabalho =
            "Av. Paulista, 1578";

    public PerfilClienteFragment() {
        super(R.layout.fragment_perfil_cliente);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        iniciarComponentes(view);
        carregarDados();
        configurarEventos();
        configurarBotaoVoltar();
        atualizarTela();
    }

    private void iniciarComponentes(
            @NonNull View view
    ) {

        scrollPerfil =
                view.findViewById(R.id.scrollPerfil);

        scrollEditarPerfil =
                view.findViewById(R.id.scrollEditarPerfil);

        txtAvatar =
                view.findViewById(R.id.txtAvatar);

        txtNomeCabecalho =
                view.findViewById(R.id.txtNomeCabecalho);

        txtEmailCabecalho =
                view.findViewById(R.id.txtEmailCabecalho);

        txtNomePerfil =
                view.findViewById(R.id.txtNomePerfil);

        txtCpfPerfil =
                view.findViewById(R.id.txtCpfPerfil);

        txtTelefonePerfil =
                view.findViewById(R.id.txtTelefonePerfil);

        txtEmailPerfil =
                view.findViewById(R.id.txtEmailPerfil);

        txtEnderecoCasa =
                view.findViewById(R.id.txtEnderecoCasa);

        txtEnderecoTrabalho =
                view.findViewById(R.id.txtEnderecoTrabalho);

        rowNome =
                view.findViewById(R.id.rowNome);

        rowCpf =
                view.findViewById(R.id.rowCpf);

        rowTelefone =
                view.findViewById(R.id.rowTelefone);

        rowEmail =
                view.findViewById(R.id.rowEmail);

        rowSenha =
                view.findViewById(R.id.rowSenha);

        rowEnderecoCasa =
                view.findViewById(R.id.rowEnderecoCasa);

        rowEnderecoTrabalho =
                view.findViewById(R.id.rowEnderecoTrabalho);

        rowAdicionarEndereco =
                view.findViewById(R.id.rowAdicionarEndereco);

        btnFotoPerfil =
                view.findViewById(R.id.btnFotoPerfil);

        btnSairConta =
                view.findViewById(R.id.btnSairConta);

        btnVoltarEdicao =
                view.findViewById(R.id.btnVoltarEdicao);

        btnSalvarPerfil =
                view.findViewById(R.id.btnSalvarPerfil);

        edtNomePerfil =
                view.findViewById(R.id.edtNomePerfil);

        edtTelefonePerfil =
                view.findViewById(R.id.edtTelefonePerfil);

        edtCpfPerfil =
                view.findViewById(R.id.edtCpfPerfil);

        edtEmailPerfil =
                view.findViewById(R.id.edtEmailPerfil);
    }

    private void configurarEventos() {

        /*
         * No MVP, tocar nos dados pessoais abre
         * a tela de edição.
         */
        View.OnClickListener abrirEdicao =
                view -> abrirEdicao();

        rowNome.setOnClickListener(abrirEdicao);
        rowCpf.setOnClickListener(abrirEdicao);
        rowTelefone.setOnClickListener(abrirEdicao);
        rowEmail.setOnClickListener(abrirEdicao);
        rowSenha.setOnClickListener(abrirEdicao);

        rowEnderecoCasa.setOnClickListener(view -> editarEndereco());

        rowEnderecoTrabalho.setOnClickListener(view -> editarEndereco());

        rowAdicionarEndereco.setOnClickListener(view -> editarEndereco());

        btnFotoPerfil.setOnClickListener(view ->
                Toast.makeText(
                        requireContext(),
                        "Seleção de foto será implementada depois.",
                        Toast.LENGTH_SHORT
                ).show()
        );

        btnVoltarEdicao.setOnClickListener(view ->
                fecharEdicao()
        );

        btnSalvarPerfil.setOnClickListener(view ->
                salvarPerfil()
        );

        btnSairConta.setOnClickListener(view ->
                sairDaConta()
        );
    }

    private void configurarBotaoVoltar() {

        callbackVoltarEdicao =
                new OnBackPressedCallback(false) {

                    @Override
                    public void handleOnBackPressed() {
                        fecharEdicao();
                    }
                };

        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(
                        getViewLifecycleOwner(),
                        callbackVoltarEdicao
                );
    }

    private void carregarDados() {
        org.json.JSONObject profile = new ProfileRepository(requireContext().getSharedPreferences("coletai", android.content.Context.MODE_PRIVATE)).current();
        nome = profile.optString("name"); telefone = profile.optString("phone");
        cpf = profile.optString("document"); email = profile.optString("email");
        enderecoCasa = profile.optString("address");
        if (enderecoCasa.isEmpty()) enderecoCasa = getString(R.string.admin_no_value);
        rowEnderecoTrabalho.setVisibility(View.GONE);
    }

    @Override public void onResume() {
        super.onResume();
        if (getView() != null) { carregarDados(); atualizarTela(); }
    }

    private void editarEndereco() {
        ProfileRepository repository = new ProfileRepository(requireContext().getSharedPreferences("coletai", android.content.Context.MODE_PRIVATE));
        org.json.JSONObject profile = repository.current();
        EditText input = new EditText(requireContext()); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        input.setHint(R.string.admin_address); input.setText(profile.optString("address"));
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.admin_address).setView(input).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            LocalJson.put(profile, "address", input.getText().toString().trim());
            try { repository.save(profile, false); carregarDados(); atualizarTela(); dialog.dismiss(); }
            catch (RuntimeException e) { input.setError(e.getMessage()); }
        }));
        dialog.show();
    }

    private void atualizarTela() {

        txtNomeCabecalho.setText(nome);
        txtEmailCabecalho.setText(email);

        txtNomePerfil.setText(nome);
        txtCpfPerfil.setText(cpf);
        txtTelefonePerfil.setText(telefone);
        txtEmailPerfil.setText(email);

        txtEnderecoCasa.setText(enderecoCasa);
        txtEnderecoTrabalho.setText(enderecoTrabalho);

        txtAvatar.setText(
                gerarIniciais(nome)
        );
    }

    private String gerarIniciais(
            String nomeCompleto
    ) {

        String nomeLimpo =
                nomeCompleto.trim();

        if (nomeLimpo.isEmpty()) {
            return "?";
        }

        String[] partes =
                nomeLimpo.split("\\s+");

        if (partes.length == 1) {
            return partes[0]
                    .substring(0, 1)
                    .toUpperCase(java.util.Locale.getDefault());
        }

        String primeira =
                partes[0]
                        .substring(0, 1);

        String ultima =
                partes[partes.length - 1]
                        .substring(0, 1);

        return (primeira + ultima)
                .toUpperCase(java.util.Locale.getDefault());
    }

    private void abrirEdicao() {

        edtNomePerfil.setText(nome);
        edtTelefonePerfil.setText(telefone);
        edtCpfPerfil.setText(cpf);
        edtEmailPerfil.setText(email);

        scrollPerfil.setVisibility(
                View.GONE
        );

        scrollEditarPerfil.setVisibility(
                View.VISIBLE
        );

        if (callbackVoltarEdicao != null) {
            callbackVoltarEdicao.setEnabled(true);
        }
    }

    private void fecharEdicao() {

        scrollEditarPerfil.setVisibility(
                View.GONE
        );

        scrollPerfil.setVisibility(
                View.VISIBLE
        );

        if (callbackVoltarEdicao != null) {
            callbackVoltarEdicao.setEnabled(false);
        }
    }

    private void salvarPerfil() {

        String novoNome =
                edtNomePerfil
                        .getText()
                        .toString()
                        .trim();

        String novoTelefone =
                edtTelefonePerfil
                        .getText()
                        .toString()
                        .trim();

        if (novoNome.isEmpty()) {

            edtNomePerfil.setError(
                    "Informe seu nome"
            );

            edtNomePerfil.requestFocus();
            return;
        }

        if (novoTelefone.isEmpty()) {

            edtTelefonePerfil.setError(
                    "Informe seu telefone"
            );

            edtTelefonePerfil.requestFocus();
            return;
        }

        ProfileRepository repository = new ProfileRepository(requireContext().getSharedPreferences("coletai", android.content.Context.MODE_PRIVATE));
        org.json.JSONObject profile = repository.current();
        LocalJson.put(profile, "name", novoNome);
        LocalJson.put(profile, "phone", novoTelefone);
        try { repository.save(profile, false); }
        catch (RuntimeException e) { Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show(); return; }
        carregarDados();

        atualizarTela();
        fecharEdicao();

        Toast.makeText(
                requireContext(),
                "Perfil atualizado!",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void sairDaConta() {
        LocalJson.commit(PlatformStore.open(requireContext()).edit().remove("logged_in_account"));

        Intent intent =
                new Intent(
                        requireContext(),
                        MainActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
    }
}
