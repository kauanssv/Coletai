package com.example.coletai;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cadastro único usado pelo login, perfis e painel administrativo. */
public final class ProfileRepository {
  private static final String KEY = "profiles_v1";
  private final SharedPreferences prefs;

  public ProfileRepository(SharedPreferences prefs) {
    this.prefs = prefs;
    initialize();
  }

  private void initialize() {
    if (prefs.contains(KEY)) return;
    JSONArray profiles = new JSONArray();
    profiles.put(
        seed(
            "cliente", "Lucas Ferreira", "cliente@gmail.com", "(11) 99999-1234", "123.456.789-00"));
    profiles.put(
        seed(
            "ambulante",
            "João Santos",
            "ambulante@gmail.com",
            "(11) 98888-5678",
            "987.654.321-00"));
    profiles.put(
        seed(
            "empresa",
            "Carlos Oliveira",
            "empresa@gmail.com",
            "(11) 3333-4444",
            "12.345.678/0001-90"));
    profiles.put(seed("admin", "Administrador", "admin@coletai.app", "", ""));
    LocalJson.commit(prefs.edit().putString(KEY, profiles.toString()));
  }

  private JSONObject seed(String role, String name, String email, String phone, String document) {
    JSONObject p = new JSONObject();
    LocalJson.put(p, "id", role);
    LocalJson.put(p, "role", role);
    LocalJson.put(p, "name", prefs.getString(role + "_nome", name));
    LocalJson.put(p, "email", prefs.getString(role + "_email", email));
    LocalJson.put(p, "phone", prefs.getString(role + "_telefone", phone));
    LocalJson.put(p, "document", prefs.getString(role + "_documento", document));
    LocalJson.put(
        p,
        "company",
        "empresa".equals(role) ? prefs.getString("empresa_nome_fantasia", "EcoColeta") : "");
    LocalJson.put(
        p,
        "legal_name",
        "empresa".equals(role)
            ? prefs.getString("empresa_razao_social", "EcoColeta Serviços Ambientais Ltda.")
            : "");
    LocalJson.put(
        p, "address", "cliente".equals(role) ? prefs.getString("endereco_salvo_cliente", "") : "");
    LocalJson.put(p, "status", "active");
    LocalJson.put(p, "revision", 1);
    LocalJson.put(p, "created_at", System.currentTimeMillis());
    return p;
  }

  public List<JSONObject> list() {
    JSONArray array = LocalJson.array(prefs.getString(KEY, "[]"));
    List<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) result.add(array.optJSONObject(i));
    return result;
  }

  public JSONObject get(String id) {
    for (JSONObject p : list()) if (id.equals(p.optString("id"))) {
      if (!PlatformGateway.configured()) {
        ReviewStats ratings = ReviewStats.calculate(LocalJson.array(prefs.getString(CollectionRepository.ORDERS, "[]")), id);
        LocalJson.put(p, "rating_count", ratings.count);
        LocalJson.put(p, "rating_average", ratings.average);
      }
      return p;
    }
    throw new IllegalArgumentException("Conta não encontrada");
  }

  public JSONObject current() {
    return get(LocalJson.account(prefs));
  }

  public boolean available(String id) {
    try {
      return "active".equals(get(id).optString("status"));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public JSONObject login(String email) {
    for (JSONObject p : list()) {
      if (email.trim().equalsIgnoreCase(p.optString("email"))) {
        if (!"active".equals(p.optString("status"))) {
          throw new IllegalArgumentException(
              "Esta conta está bloqueada ou excluída. Consulte o administrador.");
        }
        return p;
      }
    }
    throw new IllegalArgumentException("Conta não encontrada. Confira o e-mail.");
  }

  public JSONObject authenticate(String email, String password) {
    if (PlatformGateway.configured()) return PlatformGateway.login(prefs, email, password);
    if (!BuildConfig.DEBUG)
      throw new IllegalStateException(
          "Configure o endereço HTTPS da API para utilizar esta versão");
    JSONObject account = login(email);
    String key = "credential_" + account.optString("id");
    if (!prefs.contains(key)
        && java.util.Arrays.asList("cliente", "ambulante", "empresa", "admin")
            .contains(account.optString("id"))) {
      LocalJson.commit(prefs.edit().putString(key, PasswordHashes.demoHash()));
    }
    String candidate =
        "12345".equals(password)
                && java.util.Arrays.asList("cliente", "ambulante", "empresa", "admin")
                    .contains(account.optString("id"))
            ? "12345-demo"
            : password;
    if (!PasswordHashes.verify(candidate, prefs.getString(key, "")))
      throw new IllegalArgumentException("E-mail ou senha incorretos");
    return account;
  }

  public void register(JSONObject input, String password) {
    registerInternal(input, password, false);
  }

  private void registerInternal(JSONObject input, String password, boolean adminAction) {
    if (PlatformGateway.configured()) {
      JSONObject p = LocalJson.object(input.toString());
      LocalJson.put(p, "password", password);
      PlatformGateway.register(prefs, p);
      return;
    }
    String encoded = PasswordHashes.hash(password);
    String role = input.optString("role"),
        name = input.optString("name").trim(),
        email = input.optString("email").trim().toLowerCase(Locale.ROOT);
    if (!"cliente".equals(role) && !PedidoRules.coletor(role))
      throw new IllegalArgumentException("Selecione cidadão, coletor independente ou cooperativa");
    if (name.isEmpty() || name.length() > 100 || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Informe nome e e-mail válidos");
    validateProfileLengths(input);
    for (JSONObject profile : list()) {
      if (email.equalsIgnoreCase(profile.optString("email")))
        throw new IllegalArgumentException("E-mail já cadastrado");
      if ("empresa".equals(role)
          && input
              .optString("document")
              .replaceAll("\\D", "")
              .equals(profile.optString("document").replaceAll("\\D", "")))
        throw new IllegalArgumentException("CNPJ já cadastrado");
    }
    if ("empresa".equals(role)
        && (input.optString("company").trim().isEmpty()
            || !input.optString("document").replaceAll("\\D", "").matches("[0-9]{14}")))
      throw new IllegalArgumentException("Informe nome da cooperativa e CNPJ com 14 dígitos");
    JSONObject after = new JSONObject();
    for (String field :
        new String[] {"name", "email", "phone", "company", "document", "legal_name", "address", "description"})
      LocalJson.put(after, field, input.optString(field).trim());
    LocalJson.put(after, "name", name);
    LocalJson.put(after, "email", email);
    LocalJson.put(after, "role", role);
    LocalJson.put(after, "status", "active");
    LocalJson.put(after, "id", UUID.randomUUID().toString());
    LocalJson.put(after, "revision", 1);
    LocalJson.put(after, "created_at", System.currentTimeMillis());
    JSONArray output = new JSONArray();
    for (JSONObject profile : list()) output.put(profile);
    output.put(after);
    SharedPreferences.Editor editor =
        prefs
            .edit()
            .putString(KEY, output.toString())
            .putString("credential_" + after.optString("id"), encoded);
    new AuditRepository(prefs)
        .appendAs(
            editor,
            adminAction ? LocalJson.account(prefs) : after.optString("id"),
            "Conta cadastrada",
            "profile",
            after.optString("id"),
            null,
            after);
    LocalJson.commit(editor);
  }

  public void changePassword(String currentPassword, String newPassword) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      LocalJson.put(p, "id", LocalJson.account(prefs));
      LocalJson.put(p, "current_password", currentPassword);
      LocalJson.put(p, "password", newPassword);
      PlatformGateway.mutate(prefs, "profile.password", p);
      return;
    }
    JSONObject account = current();
    authenticate(account.optString("email"), currentPassword);
    LocalJson.commit(
        prefs
            .edit()
            .putString("credential_" + account.optString("id"), PasswordHashes.hash(newPassword)));
  }

  public static void validatePasswordChange(String current, String password, String confirmation) {
    if (current.isEmpty() && password.isEmpty() && confirmation.isEmpty()) return;
    if (!password.equals(confirmation)) throw new IllegalArgumentException("As senhas não são iguais");
    if (password.length() < 8 || password.length() > 128) throw new IllegalArgumentException("Use uma senha de 8 a 128 caracteres");
    if (current.isEmpty()) throw new IllegalArgumentException("Informe a senha atual");
  }

  /** Personal data and optional password are committed together, including offline sessions. */
  public void updateAccount(JSONObject changed, String current, String password, String confirmation) {
    validatePasswordChange(current, password, confirmation);
    if (!LocalJson.account(prefs).equals(changed.optString("id"))) throw new SecurityException("Conta de outra pessoa");
    String role = current().optString("role"), document = changed.optString("document").replaceAll("\\D", "");
    if (!"admin".equals(role) && !document.matches("empresa".equals(role) ? "[0-9]{14}" : "[0-9]{11}"))
      throw new IllegalArgumentException("empresa".equals(role) ? "Informe um CNPJ com 14 dígitos" : "Informe um CPF com 11 dígitos");
    if (PlatformGateway.configured()) {
      JSONObject payload = LocalJson.object(changed.toString());
      LocalJson.put(payload, "current_password", current);
      LocalJson.put(payload, "password", password);
      LocalJson.put(payload, "password_confirmation", confirmation);
      PlatformGateway.mutate(prefs, "profile.account", payload);
      return;
    }
    SnapshotPreferences transaction = new SnapshotPreferences(prefs);
    ProfileRepository staged = new ProfileRepository(transaction);
    if (!password.isEmpty()) staged.changePassword(current, password);
    staged.save(changed, false);
    transaction.persistTo(prefs);
  }

  public void requireAdmin() {
    if (!"admin".equals(prefs.getString("usuario_tipo_atual", ""))
        || !"admin".equals(current().optString("role"))
        || !available(LocalJson.account(prefs))) {
      throw new SecurityException("Entre com a conta de administrador");
    }
  }

  public void save(JSONObject changed, boolean admin) {
    if (PlatformGateway.configured()) {
      PlatformGateway.mutate(prefs, "profile.save", changed);
      return;
    }
    String id = changed.optString("id");
    JSONObject before = id.isEmpty() ? null : get(id);
    if (admin) requireAdmin();
    else if (before == null || !id.equals(LocalJson.account(prefs)) || !available(id)) {
      throw new SecurityException("Não é possível editar esta conta");
    }
    JSONObject after = before == null ? new JSONObject() : LocalJson.object(before.toString());
    if (changed.has("profile_photo")) {
      JSONObject photo = changed.optJSONObject("profile_photo");
      if (!changed.isNull("profile_photo") && photo == null) throw new IllegalArgumentException("Foto de perfil inválida");
      LocalJson.put(after, "profile_photo", photo == null ? JSONObject.NULL : PhotoRefs.copy(new JSONArray().put(photo)).optJSONObject(0));
    }
    String role = before == null ? changed.optString("role") : before.optString("role");
    if (!"cliente".equals(role)
        && !PedidoRules.coletor(role)
        && !(before != null && "admin".equals(role))) {
      throw new IllegalArgumentException("Selecione um tipo de conta válido");
    }
    if (before != null && changed.optInt("revision") != before.optInt("revision")) {
      throw new IllegalStateException("Esta conta foi alterada. Reabra os dados antes de salvar.");
    }
    for (String field : new String[] {"name", "phone", "company", "legal_name", "address", "description"}) {
      if (changed.has(field)) LocalJson.put(after, field, changed.optString(field).trim());
    }
    for (String field : new String[] {"email", "document"}) {
      if (changed.has(field)) LocalJson.put(after, field, changed.optString(field).trim());
    }
    if (after.optString("name").length() > 100)
      throw new IllegalArgumentException("O nome deve ter até 100 caracteres");
    if (after.optString("phone").length() > 40)
      throw new IllegalArgumentException("O telefone deve ter até 40 caracteres");
    if (after.optString("company").length() > 120)
      throw new IllegalArgumentException("O nome da cooperativa deve ter até 120 caracteres");
    validateProfileLengths(after);
    if (after.optString("name").trim().isEmpty())
      throw new IllegalArgumentException("Informe o nome");
    String email = after.optString("email").toLowerCase(Locale.ROOT);
    if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Informe um e-mail válido");
    for (JSONObject profile : list()) {
      if (!id.equals(profile.optString("id"))
          && email.equalsIgnoreCase(profile.optString("email"))) {
        throw new IllegalArgumentException("Este e-mail já pertence a outra conta");
      }
    }
    if ("empresa".equals(role) && after.optString("company").isEmpty()) {
      throw new IllegalArgumentException("Informe o nome fantasia da empresa");
    }
    if ("empresa".equals(role)) {
      String cnpj = after.optString("document").replaceAll("\\D", "");
      if (!cnpj.matches("[0-9]{14}"))
        throw new IllegalArgumentException("Informe um CNPJ com 14 digitos");
      for (JSONObject p : list())
        if (!id.equals(p.optString("id"))
            && "empresa".equals(p.optString("role"))
            && cnpj.equals(p.optString("document").replaceAll("\\D", "")))
          throw new IllegalArgumentException("CNPJ ja cadastrado");
    }
    if (id.isEmpty()) id = UUID.randomUUID().toString();
    LocalJson.put(after, "id", id);
    LocalJson.put(after, "role", role);
    LocalJson.put(after, "email", email);
    LocalJson.put(after, "status", before == null ? "active" : before.optString("status"));
    LocalJson.put(after, "revision", before == null ? 1 : before.optInt("revision") + 1);
    LocalJson.put(after, "updated_at", System.currentTimeMillis());
    if (before == null) LocalJson.put(after, "created_at", System.currentTimeMillis());
    persist(before, after, before == null ? "Conta criada" : "Perfil atualizado");
  }

  private static void validateProfileLengths(JSONObject profile) {
    if (profile.optString("phone").length() > 40)
      throw new IllegalArgumentException("O telefone deve ter até 40 caracteres");
    if (profile.optString("company").length() > 120)
      throw new IllegalArgumentException("O nome da cooperativa deve ter até 120 caracteres");
    if (profile.optString("legal_name").length() > 150)
      throw new IllegalArgumentException("A razão social deve ter até 150 caracteres");
  }

  public void setStatus(String id, String status) {
    if (PlatformGateway.configured()) {
      JSONObject p = new JSONObject();
      LocalJson.put(p, "id", id);
      LocalJson.put(p, "status", status);
      PlatformGateway.mutate(prefs, "profile.status", p);
      return;
    }
    requireAdmin();
    JSONObject before = get(id);
    if ("admin".equals(before.optString("role")) && !"active".equals(status))
      throw new IllegalArgumentException("A conta administrativa deve permanecer ativa");
    if (!"active".equals(status) && !"blocked".equals(status) && !"deleted".equals(status)) {
      throw new IllegalArgumentException("Situação inválida");
    }
    JSONObject after = LocalJson.object(before.toString());
    LocalJson.put(after, "status", status);
    LocalJson.put(after, "revision", before.optInt("revision") + 1);
    LocalJson.put(after, "updated_at", System.currentTimeMillis());
    if ("deleted".equals(status)) LocalJson.put(after, "deleted_at", System.currentTimeMillis());
    else after.remove("deleted_at");
    persist(
        before,
        after,
        "deleted".equals(status)
            ? "Conta excluída"
            : "blocked".equals(status) ? "Conta bloqueada" : "Conta reativada");
  }

  public void deleteAccount(String id, int revision) {
    if (!id.equals(LocalJson.account(prefs)) || !available(id)) throw new SecurityException("Não é possível excluir esta conta");
    if (PlatformGateway.configured()) {
      PlatformGateway.deleteAccount(prefs, id, revision);
      return;
    }
    JSONObject before = current();
    if (revision != before.optInt("revision")) throw new IllegalStateException("A conta foi alterada. Reabra a edição antes de excluir.");
    if ("admin".equals(before.optString("role"))) {
      boolean anotherAdmin = false;
      for (JSONObject account : list())
        if (!id.equals(account.optString("id")) && "admin".equals(account.optString("role")) && "active".equals(account.optString("status"))) anotherAdmin = true;
      if (!anotherAdmin) throw new IllegalStateException("Mantenha outro administrador ativo antes de excluir esta conta.");
    }
    JSONObject after = LocalJson.object(before.toString());
    LocalJson.put(after, "status", "deleted");
    LocalJson.put(after, "deleted_at", System.currentTimeMillis());
    LocalJson.put(after, "updated_at", System.currentTimeMillis());
    LocalJson.put(after, "revision", revision + 1);
    persist(before, after, "Conta excluída pelo titular");
  }

  private void persist(JSONObject before, JSONObject after, String action) {
    JSONArray result = new JSONArray();
    boolean found = false;
    for (JSONObject p : list()) {
      if (p.optString("id").equals(after.optString("id"))) {
        result.put(after);
        found = true;
      } else result.put(p);
    }
    if (!found) result.put(after);
    SharedPreferences.Editor editor = prefs.edit().putString(KEY, result.toString());
    if (after.optString("id").equals(LocalJson.account(prefs))) mirror(editor, after);
    new AuditRepository(prefs)
        .append(editor, action, "profile", after.optString("id"), before, after);
    LocalJson.commit(editor);
  }

  static void mirror(SharedPreferences.Editor editor, JSONObject p) {
    String role = p.optString("role");
    editor.putString(role + "_nome", p.optString("name"));
    editor.putString(role + "_telefone", p.optString("phone"));
    editor.putString(role + "_email", p.optString("email"));
    editor.putString(role + "_documento", p.optString("document"));
    if ("empresa".equals(role)) {
      editor.putString("empresa_nome_fantasia", p.optString("company"));
      editor.putString("empresa_razao_social", p.optString("legal_name"));
    }
    if ("cliente".equals(role)) editor.putString("endereco_salvo_cliente", p.optString("address"));
  }

  public void createByAdmin(JSONObject input, String password) {
    requireAdmin();
    if (PlatformGateway.configured()) {
      JSONObject p = LocalJson.object(input.toString());
      LocalJson.put(p, "password", password);
      PlatformGateway.mutate(prefs, "profile.save", p);
    } else registerInternal(input, password, true);
  }
}
