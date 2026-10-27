package com.rosergenbg.goldchecker;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Public, keyless historical XAUS chart feed. Only gold and silver are requested. */
public final class HistoryData {
    public static final class Series {
        public long[] times;
        public double[] prices;
        public String source;
        public boolean cached;
        public long coverageMs() { return times.length > 1 ? times[times.length - 1] - times[0] : 0; }
        public double deltaPct() { return prices.length > 1 ? (prices[prices.length - 1] / prices[0] - 1.0) * 100.0 : 0.0; }
    }
    private final SharedPreferences prefs;
    public HistoryData(SharedPreferences prefs) { this.prefs = prefs; }
    private static long duration(String range) {
        if ("24h".equals(range)) return 24L * 3600_000L;
        if ("7d".equals(range)) return 7L * 24L * 3600_000L;
        if ("1mo".equals(range)) return 31L * 24L * 3600_000L;
        if ("1y".equals(range)) return 366L * 24L * 3600_000L;
        return 3L * 365L * 24L * 3600_000L;
    }
    public Series load(String metal, String range) throws Exception {
        if (!("gold".equals(metal) || "silver".equals(metal))) throw new IOException("Unknown metal");
        String cacheKey = "history_" + metal + "_" + range;
        long now = System.currentTimeMillis();
        long saved = prefs.getLong(cacheKey + "_at", 0);
        String old = prefs.getString(cacheKey, "");
        long ttl = "24h".equals(range) ? 5L * 60_000L : 4L * 3600_000L;
        if (saved > 0 && now - saved < ttl && !old.isEmpty()) {
            try { return parse(new JSONObject(old), range, true); } catch (Exception ignored) { }
        }
        try {
            JSONObject payload = fetch(metal, range);
            Series series = parse(payload, range, false);
            String str = payload.toString();
            if (str.length() < 350_000) {
                prefs.edit().putString(cacheKey, str).putLong(cacheKey + "_at", now).apply();
            }
            return series;
        } catch (Exception failed) {
            if (!old.isEmpty()) {
                try { return parse(new JSONObject(old), range, true); } catch (Exception ignored) { }
            }
            throw failed;
        }
    }
    private static JSONObject fetch(String metal, String range) throws Exception {
        String sym = "gold".equals(metal) ? "xau" : "silver";
        if ("24h".equals(range)) {
            try {
                JSONObject intraday = getJson("https://xaus.com/api/v1/intraday?symbol=" + ("gold".equals(metal) ? "xau" : "xag") + "&hours=24");
                if (intraday.optJSONArray("points") != null && intraday.optJSONArray("points").length() > 2) return intraday;
            } catch (Exception ignored) { }
        }
        String dataRange = "24h".equals(range) ? "1d" : "7d".equals(range) ? "1mo" : "1mo".equals(range) ? "1mo" : "1y".equals(range) ? "1y" : "5y";
        String interval = "24h".equals(range) ? "15m" : "1y".equals(range) || "3y".equals(range) ? "1wk" : "1d";
        return getJson("https://xaus.com/api/v1/chart?symbol=" + sym + "&range=" + dataRange + "&interval=" + interval);
    }
    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(12000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "GoldSilverChecker/1.2");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("History HTTP " + code);
            StringBuilder buf = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buf.append(line);
                    if (buf.length() > 1_500_000) throw new IOException("History response too large");
                }
            }
            return new JSONObject(buf.toString());
        } finally { c.disconnect(); }
    }
    public static Series parse(JSONObject json, String period, boolean cached) throws Exception {
        JSONArray points = json.optJSONArray("points");
        if (points == null && json.optJSONObject("data") != null) points = json.optJSONObject("data").optJSONArray("points");
        if (points == null) throw new IOException("Chart has no points");
        long threshold = System.currentTimeMillis() - duration(period) - 86400_000L;
        // A little extra tolerance for non-trading days, never silently invent data.
        List<Long> times = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.optJSONObject(i);
            if (p == null) continue;
            long t = p.optLong("t", 0L);
            if (t > 0 && t < 10_000_000_000L) t *= 1000L;
            if (t <= 0 && p.has("d")) {
                try { t = java.time.LocalDate.parse(p.getString("d")).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(); }
                catch (Exception ignored) { }
            }
            double v = p.has("p") && !p.isNull("p") ? p.optDouble("p", 0) : p.optDouble("c", 0);
            if (!Double.isFinite(v) || v <= 0 || t < threshold || t > System.currentTimeMillis() + 3600_000L) continue;
            if (!times.isEmpty() && t <= times.get(times.size() - 1)) continue;
            times.add(t); values.add(v);
        }
        if (times.size() < 2) throw new IOException("Not enough actual historical quotes");
        Series result = new Series();
        int step = Math.max(1, (int)Math.ceil(times.size() / 350.0));
        List<Integer> picks = new ArrayList<>();
        for (int i = 0; i < times.size(); i += step) picks.add(i);
        if (picks.get(picks.size() - 1) != times.size() - 1) picks.add(times.size() - 1);
        result.times = new long[picks.size()]; result.prices = new double[picks.size()];
        for (int i = 0; i < picks.size(); i++) { int n=picks.get(i);result.times[i]=times.get(n); result.prices[i]=values.get(n); }
        result.cached = cached; result.source = "XAUS";
        return result;
    }
}