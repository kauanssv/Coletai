package com.example.coletai;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** One submission survives activity recreation while files are being uploaded. */
public final class RequestSubmission extends ViewModel {
  static final class Outcome {
    final RuntimeException error;
    Outcome(RuntimeException error) { this.error = error; }
  }
  private static final java.util.concurrent.ExecutorService WORK = java.util.concurrent.Executors.newSingleThreadExecutor();
  final MutableLiveData<Outcome> outcome = new MutableLiveData<>();
  boolean running;

  void start(Runnable work) {
    if (running) return;
    running = true;
    PlatformTasks.started();
    outcome.setValue(null);
    WORK.execute(() -> {
      RuntimeException failure = null;
      try { work.run(); } catch (RuntimeException error) { failure = error; }
      final Outcome result = new Outcome(failure);
      new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
        try {
          running = false;
          outcome.setValue(result);
        } finally { PlatformTasks.finished(); }
      });
    });
  }
}
