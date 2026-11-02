package com.example.coletai;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class StreetRouteTest {
  private JSONObject response(String coordinates) {
    return LocalJson.object("{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":" + coordinates
        + "},\"properties\":{\"summary\":{\"distance\":1250,\"duration\":300}}}]}");
  }

  @Test public void preservesEveryStreetBendAndConvertsLongitudeLatitude() {
    StreetRoute road = StreetRoute.parse(response("[[-46.63,-23.55],[-46.631,-23.55],[-46.631,-23.551],[-46.634,-23.551]]"));
    assertEquals(4, road.points.size());
    assertEquals(-23.55, road.points.get(1).latitude, 0.000001);
    assertEquals(-46.631, road.points.get(1).longitude, 0.000001);
    assertEquals(-23.551, road.points.get(2).latitude, 0.000001);
    assertEquals(-46.631, road.points.get(2).longitude, 0.000001);
    assertEquals(1250, road.meters, 0.000001);
    assertEquals(300, road.seconds, 0.000001);
    assertThrows(UnsupportedOperationException.class, () -> road.points.clear());
  }

  @Test public void rejectsMissingAndInvalidRoadGeometryInsteadOfDrawingAStraightLine() {
    for (String coordinates : new String[] {"[]", "[[0,0]]", "[[0,0],[181,0]]", "[[0,0],[0,91]]", "[[0,0],[null,1]]"})
      assertThrows(IllegalArgumentException.class, () -> StreetRoute.parse(response(coordinates)));
    assertThrows(IllegalArgumentException.class, () -> StreetRoute.parse(new JSONObject()));
    assertThrows(IllegalArgumentException.class, () -> StreetRoute.parse(null));
  }

  @Test public void rejectsWrongGeometryAndUnusableDistanceOrDuration() throws Exception {
    JSONObject invalid = response("[[0,0],[1,1]]");
    JSONObject feature = invalid.getJSONArray("features").getJSONObject(0);
    feature.getJSONObject("geometry").put("type", "Point");
    assertThrows(IllegalArgumentException.class, () -> StreetRoute.parse(invalid));
    feature.getJSONObject("geometry").put("type", "LineString");
    for (String field : new String[] {"distance", "duration"}) {
      feature.getJSONObject("properties").getJSONObject("summary").put(field, -1);
      assertThrows(IllegalArgumentException.class, () -> StreetRoute.parse(invalid));
      feature.getJSONObject("properties").getJSONObject("summary").put(field, 1);
    }
  }
}
