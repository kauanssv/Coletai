package com.example.coletai;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class ReviewStatsTest {
  private JSONObject order(String collector, String status, int score) {
    JSONObject order = new JSONObject();
    LocalJson.put(order, "responsible_id", collector);
    LocalJson.put(order, "status", status);
    if (score != 0) LocalJson.put(order, "review", LocalJson.put(new JSONObject(), "score", score));
    return order;
  }
  @Test public void averagesOnlySubmittedReviewsOfTheEvaluatedCollector() {
    JSONArray orders = new JSONArray().put(order("ambulante", "concluida", 5))
        .put(order("ambulante", "concluida", 3)).put(order("ambulante", "concluida", 0))
        .put(order("empresa", "concluida", 1)).put(order("ambulante", "cancelada", 1));
    ReviewStats stats = ReviewStats.calculate(orders, "ambulante");
    assertEquals(2, stats.count); assertEquals(4, stats.average, .00001);
    assertEquals(1, ReviewStats.calculate(orders, "empresa").average, .00001);
  }
  @Test public void noReviewsDoesNotInventAFiveStarRating() {
    ReviewStats empty = ReviewStats.calculate(new JSONArray().put(order("ambulante", "concluida", 0)), "ambulante");
    assertEquals(0, empty.count); assertEquals(0, empty.average, .00001);
    assertEquals("★★★☆☆", ReviewStats.stars(3));
  }
  @Test public void excludesInvalidScoresAndKeepsTheFullPrecisionOfTheAverage() {
    JSONArray orders = new JSONArray().put(order("ambulante", "concluida", 5))
        .put(order("ambulante", "concluida", 4)).put(order("ambulante", "concluida", 4))
        .put(order("ambulante", "concluida", 6)).put(order("ambulante", "concluida", -1));
    JSONObject fractional = order("ambulante", "concluida", 3);
    LocalJson.put(fractional.optJSONObject("review"), "score", 3.5);
    orders.put(fractional);
    ReviewStats stats = ReviewStats.calculate(orders, "ambulante");
    assertEquals(3, stats.count); assertEquals(13.0 / 3, stats.average, .00001);
  }
}
