package com.example.coletai;

import org.json.JSONArray;
import org.json.JSONObject;

final class ReviewStats {
  final int count;
  final double average;
  private ReviewStats(int count, int sum) { this.count = count; average = count == 0 ? 0 : (double) sum / count; }

  static ReviewStats calculate(JSONArray orders, String collector) {
    int count = 0, sum = 0;
    for (int i = 0; i < orders.length(); i++) {
      JSONObject order = orders.optJSONObject(i);
      if (order == null || !"concluida".equals(order.optString("status")) || !collector.equals(order.optString("responsible_id"))) continue;
      JSONObject review = order.optJSONObject("review");
      double score = review == null ? 0 : review.optDouble("score", 0);
      if (score < 1 || score > 5 || score != Math.rint(score)) continue;
      count++; sum += (int) score;
    }
    return new ReviewStats(count, sum);
  }

  static String stars(int score) {
    StringBuilder text = new StringBuilder();
    for (int i = 1; i <= 5; i++) text.append(i <= score ? '★' : '☆');
    return text.toString();
  }
}
