package com.example.coletai;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class BluetoothActivity
        extends AppCompatActivity
        implements BluetoothColetai.Listener {

    private TextView btnVoltar;

    private TextView txtStatusBluetooth;
    private TextView txtDispositivoConectado;
    private TextView txtUltimaMensagem;

    private Spinner spinnerDispositivos;

    private Button btnAtualizar;
    private Button btnConectar;
    private Button btnEnviarTeste;
    private Button btnDesconectar;


    private BluetoothColetai bluetoothColetai;


    private final List<BluetoothDevice>
            dispositivosPareados =
            new ArrayList<>();


    // =====================================================
    // PERMISSÃO BLUETOOTH
    // =====================================================

    private final ActivityResultLauncher<String>
            launcherPermissaoBluetooth =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    concedida -> {

                        if (concedida) {

                            prepararBluetooth();

                        } else {

                            atualizarStatus(
                                    "Permissão Bluetooth negada"
                            );


                            Toast.makeText(
                                    this,
                                    "Permita o acesso a dispositivos próximos para usar o modo Bluetooth.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );


    // =====================================================
    // ATIVAR BLUETOOTH
    // =====================================================

    private final ActivityResultLauncher<Intent>
            launcherAtivarBluetooth =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    resultado -> {

                        prepararBluetooth();
                    }
            );


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
                R.layout.activity_bluetooth
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


        bluetoothColetai =
                BluetoothColetai.getInstance(
                        this
                );


        iniciarComponentes();

        configurarEventos();

        verificarPermissaoBluetooth();
    }


    @Override
    protected void onStart() {

        super.onStart();


        bluetoothColetai.setListener(
                this
        );


        atualizarEstadoDaTela();
    }


    @Override
    protected void onStop() {

        super.onStop();


        /*
         * Não desconectamos.
         *
         * A conexão Bluetooth continua existindo
         * mesmo depois de voltar para o Login.
         */
        bluetoothColetai.setListener(
                null
        );
    }


    // =====================================================
    // COMPONENTES
    // =====================================================

    private void iniciarComponentes() {

        btnVoltar =
                findViewById(
                        R.id.btnVoltarBluetooth
                );


        txtStatusBluetooth =
                findViewById(
                        R.id.txtStatusBluetooth
                );


        txtDispositivoConectado =
                findViewById(
                        R.id.txtDispositivoConectado
                );


        txtUltimaMensagem =
                findViewById(
                        R.id.txtUltimaMensagem
                );


        spinnerDispositivos =
                findViewById(
                        R.id.spinnerDispositivos
                );


        btnAtualizar =
                findViewById(
                        R.id.btnAtualizarDispositivos
                );


        btnConectar =
                findViewById(
                        R.id.btnConectarBluetooth
                );


        btnEnviarTeste =
                findViewById(
                        R.id.btnEnviarTeste
                );


        btnDesconectar =
                findViewById(
                        R.id.btnDesconectarBluetooth
                );
    }


    // =====================================================
    // EVENTOS
    // =====================================================

    private void configurarEventos() {

        btnVoltar.setOnClickListener(
                view -> finish()
        );


        btnAtualizar.setOnClickListener(
                view -> {

                    if (
                            temPermissaoBluetooth()
                    ) {

                        carregarDispositivosPareados();
                    }
                }
        );


        btnConectar.setOnClickListener(
                view -> conectarSelecionado()
        );


        btnEnviarTeste.setOnClickListener(
                view -> enviarMensagemTeste()
        );


        btnDesconectar.setOnClickListener(
                view -> bluetoothColetai.desconectar()
        );
    }


    // =====================================================
    // PERMISSÃO
    // =====================================================

    private void verificarPermissaoBluetooth() {

        if (
                Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.S
        ) {

            if (!temPermissaoBluetooth()) {

                launcherPermissaoBluetooth.launch(
                        Manifest.permission.BLUETOOTH_CONNECT
                );

                return;
            }
        }


        prepararBluetooth();
    }


    private boolean temPermissaoBluetooth() {

        if (
                Build.VERSION.SDK_INT
                        < Build.VERSION_CODES.S
        ) {

            return true;
        }


        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
        )
                ==
                PackageManager.PERMISSION_GRANTED;
    }


    // =====================================================
    // PREPARAR
    // =====================================================

    private void prepararBluetooth() {

        if (!temPermissaoBluetooth()) {

            return;
        }


        if (!bluetoothColetai.temBluetooth()) {

            atualizarStatus(
                    "Este celular não possui Bluetooth"
            );


            btnConectar.setEnabled(
                    false
            );


            btnEnviarTeste.setEnabled(
                    false
            );


            return;
        }


        if (!bluetoothColetai.bluetoothAtivado()) {

            atualizarStatus(
                    "Bluetooth desligado"
            );


            Intent intent =
                    new Intent(
                            BluetoothAdapter.ACTION_REQUEST_ENABLE
                    );


            launcherAtivarBluetooth.launch(
                    intent
            );


            return;
        }


        bluetoothColetai.iniciarServidor();


        carregarDispositivosPareados();


        atualizarEstadoDaTela();
    }


    // =====================================================
    // PAREADOS
    // =====================================================

    private void carregarDispositivosPareados() {

        if (!temPermissaoBluetooth()) {

            return;
        }


        Set<BluetoothDevice> pareados =
                bluetoothColetai
                        .getDispositivosPareados();


        dispositivosPareados.clear();


        if (pareados != null) {

            dispositivosPareados.addAll(
                    pareados
            );
        }


        dispositivosPareados.sort(
                Comparator.comparing(
                        this::obterNomeSeguro,
                        String.CASE_INSENSITIVE_ORDER
                )
        );


        List<String> nomes =
                new ArrayList<>();


        for (
                BluetoothDevice dispositivo
                :
                dispositivosPareados
        ) {

            String nome =
                    obterNomeSeguro(
                            dispositivo
                    );


            String endereco =
                    obterEnderecoSeguro(
                            dispositivo
                    );


            nomes.add(
                    nome
                            + "  •  "
                            + endereco
            );
        }


        if (nomes.isEmpty()) {

            nomes.add(
                    "Nenhum dispositivo pareado"
            );
        }


        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        nomes
                );


        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinnerDispositivos.setAdapter(
                adapter
        );


        btnConectar.setEnabled(
                !dispositivosPareados.isEmpty()
                        &&
                        !bluetoothColetai.estaConectado()
        );
    }


    // =====================================================
    // CONECTAR
    // =====================================================

    private void conectarSelecionado() {

        if (!temPermissaoBluetooth()) {

            verificarPermissaoBluetooth();

            return;
        }


        if (
                dispositivosPareados.isEmpty()
        ) {

            Toast.makeText(
                    this,
                    "Pareie os dois celulares primeiro nas configurações do Android.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }


        int posicao =
                spinnerDispositivos
                        .getSelectedItemPosition();


        if (
                posicao < 0
                        ||
                        posicao
                                >= dispositivosPareados.size()
        ) {

            return;
        }


        BluetoothDevice dispositivo =
                dispositivosPareados.get(
                        posicao
                );


        btnConectar.setEnabled(
                false
        );


        bluetoothColetai.conectar(
                dispositivo
        );
    }


    // =====================================================
    // MENSAGEM TESTE
    // =====================================================

    private void enviarMensagemTeste() {

        if (!bluetoothColetai.estaConectado()) {

            Toast.makeText(
                    this,
                    "Conecte os celulares primeiro.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        String mensagem =
                "{"
                        + "\"evento\":\"TESTE\","
                        + "\"mensagem\":\"Olá do Coletaí!\""
                        + "}";


        bluetoothColetai.enviar(
                mensagem
        );


        Toast.makeText(
                this,
                "Mensagem enviada!",
                Toast.LENGTH_SHORT
        ).show();
    }


    // =====================================================
    // ESTADO DA TELA
    // =====================================================

    private void atualizarEstadoDaTela() {

        if (
                bluetoothColetai == null
        ) {

            return;
        }


        boolean conectado =
                bluetoothColetai
                        .estaConectado();


        if (conectado) {

            txtStatusBluetooth.setText(
                    "● Conectado"
            );


            txtStatusBluetooth.setTextColor(
                    getColor(
                            R.color.brand_700
                    )
            );


            txtDispositivoConectado.setText(
                    bluetoothColetai
                            .getNomeDispositivoConectado()
            );

        } else {

            txtStatusBluetooth.setText(
                    "● Aguardando conexão"
            );


            txtStatusBluetooth.setTextColor(
                    getColor(
                            R.color.ink_500
                    )
            );


            txtDispositivoConectado.setText(
                    "Nenhum celular conectado"
            );
        }


        btnConectar.setEnabled(
                !conectado
                        &&
                        !dispositivosPareados.isEmpty()
        );


        btnEnviarTeste.setEnabled(
                conectado
        );


        btnDesconectar.setEnabled(
                conectado
        );
    }


    private void atualizarStatus(
            String status
    ) {

        txtStatusBluetooth.setText(
                "● " + status
        );
    }


    // =====================================================
    // CALLBACKS BLUETOOTH
    // =====================================================

    @Override
    public void onStatusAlterado(
            String status
    ) {

        runOnUiThread(
                () -> {

                    atualizarStatus(
                            status
                    );


                    atualizarEstadoDaTela();
                }
        );
    }


    @Override
    public void onConectado(
            String nomeDispositivo
    ) {

        runOnUiThread(
                () -> {

                    txtStatusBluetooth.setText(
                            "● Conectado"
                    );


                    txtStatusBluetooth.setTextColor(
                            getColor(
                                    R.color.brand_700
                            )
                    );


                    txtDispositivoConectado.setText(
                            nomeDispositivo
                    );


                    btnConectar.setEnabled(
                            false
                    );


                    btnEnviarTeste.setEnabled(
                            true
                    );


                    btnDesconectar.setEnabled(
                            true
                    );


                    Toast.makeText(
                            this,
                            "Bluetooth conectado!",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
    }


    @Override
    public void onMensagemRecebida(
            String mensagem
    ) {

        runOnUiThread(
                () -> {

                    txtUltimaMensagem.setText(
                            mensagem
                    );


                    Toast.makeText(
                            this,
                            "Mensagem recebida do outro celular!",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
    }


    @Override
    public void onErro(
            String mensagem
    ) {

        runOnUiThread(
                () -> {

                    txtStatusBluetooth.setText(
                            "● Erro na conexão"
                    );


                    Toast.makeText(
                            this,
                            mensagem,
                            Toast.LENGTH_LONG
                    ).show();


                    atualizarEstadoDaTela();
                }
        );
    }


    // =====================================================
    // DADOS SEGUROS
    // =====================================================

    private String obterNomeSeguro(
            @NonNull BluetoothDevice dispositivo
    ) {

        if (!temPermissaoBluetooth()) {

            return "Dispositivo";
        }


        try {

            String nome =
                    dispositivo.getName();


            if (
                    nome == null
                            ||
                            nome.trim().isEmpty()
            ) {

                return "Dispositivo Bluetooth";
            }


            return nome;

        } catch (
                SecurityException e
        ) {

            return "Dispositivo Bluetooth";
        }
    }


    private String obterEnderecoSeguro(
            @NonNull BluetoothDevice dispositivo
    ) {

        if (!temPermissaoBluetooth()) {

            return "";
        }


        try {

            return dispositivo.getAddress();

        } catch (
                SecurityException e
        ) {

            return "";
        }
    }
}