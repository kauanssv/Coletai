package com.example.coletai;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class BluetoothColetai {

    // =====================================================
    // CALLBACKS DA TELA DE BLUETOOTH
    // =====================================================

    public interface Listener {

        void onStatusAlterado(String status);

        void onConectado(String nomeDispositivo);

        void onMensagemRecebida(String mensagem);

        void onErro(String mensagem);
    }


    // =====================================================
    // CALLBACK PARA ATUALIZAR A TELA VISÍVEL DO APP
    // =====================================================

    public interface SyncListener {

        void onSyncAtualizado();
    }


    // =====================================================
    // SINGLETON / CONFIGURAÇÃO
    // =====================================================

    private static BluetoothColetai instancia;

    private static final UUID UUID_COLETAI =
            UUID.fromString(
                    "9d7e2b28-9f45-4d86-a7a1-1f03e9f2c6a4"
            );

    private static final String NOME_SERVICO =
            "ColetaiBluetooth";


    private final Context appContext;
    private final BluetoothAdapter bluetoothAdapter;
    private final SharedPreferences preferencias;
    private final Handler mainHandler;


    private BluetoothServerSocket serverSocket;
    private BluetoothSocket socket;

    private Thread threadServidor;
    private Thread threadConexao;
    private Thread threadLeitura;

    private volatile boolean conectado = false;

    private volatile Listener listener;
    private volatile SyncListener syncListener;

    private String nomeDispositivoConectado = "";


    // =====================================================
    // ESTADO LOCAL PARA SABER QUAL EVENTO ENVIAR
    // =====================================================

    private String ultimoStatusConhecido;
    private String ultimaEtapaConhecida;

    private long ultimoEnvioPedido = 0;
    private long ultimoEnvioProposta = 0;
    private long ultimoEnvioAceite = 0;
    private long ultimoEnvioRecusa = 0;
    private long ultimoEnvioEtapa = 0;
    private long ultimoEnvioCancelamento = 0;


    private final SharedPreferences.OnSharedPreferenceChangeListener
            listenerPreferencias;


    // =====================================================
    // CONSTRUTOR
    // =====================================================

    private BluetoothColetai(
            Context context
    ) {

        appContext =
                context.getApplicationContext();


        mainHandler =
                new Handler(
                        Looper.getMainLooper()
                );


        BluetoothManager bluetoothManager =
                (BluetoothManager)
                        appContext.getSystemService(
                                Context.BLUETOOTH_SERVICE
                        );


        if (bluetoothManager != null) {

            bluetoothAdapter =
                    bluetoothManager.getAdapter();

        } else {

            bluetoothAdapter =
                    null;
        }


        preferencias =
                appContext.getSharedPreferences(
                        "coletai",
                        Context.MODE_PRIVATE
                );


        ultimoStatusConhecido =
                preferencias.getString(
                        "pedido_status",
                        ""
                );


        ultimaEtapaConhecida =
                preferencias.getString(
                        "coleta_etapa",
                        ""
                );


        listenerPreferencias =
                (sharedPreferences, chave) -> {

                    if (
                            "pedido_status".equals(
                                    chave
                            )
                    ) {

                        processarAlteracaoStatusLocal();
                    }


                    if (
                            "coleta_etapa".equals(
                                    chave
                            )
                    ) {

                        processarAlteracaoEtapaLocal();
                    }
                };


        preferencias
                .registerOnSharedPreferenceChangeListener(
                        listenerPreferencias
                );
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
    // LISTENERS
    // =====================================================

    public void setListener(
            Listener listener
    ) {

        this.listener =
                listener;


        if (listener == null) {

            return;
        }


        if (estaConectado()) {

            notificarConectado(
                    nomeDispositivoConectado
            );

        } else {

            notificarStatus(
                    "Aguardando conexão"
            );
        }
    }


    public void setSyncListener(
            SyncListener syncListener
    ) {

        this.syncListener =
                syncListener;
    }


    // =====================================================
    // DETECTAR ALTERAÇÃO LOCAL DE STATUS
    // =====================================================

    private void processarAlteracaoStatusLocal() {

        String statusAtual =
                preferencias.getString(
                        "pedido_status",
                        ""
                );


        String statusAnterior =
                ultimoStatusConhecido == null
                        ? ""
                        : ultimoStatusConhecido;


        ultimoStatusConhecido =
                statusAtual == null
                        ? ""
                        : statusAtual;


        if (!estaConectado()) {

            return;
        }


        String tipoUsuario =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        // -------------------------------------------------
        // CLIENTE
        // -------------------------------------------------

        if (
                "cliente".equals(
                        tipoUsuario
                )
        ) {

            /*
             * Criou um NOVO PEDIDO:
             * vazio/done -> waiting
             */
            if (
                    "waiting".equals(
                            statusAtual
                    )
                            &&
                            !"proposal".equals(
                                    statusAnterior
                            )
            ) {

                if (
                        podeEnviar(
                                ultimoEnvioPedido
                        )
                ) {

                    ultimoEnvioPedido =
                            System.currentTimeMillis();


                    enviarPedidoAtual();
                }


                return;
            }


            /*
             * Recusou uma PROPOSTA:
             * proposal -> waiting
             */
            if (
                    "proposal".equals(
                            statusAnterior
                    )
                            &&
                            "waiting".equals(
                                    statusAtual
                            )
            ) {

                if (
                        podeEnviar(
                                ultimoEnvioRecusa
                        )
                ) {

                    ultimoEnvioRecusa =
                            System.currentTimeMillis();


                    enviarEventoSimples(
                            "PROPOSTA_RECUSADA"
                    );
                }


                return;
            }


            /*
             * Aceitou a proposta:
             * proposal -> active
             */
            if (
                    "proposal".equals(
                            statusAnterior
                    )
                            &&
                            "active".equals(
                                    statusAtual
                            )
            ) {

                if (
                        podeEnviar(
                                ultimoEnvioAceite
                        )
                ) {

                    ultimoEnvioAceite =
                            System.currentTimeMillis();


                    enviarPropostaAceita();
                }


                return;
            }


            /*
             * Cancelou o pedido:
             * waiting/proposal/active -> vazio
             */
            if (
                    statusAtual != null
                            &&
                            statusAtual.isEmpty()
                            &&
                            statusAnterior != null
                            &&
                            !statusAnterior.isEmpty()
            ) {

                if (
                        podeEnviar(
                                ultimoEnvioCancelamento
                        )
                ) {

                    ultimoEnvioCancelamento =
                            System.currentTimeMillis();


                    enviarEventoSimples(
                            "PEDIDO_CANCELADO"
                    );
                }
            }


            return;
        }


        // -------------------------------------------------
        // AMBULANTE / EMPRESA
        // -------------------------------------------------

        boolean ehColetor =
                "ambulante".equals(
                        tipoUsuario
                )
                        ||
                        "empresa".equals(
                                tipoUsuario
                        );


        if (!ehColetor) {

            return;
        }


        /*
         * Enviou proposta:
         * waiting -> proposal
         */
        if (
                "proposal".equals(
                        statusAtual
                )
                        &&
                        !"proposal".equals(
                                statusAnterior
                        )
        ) {

            if (
                    podeEnviar(
                            ultimoEnvioProposta
                    )
            ) {

                ultimoEnvioProposta =
                        System.currentTimeMillis();


                enviarPropostaAtual();
            }
        }
    }


    // =====================================================
    // DETECTAR ALTERAÇÃO LOCAL DA COLETA
    // =====================================================

    private void processarAlteracaoEtapaLocal() {

        String etapaAtual =
                preferencias.getString(
                        "coleta_etapa",
                        ""
                );


        String etapaAnterior =
                ultimaEtapaConhecida == null
                        ? ""
                        : ultimaEtapaConhecida;


        ultimaEtapaConhecida =
                etapaAtual == null
                        ? ""
                        : etapaAtual;


        if (!estaConectado()) {

            return;
        }


        String tipoUsuario =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        boolean ehColetor =
                "ambulante".equals(
                        tipoUsuario
                )
                        ||
                        "empresa".equals(
                                tipoUsuario
                        );


        if (!ehColetor) {

            return;
        }


        if (
                etapaAtual == null
                        ||
                        etapaAtual.isEmpty()
                        ||
                        etapaAtual.equals(
                                etapaAnterior
                        )
        ) {

            return;
        }


        if (
                "going".equals(
                        etapaAtual
                )
                        ||
                        "collecting".equals(
                                etapaAtual
                        )
        ) {

            if (
                    podeEnviar(
                            ultimoEnvioEtapa
                    )
            ) {

                ultimoEnvioEtapa =
                        System.currentTimeMillis();


                enviarEtapaColeta(
                        etapaAtual
                );
            }


            return;
        }


        if (
                "done".equals(
                        etapaAtual
                )
        ) {

            if (
                    podeEnviar(
                            ultimoEnvioEtapa
                    )
            ) {

                ultimoEnvioEtapa =
                        System.currentTimeMillis();


                enviarColetaFinalizada();
            }
        }
    }


    private boolean podeEnviar(
            long ultimoEnvio
    ) {

        return (
                System.currentTimeMillis()
                        -
                        ultimoEnvio
        ) > 700;
    }


    // =====================================================
    // ENVIAR NOVO PEDIDO
    // =====================================================

    public void enviarPedidoAtual() {

        if (!estaConectado()) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject();


            json.put(
                    "evento",
                    "NOVO_PEDIDO"
            );


            json.put(
                    "tipo",
                    preferencias.getString(
                            "pedido_tipo",
                            ""
                    )
            );


            json.put(
                    "emoji",
                    preferencias.getString(
                            "pedido_emoji",
                            ""
                    )
            );


            json.put(
                    "descricao",
                    preferencias.getString(
                            "pedido_descricao",
                            ""
                    )
            );


            json.put(
                    "peso",
                    preferencias.getString(
                            "pedido_peso",
                            ""
                    )
            );


            json.put(
                    "regiao",
                    preferencias.getString(
                            "pedido_regiao",
                            ""
                    )
            );


            json.put(
                    "cidade",
                    preferencias.getString(
                            "pedido_cidade",
                            ""
                    )
            );


            json.put(
                    "observacao",
                    preferencias.getString(
                            "pedido_observacao",
                            ""
                    )
            );


            json.put(
                    "enderecoCompleto",
                    preferencias.getString(
                            "pedido_endereco_completo",
                            ""
                    )
            );


            enviar(
                    json.toString()
            );

        } catch (
                JSONException e
        ) {

            notificarErro(
                    "Não foi possível preparar o pedido."
            );
        }
    }


    // =====================================================
    // ENVIAR PROPOSTA
    // =====================================================

    public void enviarPropostaAtual() {

        if (!estaConectado()) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject();


            json.put(
                    "evento",
                    "PROPOSTA"
            );


            json.put(
                    "preco",
                    preferencias.getString(
                            "proposta_preco",
                            ""
                    )
            );


            json.put(
                    "mensagem",
                    preferencias.getString(
                            "proposta_mensagem",
                            ""
                    )
            );


            json.put(
                    "nome",
                    preferencias.getString(
                            "proposta_coletor_nome",
                            ""
                    )
            );


            json.put(
                    "avaliacao",
                    preferencias.getFloat(
                            "proposta_coletor_avaliacao",
                            4.9f
                    )
            );


            json.put(
                    "coletas",
                    preferencias.getInt(
                            "proposta_coletor_coletas",
                            0
                    )
            );


            json.put(
                    "tipoColetor",
                    preferencias.getString(
                            "proposta_coletor_tipo",
                            ""
                    )
            );


            json.put(
                    "email",
                    preferencias.getString(
                            "proposta_coletor_email",
                            ""
                    )
            );


            enviar(
                    json.toString()
            );

        } catch (
                JSONException e
        ) {

            notificarErro(
                    "Não foi possível preparar a proposta."
            );
        }
    }


    // =====================================================
    // ENVIAR ACEITE
    // =====================================================

    private void enviarPropostaAceita() {

        if (!estaConectado()) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject();


            json.put(
                    "evento",
                    "PROPOSTA_ACEITA"
            );


            json.put(
                    "pedido_status",
                    "active"
            );


            json.put(
                    "coleta_etapa",
                    "ready"
            );


            enviar(
                    json.toString()
            );

        } catch (
                JSONException e
        ) {

            notificarErro(
                    "Não foi possível enviar o aceite."
            );
        }
    }


    // =====================================================
    // ENVIAR ETAPA
    // =====================================================

    private void enviarEtapaColeta(
            String etapa
    ) {

        if (!estaConectado()) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject();


            json.put(
                    "evento",
                    "COLETA_ETAPA"
            );


            json.put(
                    "pedido_status",
                    "active"
            );


            json.put(
                    "coleta_etapa",
                    etapa
            );


            enviar(
                    json.toString()
            );

        } catch (
                JSONException e
        ) {

            notificarErro(
                    "Não foi possível sincronizar a coleta."
            );
        }
    }


    // =====================================================
    // ENVIAR FINALIZAÇÃO
    // =====================================================

    private void enviarColetaFinalizada() {

        if (!estaConectado()) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject();


            json.put(
                    "evento",
                    "COLETA_FINALIZADA"
            );


            json.put(
                    "pedido_status",
                    "done"
            );


            json.put(
                    "coleta_etapa",
                    "done"
            );


            enviar(
                    json.toString()
            );

        } catch (
                JSONException e
        ) {

            notificarErro(
                    "Não foi possível finalizar a sincronização."
            );
        }
    }


    // =====================================================
    // EVENTO SIMPLES
    // =====================================================

    private void enviarEventoSimples(
            String evento
    ) {

        if (!estaConectado()) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject();


            json.put(
                    "evento",
                    evento
            );


            enviar(
                    json.toString()
            );

        } catch (
                JSONException e
        ) {

            notificarErro(
                    "Não foi possível enviar a atualização."
            );
        }
    }


    // =====================================================
    // BLUETOOTH
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

        } catch (
                SecurityException e
        ) {

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


    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getDispositivosPareados() {

        if (bluetoothAdapter == null) {

            return null;
        }


        try {

            return bluetoothAdapter
                    .getBondedDevices();

        } catch (
                SecurityException e
        ) {

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


                                if (
                                        socketRecebido != null
                                ) {

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

                                if (!conectado) {

                                    notificarStatus(
                                            "Servidor Bluetooth encerrado"
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
                                        "Não foi possível conectar."
                                );


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


        /*
         * Atualiza os estados conhecidos quando a conexão começa.
         */
        ultimoStatusConhecido =
                preferencias.getString(
                        "pedido_status",
                        ""
                );


        ultimaEtapaConhecida =
                preferencias.getString(
                        "coleta_etapa",
                        ""
                );


        notificarConectado(
                nomeDispositivoConectado
        );


        iniciarLeitura();
    }


    // =====================================================
    // LEITURA
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
                                                (
                                                        mensagem =
                                                                leitor.readLine()
                                                )
                                                        != null
                                ) {

                                    processarMensagemRecebida(
                                            mensagem
                                    );


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
    // PROCESSAR MENSAGEM
    // =====================================================

    private void processarMensagemRecebida(
            String mensagem
    ) {

        if (
                mensagem == null
                        ||
                        mensagem.trim().isEmpty()
        ) {

            return;
        }


        try {

            JSONObject json =
                    new JSONObject(
                            mensagem
                    );


            String evento =
                    json.optString(
                            "evento",
                            ""
                    );


            switch (evento) {

                case "NOVO_PEDIDO":

                    receberNovoPedido(
                            json
                    );

                    break;


                case "PROPOSTA":

                    receberProposta(
                            json
                    );

                    break;


                case "PROPOSTA_ACEITA":

                    receberPropostaAceita();

                    break;


                case "PROPOSTA_RECUSADA":

                    receberPropostaRecusada();

                    break;


                case "COLETA_ETAPA":

                    receberEtapaColeta(
                            json
                    );

                    break;


                case "COLETA_FINALIZADA":

                    receberColetaFinalizada();

                    break;


                case "PEDIDO_CANCELADO":

                    receberPedidoCancelado();

                    break;
            }

        } catch (
                JSONException ignored
        ) {

            /*
             * Mantém compatibilidade com a mensagem TESTE
             * da tela de Bluetooth.
             */
        }
    }


    // =====================================================
    // RECEBER NOVO PEDIDO
    // =====================================================

    private void receberNovoPedido(
            JSONObject json
    ) {

        SharedPreferences.Editor editor =
                preferencias.edit();


        limparProposta(
                editor
        );


        editor.putString(
                "pedido_tipo",
                json.optString(
                        "tipo",
                        ""
                )
        );


        editor.putString(
                "pedido_emoji",
                json.optString(
                        "emoji",
                        ""
                )
        );


        editor.putString(
                "pedido_descricao",
                json.optString(
                        "descricao",
                        ""
                )
        );


        editor.putString(
                "pedido_peso",
                json.optString(
                        "peso",
                        ""
                )
        );


        editor.putString(
                "pedido_regiao",
                json.optString(
                        "regiao",
                        ""
                )
        );


        editor.putString(
                "pedido_cidade",
                json.optString(
                        "cidade",
                        ""
                )
        );


        editor.putString(
                "pedido_observacao",
                json.optString(
                        "observacao",
                        ""
                )
        );


        editor.putString(
                "pedido_endereco_completo",
                json.optString(
                        "enderecoCompleto",
                        ""
                )
        );


        editor.putString(
                "coleta_etapa",
                "ready"
        );


        editor.putString(
                "pedido_status",
                "waiting"
        );


        editor.apply();


        ultimoStatusConhecido =
                "waiting";


        ultimaEtapaConhecida =
                "ready";


        notificarSyncAtualizado();

        abrirPedidoSeForColetor();
    }


    // =====================================================
    // RECEBER PROPOSTA
    // =====================================================

    private void receberProposta(
            JSONObject json
    ) {

        SharedPreferences.Editor editor =
                preferencias.edit();


        editor.putString(
                "proposta_preco",
                json.optString(
                        "preco",
                        ""
                )
        );


        editor.putString(
                "proposta_mensagem",
                json.optString(
                        "mensagem",
                        ""
                )
        );


        editor.putString(
                "proposta_coletor_nome",
                json.optString(
                        "nome",
                        "Coletor"
                )
        );


        editor.putFloat(
                "proposta_coletor_avaliacao",
                (float) json.optDouble(
                        "avaliacao",
                        4.9
                )
        );


        editor.putInt(
                "proposta_coletor_coletas",
                json.optInt(
                        "coletas",
                        0
                )
        );


        editor.putString(
                "proposta_coletor_tipo",
                json.optString(
                        "tipoColetor",
                        ""
                )
        );


        editor.putString(
                "proposta_coletor_email",
                json.optString(
                        "email",
                        ""
                )
        );


        editor.putString(
                "pedido_status",
                "proposal"
        );


        editor.apply();


        ultimoStatusConhecido =
                "proposal";


        notificarSyncAtualizado();


        mostrarToastSeUsuario(
                "cliente",
                "Nova proposta recebida!"
        );
    }


    // =====================================================
    // RECEBER ACEITE
    // =====================================================

    private void receberPropostaAceita() {

        preferencias
                .edit()
                .putString(
                        "pedido_status",
                        "active"
                )
                .putString(
                        "coleta_etapa",
                        "ready"
                )
                .apply();


        ultimoStatusConhecido =
                "active";


        ultimaEtapaConhecida =
                "ready";


        notificarSyncAtualizado();


        String tipoUsuario =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        boolean ehColetor =
                "ambulante".equals(
                        tipoUsuario
                )
                        ||
                        "empresa".equals(
                                tipoUsuario
                        );


        if (!ehColetor) {

            return;
        }


        mainHandler.post(
                () -> {

                    Toast.makeText(
                            appContext,
                            "Proposta aceita pelo cliente!",
                            Toast.LENGTH_SHORT
                    ).show();


                    Intent intent =
                            new Intent(
                                    appContext,
                                    coleta_ativa_ambulante.class
                            );


                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    |
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );


                    appContext.startActivity(
                            intent
                    );
                }
        );
    }


    // =====================================================
    // RECEBER RECUSA
    // =====================================================

    private void receberPropostaRecusada() {

        SharedPreferences.Editor editor =
                preferencias.edit();


        limparProposta(
                editor
        );


        editor.putString(
                "pedido_status",
                "waiting"
        );


        editor.putString(
                "coleta_etapa",
                "ready"
        );


        editor.apply();


        ultimoStatusConhecido =
                "waiting";


        ultimaEtapaConhecida =
                "ready";


        notificarSyncAtualizado();


        String tipoUsuario =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        if (
                "ambulante".equals(
                        tipoUsuario
                )
                        ||
                        "empresa".equals(
                                tipoUsuario
                        )
        ) {

            mainHandler.post(
                    () -> Toast.makeText(
                            appContext,
                            "O cliente recusou a proposta.",
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }
    }


    // =====================================================
    // RECEBER ETAPA
    // =====================================================

    private void receberEtapaColeta(
            JSONObject json
    ) {

        String etapa =
                json.optString(
                        "coleta_etapa",
                        "ready"
                );


        preferencias
                .edit()
                .putString(
                        "pedido_status",
                        "active"
                )
                .putString(
                        "coleta_etapa",
                        etapa
                )
                .apply();


        ultimoStatusConhecido =
                "active";


        ultimaEtapaConhecida =
                etapa;


        notificarSyncAtualizado();
    }


    // =====================================================
    // RECEBER FINALIZAÇÃO
    // =====================================================

    private void receberColetaFinalizada() {

        preferencias
                .edit()
                .putString(
                        "pedido_status",
                        "done"
                )
                .putString(
                        "coleta_etapa",
                        "done"
                )
                .apply();


        ultimoStatusConhecido =
                "done";


        ultimaEtapaConhecida =
                "done";


        notificarSyncAtualizado();


        mostrarToastSeUsuario(
                "cliente",
                "Coleta finalizada!"
        );
    }


    // =====================================================
    // RECEBER CANCELAMENTO
    // =====================================================

    private void receberPedidoCancelado() {

        SharedPreferences.Editor editor =
                preferencias.edit();


        editor.remove(
                "pedido_status"
        );


        editor.remove(
                "coleta_etapa"
        );


        limparProposta(
                editor
        );


        editor.apply();


        ultimoStatusConhecido =
                "";


        ultimaEtapaConhecida =
                "";


        notificarSyncAtualizado();


        String tipoUsuario =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        if (
                "ambulante".equals(
                        tipoUsuario
                )
                        ||
                        "empresa".equals(
                                tipoUsuario
                        )
        ) {

            mainHandler.post(
                    () -> {

                        Toast.makeText(
                                appContext,
                                "O cliente cancelou o pedido.",
                                Toast.LENGTH_SHORT
                        ).show();


                        Class<?> telaInicial =
                                "empresa".equals(
                                        tipoUsuario
                                )
                                        ? tela_inicial_empresa.class
                                        : tela_inicial_ambulante.class;


                        Intent intent =
                                new Intent(
                                        appContext,
                                        telaInicial
                                );


                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        |
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                        );


                        appContext.startActivity(
                                intent
                        );
                    }
            );
        }
    }


    // =====================================================
    // LIMPAR PROPOSTA
    // =====================================================

    private void limparProposta(
            SharedPreferences.Editor editor
    ) {

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

        editor.remove(
                "proposta_coletor_tipo"
        );

        editor.remove(
                "proposta_coletor_email"
        );
    }


    // =====================================================
    // ABRIR PEDIDO NO COLETOR
    // =====================================================

    private void abrirPedidoSeForColetor() {

        String tipoUsuario =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        boolean ehColetor =
                "ambulante".equals(
                        tipoUsuario
                )
                        ||
                        "empresa".equals(
                                tipoUsuario
                        );


        if (!ehColetor) {

            return;
        }


        mainHandler.post(
                () -> {

                    Toast.makeText(
                            appContext,
                            "Novo pedido recebido!",
                            Toast.LENGTH_SHORT
                    ).show();


                    Intent intent =
                            new Intent(
                                    appContext,
                                    detalhes_pedido_ambulante.class
                            );


                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    |
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                    );


                    appContext.startActivity(
                            intent
                    );
                }
        );
    }


    // =====================================================
    // SINCRONIZAÇÃO DA TELA
    // =====================================================

    private void notificarSyncAtualizado() {

        SyncListener atual =
                syncListener;


        if (atual == null) {

            return;
        }


        mainHandler.post(
                atual::onSyncAtualizado
        );
    }


    private void mostrarToastSeUsuario(
            String tipoEsperado,
            String mensagem
    ) {

        String tipoAtual =
                preferencias.getString(
                        "usuario_tipo_atual",
                        ""
                );


        if (
                !tipoEsperado.equals(
                        tipoAtual
                )
        ) {

            return;
        }


        mainHandler.post(
                () -> Toast.makeText(
                        appContext,
                        mensagem,
                        Toast.LENGTH_SHORT
                ).show()
        );
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
    // CALLBACKS DA TELA BLUETOOTH
    // =====================================================

    private void notificarStatus(
            String status
    ) {

        Listener atual =
                listener;


        if (atual == null) {

            return;
        }


        mainHandler.post(
                () -> atual.onStatusAlterado(
                        status
                )
        );
    }


    private void notificarConectado(
            String nome
    ) {

        Listener atual =
                listener;


        if (atual == null) {

            return;
        }


        mainHandler.post(
                () -> atual.onConectado(
                        nome
                )
        );
    }


    private void notificarMensagem(
            String mensagem
    ) {

        Listener atual =
                listener;


        if (atual == null) {

            return;
        }


        mainHandler.post(
                () -> atual.onMensagemRecebida(
                        mensagem
                )
        );
    }


    private void notificarErro(
            String mensagem
    ) {

        Listener atual =
                listener;


        if (atual == null) {

            return;
        }


        mainHandler.post(
                () -> atual.onErro(
                        mensagem
                )
        );
    }
}