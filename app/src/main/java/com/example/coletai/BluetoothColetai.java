package com.example.coletai;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class BluetoothColetai {

    // =====================================================
    // LISTENER
    // =====================================================

    public interface Listener {

        void onStatusAlterado(
                String status
        );

        void onConectado(
                String nomeDispositivo
        );

        void onMensagemRecebida(
                String mensagem
        );

        void onErro(
                String mensagem
        );
    }


    // =====================================================
    // SINGLETON
    // =====================================================

    private static BluetoothColetai instancia;


    /*
     * Este UUID identifica o "serviço" Bluetooth do Coletaí.
     *
     * É muito importante que seja sempre o mesmo
     * nos dois celulares.
     */
    private static final UUID UUID_COLETAI =
            UUID.fromString(
                    "9d7e2b28-9f45-4d86-a7a1-1f03e9f2c6a4"
            );


    private static final String NOME_SERVICO =
            "ColetaiBluetooth";


    private final BluetoothAdapter bluetoothAdapter;


    private BluetoothServerSocket serverSocket;

    private BluetoothSocket socket;


    private Thread threadServidor;

    private Thread threadConexao;

    private Thread threadLeitura;


    private volatile boolean conectado =
            false;


    private volatile Listener listener;


    private String nomeDispositivoConectado =
            "";


    private BluetoothColetai(
            Context context
    ) {

        BluetoothManager bluetoothManager =
                (BluetoothManager)
                        context
                                .getApplicationContext()
                                .getSystemService(
                                        Context.BLUETOOTH_SERVICE
                                );


        if (bluetoothManager != null) {

            bluetoothAdapter =
                    bluetoothManager.getAdapter();

        } else {

            bluetoothAdapter =
                    null;
        }
    }


    public static synchronized BluetoothColetai getInstance(
            Context context
    ) {

        if (instancia == null) {

            instancia =
                    new BluetoothColetai(
                            context
                    );
        }


        return instancia;
    }


    // =====================================================
    // LISTENER
    // =====================================================

    public void setListener(
            Listener listener
    ) {

        this.listener =
                listener;


        if (listener == null) {

            return;
        }


        if (conectado) {

            listener.onConectado(
                    nomeDispositivoConectado
            );

        } else {

            listener.onStatusAlterado(
                    "Aguardando conexão"
            );
        }
    }


    // =====================================================
    // INFORMAÇÕES
    // =====================================================

    public boolean temBluetooth() {

        return bluetoothAdapter != null;
    }


    @SuppressLint("MissingPermission")
    public boolean bluetoothAtivado() {

        if (bluetoothAdapter == null) {

            return false;
        }


        try {

            return bluetoothAdapter.isEnabled();

        } catch (SecurityException e) {

            return false;
        }
    }


    public boolean estaConectado() {

        return conectado
                &&
                socket != null
                &&
                socket.isConnected();
    }


    public String getNomeDispositivoConectado() {

        return nomeDispositivoConectado;
    }


    // =====================================================
    // DISPOSITIVOS PAREADOS
    // =====================================================

    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getDispositivosPareados() {

        if (bluetoothAdapter == null) {

            return null;
        }


        try {

            return bluetoothAdapter
                    .getBondedDevices();

        } catch (SecurityException e) {

            notificarErro(
                    "Permissão Bluetooth não concedida."
            );

            return null;
        }
    }


    // =====================================================
    // SERVIDOR
    // =====================================================

    @SuppressLint("MissingPermission")
    public synchronized void iniciarServidor() {

        if (bluetoothAdapter == null) {

            notificarErro(
                    "Bluetooth não disponível."
            );

            return;
        }


        if (estaConectado()) {

            return;
        }


        if (
                threadServidor != null
                        &&
                        threadServidor.isAlive()
        ) {

            return;
        }


        threadServidor =
                new Thread(
                        () -> {

                            try {

                                notificarStatus(
                                        "Aguardando outro celular..."
                                );


                                BluetoothServerSocket servidor =
                                        bluetoothAdapter
                                                .listenUsingRfcommWithServiceRecord(
                                                        NOME_SERVICO,
                                                        UUID_COLETAI
                                                );


                                synchronized (
                                        BluetoothColetai.this
                                ) {

                                    serverSocket =
                                            servidor;
                                }


                                BluetoothSocket socketRecebido =
                                        servidor.accept();


                                if (socketRecebido != null) {

                                    definirConexao(
                                            socketRecebido
                                    );
                                }

                            } catch (
                                    SecurityException e
                            ) {

                                notificarErro(
                                        "Sem permissão para usar o Bluetooth."
                                );

                            } catch (
                                    IOException e
                            ) {

                                /*
                                 * Se uma conexão já foi estabelecida,
                                 * fechar o ServerSocket é normal.
                                 */
                                if (!conectado) {

                                    notificarErro(
                                            "Não foi possível aguardar uma conexão."
                                    );
                                }

                            } finally {

                                threadServidor =
                                        null;
                            }

                        },
                        "Coletai-Bluetooth-Servidor"
                );


        threadServidor.start();
    }


    // =====================================================
    // CONECTAR
    // =====================================================

    @SuppressLint("MissingPermission")
    public synchronized void conectar(
            BluetoothDevice dispositivo
    ) {

        if (dispositivo == null) {

            notificarErro(
                    "Selecione um dispositivo."
            );

            return;
        }


        if (estaConectado()) {

            notificarStatus(
                    "Já existe um celular conectado."
            );

            return;
        }


        if (
                threadConexao != null
                        &&
                        threadConexao.isAlive()
        ) {

            return;
        }


        threadConexao =
                new Thread(
                        () -> {

                            BluetoothSocket novoSocket =
                                    null;


                            try {

                                String nome =
                                        obterNomeDispositivo(
                                                dispositivo
                                        );


                                notificarStatus(
                                        "Conectando a "
                                                + nome
                                                + "..."
                                );


                                novoSocket =
                                        dispositivo
                                                .createRfcommSocketToServiceRecord(
                                                        UUID_COLETAI
                                                );


                                /*
                                 * connect() é bloqueante,
                                 * por isso estamos em uma Thread.
                                 */
                                novoSocket.connect();


                                definirConexao(
                                        novoSocket
                                );

                            } catch (
                                    SecurityException e
                            ) {

                                fecharSocketSilenciosamente(
                                        novoSocket
                                );


                                notificarErro(
                                        "Permissão Bluetooth não concedida."
                                );

                            } catch (
                                    IOException e
                            ) {

                                fecharSocketSilenciosamente(
                                        novoSocket
                                );


                                notificarErro(
                                        "Não foi possível conectar. "
                                                + "Confirme se o Coletaí está aberto no outro celular."
                                );


                                /*
                                 * Voltamos a aguardar uma conexão.
                                 */
                                iniciarServidor();

                            } finally {

                                threadConexao =
                                        null;
                            }

                        },
                        "Coletai-Bluetooth-Conectar"
                );


        threadConexao.start();
    }


    // =====================================================
    // DEFINIR CONEXÃO
    // =====================================================

    @SuppressLint("MissingPermission")
    private synchronized void definirConexao(
            BluetoothSocket novoSocket
    ) {

        if (novoSocket == null) {

            return;
        }


        /*
         * Se já havia algum socket antigo,
         * fechamos antes.
         */
        if (
                socket != null
                        &&
                        socket != novoSocket
        ) {

            fecharSocketSilenciosamente(
                    socket
            );
        }


        socket =
                novoSocket;


        conectado =
                true;


        nomeDispositivoConectado =
                obterNomeDispositivo(
                        novoSocket.getRemoteDevice()
                );


        fecharServidor();


        notificarConectado(
                nomeDispositivoConectado
        );


        iniciarLeitura();
    }


    // =====================================================
    // RECEBER MENSAGENS
    // =====================================================

    private synchronized void iniciarLeitura() {

        if (socket == null) {

            return;
        }


        if (
                threadLeitura != null
                        &&
                        threadLeitura.isAlive()
        ) {

            return;
        }


        final BluetoothSocket socketAtual =
                socket;


        threadLeitura =
                new Thread(
                        () -> {

                            try {

                                BufferedReader leitor =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        socketAtual
                                                                .getInputStream(),
                                                        StandardCharsets.UTF_8
                                                )
                                        );


                                String mensagem;


                                while (
                                        conectado
                                                &&
                                                (mensagem =
                                                        leitor.readLine())
                                                        != null
                                ) {

                                    notificarMensagem(
                                            mensagem
                                    );
                                }

                            } catch (
                                    IOException e
                            ) {

                                if (conectado) {

                                    conexaoPerdida();
                                }

                            } finally {

                                threadLeitura =
                                        null;
                            }

                        },
                        "Coletai-Bluetooth-Leitura"
                );


        threadLeitura.start();
    }


    // =====================================================
    // ENVIAR MENSAGEM
    // =====================================================

    public void enviar(
            String mensagem
    ) {

        if (
                mensagem == null
                        ||
                        mensagem.trim().isEmpty()
        ) {

            return;
        }


        if (!estaConectado()) {

            notificarErro(
                    "Nenhum celular conectado."
            );

            return;
        }


        final BluetoothSocket socketAtual =
                socket;


        new Thread(
                () -> {

                    try {

                        OutputStream outputStream =
                                socketAtual
                                        .getOutputStream();


                        String mensagemCompleta =
                                mensagem
                                        + "\n";


                        outputStream.write(
                                mensagemCompleta.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );


                        outputStream.flush();

                    } catch (
                            IOException e
                    ) {

                        conexaoPerdida();
                    }

                },
                "Coletai-Bluetooth-Envio"
        ).start();
    }


    // =====================================================
    // CONEXÃO PERDIDA
    // =====================================================

    private synchronized void conexaoPerdida() {

        fecharSocketSilenciosamente(
                socket
        );


        socket =
                null;


        conectado =
                false;


        nomeDispositivoConectado =
                "";


        notificarStatus(
                "Conexão encerrada"
        );


        iniciarServidor();
    }


    // =====================================================
    // DESCONECTAR
    // =====================================================

    public synchronized void desconectar() {

        fecharSocketSilenciosamente(
                socket
        );


        socket =
                null;


        conectado =
                false;


        nomeDispositivoConectado =
                "";


        notificarStatus(
                "Desconectado"
        );


        iniciarServidor();
    }


    // =====================================================
    // FECHAR SERVIDOR
    // =====================================================

    private synchronized void fecharServidor() {

        if (serverSocket != null) {

            try {

                serverSocket.close();

            } catch (
                    IOException ignored
            ) {

            }


            serverSocket =
                    null;
        }
    }


    private void fecharSocketSilenciosamente(
            BluetoothSocket socket
    ) {

        if (socket == null) {

            return;
        }


        try {

            socket.close();

        } catch (
                IOException ignored
        ) {

        }
    }


    // =====================================================
    // NOME DO DISPOSITIVO
    // =====================================================

    @SuppressLint("MissingPermission")
    private String obterNomeDispositivo(
            BluetoothDevice dispositivo
    ) {

        if (dispositivo == null) {

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


    // =====================================================
    // CALLBACKS
    // =====================================================

    private void notificarStatus(
            String status
    ) {

        Listener listenerAtual =
                listener;


        if (listenerAtual != null) {

            listenerAtual.onStatusAlterado(
                    status
            );
        }
    }


    private void notificarConectado(
            String nome
    ) {

        Listener listenerAtual =
                listener;


        if (listenerAtual != null) {

            listenerAtual.onConectado(
                    nome
            );
        }
    }


    private void notificarMensagem(
            String mensagem
    ) {

        Listener listenerAtual =
                listener;


        if (listenerAtual != null) {

            listenerAtual.onMensagemRecebida(
                    mensagem
            );
        }
    }


    private void notificarErro(
            String mensagem
    ) {

        Listener listenerAtual =
                listener;


        if (listenerAtual != null) {

            listenerAtual.onErro(
                    mensagem
            );
        }
    }
}