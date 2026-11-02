package com.example.coletai;

import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;

public class ReviewDialog extends DialogFragment {
  static final String TAG = "collection_review";
  private RatingBar stars;
  private TextInputEditText comment;
  private MaterialButton send;
  private RequestSubmission submission;

  static ReviewDialog create(String requestId) {
    ReviewDialog dialog = new ReviewDialog();
    Bundle arguments = new Bundle(); arguments.putString("request_id", requestId); dialog.setArguments(arguments);
    return dialog;
  }

  static boolean pending(JSONObject request) {
    return "concluida".equals(request.optString("status")) && !request.has("review") && !request.optString("responsible_id").isEmpty();
  }

  static void promptOnce(FragmentActivity owner, JSONObject request) {
    if (!pending(request)) return;
    String id = request.optString("id"), citizen = request.optString("citizen_id");
    owner.getWindow().getDecorView().post(() -> {
      android.content.SharedPreferences prefs = PlatformStore.open(owner);
      String key = "review_prompted_" + citizen + "_" + id;
      if (owner.isFinishing() || !owner.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
          || !citizen.equals(LocalJson.account(prefs)) || prefs.getBoolean(key, false)
          || owner.getSupportFragmentManager().isStateSaved()
          || owner.getSupportFragmentManager().getFragments().stream().anyMatch(f -> f instanceof DialogFragment && f.isAdded())) return;
      // A posted prompt may outlive the snapshot that scheduled it.
      try {
        if (!pending(new CollectionRepository(prefs).order(id))) return;
      } catch (RuntimeException unavailable) { return; }
      LocalJson.commit(prefs.edit().putBoolean(key, true));
      create(id).show(owner.getSupportFragmentManager(), TAG);
    });
  }

  @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle state) {
    CollectionRepository repo = new CollectionRepository(PlatformStore.open(requireContext()));
    String id = requireArguments().getString("request_id", "");
    JSONObject request;
    try {
      request = repo.order(id);
      if (!pending(request) || !request.optString("citizen_id").equals(LocalJson.account(PlatformStore.open(requireContext()))))
        throw new IllegalStateException("Esta coleta não está disponível para avaliação.");
    } catch (RuntimeException error) {
      return new MaterialAlertDialogBuilder(requireContext()).setTitle("Avaliar coleta")
          .setMessage(error.getMessage()).setPositiveButton("Fechar", null).create();
    }
    LinearLayout content = PlatformUi.column(requireContext());
    content.setFocusableInTouchMode(true);
    content.requestFocus();
    int pad = PlatformUi.dp(requireContext(), 24);
    content.setPadding(pad, 0, pad, pad);
    PlatformUi.text(content, "Como foi a coleta de " + request.optString("responsible_name", "seu coletor") + "?", 16, true);
    PlatformUi.text(content, "A nota será incluída na média do perfil do coletor.", 14, false).setTextColor(0xFF6F817D);
    LinearLayout row = PlatformUi.column(requireContext());
    row.setGravity(Gravity.CENTER);
    content.addView(row, new LinearLayout.LayoutParams(-1, -2));
    stars = new RatingBar(requireContext());
    stars.setNumStars(5);
    stars.setStepSize(1);
    stars.setRating(state == null ? 0 : state.getInt("score", 0));
    stars.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFB77910));
    stars.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFDCE7E3));
    stars.setContentDescription("Nota da coleta, de 1 a 5 estrelas");
    row.addView(stars, new LinearLayout.LayoutParams(-2, -2));
    TextView selected = PlatformUi.text(row, "", 13, false);
    row.addView(selected);
    selected.setGravity(Gravity.CENTER);
    selected.setText(stars.getRating() == 0 ? "Toque nas estrelas para avaliar" : (int) stars.getRating() + " de 5 estrelas");
    comment = PlatformUi.field(content, "Comentário (opcional)", state == null ? "" : state.getString("comment", ""),
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    comment.setMinLines(2);
    send = PlatformUi.button(content, "Enviar avaliação", () -> {
      int score = Math.round(stars.getRating());
      if (score < 1 || score > 5 || submission.running) return;
      String message = PlatformUi.value(comment);
      submission.start(() -> repo.review(id, score, message));
    });
    stars.setOnRatingBarChangeListener((bar, rating, fromUser) -> {
      selected.setText(rating == 0 ? "Toque nas estrelas para avaliar" : Math.round(rating) + " de 5 estrelas");
      updateState();
    });
    submission = new androidx.lifecycle.ViewModelProvider(this).get(RequestSubmission.class);
    submission.outcome.observe(this, result -> {
      updateState();
      if (result == null || !isAdded()) return;
      submission.outcome.setValue(null);
      if (result.error == null) {
        android.content.SharedPreferences prefs = PlatformStore.open(requireContext());
        LocalJson.commit(prefs.edit().putBoolean("review_prompted_" + request.optString("citizen_id") + "_" + id, true));
        getParentFragmentManager().setFragmentResult("platform_updated", new Bundle());
        android.widget.Toast.makeText(requireContext(), "Avaliação enviada. Obrigado!", android.widget.Toast.LENGTH_SHORT).show();
        dismiss();
      } else if (result.error instanceof PlatformGateway.SessionExpired) {
        LocalJson.commit(PlatformStore.open(requireContext()).edit().clear());
        startActivity(new android.content.Intent(requireContext(), MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        dismiss();
      } else android.widget.Toast.makeText(requireContext(), result.error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
    });
    updateState();
    return new MaterialAlertDialogBuilder(requireContext()).setTitle("Avaliar coleta").setView(content)
        .setNegativeButton("Agora não", (dialog, which) -> { }).create();
  }

  private void updateState() {
    if (submission == null || stars == null) return;
    boolean busy = submission.running;
    setCancelable(!busy);
    send.setEnabled(!busy && stars.getRating() >= 1);
    send.setText(busy ? "Enviando avaliação…" : "Enviar avaliação");
    stars.setEnabled(!busy); comment.setEnabled(!busy);
    if (getDialog() instanceof androidx.appcompat.app.AlertDialog) {
      android.widget.Button later = ((androidx.appcompat.app.AlertDialog) getDialog()).getButton(-2);
      if (later != null) later.setEnabled(!busy);
    }
  }

  @Override public void onStart() { super.onStart(); updateState(); }
  @Override public void onSaveInstanceState(@NonNull Bundle state) {
    if (stars != null) state.putInt("score", Math.round(stars.getRating()));
    if (comment != null) state.putString("comment", PlatformUi.value(comment));
    super.onSaveInstanceState(state);
  }
}
