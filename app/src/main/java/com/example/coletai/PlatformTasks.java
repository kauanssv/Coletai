package com.example.coletai;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.Executors;

/** Trabalho de autenticação/rede fora da interface. */
final class PlatformTasks {
  private static final java.util.concurrent.ExecutorService WORK =
      Executors.newSingleThreadExecutor();
  private static final java.util.concurrent.atomic.AtomicInteger PENDING =
      new java.util.concurrent.atomic.AtomicInteger();
  static volatile Runnable idleListener;

  static boolean idle() {
    return PENDING.get() == 0;
  }

  static void started() { PENDING.incrementAndGet(); }

  static void finished() {
    if (PENDING.decrementAndGet() == 0 && idleListener != null) idleListener.run();
  }

  static void mutate(Activity activity, Runnable work, Runnable success) {
    if (PlatformGateway.configured()) {
      run(activity, work, success);
      return;
    }
    try {
      work.run();
      success.run();
    } catch (RuntimeException e) {
      Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  static void run(Activity activity, Runnable work, Runnable success) {
    run(activity, work, success, () -> {});
  }

  static void run(Activity activity, Runnable work, Runnable success, Runnable finished) {
    run(activity, work, success, finished, error -> Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show());
  }

  static void run(Activity activity, Runnable work, Runnable success, Runnable finished,
      java.util.function.Consumer<RuntimeException> failureHandler) {
    started();
    WORK.execute(() -> {
      RuntimeException failure = null;
      try {
        work.run();
      } catch (RuntimeException e) {
        failure = e;
      }
      RuntimeException error = failure;
      new Handler(Looper.getMainLooper())
          .post(() -> {
            try {
              if (!activity.isFinishing() && !activity.isDestroyed()) {
                try {
                  if (error == null) success.run();
                  else if (error instanceof PlatformGateway.SessionExpired) {
                    LocalJson.commit(PlatformStore.open(activity).edit().clear());
                    activity.startActivity(
                        new android.content.Intent(activity, MainActivity.class)
                            .addFlags(
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    activity.finish();
                  } else failureHandler.accept(error);
                } finally {
                  finished.run();
                }
              }
            } finally {
              finished();
            }
          });
    });
  }

  static void mutate(Activity activity, Runnable work, Runnable success, Runnable finished) {
    if (PlatformGateway.configured()) {
      run(activity, work, success, finished);
      return;
    }
    try {
      work.run();
      success.run();
    } catch (RuntimeException e) {
      Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
    } finally {
      finished.run();
    }
  }
}
