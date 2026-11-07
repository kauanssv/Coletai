package com.example.coletai;

import static org.junit.Assert.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class DisposalPointTest {
  static JSONObject element(long id, double lat, double lng, String tags) {
    JSONObject row = LocalJson.object("{\"type\":\"node\"}");
    LocalJson.put(row, "id", id); LocalJson.put(row, "lat", lat); LocalJson.put(row, "lon", lng);
    LocalJson.put(row, "tags", LocalJson.object(tags));
    return row;
  }

  static List<DisposalPoint> points(JSONObject... elements) {
    JSONObject response = new JSONObject();
    LocalJson.put(response, "elements", new JSONArray(java.util.Arrays.asList(elements)));
    return DisposalPoint.parse(response);
  }

  static JSONObject order(String... materials) {
    JSONObject order = new JSONObject(); JSONArray items = new JSONArray();
    for (String name : materials) {
      JSONObject item = new JSONObject(); LocalJson.put(item, "name", name); items.put(item);
    }
    LocalJson.put(order, "items", items); return order;
  }

  @Test public void pharmacyIsNotAssumedToReceiveMedicines() {
    assertTrue(points(element(1, -23.55, -46.63, "{\"amenity\":\"pharmacy\",\"name\":\"Farmacia\"}")).isEmpty());
    DisposalPoint point = points(element(2, -23.55, -46.63, "{\"amenity\":\"pharmacy\",\"recycling:drugs\":\"yes\"}")).get(0);
    assertEquals("medicine", point.category);
    assertTrue(point.accepts(order("Medicamentos")));
    assertFalse(point.accepts(order("Pilhas")));
    assertEquals("https://www.openstreetmap.org/node/2", point.sourceUrl);
  }

  @Test public void wasteDrugsAndWayCentersAreSupported() {
    JSONObject way = LocalJson.object("{\"type\":\"way\",\"id\":3,\"center\":{\"lat\":0,\"lon\":0},\"tags\":{\"amenity\":\"waste_disposal\",\"waste\":\"drugs\"}}");
    DisposalPoint point = points(way).get(0);
    assertTrue(point.accepts(order("Remédios")));
    assertEquals(0, point.latitude, 0);
  }

  @Test public void nearestUsesCollectorPositionAndRequiresEveryMaterial() {
    List<DisposalPoint> points = points(
        element(1, -23.55, -46.63, "{\"amenity\":\"recycling\",\"recycling:paper\":\"yes\"}"),
        element(2, -23.56, -46.63, "{\"amenity\":\"recycling\",\"recycling:paper\":\"yes\",\"recycling:cardboard\":\"yes\"}"),
        element(3, -23.60, -46.63, "{\"amenity\":\"recycling\",\"recycling:paper\":\"yes\",\"recycling:cardboard\":\"yes\"}"));
    assertEquals("node/2", DisposalPoint.nearest(points, order("Papel", "Papelão"), -23.55, -46.63).id);
    assertEquals("node/3", DisposalPoint.nearest(points, order("Papel", "Papelão"), -23.60, -46.63).id);
  }

  @Test public void restrictedUnknownAndDistantPointsAreNotSuggested() {
    List<DisposalPoint> points = points(
        element(1, -23.55, -46.63, "{\"amenity\":\"recycling\",\"access\":\"private\",\"recycling:paper\":\"yes\"}"),
        element(2, -23.55, -46.63, "{\"amenity\":\"recycling\"}"),
        element(3, -24.55, -46.63, "{\"amenity\":\"recycling\",\"recycling:paper\":\"yes\"}"));
    assertEquals(3, points.size());
    assertNull(DisposalPoint.nearest(points, order("Papel"), -23.55, -46.63));
    assertNull(DisposalPoint.nearest(points, order("Material desconhecido"), -23.55, -46.63));
  }

  @Test public void specificAcceptanceDoesNotImplyAllWasteOfTheSameFamily() {
    DisposalPoint bottles = points(element(1, 0, 0, "{\"amenity\":\"recycling\",\"recycling:plastic_bottles\":\"yes\"}")).get(0);
    assertFalse(bottles.accepts(order("Plástico")));
    assertTrue(bottles.accepts(order("Garrafas plásticas")));
    DisposalPoint plastic = points(element(2, 0, 0, "{\"amenity\":\"recycling\",\"recycling:plastic\":\"yes\"}")).get(0);
    assertTrue(plastic.accepts(order("Garrafas plásticas")));
    DisposalPoint carBattery = points(element(3, 0, 0, "{\"amenity\":\"recycling\",\"recycling:car_batteries\":\"yes\"}")).get(0);
    assertFalse(carBattery.accepts(order("Pilhas")));
    assertTrue(carBattery.accepts(order("Baterias automotivas")));
  }

  @Test public void badAndDuplicateElementsDoNotPolluteReferences() {
    JSONObject valid = element(1, 0, 0, "{\"amenity\":\"recycling\"}");
    JSONObject invalid = LocalJson.object("{\"type\":\"node\",\"id\":2,\"tags\":{\"amenity\":\"recycling\"}}");
    assertEquals(1, points(valid, invalid, valid, element(3, 91, 0, "{\"amenity\":\"recycling\"}"),
        element(4, 0, 0, "{\"amenity\":\"recycling\",\"disused\":\"yes\"}")).size());
  }

  @Test public void partialOverpassResponseIsNotTreatedAsNoResults() {
    assertThrows(IllegalStateException.class, () -> DisposalPoint.parse(LocalJson.object("{\"elements\":[],\"remark\":\"timeout\"}")));
  }

  @Test public void geodesicDistanceHandlesTheDateLineAndInvalidGps() {
    assertEquals(22239, DisposalPoint.distance(0, 179.9, 0, -179.9), 20);
    assertEquals(0, DisposalPoint.distance(-23.55, -46.63, -23.55, -46.63), .001);
    assertThrows(IllegalArgumentException.class, () -> DisposalPoint.nearest(java.util.Collections.emptyList(), order("Papel"), Double.NaN, 0));
  }
}
