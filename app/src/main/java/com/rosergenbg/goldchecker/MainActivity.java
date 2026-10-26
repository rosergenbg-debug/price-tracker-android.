package com.rosergenbg.goldchecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gold & Silver Price Checker. German / English only, no keys, no backend. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF081511;
    private static final int PANEL = 0xFF14291F;
    private static final int FIELD = 0xFF091B16;
    private static final int BORDER = 0xFF305443;
    private static final int WHITE = 0xFFE9F8ED;
    private static final int MUTED = 0xFF9CB6A5;
    private static final int MINT = 0xFF7FF0B3;
    private static final int GOLD = 0xFFECC46D;
    private static final int SILVER = 0xFFD4E3EB;
    private static final int WARNING = 0xFFFFC978;
    private static final int[] GOLD_PURITY = {999, 986, 875, 835, 750, 585, 375, 333};
    private static final int[] SILVER_PURITY = {999, 925, 900, 835, 800, 750};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<WeightRow> weights = new ArrayList<>();
    private SharedPreferences prefs;
    private MarketData.Quote quote;
    private boolean german;
    private String currency;
    private String unit;
    private String source;
    private double goldPayout;
    private double silverPayout;
    private TextView status, result, subtitle;
    private Button refreshButton;
    private LinearLayout goldRows, silverRows;
    private boolean loading;
    private long lastRefreshAttempt;

    private static class WeightRow {
        int purity;
        boolean gold;
        EditText input;
        TextView price;
        String key() { return (gold ? "g_" : "s_") + purity; }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        german = prefs.getBoolean("language_de", true);
        currency = prefs.getString("currency", german ? "EUR" : "USD");
        unit = prefs.getString("weight_unit", "g");
        source = prefs.getString("source", "AUTO");
        goldPayout = clamp(prefs.getFloat("payout_gold_v2", 0.983f));
        silverPayout = clamp(prefs.getFloat("payout_silver_v2", 0.808f));
        quote = new MarketData(prefs).cached();
        draw();
        refresh();
    }

    private String t(String de, String en) { return german ? de : en; }
    private boolean euro() { return "EUR".equals(currency); }
    private double gramsPerUnit() {
        if ("oz".equals(unit)) return MetalMath.GRAMS_PER_TROY_OUNCE;
        if ("dwt".equals(unit)) return MetalMath.GRAMS_PER_TROY_OUNCE / 20.0;
        return 1.0;
    }
    private String symbol() { return euro() ? "€" : "$"; }
    private Locale locale() { return german ? Locale.GERMANY : Locale.US; }

    private void draw() {
        weights.clear();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(10), dp(15), dp(10), dp(28));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout top = horizontal(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_gold_silver);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(round(PANEL, BORDER, 13));
        top.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout heading = vertical();
        TextView name = txt("Gold & Silver", 22, WHITE, true);
        heading.addView(name);
        heading.addView(txt("PRICE CHECKER", 11, MINT, true));
        LinearLayout.LayoutParams head = new LinearLayout.LayoutParams(0, -2, 1);
        head.setMargins(dp(9), 0, 0, 0);
        top.addView(heading, head);
        Button de = button("DE", true);
        Button en = button("EN", true);
        de.setBackground(round(german ? MINT : PANEL, BORDER, 10));
        de.setTextColor(german ? BG : WHITE);
        en.setBackground(round(!german ? MINT : PANEL, BORDER, 10));
        en.setTextColor(!german ? BG : WHITE);
        de.setOnClickListener(v -> switchLanguage(true));
        en.setOnClickListener(v -> switchLanguage(false));
        top.addView(de, new LinearLayout.LayoutParams(dp(42), dp(41)));
        LinearLayout.LayoutParams englishLp = new LinearLayout.LayoutParams(dp(42), dp(41));
        englishLp.setMargins(dp(4), 0, 0, 0);
        top.addView(en, englishLp);
        root.addView(top);

        LinearLayout toolbar = horizontal(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, dp(14), 0, dp(7));
        subtitle = txt("", 11, MUTED, false);
        toolbar.addView(subtitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button options = button("⚙ " + t("Optionen", "Options"), true);
        options.setOnClickListener(v -> settings());
        toolbar.addView(options);
        root.addView(toolbar);
        updateSubtitle();

        status = txt(t("Kurse werden geladen …", "Loading prices …"), 11, MUTED, false);
        status.setPadding(dp(3), dp(2), dp(3), dp(10));
        root.addView(status);

        LinearLayout guide = horizontal(Gravity.CENTER_VERTICAL);
        TextView instruction = txt(t("Gewicht je Feinheit eingeben", "Enter weight for each fineness"), 12, MUTED, false);
        guide.addView(instruction, new LinearLayout.LayoutParams(0, -2, 1));
        refreshButton = button("↻", true);
        refreshButton.setContentDescription(t("Kurse aktualisieren", "Refresh prices"));
        refreshButton.setOnClickListener(v -> refresh());
        guide.addView(refreshButton, new LinearLayout.LayoutParams(dp(44), dp(38)));
        root.addView(guide);

        LinearLayout cols = horizontal(Gravity.TOP);
        LinearLayout goldPanel = buildMetal(true);
        LinearLayout silverPanel = buildMetal(false);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, -2, 1);
        gp.setMargins(0, 0, dp(4), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, -2, 1);
        sp.setMargins(dp(4), 0, 0, 0);
        cols.addView(goldPanel, gp);
        cols.addView(silverPanel, sp);
        root.addView(cols);

        LinearLayout actions = horizontal(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(14), 0, dp(10));
        Button calculate = button(t("BERECHNEN", "CALCULATE"), false);
        calculate.setBackground(round(MINT, MINT, 13));
        calculate.setTextColor(BG);
        calculate.setOnClickListener(v -> calculate());
        actions.addView(calculate, new LinearLayout.LayoutParams(0, dp(51), 1.5f));
        Button clear = button(t("ZURÜCKSETZEN", "RESET"), true);
        clear.setOnClickListener(v -> clearRows());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(51), 1.1f);
        cp.setMargins(dp(8), 0, 0, 0);
        actions.addView(clear, cp);
        root.addView(actions);

        LinearLayout totalPanel = vertical();
        totalPanel.setPadding(dp(13), dp(12), dp(13), dp(12));
        totalPanel.setBackground(round(PANEL, BORDER, 14));
        TextView resultTitle = txt(t("GESAMTERGEBNIS", "TOTAL RESULT"), 12, MINT, true);
        totalPanel.addView(resultTitle);
        result = txt(t("Gewicht eintragen und berechnen.", "Enter weight and calculate."), 14, WHITE, false);
        result.setPadding(0, dp(9), 0, dp(2));
        totalPanel.addView(result);
        root.addView(totalPanel);

        TextView note = txt(t("Materialwert = Feingehalt × aktueller Spotkurs. Ankauf = unverbindliche Schätzung, orientiert an veröffentlichten Heubach-Preisen vom 26.10.2026 (Au 98,3 %, Ag 80,8 %). Keine Live-Ankaufsofferte. Preise und Feingehalt vor dem Verkauf prüfen.",
                "Material value = fineness × current spot price. Dealer estimate is based on published Heubach prices from 26 Oct 2026 (Au 98.3%, Ag 80.8%). This is not a live dealer offer. Verify prices and purity before selling."), 10, MUTED, false);
        note.setPadding(dp(3), dp(12), dp(3), dp(7));
        root.addView(note);
        root.addView(txt(t("Quellen: Gold API / XAUS · ohne Anmeldung", "Sources: Gold API / XAUS · no signup"), 10, MUTED, false));
        updateRows();
        updateStatus();
    }

    private LinearLayout buildMetal(boolean gold) {
        LinearLayout panel = vertical();
        panel.setPadding(dp(5), dp(9), dp(5), dp(10));
        panel.setBackground(round(PANEL, BORDER, 14));
        LinearLayout banner = horizontal(Gravity.CENTER_VERTICAL);
        ImageView symbol = new ImageView(this);
        symbol.setImageResource(gold ? R.drawable.gold : R.drawable.silver);
        symbol.setScaleType(ImageView.ScaleType.FIT_CENTER);
        banner.addView(symbol, new LinearLayout.LayoutParams(dp(34), dp(30)));
        TextView heading = txt(gold ? "GOLD" : "SILVER", 15, gold ? GOLD : SILVER, true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.setMargins(dp(4), 0, 0, 0);
        banner.addView(heading, hlp);
        panel.addView(banner);

        LinearLayout head = horizontal(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(2), dp(11), dp(1), dp(7));
        TextView purity = txt(t("Probe", "Fineness"), 9, MUTED, false);
        head.addView(purity, new LinearLayout.LayoutParams(dp(38), -2));
        TextView weightHeader = txt(t("Menge", "Weight"), 9, MUTED, false);
        weightHeader.setGravity(Gravity.CENTER);
        head.addView(weightHeader, new LinearLayout.LayoutParams(dp(58), -2));
        TextView price = txt(symbol() + "/" + unit, 9, MUTED, false);
        price.setGravity(Gravity.END);
        head.addView(price, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(head);

        LinearLayout rows = vertical();
        int[] purities = gold ? GOLD_PURITY : SILVER_PURITY;
        for (int p : purities) {
            WeightRow entry = new WeightRow();
            entry.purity = p;
            entry.gold = gold;
            LinearLayout line = horizontal(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(3), dp(5), dp(3), dp(5));
            line.setBackground(round(FIELD, BORDER, 8));
            TextView grade = txt(Integer.toString(p), 11, gold ? GOLD : SILVER, true);
            line.addView(grade, new LinearLayout.LayoutParams(dp(35), -2));
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setSelectAllOnFocus(false);
            input.setTextSize(12);
            input.setTextColor(WHITE);
            input.setHintTextColor(MUTED);
            input.setHint("0");
            input.setGravity(Gravity.CENTER);
            input.setPadding(dp(2), 0, dp(2), 0);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setBackground(round(PANEL, BORDER, 6));
            input.setText(prefs.getString("weight_" + entry.key(), ""));
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    prefs.edit().putString("weight_" + entry.key(), s.toString()).apply();
                }
                public void afterTextChanged(Editable e) {}
            });
            entry.input = input;
            line.addView(input, new LinearLayout.LayoutParams(dp(59), dp(38)));
            entry.price = txt("—", gold ? 10 : 10, MUTED, true);
            entry.price.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(0, dp(38), 1);
            priceLp.setMargins(dp(2), 0, 0, 0);
            line.addView(entry.price, priceLp);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, -2);
            lineLp.setMargins(0, 0, 0, dp(4));
            rows.addView(line, lineLp);
            weights.add(entry);
        }
        panel.addView(rows);
        if (gold) goldRows = rows; else silverRows = rows;
        return panel;
    }

    private void switchLanguage(boolean de) {
        if (german == de) return;
        german = de;
        SharedPreferences.Editor e = prefs.edit().putBoolean("language_de", de);
        // Language supplies a default currency only until the user explicitly chooses one.
        if (!prefs.getBoolean("currency_custom", false)) {
            currency = de ? "EUR" : "USD";
            e.putString("currency", currency);
        }
        e.apply();
        draw();
    }

    private void settings() {
        String[] options = {
            t("Währung: ", "Currency: ") + currency,
            t("Gewichtseinheit: ", "Weight unit: ") + unit,
            t("Kursquelle: ", "Price source: ") + ("AUTO".equals(source) ? "Gold API → XAUS" : source),
            t("Gold Ankaufquote: ", "Gold payout ratio: ") + pct(goldPayout),
            t("Silber Ankaufquote: ", "Silver payout ratio: ") + pct(silverPayout)
        };
        new AlertDialog.Builder(this).setTitle(t("Einstellungen", "Settings")).setItems(options, (dlg, n) -> {
            if (n == 0) chooseCurrency();
            else if (n == 1) chooseUnit();
            else if (n == 2) chooseSource();
            else changePayout(n == 3);
        }).setNegativeButton(t("Schließen", "Close"), null).show();
    }

    private void chooseCurrency() {
        new AlertDialog.Builder(this).setTitle(t("Währung", "Currency"))
            .setSingleChoiceItems(new String[]{"EUR (€)", "USD ($)"}, euro() ? 0 : 1, (dialog, n) -> {
                currency = n == 0 ? "EUR" : "USD";
                prefs.edit().putString("currency", currency).putBoolean("currency_custom", true).apply();
                dialog.dismiss();
                draw();
                if (quote == null || quote.usdToEur <= 0) refresh();
            }).setNegativeButton(t("Abbrechen", "Cancel"), null).show();
    }

    private void chooseUnit() {
        String[] choices = {t("Gramm (g)", "Grams (g)"), t("Feinunze (oz t)", "Troy ounces (oz t)"), t("Pennyweight (dwt)", "Pennyweight (dwt)")};
        int selected = "oz".equals(unit) ? 1 : "dwt".equals(unit) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle(t("Gewichtseinheit", "Weight unit"))
            .setSingleChoiceItems(choices, selected, (dialog, n) -> {
                String next = n == 0 ? "g" : n == 1 ? "oz" : "dwt";
                if (!next.equals(unit)) {
                    double oldGrams = gramsPerUnit();
                    double newGrams = "oz".equals(next) ? MetalMath.GRAMS_PER_TROY_OUNCE : "dwt".equals(next) ? MetalMath.GRAMS_PER_TROY_OUNCE / 20.0 : 1.0;
                    SharedPreferences.Editor editor = prefs.edit();
                    for (WeightRow row : weights) {
                        String raw = row.input.getText().toString().trim();
                        if (!raw.isEmpty()) {
                            try { editor.putString("weight_" + row.key(), decimal(parseWeight(raw) * oldGrams / newGrams, 5)); }
                            catch (Exception e) { editor.putString("weight_" + row.key(), ""); }
                        }
                    }
                    unit = next;
                    editor.putString("weight_unit", unit).apply();
                    draw();
                }
                dialog.dismiss();
            }).setNegativeButton(t("Abbrechen", "Cancel"), null).show();
    }

    private void chooseSource() {
        int chosen = "XAUS".equals(source) ? 2 : "GOLD".equals(source) ? 1 : 0;
        String[] labels = {t("Automatisch: Gold API → XAUS", "Automatic: Gold API → XAUS"), "Gold API", "XAUS"};
        new AlertDialog.Builder(this).setTitle(t("Kursquelle", "Price source"))
            .setSingleChoiceItems(labels, chosen, (dialog, n) -> {
                source = n == 0 ? "AUTO" : n == 1 ? "GOLD" : "XAUS";
                prefs.edit().putString("source", source).apply();
                dialog.dismiss();
                updateSubtitle();
                refreshForce();
            }).setNegativeButton(t("Abbrechen", "Cancel"), null).show();
    }

    private void changePayout(boolean gold) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(decimal((gold ? goldPayout : silverPayout) * 100, 1));
        input.setPadding(dp(20), dp(10), dp(20), dp(10));
        new AlertDialog.Builder(this).setTitle(t("Ankaufquote in %", "Estimated dealer payout in %"))
            .setMessage(t("Anteil am reinen Materialwert. Richtwert, kein verbindliches Angebot.", "Share of spot material value. Estimate, not a binding offer."))
            .setView(input).setPositiveButton(t("Speichern", "Save"), (d, n) -> {
                try {
                    double rate = parseWeight(input.getText().toString()) / 100.0;
                    if (rate < 0 || rate > 1) throw new NumberFormatException();
                    if (gold) { goldPayout = rate; prefs.edit().putFloat("payout_gold_v2", (float) rate).apply(); }
                    else { silverPayout = rate; prefs.edit().putFloat("payout_silver_v2", (float) rate).apply(); }
                    toast(t("Ankaufquote gespeichert", "Payout ratio saved"));
                    calculateIfAny();
                } catch (Exception ex) { toast(t("Zahl zwischen 0 und 100 eingeben", "Enter a value between 0 and 100")); }
            }).setNegativeButton(t("Abbrechen", "Cancel"), null).show();
    }

    private void refresh() { refreshInternal(false); }
    private void refreshForce() { refreshInternal(true); }
    private void refreshInternal(boolean force) {
        if (loading) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRefreshAttempt < 30_000L && quote != null) {
            updateStatus();
            return;
        }
        lastRefreshAttempt = now;
        loading = true;
        refreshButton.setEnabled(false);
        status.setText(t("Kurse werden aktualisiert …", "Updating prices …"));
        final String chosen = source;
        io.execute(() -> {
            MarketData.Quote incoming = null;
            try { incoming = new MarketData(prefs).load(chosen); } catch (Exception ignored) { }
            final MarketData.Quote ready = incoming;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                refreshButton.setEnabled(true);
                if (!source.equals(chosen)) { refreshForce(); return; }
                if (ready != null) quote = ready;
                updateStatus();
                updateRows();
                calculateIfAny();
            });
        });
    }

    private void updateStatus() {
        if (status == null) return;
        if (quote == null) {
            status.setText(t("Keine Kurse verfügbar. Internet prüfen.", "No quote available. Check internet."));
            status.setTextColor(WARNING);
            return;
        }
        String when = new SimpleDateFormat("dd.MM.yyyy HH:mm", locale()).format(new Date(quote.timestamp));
        boolean old = quote.stale || quote.offline || System.currentTimeMillis() - quote.timestamp > 15 * 60_000L;
        String suffix = old ? t(" · VERALTET / OFFLINE", " · STALE / OFFLINE") : t(" · Aktuell", " · Live");
        if (quote.staleFx && euro()) suffix += t(" · Währungskurs alt", " · FX rate stale");
        status.setText(quote.provider + " · " + when + suffix);
        status.setTextColor(old || (quote.staleFx && euro()) ? WARNING : MINT);
    }

    private void updateSubtitle() {
        if (subtitle != null) subtitle.setText(currency + " · " + unit + "  |  " + ("AUTO".equals(source) ? "Gold API → XAUS" : source));
    }

    private void updateRows() {
        for (WeightRow row : weights) {
            if (quote == null) { row.price.setText("—"); continue; }
            double ozPrice = row.gold ? quote.goldUsdOz : quote.silverUsdOz;
            double unitPrice = ozPrice / MetalMath.GRAMS_PER_TROY_OUNCE * gramsPerUnit() * (row.purity / 1000.0) * (euro() ? quote.usdToEur : 1.0);
            row.price.setText(decimal(unitPrice, row.gold ? 2 : ("g".equals(unit) ? 3 : 2)));
        }
    }

    private void calculateIfAny() {
        for (WeightRow row : weights) {
            if (!row.input.getText().toString().trim().isEmpty()) {
                try { if (parseWeight(row.input.getText().toString()) > 0) { calculate(); return; } }
                catch (Exception ignored) { return; }
            }
        }
    }

    private void calculate() {
        if (quote == null || quote.usdToEur <= 0) { toast(t("Bitte zuerst Kurse laden", "Please load prices first")); return; }
        double goldSpot = 0, silverSpot = 0, goldWeight = 0, silverWeight = 0;
        int count = 0;
        for (WeightRow row : weights) {
            double amount;
            try { amount = parseWeight(row.input.getText().toString()); }
            catch (Exception e) { toast(t("Ungültiges Gewicht bei Probe ", "Invalid weight at fineness ") + row.purity); return; }
            if (amount <= 0) continue;
            count++;
            double value = MetalMath.spotValue(row.gold ? quote.goldUsdOz : quote.silverUsdOz,
                    quote.usdToEur, euro(), row.purity, amount * gramsPerUnit());
            if (row.gold) { goldSpot += value; goldWeight += amount; }
            else { silverSpot += value; silverWeight += amount; }
        }
        if (count == 0) { result.setText(t("Gewicht eintragen und berechnen.", "Enter weight and calculate.")); return; }
        double spot = goldSpot + silverSpot;
        double payout = goldSpot * goldPayout + silverSpot * silverPayout;
        String s = symbol();
        StringBuilder lines = new StringBuilder();
        if (goldWeight > 0) lines.append("GOLD · ").append(decimal(goldWeight, 3)).append(" ").append(unit)
                .append(" · ").append(s).append(decimal(goldSpot, 2)).append("\n");
        if (silverWeight > 0) lines.append("SILVER · ").append(decimal(silverWeight, 3)).append(" ").append(unit)
                .append(" · ").append(s).append(decimal(silverSpot, 2)).append("\n");
        lines.append(t("Materialwert gesamt: ", "Total spot value: ")).append(s).append(decimal(spot, 2));
        lines.append("\n").append(t("Ankauf geschätzt: ", "Est. dealer payout: ")).append(s).append(decimal(payout, 2));
        lines.append("\n").append(t("Abschlag: ", "Deduction: ")).append(s).append(decimal(spot - payout, 2));
        if (quote.offline || quote.stale || System.currentTimeMillis() - quote.timestamp > 15 * 60_000L)
            lines.append("\n⚠ ").append(t("Veraltete Kursdaten", "Stale market data"));
        result.setText(lines.toString());
        View focused = getCurrentFocus();
        if (focused != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    private void clearRows() {
        SharedPreferences.Editor e = prefs.edit();
        for (WeightRow row : weights) {
            row.input.setText("");
            e.remove("weight_" + row.key());
        }
        e.apply();
        result.setText(t("Gewicht eintragen und berechnen.", "Enter weight and calculate."));
        toast(t("Alle Eingaben zurückgesetzt", "All weights cleared"));
    }

    private static double parseWeight(String s) {
        String text = s.trim().replace(" ", "").replace("\u00a0", "").replace(',', '.');
        if (text.isEmpty()) return 0.0;
        double weight = Double.parseDouble(text);
        if (!Double.isFinite(weight) || weight < 0 || weight > 1000000000) throw new NumberFormatException();
        return weight;
    }
    private static double clamp(double n) { return Math.max(0, Math.min(1, n)); }
    private String pct(double rate) { return decimal(rate * 100, 1) + "%"; }
    private String decimal(double number, int places) {
        NumberFormat f = NumberFormat.getNumberInstance(locale());
        f.setGroupingUsed(false);
        f.setMinimumFractionDigits(places);
        f.setMaximumFractionDigits(places);
        return f.format(number);
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout horizontal(int gravity) { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(gravity); return v; }
    private TextView txt(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v;
    }
    private Button button(String value, boolean small) {
        Button v = new Button(this); v.setText(value); v.setAllCaps(false); v.setTextColor(WHITE);
        v.setTextSize(small ? 11 : 14); v.setPadding(dp(4), 0, dp(4), 0);
        v.setBackground(round(PANEL, BORDER, 11)); return v;
    }
    private GradientDrawable round(int fill, int stroke, int radius) {
        GradientDrawable b = new GradientDrawable(); b.setColor(fill); b.setCornerRadius(dp(radius)); b.setStroke(dp(1), stroke); return b;
    }
    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}