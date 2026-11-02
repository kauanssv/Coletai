package com.example.coletai;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Human-readable audit snapshots. Only known business fields are presented. */
final class AdminAuditDetails {
  private AdminAuditDetails() {}

  static Map<String, String> values(Context context, JSONObject object) {
    Map<String, String> result = new LinkedHashMap<>();
    if (object == null) return result;
    String[][] fields = {
      {"name", "Nome"}, {"email", "E-mail"}, {"phone", "Telefone"},
      {"role", "Tipo de conta"}, {"status", "Situação"}, {"active", "Disponibilidade"},
      {"document", "Documento"}, {"company", "Nome fantasia"}, {"legal_name", "Razão social"},
      {"citizen_name", "Cliente"}, {"responsible_name", "Coletor responsável"},
      {"description", "Descrição"}, {"notes", "Observações"}, {"desired_date", "Data da coleta"},
      {"address", "Endereço"}, {"nickname", "Apelido"}, {"street", "Rua"}, {"number", "Número"},
      {"complement", "Complemento"}, {"district", "Bairro"}, {"city", "Cidade"}, {"state", "UF"}, {"zip", "CEP"},
      {"items", "Materiais"}, {"photos", "Fotos do pedido"},
      {"proposal_price", "Valor da proposta"}, {"proposal_message", "Mensagem da proposta"},
      {"review", "Avaliação"}, {"deleted", "Na lixeira"}, {"support_email", "E-mail de suporte"},
      {"pedido_tipo", "Material"}, {"pedido_descricao", "Descrição do pedido"}, {"pedido_peso", "Peso"},
      {"pedido_endereco_completo", "Endereço da coleta"}, {"pedido_observacao", "Observações do pedido"},
      {"proposta_preco", "Valor combinado"},
      {"accepted_at", "Aceite confirmado"}, {"proposed_at", "Proposta enviada"},
      {"going_at", "Saída para a coleta"}, {"arrived_at", "Chegada ao local"},
      {"collected_at", "Retirada confirmada"}, {"completed_at", "Conclusão confirmada"}
    };
    for (String[] field : fields) {
      String key = field[0];
      if (!object.has(key) || object.isNull(key)) continue;
      String value = object.optString(key);
      if (key.endsWith("_at")) value = AdminText.date(object.optLong(key));
      else if ("role".equals(key)) value = AdminText.role(context, value);
      else if ("status".equals(key)) value = object.has("role") ? AdminText.profileStatus(context, value) : AdminText.orderStatus(context, object);
      else if ("deleted".equals(key)) value = object.optBoolean(key) ? "Sim" : "Não";
      else if ("active".equals(key)) value = object.optBoolean(key) ? "Disponível" : "Indisponível";
      else if ("desired_date".equals(key)) value = date(value);
      else if ("address".equals(key) && object.optJSONObject(key) != null) value = CollectionRepository.addressText(object.optJSONObject(key));
      else if ("items".equals(key)) {
        JSONArray items = object.optJSONArray(key);
        StringBuilder materials = new StringBuilder();
        if (items != null) for (int i = 0; i < items.length(); i++) {
          JSONObject item = items.optJSONObject(i);
          if (item == null) continue;
          if (materials.length() > 0) materials.append('\n');
          materials.append(item.optString("name", "Material")).append(" · ").append(decimal(item.optString("weight"))).append(" kg");
        }
        value = materials.toString();
      } else if ("photos".equals(key)) {
        JSONArray photos = object.optJSONArray(key);
        int count = photos == null ? 0 : photos.length();
        value = count + (count == 1 ? " foto" : " fotos");
      } else if ("review".equals(key)) {
        JSONObject review = object.optJSONObject(key);
        value = review == null ? "" : review.optInt("score") + " de 5 estrelas" + (review.optString("comment").isEmpty() ? "" : "\n" + review.optString("comment"));
      } else if ("proposal_price".equals(key) || "proposta_preco".equals(key)) value = value.isEmpty() ? "" : "R$ " + decimal(value);
      if (object.opt(key) instanceof JSONObject && !"address".equals(key) && !"review".equals(key)) continue;
      result.put(field[1], value.isEmpty() ? "Não informado" : value);
    }
    return result;
  }

  static String date(String value) {
    java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
    input.setLenient(false);
    java.text.ParsePosition position = new java.text.ParsePosition(0);
    java.util.Date parsed = input.parse(value, position);
    return parsed == null || position.getIndex() != value.length() ? value
        : new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.forLanguageTag("pt-BR")).format(parsed);
  }

  private static String decimal(String value) {
    try {
      java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("pt-BR"));
      format.setMaximumFractionDigits(3);
      return format.format(new java.math.BigDecimal(value));
    } catch (RuntimeException invalid) { return value; }
  }

  static void show(Context context, JSONObject item, String actor) {
    ScrollView scroll = new ScrollView(context);
    LinearLayout body = PlatformUi.column(context);
    int pad = PlatformUi.dp(context, 20);
    body.setPadding(pad, 0, pad, pad);
    scroll.addView(body);
    LinearLayout metadata = PlatformUi.card(body);
    label(metadata, AdminText.entity(item.optString("entity")), 0xFF0F7667);
    PlatformUi.text(metadata, actor, 16, true);
    PlatformUi.text(metadata, AdminText.date(item.optLong("data")), 13, false);
    TextView target = PlatformUi.text(metadata, "Registro: " + item.optString("target"), 12, false);
    target.setTextIsSelectable(true);
    JSONObject before = item.optJSONObject("before"), after = item.optJSONObject("after");
    Map<String, String> previous = values(context, before), next = values(context, after);
    PlatformUi.section(body, before == null ? "DADOS CADASTRADOS" : "O QUE MUDOU");
    java.util.Set<String> keys = new java.util.LinkedHashSet<>(previous.keySet());
    keys.addAll(next.keySet());
    int changes = 0;
    for (String key : keys) {
      String oldValue = previous.get(key), newValue = next.get(key);
      boolean photosChanged = "Fotos do pedido".equals(key) && before != null && after != null
          && !String.valueOf(before.opt("photos")).equals(String.valueOf(after.opt("photos")));
      if (java.util.Objects.equals(oldValue, newValue) && !photosChanged) continue;
      changes++;
      LinearLayout card = PlatformUi.card(body);
      PlatformUi.text(card, key, 15, true);
      if (before != null) {
        label(card, "ANTES", 0xFF73837F);
        value(card, oldValue);
      }
      label(card, before == null ? "VALOR CADASTRADO" : "DEPOIS", 0xFF0F7667);
      value(card, newValue);
      if (photosChanged && java.util.Objects.equals(oldValue, newValue)) PlatformUi.text(card, "As imagens foram substituídas.", 13, false);
    }
    if (changes == 0) PlatformUi.text(body, "Nenhum campo de exibição foi alterado neste registro.", 14, false);
    LinearLayout full = PlatformUi.column(context);
    full.setVisibility(android.view.View.GONE);
    com.google.android.material.button.MaterialButton expand = PlatformUi.button(body, "Ver dados completos", () -> {});
    expand.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5F0));
    expand.setTextColor(0xFF0F7667);
    expand.setOnClickListener(v -> {
      boolean show = full.getVisibility() != android.view.View.VISIBLE;
      full.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
      expand.setText(show ? "Ocultar dados completos" : "Ver dados completos");
    });
    body.addView(full);
    snapshot(full, "ANTES DA ALTERAÇÃO", previous);
    snapshot(full, "DEPOIS DA ALTERAÇÃO", next);
    new MaterialAlertDialogBuilder(context).setTitle(AdminText.action(item)).setView(scroll).setPositiveButton("Fechar", null).show();
  }

  private static void snapshot(LinearLayout parent, String title, Map<String, String> values) {
    PlatformUi.section(parent, title);
    LinearLayout card = PlatformUi.card(parent);
    if (values.isEmpty()) PlatformUi.text(card, "Sem dados neste momento do registro.", 14, false);
    for (Map.Entry<String, String> entry : values.entrySet()) {
      label(card, entry.getKey(), 0xFF73837F);
      value(card, entry.getValue());
    }
  }

  static void showOrder(Context context, JSONObject request) {
    ScrollView scroll = new ScrollView(context);
    LinearLayout body = PlatformUi.column(context);
    int pad = PlatformUi.dp(context, 20);
    body.setPadding(pad, 0, pad, pad);
    scroll.addView(body);
    snapshot(body, "DADOS DA COLETA", values(context, request));
    RequestPhotosView.section(body, request.optJSONArray("photos"));
    new MaterialAlertDialogBuilder(context).setTitle("Pedido #" + AdminText.shortId(request.optString("id")))
        .setView(scroll).setPositiveButton("Fechar", null).show();
  }

  private static void label(LinearLayout parent, String text, int color) {
    PlatformUi.text(parent, text, 12, true).setTextColor(color);
  }

  private static void value(LinearLayout parent, String value) {
    PlatformUi.text(parent, value == null ? "Não informado" : value, 15, false).setTextIsSelectable(true);
  }
}
