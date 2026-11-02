package com.example.coletai;

import android.content.Context;
import android.content.SharedPreferences;

/** Mantém os dados locais separados do cache autenticado da API. */
final class PlatformStore {
  static SharedPreferences open(Context context) {
    BluetoothPlatform.get(context);
    OfflineRoads.initialize(context);
    return context.getSharedPreferences(
        BluetoothPlatform.active() ? "coletai_bluetooth" : PlatformGateway.configured() ? "coletai_remote" : "coletai", Context.MODE_PRIVATE);
  }
}
