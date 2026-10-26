package com.rosergenbg.goldchecker;

/** Values use raw alloy weight in grams. Currency can be EUR or USD. */
public final class MetalMath {
    public static final double GRAMS_PER_TROY_OUNCE = 31.1034768;
    private MetalMath() {}
    public static double spotValue(double usdPerTroyOz, double usdToEur, boolean euro,
                                   int fineness, double grams) {
        if (!Double.isFinite(usdPerTroyOz) || usdPerTroyOz <= 0 ||
                !Double.isFinite(usdToEur) || usdToEur <= 0 ||
                fineness < 0 || fineness > 1000 ||
                !Double.isFinite(grams) || grams < 0 || grams > 1e10)
            throw new IllegalArgumentException("Invalid metal inputs");
        return grams / GRAMS_PER_TROY_OUNCE * usdPerTroyOz * (fineness / 1000.0) * (euro ? usdToEur : 1.0);
    }
    public static double indicativePayout(double spot, double ratio) {
        if (!Double.isFinite(spot) || spot < 0 || !Double.isFinite(ratio) || ratio < 0 || ratio > 1)
            throw new IllegalArgumentException("Invalid payout inputs");
        return spot * ratio;
    }
}