package com.example.coletai;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.content.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Multi-client RFCOMM star: every phone, including the host UI, uses the same server. */
final class BluetoothPlatform {
  private static final UUID SERVICE = UUID.fromString("eaab7f44-c521-4508-91e5-e762168af401");
  private static final ThreadLocal<Boolean> LOCAL = new ThreadLocal<>();
  private static volatile BluetoothPlatform instance;
  private final Context context;
  private final SharedPreferences settings;
  private final BluetoothAdapter adapter;
  private final BluetoothEngine engine;
  private final ExecutorService workers = Executors.newCachedThreadPool();
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final Set<Peer> peers = ConcurrentHashMap.newKeySet();
  private final Map<String, CompletableFuture<JSONObject>> pending = new ConcurrentHashMap<>();
  private volatile String mode = "", status = "Bluetooth offline desativado";
  private volatile Peer upstream;
  private volatile BluetoothServerSocket server;
  private volatile BluetoothSocket connecting;
  private volatile long generation;
  private volatile boolean connectingNow;
  private boolean startingServer;

  interface Work<T> { T run(); }
  static <T> T locally(Work<T> work) {
    Boolean previous = LOCAL.get(); LOCAL.set(true);
    try { return work.run(); } finally { if (previous == null) LOCAL.remove(); else LOCAL.set(previous); }
  }
  static boolean localExecution() { return Boolean.TRUE.equals(LOCAL.get()); }
  static boolean active() { return !localExecution() && instance != null && !instance.mode.isEmpty(); }
  static BluetoothPlatform get(Context context) {
    synchronized (BluetoothPlatform.class) { if (instance == null) instance = new BluetoothPlatform(context); }
    return instance;
  }
  static BluetoothPlatform current() {
    BluetoothPlatform value = instance;
    if (value == null) throw new IllegalStateException("Abra a configuração Bluetooth primeiro");
    return value;
  }
  private BluetoothPlatform(Context value) {
    context = value.getApplicationContext();
    settings = context.getSharedPreferences("bluetooth_transport", Context.MODE_PRIVATE);
    BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    adapter = manager == null ? null : manager.getAdapter();
    engine = new BluetoothEngine(context.getSharedPreferences("coletai_bluetooth_host", Context.MODE_PRIVATE));
    // Mode remains selected after process death; never silently falls back to local writes.
    mode = settings.getString("mode", "");
    status = mode.isEmpty() ? status : "Retome a conexão na configuração Bluetooth";
    timer.scheduleWithFixedDelay(() -> {
      if ("client".equals(mode) && upstream == null && !connectingNow) connectClient();
      else if ("host".equals(mode) && server == null && enabled()) startServer();
    }, 3, 3, TimeUnit.SECONDS);
  }
  String mode() { return mode; }
  int participantCount() { return peers.size(); }
  String status() {
    if ("host".equals(mode)) return status + " · " + peers.size() + " celular(es) conectado(s)";
    return status;
  }
  boolean ready() { return "host".equals(mode) && server != null || "client".equals(mode) && upstream != null; }
  @SuppressLint("MissingPermission") Set<BluetoothDevice> paired() {
    if (adapter == null) return Collections.emptySet();
    return adapter.getBondedDevices();
  }
  boolean supported() { return adapter != null; }
  @SuppressLint("MissingPermission") boolean enabled() {
    try { return adapter != null && adapter.isEnabled(); }
    catch (SecurityException error) { status = "Permita o acesso a dispositivos próximos para retomar a sessão"; return false; }
  }

  void host() {
    if (!enabled()) throw new IllegalStateException("Ative o Bluetooth primeiro");
    stopConnections(); mode = "host";
    String group = settings.getString("host_group", "");
    if (group.isEmpty()) group = UUID.randomUUID().toString();
    LocalJson.commit(settings.edit().putString("mode", mode).putString("host_group", group));
    startService(); startServer();
  }
  @SuppressLint("MissingPermission") void join(BluetoothDevice device) {
    if (!enabled() || device == null) throw new IllegalStateException("Selecione o coordenador e ative o Bluetooth");
    stopConnections(); mode = "client";
    LocalJson.commit(settings.edit().putString("mode", mode).putString("host_address", device.getAddress()));
    startService(); connectClient();
  }
  void resume() {
    if (mode.isEmpty()) return;
    if (!enabled()) { status = "Ative o Bluetooth para retomar a sessão"; return; }
    if (!BluetoothSessionService.running) startService();
    if ("host".equals(mode) && server == null) startServer(); else if ("client".equals(mode)) connectClient();
  }
  void leave() {
    mode = ""; settings.edit().remove("mode").commit(); stopConnections();
    status = "Bluetooth offline desativado";
    context.stopService(new Intent(context, BluetoothSessionService.class));
  }
  private void startService() {
    Intent intent = new Intent(context, BluetoothSessionService.class);
    androidx.core.content.ContextCompat.startForegroundService(context, intent);
  }
  private synchronized void stopConnections() {
    generation++;
    close(server); server = null; close(connecting); connecting = null;
    for (Peer peer : new ArrayList<>(peers)) peer.close();
    Peer old = upstream; upstream = null; if (old != null) old.close();
    failPending(); connectingNow = false; startingServer = false;
  }
  private void failPending() {
    for (CompletableFuture<JSONObject> future : pending.values())
      future.completeExceptionally(new IOException("Conexão interrompida. Reconecte e atualize antes de tentar novamente."));
    pending.clear();
  }
  @SuppressLint("MissingPermission") private synchronized void startServer() {
    if (startingServer || server != null || !"host".equals(mode)) return;
    startingServer = true;
    final long epoch = generation;
    status = "Abrindo sessão offline…";
    workers.execute(() -> {
      BluetoothServerSocket opened = null;
      try {
        opened = adapter.listenUsingRfcommWithServiceRecord("ColetaiOfflineV1", SERVICE);
        synchronized (this) {
          if (epoch != generation || !"host".equals(mode)) { close(opened); return; }
          server = opened;
        }
        status = "Coordenador pronto";
        while (epoch == generation && "host".equals(mode)) {
          BluetoothSocket socket = opened.accept();
          if (epoch != generation || peers.size() >= 6) { close(socket); continue; }
          Peer peer = new Peer(socket, true); peers.add(peer); workers.execute(peer::read);
        }
      } catch (IOException | SecurityException error) {
        if (epoch == generation) status = "Não foi possível manter o coordenador. Ative o Bluetooth e toque em Retomar.";
      } finally {
        close(opened); synchronized (this) { if (server == opened) server = null; if (epoch == generation) startingServer = false; }
      }
    });
  }
  @SuppressLint("MissingPermission") private synchronized void connectClient() {
    if (!"client".equals(mode) || upstream != null || connectingNow || !enabled()) return;
    connectingNow = true; final long epoch = generation;
    workers.execute(() -> {
      BluetoothSocket socket = null;
      try {
        status = "Conectando ao coordenador…";
        socket = adapter.getRemoteDevice(settings.getString("host_address", "")).createRfcommSocketToServiceRecord(SERVICE);
        synchronized (this) { if (epoch != generation) { close(socket); return; } connecting = socket; }
        BluetoothSocket attempt = socket;
        ScheduledFuture<?> timeout = timer.schedule(() -> close(attempt), 15, TimeUnit.SECONDS);
        try { socket.connect(); } finally { timeout.cancel(false); }
        Peer peer = new Peer(socket, false);
        synchronized (this) {
          if (epoch != generation) { peer.close(); return; }
          upstream = peer;
        }
        status = "Conectado ao coordenador"; peer.read();
      } catch (IOException | RuntimeException error) {
        close(socket);
        if (epoch == generation) status = "Coordenador desconectado. Tentando reconectar…";
      } finally {
        synchronized (this) { if (epoch == generation) { connectingNow = false; connecting = null; } }
      }
    });
  }

  JSONObject request(String operation, JSONObject payload, String token) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) throw new IllegalStateException("Aguarde a operação em segundo plano");
    JSONObject command = new JSONObject();
    LocalJson.put(command, "version", 1); LocalJson.put(command, "id", UUID.randomUUID().toString());
    LocalJson.put(command, "operation", operation); LocalJson.put(command, "payload", payload == null ? new JSONObject() : payload);
    LocalJson.put(command, "token", token);
    boolean durable = "action".equals(operation) || "photo.upload".equals(operation) || "register".equals(operation);
    String pendingKey = "";
    if (durable) {
      JSONObject identity = LocalJson.object(command.toString()); identity.remove("id");
      LocalJson.put(identity, "host", "host".equals(mode) ? settings.getString("host_group", "") : settings.getString("host_address", ""));
      try {
        pendingKey = "pending_" + BluetoothBytes.encode(java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
      String savedId = settings.getString(pendingKey, command.optString("id"));
      LocalJson.put(command, "id", savedId);
      LocalJson.commit(settings.edit().putString(pendingKey, savedId));
    }
    JSONObject reply;
    if ("host".equals(mode)) reply = engine.execute(command);
    else {
      Peer peer = upstream;
      if (!"client".equals(mode) || peer == null) throw new IllegalStateException("Sem conexão com o coordenador. Aguarde a reconexão; nenhuma alteração local será feita.");
      CompletableFuture<JSONObject> future = new CompletableFuture<>();
      pending.put(command.optString("id"), future);
      try {
        peer.send(command);
        try { reply = future.get(12, TimeUnit.SECONDS); }
        catch (TimeoutException timeout) {
          // Same ID: host deduplication prevents a duplicate order after a lost reply.
          peer.send(command); reply = future.get(12, TimeUnit.SECONDS);
        }
      } catch (Exception error) {
        throw new IllegalStateException("Não foi possível confirmar a operação. Atualize a lista antes de tentar novamente.", error);
      } finally { pending.remove(command.optString("id")); }
    }
    if (durable) LocalJson.commit(settings.edit().remove(pendingKey));
    if (!reply.optBoolean("ok")) {
      if (reply.optInt("code") == 401 && !"login".equals(operation)) throw new PlatformGateway.SessionExpired();
      throw new IllegalStateException(reply.optString("error", "Falha na operação Bluetooth"));
    }
    return reply;
  }

  private final class Peer {
    final BluetoothSocket socket; final boolean client;
    Peer(BluetoothSocket socket, boolean client) { this.socket = socket; this.client = client; }
    synchronized void send(JSONObject value) throws IOException { BluetoothFrames.write(socket.getOutputStream(), value); }
    void read() {
      try {
        while (true) {
          JSONObject frame = BluetoothFrames.read(socket.getInputStream());
          if (frame == null) break;
          if (client) send(engine.execute(frame));
          else {
            CompletableFuture<JSONObject> future = pending.get(frame.optString("id"));
            if (future != null) future.complete(frame);
          }
        }
      } catch (IOException | RuntimeException ignored) {
        // Socket closure unblocks the reader; clients reconnect without changing cached data.
      } finally { close(); }
    }
    void close() {
      BluetoothPlatform.close(socket); peers.remove(this);
      if (upstream == this) { upstream = null; status = "Conexão perdida. Reconectando…"; failPending(); }
    }
  }
  private static void close(Closeable value) { if (value != null) try { value.close(); } catch (IOException ignored) { } }
}
