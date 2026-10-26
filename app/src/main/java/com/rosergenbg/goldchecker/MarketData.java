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
import java.time.Instant;

/** Public HTTPS feeds. No keys, login, account, app server or invented fallback quotes. */
public final class MarketData {
    public static final class Quote {
        public double goldUsdOz;
        public double silverUsdOz;
        public double usdToEur;
        public long timestamp;
        public String provider;
        public boolean stale;
        public boolean staleFx;
        public boolean offline;
    }

    private final SharedPreferences prefs;

    public MarketData(SharedPreferences prefs) { this.prefs = prefs; }

    public Quote load(String choice, boolean european) throws Exception {
        Exception failure = null;
        if (!"XAUS".equals(choice)) {
            try {
                Quote q = goldApi(european);
                save(q, european);
                return q;
            } catch (Exception e) { failure = e; }
        }
        // Automatic fallback even if the default primary was selected explicitly.
        try {
            Quote q = xaus(european);
            save(q, european);
            return q;
        } catch (Exception e) {
            Quote cached = cached(european);
            if (cached != null) return cached;
            throw new IOException("No quote from public feeds; check Internet. " +
                    (failure == null ? e.getMessage() : failure.getMessage()));
        }
    }

    private Quote goldApi(boolean european) throws Exception {
        JSONObject g = getJson("https://api.gold-api.com/price/XAU");
        JSONObject s = getJson("https://api.gold-api.com/price/XAG");
        Quote q = new Quote();
        q.goldUsdOz = positive(g, "price");
        q.silverUsdOz = positive(s, "price");
        q.timestamp = Math.min(parseTime(g.optString("updatedAt")), parseTime(s.optString("updatedAt")));
        q.provider = "Gold API";
        q.usdToEur = 1.0;
        if (european) q.usdToEur = exchangeRate(q);
        q.stale = outdated(q.timestamp);
        return q;
    }

    private Quote xaus(boolean european) throws Exception {
        String url = "https://xaus.com/api/v1/spot?compact=1&currency=" + (european ? "EUR" : "USD");
        JSONObject j = getJson(url);
        Quote q = new Quote();
        q.goldUsdOz = positive(j, "spot_usd_oz");
        q.silverUsdOz = positive(j, "silver_usd_oz");
        q.usdToEur = european ? positive(j, "fx_rate") : 1.0;
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
            JSONObject r = getJson("https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR");
            double rate = positive(r.getJSONObject("rates"), "EUR");
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

    public Quote cached(boolean european) {
        if (european && !prefs.getBoolean("quote_european", false)) return null;
        if (!prefs.contains("gold_bits") || !prefs.contains("silver_bits")) return null;
        Quote q = new Quote();
        q.goldUsdOz = Double.longBitsToDouble(prefs.getLong("gold_bits", 0L));
        q.silverUsdOz = Double.longBitsToDouble(prefs.getLong("silver_bits", 0L));
        q.usdToEur = Double.longBitsToDouble(prefs.getLong("quote_fx_bits", 0L));
        q.timestamp = prefs.getLong("quote_time", 0L);
        q.provider = prefs.getString("quote_source", "Кэш");
        q.offline = true;
        q.stale = true;
        q.staleFx = prefs.getBoolean("quote_fx_stale", true);
        if (q.goldUsdOz <= 0 || q.silverUsdOz <= 0 || q.usdToEur <= 0 || q.timestamp <= 0) return null;
        return q;
    }

    private void save(Quote q, boolean european) {
        prefs.edit().putLong("gold_bits", Double.doubleToRawLongBits(q.goldUsdOz))
                .putLong("silver_bits", Double.doubleToRawLongBits(q.silverUsdOz))
                .putLong("quote_fx_bits", Double.doubleToRawLongBits(q.usdToEur))
                .putBoolean("quote_european", european).putLong("quote_time", q.timestamp).putString("quote_source", q.provider)
                .putBoolean("quote_fx_stale", q.staleFx).apply();
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "GoldSilverChecker/1.0 (+https://github.com/rosergenbg-debug)");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code > 299) throw new IOException("HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                    if (sb.length() > 1024 * 1024) throw new IOException("Unexpected large response");
                }
            }
            return new JSONObject(sb.toString());
        } finally { conn.disconnect(); }
    }

    private static double positive(JSONObject json, String key) throws Exception {
        double value = json.getDouble(key);
        if (!Double.isFinite(value) || value <= 0) throw new IOException("Invalid data: " + key);
        return value;
    }
    private static long parseTime(String s) throws Exception { return Instant.parse(s).toEpochMilli(); }
    private static boolean outdated(long timestamp) {
        long age = System.currentTimeMillis() - timestamp;
        return age > 15 * 60_000L || age < -5 * 60_000L;
    }
}
