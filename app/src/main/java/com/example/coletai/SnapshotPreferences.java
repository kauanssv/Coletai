package com.example.coletai;

import android.content.SharedPreferences;
import java.util.*;

/** Private working copy: a failed command never partially changes the host database. */
final class SnapshotPreferences implements SharedPreferences {
  private final Map<String, Object> values = new HashMap<>();
  SnapshotPreferences(SharedPreferences source) { values.putAll(source.getAll()); }
  void persistTo(SharedPreferences target) {
    Editor editor = target.edit().clear();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      String key = entry.getKey(); Object value = entry.getValue();
      if (value instanceof String) editor.putString(key, (String) value);
      else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
      else if (value instanceof Integer) editor.putInt(key, (Integer) value);
      else if (value instanceof Long) editor.putLong(key, (Long) value);
      else if (value instanceof Float) editor.putFloat(key, (Float) value);
      else if (value instanceof Set) editor.putStringSet(key, getStringSet(key, Collections.emptySet()));
    }
    LocalJson.commit(editor);
  }
  public Map<String, ?> getAll() { return new HashMap<>(values); }
  public String getString(String k, String d) { return (String) values.getOrDefault(k, d); }
  @SuppressWarnings("unchecked") public Set<String> getStringSet(String k, Set<String> d) { return new HashSet<>((Set<String>) values.getOrDefault(k, d)); }
  public int getInt(String k, int d) { return (Integer) values.getOrDefault(k, d); }
  public long getLong(String k, long d) { return (Long) values.getOrDefault(k, d); }
  public float getFloat(String k, float d) { return (Float) values.getOrDefault(k, d); }
  public boolean getBoolean(String k, boolean d) { return (Boolean) values.getOrDefault(k, d); }
  public boolean contains(String k) { return values.containsKey(k); }
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
  public Editor edit() { return new Editor() {
    final Map<String, Object> changes = new HashMap<>(); boolean clear;
    public Editor putString(String k, String v) { changes.put(k, v); return this; }
    public Editor putStringSet(String k, Set<String> v) { changes.put(k, new HashSet<>(v)); return this; }
    public Editor putInt(String k, int v) { changes.put(k, v); return this; }
    public Editor putLong(String k, long v) { changes.put(k, v); return this; }
    public Editor putFloat(String k, float v) { changes.put(k, v); return this; }
    public Editor putBoolean(String k, boolean v) { changes.put(k, v); return this; }
    public Editor remove(String k) { changes.put(k, null); return this; }
    public Editor clear() { clear = true; return this; }
    public boolean commit() {
      if (clear) values.clear();
      for (Map.Entry<String, Object> entry : changes.entrySet())
        if (entry.getValue() == null) values.remove(entry.getKey()); else values.put(entry.getKey(), entry.getValue());
      return true;
    }
    public void apply() { commit(); }
  }; }
}
