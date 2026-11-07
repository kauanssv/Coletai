package com.example.coletai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;

final class AdminRecordAdapter
    extends ListAdapter<AdminRecordAdapter.Row, AdminRecordAdapter.Holder> {
  interface Click {
    void open(Row row);
  }

  static final class Row {
    final String key, title, subtitle, status, kind;
    final JSONObject data;

    Row(String kind, JSONObject data, String title, String subtitle, String status) {
      this.kind = kind;
      this.data = data;
      this.title = title;
      this.subtitle = subtitle;
      this.status = status;
      this.key = kind + ":" + data.optString("account_id") + ":" + data.optString("id");
    }
  }

  private final Click click;

  AdminRecordAdapter(Click click) {
    super(
        new DiffUtil.ItemCallback<Row>() {
          @Override
          public boolean areItemsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.key.equals(b.key);
          }

          @Override
          public boolean areContentsTheSame(@NonNull Row a, @NonNull Row b) {
            return a.data.toString().equals(b.data.toString())
                && a.title.equals(b.title)
                && a.subtitle.equals(b.subtitle)
                && a.status.equals(b.status);
          }
        });
    this.click = click;
  }

  @NonNull
  @Override
  public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
    return new Holder(
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_admin_record, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull Holder holder, int position) {
    Row row = getItem(position);
    holder.title.setText(row.title);
    holder.subtitle.setText(row.subtitle);
    holder.status.setText(row.status);
    if ("request".equals(row.kind)) {
      android.content.Context context = holder.itemView.getContext();
      holder.title.setTextSize(15);
      holder.status.setTextSize(11);
      boolean canceled = "cancelada".equals(row.data.optString("status"));
      holder.status.setTextColor(canceled ? 0xFFDC5B67 : 0xFF0F7667);
      holder.status.setBackground(PlatformUi.surface(context, canceled ? 0xFFFFEFF1 : 0xFFEAF8F3, 12));
      holder.status.setPadding(PlatformUi.dp(context, 10), PlatformUi.dp(context, 4), PlatformUi.dp(context, 10), PlatformUi.dp(context, 4));
      String description = row.data.optString("description");
      String collector = row.data.optString("responsible_name");
      String price = row.data.optString("proposal_price");
      holder.subtitle.setMaxLines(4);
      holder.subtitle.setText(description + (collector.isEmpty() ? "" : "\n" + collector)
          + (price.isEmpty() ? "" : "  ·  R$ " + price.replace('.', ','))
          + "\n" + row.data.optString("desired_date"));
    } else {
      holder.status.setBackground(null);
      holder.status.setPadding(0, 0, 0, 0);
      holder.status.setTextColor(0xFF0F7667);
      holder.title.setTextSize(16);
      holder.status.setTextSize(12);
      holder.subtitle.setMaxLines(2);
    }
    holder.subtitle.setVisibility(row.subtitle.trim().isEmpty() ? View.GONE : View.VISIBLE);
    holder.itemView.setOnClickListener(v -> click.open(row));
  }

  static final class Holder extends RecyclerView.ViewHolder {
    final TextView title, subtitle, status;

    Holder(View view) {
      super(view);
      title = view.findViewById(R.id.adminRowTitle);
      subtitle = view.findViewById(R.id.adminRowSubtitle);
      status = view.findViewById(R.id.adminRowStatus);
    }
  }
}
