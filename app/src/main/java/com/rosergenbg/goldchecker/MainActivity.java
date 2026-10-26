package com.rosergenbg.goldchecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8, 20, 17);
    private static final int PANEL = Color.rgb(19, 43, 35);
    private static final int BORDER = Color.rgb(40, 76, 60);
    private static final int WHITE = Color.rgb(236, 247, 240);
    private static final int MUTED = Color.rgb(151, 184, 163);
    private static final int MINT = Color.rgb(122, 244, 182);
    private static final int GOLD = Color.rgb(240, 197, 105);
    private static final int SILVER = Color.rgb(206, 224, 228);
    private static final int WARNING = Color.rgb(255, 189, 96);
    private static final int[] GOLD_PURITY = {999, 986, 980, 960, 916, 900, 875, 835, 800, 750, 700, 625, 585, 583, 500, 417, 375, 333};
    private static final int[] SILVER_PURITY = {999, 958, 950, 925, 900, 875, 835, 830, 800, 750, 625, 500};

    private SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private MarketData.Quote quote;
    private boolean european;
    private String selectedSource;
    private double goldFraction;
    private double silverFraction;
    private Column goldColumn;
    private Column silverColumn;
    private TextView status;
    private TextView result;
    private Button refreshButton;
    private TextView settingsLabel;
    private boolean loading;

    private static class Column {
        int purity;
        int color;
        boolean isGold;
        LinearLayout rows;
        EditText weight;
        Column(int p, int c, boolean g) { purity = p; color = c; isGold = g; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        european = prefs.getBoolean("european", true);
        selectedSource = prefs.getString("source", "AUTO");
        goldFraction = limit(prefs.getFloat("gold_payout", 0.85f));
        silverFraction = limit(prefs.getFloat("silver_payout", 0.70f));
        quote = new MarketData(prefs).cached(european);
        buildScreen();
        refreshPrices();
    }

    private void buildScreen() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(14), dp(18), dp(14), dp(34));
        sc.addView(root);
        setContentView(sc);

        LinearLayout top = horizontal(Gravity.CENTER_VERTICAL);
        LinearLayout headline = vertical();
        TextView eyebrow = txt("PRECIOUS METALS  /  LIVE", 10, MINT, true);
        headline.addView(eyebrow);
        TextView title = txt("Gold & Silver", 29, WHITE, true);
        headline.addView(title);
        headline.addView(txt("PRICE CHECKER", 14, MUTED, true));
        top.addView(headline, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button set = button("⚙", false);
        set.setContentDescription("Настройки");
        set.setOnClickListener(v -> showSettings());
        top.addView(set, new LinearLayout.LayoutParams(dp(51), dp(48)));
        root.addView(top);

        status = txt("Подключение к котировкам...", 12, MUTED, false);
        status.setPadding(dp(2), dp(14), dp(2), dp(10));
        root.addView(status);
        LinearLayout tools = horizontal(Gravity.CENTER_VERTICAL);
        settingsLabel = txt("", 12, MUTED, false);
        tools.addView(settingsLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        refreshButton = button("↻ Обновить", true);
        refreshButton.setOnClickListener(v -> refreshPrices());
        tools.addView(refreshButton);
        root.addView(tools);
        updateSettingsText();

        TextView helper = txt("Выбери пробу · Введи вес · Посчитай стоимость", 12, MUTED, false);
        helper.setPadding(0, dp(17), 0, dp(10));
        root.addView(helper);

        LinearLayout columns = horizontal(Gravity.TOP);
        goldColumn = buildColumn(true);
        silverColumn = buildColumn(false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        left.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        right.setMargins(dp(5), 0, 0, 0);
        columns.addView((View) goldColumn.rows.getParent(), left);
        columns.addView((View) silverColumn.rows.getParent(), right);
        root.addView(columns);

        LinearLayout actions = horizontal(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(16), 0, dp(8));
        Button calculate = button("ПОСЧИТАТЬ", false);
        calculate.setBackground(round(MINT, MINT, 15));
        calculate.setTextColor(BG);
        calculate.setOnClickListener(v -> calculate());
        actions.addView(calculate, new LinearLayout.LayoutParams(0, dp(56), 1));
        result = txt("Результат появится здесь", 15, WHITE, true);
        result.setPadding(dp(14), dp(13), dp(12), dp(13));
        result.setBackground(round(PANEL, BORDER, 15));
        LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        out.setMargins(dp(10), 0, 0, 0);
        actions.addView(result, out);
        root.addView(actions);

        TextView disclaimer = txt("Максимум = стоимость чистого металла по биржевой котировке. Выкуп = приблизительный расчёт, не предложение скупщика. Реальная цена зависит от пробирной проверки, комиссии и магазина.", 11, MUTED, false);
        disclaimer.setPadding(dp(5), dp(10), dp(5), dp(12));
        root.addView(disclaimer);
        TextView credit = txt("Данные: Gold API · XAUS · Frankfurter  •  Без ключей и регистрации", 10, MUTED, false);
        root.addView(credit);
        renderRows(goldColumn);
        renderRows(silverColumn);
    }

    /** Parent column is attached temporarily to a tag-free holding LinearLayout. */
    private Column buildColumn(boolean isGold) {
        Column c = new Column(isGold ? prefs.getInt("gold_purity", 585) : prefs.getInt("silver_purity", 925),
                isGold ? GOLD : SILVER, isGold);
        LinearLayout shell = vertical();
        shell.setBackground(round(PANEL, BORDER, 18));
        shell.setPadding(dp(7), dp(10), dp(7), dp(12));

        ImageView image = new ImageView(this);
        image.setImageResource(isGold ? R.drawable.gold : R.drawable.silver);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(52), dp(44));
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        shell.addView(image, imageParams);
        TextView label = txt(isGold ? "ЗОЛОТО" : "СЕРЕБРО", 15, c.color, true);
        label.setGravity(Gravity.CENTER);
        shell.addView(label);
        TextView colHint = txt("Проба    Цена / ед.", 10, MUTED, false);
        colHint.setGravity(Gravity.CENTER);
        colHint.setPadding(0, dp(9), 0, dp(5));
        shell.addView(colHint);

        c.rows = vertical();
        shell.addView(c.rows);
        TextView weightLabel = txt("ВЕС (" + (european ? "г" : "oz t") + ")", 10, c.color, true);
        weightLabel.setGravity(Gravity.CENTER);
        weightLabel.setPadding(0, dp(12), 0, dp(5));
        shell.addView(weightLabel);
        c.weight = new EditText(this);
        c.weight.setSingleLine(true);
        c.weight.setTextSize(18);
        c.weight.setTextColor(WHITE);
        c.weight.setHintTextColor(MUTED);
        c.weight.setHint("0,00");
        c.weight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        c.weight.setPadding(dp(8), dp(6), dp(8), dp(6));
        c.weight.setGravity(Gravity.CENTER);
        c.weight.setBackground(round(BG, BORDER, 10));
        c.weight.setText(prefs.getString(isGold ? "gold_weight" : "silver_weight", ""));
        c.weight.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                prefs.edit().putString(c.isGold ? "gold_weight" : "silver_weight", s.toString()).apply();
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        shell.addView(c.weight, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        // Allows columns to be assembled after both have been created.
        return c;
    }

    private void renderRows(Column c) {
        c.rows.removeAllViews();
        int[] purities = c.isGold ? GOLD_PURITY : SILVER_PURITY;
        for (int p : purities) {
            final int purity = p;
            boolean checked = c.purity == p;
            LinearLayout row = horizontal(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(9), dp(4), dp(9));
            row.setBackground(round(checked ? 0xFF284C38 : PANEL, checked ? c.color : BORDER, 9));
            TextView fineness = txt(String.valueOf(p), 13, checked ? c.color : WHITE, true);
            row.addView(fineness, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
            String cost = "—";
            if (quote != null) {
                double usdOz = c.isGold ? quote.goldUsdOz : quote.silverUsdOz;
                double v = MetalMath.spotValue(usdOz, quote.usdToEur, european, p, 1);
                cost = format(v, c.isGold ? 2 : 3);
            }
            TextView price = txt(cost, 11, checked ? WHITE : MUTED, checked);
            price.setGravity(Gravity.END);
            row.addView(price, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
            row.setOnClickListener(v -> {
                c.purity = purity;
                prefs.edit().putInt(c.isGold ? "gold_purity" : "silver_purity", purity).apply();
                renderRows(c);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(2), 0, dp(2));
            c.rows.addView(row, lp);
        }
    }

    private void refreshPrices() {
        if (loading) return;
        loading = true;
        refreshButton.setEnabled(false);
        status.setText("Обновляем данные из открытых источников…");
        final boolean eu = european;
        final String source = selectedSource;
        io.execute(() -> {
            MarketData.Quote got = null;
            String error = null;
            try { got = new MarketData(prefs).load(source, eu); }
            catch (Exception e) { error = e.getMessage(); }
            final MarketData.Quote ready = got;
            final String message = error;
            runOnUiThread(() -> {
                loading = false;
                refreshButton.setEnabled(true);
                if (eu != european || !source.equals(selectedSource)) {
                    refreshPrices();
                    return;
                }
                if (ready != null) quote = ready;
                if (quote != null) {
                    String when = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(new Date(quote.timestamp));
                    String flag = quote.offline ? " • НЕТ СВЯЗИ / СТАРЫЕ ДАННЫЕ" :
                            quote.stale ? " • УСТАРЕВШАЯ КОТИРОВКА" : " • онлайн";
                    String fx = quote.staleFx && european ? " • курс EUR устарел" : "";
                    status.setText("Источник: " + quote.provider + " • " + when + flag + fx);
                    status.setTextColor(quote.offline || quote.stale || quote.staleFx ? WARNING : MINT);
                    renderRows(goldColumn);
                    renderRows(silverColumn);
                } else {
                    status.setText("Нет котировок. " + (message == null ? "Проверь интернет." : message));
                    status.setTextColor(WARNING);
                }
            });
        });
    }

    private void calculate() {
        if (quote == null) { toast("Нет цены металла — нажми «Обновить»"); return; }
        double goldWeight;
        double silverWeight;
        try {
            goldWeight = parseWeight(goldColumn.weight.getText().toString());
            silverWeight = parseWeight(silverColumn.weight.getText().toString());
        } catch (Exception ex) { toast("Введи корректный положительный вес, например 12,5"); return; }
        if (goldWeight == 0 && silverWeight == 0) { toast("Укажи вес золота или серебра"); return; }
        double g = MetalMath.spotValue(quote.goldUsdOz, quote.usdToEur, european, goldColumn.purity, goldWeight);
        double s = MetalMath.spotValue(quote.silverUsdOz, quote.usdToEur, european, silverColumn.purity, silverWeight);
        double bid = MetalMath.indicativePayout(g, goldFraction) + MetalMath.indicativePayout(s, silverFraction);
        String currency = european ? "€" : "$";
        StringBuilder b = new StringBuilder();
        if (goldWeight > 0) b.append("Au ").append(goldColumn.purity).append(": ").append(currency).append(format(g, 2)).append("\n");
        if (silverWeight > 0) b.append("Ag ").append(silverColumn.purity).append(": ").append(currency).append(format(s, 2)).append("\n");
        b.append("МАКС. ").append(currency).append(format(g + s, 2));
        b.append("\nСКУПКА ≈ ").append(currency).append(format(bid, 2));
        if (quote.offline || quote.stale) b.append("\n⚠ Старая котировка");
        result.setText(b.toString());
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(result.getWindowToken(), 0);
        result.requestFocus();
    }

    private void showSettings() {
        String[] opts = {
                "Единицы: " + (european ? "Европа — EUR / грамм" : "США — USD / тройская унция"),
                "Источник: " + ("AUTO".equals(selectedSource) ? "Авто: Gold API → XAUS" : selectedSource),
                "Скупка Au: " + Math.round(goldFraction * 100) + "% от биржевой цены",
                "Скупка Ag: " + Math.round(silverFraction * 100) + "% от биржевой цены"
        };
        new AlertDialog.Builder(this).setTitle("Настройки").setItems(opts, (d, which) -> {
            if (which == 0) new AlertDialog.Builder(this).setTitle("Система измерения")
                    .setSingleChoiceItems(new String[]{"Европа: евро, граммы", "США: доллары, тройские унции"}, european ? 0 : 1,
                            (dlg, n) -> {
                                boolean before = european;
                                european = n == 0;
                                prefs.edit().putBoolean("european", european).apply();
                                dlg.dismiss();
                                if (before != european) {
                                    // Old input weight is not silently reinterpreted as a different unit.
                                    goldColumn.weight.setText("");
                                    silverColumn.weight.setText("");
                                    quote = new MarketData(prefs).cached(european);
                                    buildScreen();
                                    refreshPrices();
                                }
                            }).setNegativeButton("Отмена", null).show();
            else if (which == 1) new AlertDialog.Builder(this).setTitle("Источник котировок")
                    .setSingleChoiceItems(new String[]{"Автоматически (Gold API → XAUS)", "Gold API", "XAUS"},
                            "AUTO".equals(selectedSource) ? 0 : "GOLD".equals(selectedSource) ? 1 : 2,
                            (dlg, n) -> {
                                selectedSource = n == 0 ? "AUTO" : n == 1 ? "GOLD" : "XAUS";
                                prefs.edit().putString("source", selectedSource).apply();
                                dlg.dismiss();
                                updateSettingsText();
                                refreshPrices();
                            }).setNegativeButton("Отмена", null).show();
            else editPayout(which == 2);
        }).setNegativeButton("Закрыть", null).show();
    }

    private void editPayout(boolean gold) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(Math.round((gold ? goldFraction : silverFraction) * 100)));
        input.setPadding(dp(18), dp(10), dp(18), dp(10));
        new AlertDialog.Builder(this).setTitle("Доля выплаты скупщика, %")
                .setMessage("Приблизительная скидка к стоимости чистого металла. Это не реальная котировка конкретной скупки.")
                .setView(input).setPositiveButton("Сохранить", (dlg, n) -> {
                    try {
                        double percent = Double.parseDouble(input.getText().toString().replace(',', '.'));
                        if (percent < 0 || percent > 100 || !Double.isFinite(percent)) throw new NumberFormatException();
                        if (gold) { goldFraction = percent / 100; prefs.edit().putFloat("gold_payout", (float) goldFraction).apply(); }
                        else { silverFraction = percent / 100; prefs.edit().putFloat("silver_payout", (float) silverFraction).apply(); }
                        toast("Процент сохранён. Нажми «Посчитать» снова.");
                    } catch (NumberFormatException ex) { toast("Допустимо только от 0 до 100%"); }
                }).setNegativeButton("Отмена", null).show();
    }

    private void updateSettingsText() {
        settingsLabel.setText((european ? "🇪🇺 EUR · г" : "🇺🇸 USD · oz t") + "   |   " +
                ("AUTO".equals(selectedSource) ? "Gold API → XAUS" : selectedSource));
    }
    private static double parseWeight(String input) {
        String normalized = input.trim().replace(" ", "").replace("\u00a0", "").replace(',', '.');
        if (normalized.isEmpty()) return 0;
        double value = Double.parseDouble(normalized);
        if (!Double.isFinite(value) || value < 0 || value > 1e9) throw new NumberFormatException();
        return value;
    }
    private static double limit(double value) { return Math.max(0, Math.min(1, value)); }
    private String format(double value, int precision) {
        NumberFormat nf = NumberFormat.getNumberInstance(european ? Locale.GERMANY : Locale.US);
        nf.setMinimumFractionDigits(precision);
        nf.setMaximumFractionDigits(precision);
        return nf.format(value);
    }
    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show(); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private LinearLayout horizontal(int align) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(align);
        return v;
    }
    private TextView txt(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String name, boolean small) {
        Button v = new Button(this);
        v.setAllCaps(false);
        v.setText(name);
        v.setTextSize(small ? 12 : 14);
        v.setTextColor(WHITE);
        v.setPadding(dp(6), 0, dp(6), 0);
        v.setBackground(round(PANEL, BORDER, 12));
        return v;
    }
    private GradientDrawable round(int fill, int outline, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), outline);
        return d;
    }
    @Override protected void onDestroy() { super.onDestroy(); io.shutdownNow(); }
}
