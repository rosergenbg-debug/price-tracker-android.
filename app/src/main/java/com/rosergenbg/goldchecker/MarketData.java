package com.rosergenbg.goldchecker;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Public HTTPS feeds, no accounts, no user-specific keys. */
public final class MarketData {
    public static final class Quote {
        public double goldUsdOz, silverUsdOz, usdToEur;
        public long timestamp;
        public String provider;
        public boolean stale, staleFx, offline;
    }
    private final SharedPreferences prefs;
    public MarketData(SharedPreferences prefs) { this.prefs = prefs; }

    public Quote load(String source) throws Exception {
        Exception error = null;
        if (!"XAUS".equals(source)) {
            try { Quote q = goldApi(); save(q); return q; }
            catch (Exception e) { error = e; }
        }
        try { Quote q = xaus(); save(q); return q; }
        catch (Exception e) {
            Quote cached = cached();
            if (cached != null) return cached;
            throw new IOException("Public quote providers unavailable", error == null ? e : error);
        }
    }
    private Quote goldApi() throws Exception {
        JSONObject gold = getJson("https://api.gold-api.com/price/XAU");
        JSONObject silver = getJson("https://api.gold-api.com/price/XAG");
        Quote q = new Quote();
        q.goldUsdOz = positive(gold, "price");
        q.silverUsdOz = positive(silver, "price");
        q.timestamp = Math.min(parseTime(gold.getString("updatedAt")), parseTime(silver.getString("updatedAt")));
        q.provider = "Gold API";
        q.usdToEur = exchangeRate(q);
        q.stale = outdated(q.timestamp);
        return q;
    }
    private Quote xaus() throws Exception {
        JSONObject j = getJson("https://xaus.com/api/v1/spot?compact=1&currency=EUR");
        Quote q = new Quote();
        q.goldUsdOz = positive(j, "spot_usd_oz");
        q.silverUsdOz = positive(j, "silver_usd_oz");
        q.usdToEur = positive(j, "fx_rate");
        q.timestamp = parseTime(j.optString("price_as_of", j.optString("updated_at")));
        q.provider = "XAUS";
        q.staleFx = j.optBoolean("fx_stale", false);
        q.stale = j.optBoolean("stale", false) ||
                "stale".equals(j.optJSONObject("data_state") == null ? "" :
                        j.optJSONObject("data_state").optString("status")) || outdated(q.timestamp);
        return q;
    }
    private double exchangeRate(Quote q) throws Exception {
        long savedAt = prefs.getLong("fx_updated_ms", 0L);
        double saved = Double.longBitsToDouble(prefs.getLong("fx_eur_bits", 0L));
        long age = System.currentTimeMillis() - savedAt;
        if (saved > 0 && age >= 0 && age < 12L * 3600_000L) return saved;
        try {
            JSONObject j = getJson("https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR");
            double rate = positive(j.getJSONObject("rates"), "EUR");
            prefs.edit().putLong("fx_updated_ms", System.currentTimeMillis())
                    .putLong("fx_eur_bits", Double.doubleToRawLongBits(rate)).apply();
            return rate;
        } catch (Exception e) {
            if (saved > 0 && age >= 0 && age < 7L * 24 * 3600_000L) {
                q.staleFx = true;
                return saved;
            }
            throw e;
        }
    }
    public Quote cached() {
        if (!prefs.contains("gold_bits") || !prefs.contains("silver_bits")) return null;
        Quote q = new Quote();
        q.goldUsdOz = Double.longBitsToDouble(prefs.getLong("gold_bits", 0L));
        q.silverUsdOz = Double.longBitsToDouble(prefs.getLong("silver_bits", 0L));
        q.usdToEur = Double.longBitsToDouble(prefs.getLong("quote_fx_bits", 0L));
        q.timestamp = prefs.getLong("quote_time", 0L);
        q.provider = prefs.getString("quote_source", "Cache");
        q.offline = true;
        q.stale = true;
        q.staleFx = prefs.getBoolean("quote_fx_stale", true);
        if (q.goldUsdOz <= 0 || q.silverUsdOz <= 0 || q.usdToEur <= 0 || q.timestamp <= 0) return null;
        return q;
    }
    private void save(Quote q) {
        prefs.edit().putLong("gold_bits", Double.doubleToRawLongBits(q.goldUsdOz))
                .putLong("silver_bits", Double.doubleToRawLongBits(q.silverUsdOz))
                .putLong("quote_fx_bits", Double.doubleToRawLongBits(q.usdToEur))
                .putLong("quote_time", q.timestamp).putString("quote_source", q.provider)
                .putBoolean("quote_fx_stale", q.staleFx).apply();
    }
    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(9000); conn.setReadTimeout(9000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "GoldSilverChecker/1.1");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code > 299) throw new IOException("HTTP " + code);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 1024 * 1024) throw new IOException("Response too large");
                }
            }
            return new JSONObject(body.toString());
        } finally { conn.disconnect(); }
    }
    private static double positive(JSONObject j, String k) throws Exception {
        double v = j.getDouble(k);
        if (!Double.isFinite(v) || v <= 0) throw new IOException("Bad feed value: " + k);
        return v;
    }
    private static long parseTime(String text) throws Exception { return Instant.parse(text).toEpochMilli(); }
    private static boolean outdated(long t) {
        long age = System.currentTimeMillis() - t;
        return age > 15 * 60000L || age < -5 * 60000L;
    }
}