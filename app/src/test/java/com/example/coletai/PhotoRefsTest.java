package com.example.coletai;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PhotoRefsTest {
  private JSONObject photo(int value) {
    return LocalJson.object("{\"id\":\"00000000-0000-0000-0000-00000000000" + value + "\",\"source\":\"local\"}");
  }
  @Test public void acceptsUpToThreePhotosAndCopiesReferences() {
    JSONArray source = new JSONArray().put(photo(1)).put(photo(2)).put(photo(3));
    JSONArray saved = PhotoRefs.copy(source);
    LocalJson.put(source.optJSONObject(0), "id", "changed");
    assertEquals(3, saved.length());
    assertTrue(PhotoRefs.validId(saved.optJSONObject(0).optString("id")));
    assertEquals(0, PhotoRefs.copy(new JSONArray()).length());
  }
  @Test public void rejectsDuplicateExcessAndUnsafeFileNames() {
    assertThrows(IllegalArgumentException.class, () -> PhotoRefs.copy(new JSONArray().put(photo(1)).put(photo(1))));
    assertThrows(IllegalArgumentException.class, () -> PhotoRefs.copy(new JSONArray().put(photo(1)).put(photo(2)).put(photo(3)).put(photo(4))));
    JSONObject invalid = photo(1);
    LocalJson.put(invalid, "id", "../../private");
    assertThrows(IllegalArgumentException.class, () -> PhotoRefs.copy(new JSONArray().put(invalid)));
    assertThrows(IllegalArgumentException.class, () -> PhotoRefs.copy(null));
    assertThrows(IllegalArgumentException.class, () -> PhotoRefs.copy(new JSONArray().put("file:///private")));
    LocalJson.put(invalid, "id", photo(1).optString("id"));
    LocalJson.put(invalid, "source", "https://example.com");
    assertThrows(IllegalArgumentException.class, () -> PhotoRefs.copy(new JSONArray().put(invalid)));
  }
}
