package com.example.coletai;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private volatile BluetoothSocket socket;

    private volatile Thread threadServidor;
    private long geracaoConexao;
    private BluetoothSocket socketConectando;
    private boolean servidorDesejado;
    private String mensagemRecebidaId;
    private volatile Thread threadConexao;
    private final ExecutorService envioExecutor = Executors.newSingleThreadExecutor();
    private final PedidoRepository pedidos;
    private final EventStore eventos;
    private boolean aplicandoRemoto;
    private boolean sessaoAtiva = true;
    private String peerAtual = "";
    private final Runnable retryEnvio = this::reenviarPendentes;

    private volatile boolean conectado = false;

    private volatile Listener listener;
    private final java.util.Set<SyncListener> syncListeners = new java.util.concurrent.CopyOnWriteArraySet<>();

    private volatile String nomeDispositivoConectado = "";

    // =====================================================
    // ESTADO LOCAL PARA SABER QUAL EVENTO ENVIAR
    // =====================================================

    private String ultimoStatusConhecido;
    private String ultimaEtapaConhecida;

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

        pedidos = new PedidoRepository(preferencias);
        eventos = new EventStore(preferencias);

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
                    if (aplicandoRemoto) return;

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

    public void addSyncListener(SyncListener listener) { syncListeners.add(listener); }
    public void removeSyncListener(SyncListener listener) { syncListeners.remove(listener); }

    // =====================================================
    // DETECTAR ALTERAÇÃO LOCAL DE STATUS
    // =====================================================

    private void processarAlteracaoStatusLocal() {
        String atual = preferencias.getString("pedido_status", "");
        String anterior = ultimoStatusConhecido;
        ultimoStatusConhecido = atual;
        notificarSyncAtualizado();
        if (atual.equals(anterior) || !sessaoAtiva) return;
        String perfil = preferencias.getString("usuario_tipo_atual", "");
        if ("done".equals(atual)) pedidos.arquivar("concluido");
        if ("cliente".equals(perfil)) {
            if ("waiting".equals(atual)) {
                if ("proposal".equals(anterior)) enviarEventoSimples("PROPOSTA_RECUSADA");
                else enviarPedidoAtual();
            } else if ("active".equals(atual) && "proposal".equals(anterior)) {
                enviarPropostaAceita();
            } else if (atual.isEmpty() && !anterior.isEmpty() && !"done".equals(anterior)) {
                enviarEventoSimples("PEDIDO_CANCELADO");
            }
        } else if (PedidoRules.coletor(perfil) && "proposal".equals(atual)) {
            enviarPropostaAtual();
        }
    }

    // =====================================================
    // DETECTAR ALTERAÇÃO LOCAL DA COLETA
    // =====================================================

    private void processarAlteracaoEtapaLocal() {
        String atual = preferencias.getString("coleta_etapa", "");
        String anterior = ultimaEtapaConhecida;
        ultimaEtapaConhecida = atual;
        notificarSyncAtualizado();
        if (!sessaoAtiva || atual.equals(anterior)
                || !PedidoRules.coletor(preferencias.getString("usuario_tipo_atual", ""))) return;
        if ("going".equals(atual) || "collecting".equals(atual)) enviarEtapaColeta(atual);
        else if ("done".equals(atual)) enviarColetaFinalizada();
    }

    public void pausarSessao() {
        sessaoAtiva = false;
        mainHandler.removeCallbacks(retryEnvio);
    }

    public void selecionarPerfil(String perfil) {
        selecionarConta(perfil);
    }

    public void selecionarConta(String account) {
        ProfileRepository profiles = new ProfileRepository(preferencias);
        JSONObject profile = profiles.get(account);
        if (!profiles.available(account)) throw new IllegalArgumentException("Conta bloqueada ou excluída");
        aplicandoRemoto = true;
        try {
            pedidos.selecionarConta(account, profile.optString("role"));
            SharedPreferences.Editor editor = preferencias.edit();
            ProfileRepository.mirror(editor, profile);
            LocalJson.commit(editor);
            ultimoStatusConhecido = preferencias.getString("pedido_status", "");
            ultimaEtapaConhecida = preferencias.getString("coleta_etapa", "");
        } finally {
            aplicandoRemoto = false;
        }
        sessaoAtiva = !"admin".equals(profile.optString("role"));
        reenviarPendentes();
    }

    // =====================================================
    // ENVIAR NOVO PEDIDO
    // =====================================================

    public void enviarPedidoAtual() {
        try { enviar(PedidoMessages.pedido(preferencias).toString()); }
        catch (JSONException e) { notificarErro("Falha ao preparar evento de coleta."); }
    }

    // =====================================================
    // ENVIAR PROPOSTA
    // =====================================================

    public void enviarPropostaAtual() {
        try { enviar(PedidoMessages.proposta(preferencias).toString()); }
        catch (JSONException e) { notificarErro("Falha ao preparar evento de coleta."); }
    }

    // =====================================================
    // ENVIAR ACEITE
    // =====================================================

    private void enviarPropostaAceita() {
        try { enviar(PedidoMessages.evento("PROPOSTA_ACEITA").toString()); }
        catch (JSONException e) { notificarErro("Falha ao preparar evento de coleta."); }
    }

    // =====================================================
    // ENVIAR ETAPA
    // =====================================================

    private void enviarEtapaColeta(String etapa) {
        try { enviar(PedidoMessages.etapa(etapa).toString()); }
        catch (JSONException e) { notificarErro("Falha ao preparar evento de coleta."); }
    }

    // =====================================================
    // ENVIAR FINALIZAÇÃO
    // =====================================================

    private void enviarColetaFinalizada() {
        try { enviar(PedidoMessages.evento("COLETA_FINALIZADA").toString()); }
        catch (JSONException e) { notificarErro("Falha ao preparar evento de coleta."); }
    }

    // =====================================================
    // EVENTO SIMPLES
    // =====================================================

    private void enviarEventoSimples(String evento) {
        try { enviar(PedidoMessages.evento(evento).toString()); }
        catch (JSONException e) { notificarErro("Falha ao preparar evento de coleta."); }
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
        if (bluetoothAdapter == null || !bluetoothAtivado()) return;
        servidorDesejado = true;
        if (conectado || (threadServidor != null && threadServidor.isAlive())) return;
        final long geracao = geracaoConexao;
        threadServidor = new Thread(() -> {
            BluetoothServerSocket servidor = null;
            try {
                servidor = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NOME_SERVICO, UUID_COLETAI);
                synchronized (this) {
                    if (geracao != geracaoConexao || conectado || !servidorDesejado) return;
                    serverSocket = servidor;
                }
                notificarStatus("Aguardando outro celular...");
                BluetoothSocket recebido = servidor.accept();
                definirConexao(recebido, geracao);
            } catch (SecurityException e) {
                notificarErro("Sem permissão para usar o Bluetooth.");
                synchronized (this) { servidorDesejado = false; }
            } catch (IOException e) {
                // O fechamento do servidor também interrompe accept().
            } finally {
                if (servidor != null) try { servidor.close(); } catch (IOException ignored) { }
                synchronized (this) {
                    if (serverSocket == servidor) serverSocket = null;
                    if (threadServidor == Thread.currentThread()) threadServidor = null;
                }
                mainHandler.postDelayed(() -> {
                    synchronized (BluetoothColetai.this) {
                        if (geracao == geracaoConexao && servidorDesejado && !conectado) iniciarServidor();
                    }
                }, 1000);
            }
        }, "Coletai-Bluetooth-Servidor");
        threadServidor.start();
    }

    // =====================================================
    // CONECTAR
    // =====================================================

    @SuppressLint("MissingPermission")
    public synchronized void conectar(BluetoothDevice dispositivo) {
        if (dispositivo == null || bluetoothAdapter == null || !bluetoothAtivado()) {
            notificarErro("Selecione um dispositivo e ative o Bluetooth.");
            return;
        }
        if (conectado || (threadConexao != null && threadConexao.isAlive())) return;
        final long geracao = geracaoConexao;
        threadConexao = new Thread(() -> {
            BluetoothSocket novo = null;
            try {
                notificarStatus("Conectando a " + obterNomeDispositivo(dispositivo) + "...");
                novo = dispositivo.createRfcommSocketToServiceRecord(UUID_COLETAI);
                synchronized (this) {
                    if (geracao != geracaoConexao) {
                        fecharSocketSilenciosamente(novo);
                        return;
                    }
                    socketConectando = novo;
                }
                novo.connect();
                definirConexao(novo, geracao);
            } catch (SecurityException | IOException e) {
                fecharSocketSilenciosamente(novo);
                synchronized (this) {
                    if (geracao == geracaoConexao && !conectado) {
                        notificarErro("Não foi possível conectar. Verifique o pareamento e a permissão Bluetooth.");
                        iniciarServidor();
                    }
                }
            } finally {
                synchronized (this) {
                    if (socketConectando == novo) socketConectando = null;
                    if (threadConexao == Thread.currentThread()) threadConexao = null;
                }
            }
        }, "Coletai-Bluetooth-Conectar");
        threadConexao.start();
    }

    // =====================================================
    // DEFINIR CONEXÃO
    // =====================================================

    @SuppressLint("MissingPermission")
    private synchronized void definirConexao(BluetoothSocket novoSocket, long geracao) {
        if (novoSocket == null) return;
        if (geracao != geracaoConexao) {
            fecharSocketSilenciosamente(novoSocket);
            return;
        }
        if (socket != null && conectado) {
            fecharSocketSilenciosamente(novoSocket);
            return;
        }
        if (socket != null && socket != novoSocket) fecharSocketSilenciosamente(socket);
        socket = novoSocket;
        conectado = true;
        peerAtual = novoSocket.getRemoteDevice().getAddress();
        nomeDispositivoConectado = obterNomeDispositivo(novoSocket.getRemoteDevice());
        fecharServidor();
        notificarConectado(nomeDispositivoConectado);
        iniciarLeitura();
        mainHandler.post(this::reenviarPendentes);
    }

    // =====================================================
    // LEITURA
    // =====================================================

    private synchronized void iniciarLeitura() {
        final BluetoothSocket socketAtual = socket;
        if (socketAtual == null) return;
        new Thread(() -> {
            try (BufferedReader leitor = new BufferedReader(new InputStreamReader(
                    socketAtual.getInputStream(), StandardCharsets.UTF_8))) {
                String mensagem;
                while ((mensagem = MessageFrames.read(leitor)) != null) {
                    final String recebida = mensagem;
                    mainHandler.post(() -> {
                        if (socket != socketAtual) return;
                        processarMensagemRecebida(recebida);
                        notificarMensagem(recebida);
                    });
                }
            } catch (IOException e) {
                // EOF e falhas de leitura têm o mesmo tratamento de encerramento.
            } finally {
                mainHandler.post(() -> conexaoPerdida(socketAtual));
            }
        }, "Coletai-Bluetooth-Leitura").start();
    }

    // =====================================================
    // PROCESSAR MENSAGEM
    // =====================================================

    private void processarMensagemRecebida(String mensagem) {
        try {
            JSONObject json = new JSONObject(mensagem);
            String evento = json.optString("evento");
            if ("TESTE".equals(evento)) return;
            if (!sessaoAtiva) return;
            String perfil = preferencias.getString("usuario_tipo_atual", "");
            if ("ACK".equals(evento)) {
                JSONObject original = eventos.acknowledge(json.optString("mensagem_id"), peerAtual, perfil, LocalJson.account(preferencias));
                if (original != null) {
                    if (!json.optBoolean("aceito", false)) {
                        if (original.optString("pedido_id").equals(preferencias.getString("pedido_id", ""))) {
                            preferencias.edit().putString("pedido_entrega", "rejected").apply();
                        }
                        notificarErro("O outro celular recusou o evento: " + original.optString("evento"));
                    } else if ("NOVO_PEDIDO".equals(original.optString("evento"))
                            && original.optString("pedido_id").equals(preferencias.getString("pedido_id", ""))) {
                        preferencias.edit().putString("pedido_entrega", "delivered").apply();
                    }
                    notificarSyncAtualizado();
                    reenviarPendentes();
                }
                return;
            }
            String id = json.optString("mensagem_id");
            String pedidoId = json.optString("pedido_id");
            if (json.optInt("versao") != 1 || id.isEmpty() || pedidoId.isEmpty()) {
                notificarErro("Protocolo incompatível. Atualize o Coletaí nos dois celulares.");
                return;
            }
            if (eventos.processed(LocalJson.account(preferencias), peerAtual + "_" + id)) {
                confirmar(id, true);
                return;
            }
            String remetente = json.optString("remetente");
            boolean origemValida = "cliente".equals(perfil) ? PedidoRules.coletor(remetente)
                    : PedidoRules.coletor(perfil) && "cliente".equals(remetente);
            boolean novo = "NOVO_PEDIDO".equals(evento);
            boolean mesmoPedido = pedidoId.equals(preferencias.getString("pedido_id", ""));
            String peerPedido = preferencias.getString("pedido_peer", "");
            if (!conteudoValido(json) || (novo && pedidos.arquivado(pedidoId))
                    || !origemValida || (!novo && (!mesmoPedido || !peerAtual.equals(peerPedido)))
                    || !PedidoRules.podeReceber(evento, perfil,
                    preferencias.getString("pedido_status", ""),
                    preferencias.getString("coleta_etapa", "ready"), json.optString("coleta_etapa"))) {
                confirmar(id, false);
                return;
            }
            aplicandoRemoto = true;
            try {
                mensagemRecebidaId = id;
                if ("PEDIDO_CANCELADO".equals(evento)) pedidos.arquivar("cancelado");
                executarEvento(json);
                if ("COLETA_FINALIZADA".equals(evento)) pedidos.arquivar("concluido");

            } finally {
                aplicandoRemoto = false;
                mensagemRecebidaId = null;
                ultimoStatusConhecido = preferencias.getString("pedido_status", "");
                ultimaEtapaConhecida = preferencias.getString("coleta_etapa", "");
            }
            confirmar(id, true);
        } catch (JSONException | IllegalStateException e) {
            notificarErro("Mensagem inválida ou falha ao salvar: " + e.getMessage());
        }
    }

    private void confirmar(String id, boolean aceito) throws JSONException {
        JSONObject ack = new JSONObject();
        ack.put("evento", "ACK");
        ack.put("mensagem_id", id);
        ack.put("aceito", aceito);
        transmitir(ack.toString());
    }

    private void executarEvento(JSONObject json) {
        String evento = json.optString("evento");
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

    }

    // =====================================================
    // RECEBER NOVO PEDIDO
    // =====================================================

    private void receberNovoPedido(JSONObject json) {
        SharedPreferences.Editor editor = editorRemoto();
        PedidoRepository.limparProposta(editor);
        for (String key : new String[]{"tipo", "emoji", "descricao", "peso", "regiao", "cidade", "observacao"}) {
            editor.putString("pedido_" + key, json.optString(key));
        }
        editor.putString("pedido_endereco_completo", json.optString("enderecoCompleto"));
        editor.putString("pedido_id", json.optString("pedido_id"));
        editor.putString("pedido_peer", peerAtual);
        editor.putString("coleta_etapa", "ready");
        editor.putString("pedido_status", "waiting");
        salvarRemoto(editor);
    }

    // =====================================================
    // RECEBER PROPOSTA
    // =====================================================

    private void receberProposta(JSONObject json) {
        SharedPreferences.Editor editor = editorRemoto();
        editor.putString("proposta_preco", json.optString("preco"));
        editor.putString("proposta_mensagem", json.optString("mensagem"));
        editor.putString("proposta_coletor_nome", json.optString("nome", "Coletor"));
        editor.putFloat("proposta_coletor_avaliacao", (float) json.optDouble("avaliacao", 0));
        editor.putInt("proposta_coletor_coletas", json.optInt("coletas", 0));
        editor.putString("proposta_coletor_tipo", json.optString("tipoColetor"));
        editor.putString("proposta_coletor_email", json.optString("email"));
        editor.putString("pedido_status", "proposal");
        salvarRemoto(editor);
        mostrarToastSeUsuario("cliente", "Nova proposta recebida!");
    }

    // =====================================================
    // RECEBER ACEITE
    // =====================================================

    private void receberPropostaAceita() {
        salvarRemoto(editorRemoto().putString("pedido_status", "active").putString("coleta_etapa", "ready"));
    }

    // =====================================================
    // RECEBER RECUSA
    // =====================================================

    private void receberPropostaRecusada() {
        SharedPreferences.Editor editor = editorRemoto();
        PedidoRepository.limparProposta(editor);
        salvarRemoto(editor.putString("pedido_status", "waiting").putString("coleta_etapa", "ready"));
    }

    // =====================================================
    // RECEBER ETAPA
    // =====================================================

    private void receberEtapaColeta(JSONObject json) {
        salvarRemoto(editorRemoto().putString("pedido_status", "active")
                .putString("coleta_etapa", json.optString("coleta_etapa")));
    }

    // =====================================================
    // RECEBER FINALIZAÇÃO
    // =====================================================

    private void receberColetaFinalizada() {
        SharedPreferences.Editor editor = editorRemoto();
        pedidos.arquivar("concluido", editor);
        salvarRemoto(editor.putString("pedido_status", "done").putString("coleta_etapa", "done"));
        mostrarToastSeUsuario("cliente", "Coleta finalizada!");
    }

    // =====================================================
    // RECEBER CANCELAMENTO
    // =====================================================

    private void receberPedidoCancelado() {
        SharedPreferences.Editor editor = editorRemoto();
        PedidoRepository.limparProposta(editor);
        salvarRemoto(editor.remove("pedido_status").remove("coleta_etapa"));
    }

    // =====================================================
    // PERSISTÊNCIA DOS EVENTOS RECEBIDOS
    // =====================================================

    private SharedPreferences.Editor editorRemoto() {
        String role = LocalJson.account(preferencias);
        return preferencias.edit().putBoolean("sync_received_" + role + "_" + peerAtual + "_" + mensagemRecebidaId, true);
    }

    private void salvarRemoto(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Falha ao salvar pedido recebido");
        notificarSyncAtualizado();
    }

    private boolean conteudoValido(JSONObject json) {
        try {
            if ("NOVO_PEDIDO".equals(json.optString("evento"))) {
                String peso = json.optString("peso").replace(" kg", "");
                DecimalValues.positive(peso, 3);
                return !json.optString("tipo").trim().isEmpty()
                        && !json.optString("descricao").trim().isEmpty()
                        && !json.optString("enderecoCompleto").trim().isEmpty();
            }
            if ("PROPOSTA".equals(json.optString("evento"))) {
                DecimalValues.positive(json.optString("preco"), 2);
                return PedidoRules.coletor(json.optString("tipoColetor"));
            }
            return true;
        } catch (IllegalArgumentException e) { return false; }
    }

    // =====================================================
    // SINCRONIZAÇÃO DA TELA
    // =====================================================

    private void notificarSyncAtualizado() {
        mainHandler.post(() -> {
            for (SyncListener observer : syncListeners) observer.onSyncAtualizado();
        });
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

    public void enviar(String mensagem) {
        if (mensagem == null || mensagem.trim().isEmpty()) return;
        try {
            JSONObject event = new JSONObject(mensagem);
            if ("TESTE".equals(event.optString("evento"))) {
                if (!estaConectado()) notificarErro("Nenhum celular conectado.");
                else transmitir(mensagem);
                return;
            }
            String id = preferencias.getString("pedido_id", "");
            if (id.isEmpty()) {
                notificarErro("Pedido antigo sem identificação. Cancele e crie um novo pedido.");
                return;
            }
            event.put("conta_id", LocalJson.account(preferencias));
            eventos.add(event, preferencias.getString("usuario_tipo_atual", ""),
                    id, preferencias.getString("pedido_peer", ""));
            reenviarPendentes();
        } catch (JSONException | IllegalStateException e) {
            notificarErro("Não foi possível colocar o evento na fila: " + e.getMessage());
        }
    }

    private void reenviarPendentes() {
        mainHandler.removeCallbacks(retryEnvio);
        if (!sessaoAtiva || !estaConectado()) return;
        try {
            JSONObject event = eventos.next(preferencias.getString("usuario_tipo_atual", ""), peerAtual, LocalJson.account(preferencias));
            if (event == null) return;
            if (event.optString("pedido_id").equals(preferencias.getString("pedido_id", ""))) {
                preferencias.edit().putString("pedido_peer", peerAtual).apply();
            }
            transmitir(event.toString());
            mainHandler.postDelayed(retryEnvio, 5000);
        } catch (JSONException | IllegalStateException e) {
            notificarErro("Não foi possível recuperar a fila de mensagens.");
        }
    }

    private void transmitir(String mensagem) {
        final BluetoothSocket atual = socket;
        if (atual == null) return;
        envioExecutor.execute(() -> {
            if (socket != atual) return;
            try {
                OutputStream out = atual.getOutputStream();
                out.write((mensagem + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                mainHandler.post(() -> conexaoPerdida(atual));
            }
        });
    }

    // =====================================================
    // CONEXÃO PERDIDA
    // =====================================================

    private synchronized void conexaoPerdida(BluetoothSocket origem) {
        if (socket != origem) return;
        fecharSocketSilenciosamente(origem);
        socket = null;
        conectado = false;
        nomeDispositivoConectado = "";
        mainHandler.removeCallbacks(retryEnvio);
        notificarStatus("Conexão encerrada. Eventos pendentes serão reenviados ao reconectar.");
        iniciarServidor();
    }

    // =====================================================
    // DESCONECTAR
    // =====================================================

    public synchronized void desconectar() {
        geracaoConexao++;
        servidorDesejado = false;
        fecharSocketSilenciosamente(socketConectando);
        socketConectando = null;
        BluetoothSocket antigo = socket;
        socket = null;
        conectado = false;
        fecharSocketSilenciosamente(antigo);
        fecharServidor();
        nomeDispositivoConectado = "";
        mainHandler.removeCallbacks(retryEnvio);
        notificarStatus("Desconectado");
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
                () -> { if (listener == atual) atual.onStatusAlterado(
                        status
                ); }
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
                () -> { if (listener == atual) atual.onConectado(
                        nome
                ); }
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
                () -> { if (listener == atual) atual.onMensagemRecebida(
                        mensagem
                ); }
        );
    }

    private void notificarErro(String mensagem) {
        mainHandler.post(() -> {
            Listener atual = listener;
            if (atual != null) atual.onErro(mensagem);
            else Toast.makeText(appContext, mensagem, Toast.LENGTH_LONG).show();
        });
    }
}
