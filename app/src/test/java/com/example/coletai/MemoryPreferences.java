package com.example.coletai;

import android.content.SharedPreferences;
import java.util.*;

/** Fake transacional para verificar os contratos de persistência sem dispositivo Android. */
final class MemoryPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    private final Set<OnSharedPreferenceChangeListener> listeners = new HashSet<>();
    boolean failWrites;
    @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
    @Override public String getString(String k, String d) { return (String) values.getOrDefault(k, d); }
    @SuppressWarnings("unchecked")
    @Override public Set<String> getStringSet(String k, Set<String> d) { return (Set<String>) values.getOrDefault(k, d); }
    @Override public int getInt(String k, int d) { return (Integer) values.getOrDefault(k, d); }
    @Override public long getLong(String k, long d) { return (Long) values.getOrDefault(k, d); }
    @Override public float getFloat(String k, float d) { return (Float) values.getOrDefault(k, d); }
    @Override public boolean getBoolean(String k, boolean d) { return (Boolean) values.getOrDefault(k, d); }
    @Override public boolean contains(String k) { return values.containsKey(k); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.add(l); }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.remove(l); }
    @Override public Editor edit() {
        return new Editor() {
            private final Map<String, Object> changes = new HashMap<>();
            private boolean clear;
            @Override public Editor putString(String k, String v) { changes.put(k, v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { changes.put(k, new HashSet<>(v)); return this; }
            @Override public Editor putInt(String k, int v) { changes.put(k, v); return this; }
            @Override public Editor putLong(String k, long v) { changes.put(k, v); return this; }
            @Override public Editor putFloat(String k, float v) { changes.put(k, v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { changes.put(k, v); return this; }
            @Override public Editor remove(String k) { changes.put(k, null); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() {
                if (failWrites) return false;
                if (clear) values.clear();
                for (Map.Entry<String, Object> entry : changes.entrySet()) {
                    if (entry.getValue() == null) values.remove(entry.getKey());
                    else values.put(entry.getKey(), entry.getValue());
                }
                for (String key : changes.keySet()) for (OnSharedPreferenceChangeListener l : listeners) {
                    l.onSharedPreferenceChanged(MemoryPreferences.this, key);
                }
                return true;
            }
            @Override public void apply() { commit(); }
        };
    }
}
