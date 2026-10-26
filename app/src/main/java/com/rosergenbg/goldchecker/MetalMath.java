package com.rosergenbg.goldchecker;

/** Currency/weight and fineness calculations, independent of Android. */
public final class MetalMath {
    public static final double GRAMS_PER_TROY_OUNCE = 31.1034768;
    private MetalMath() {}

    public static double spotValue(double usdPerTroyOz, double usdToEur,
                                   boolean european, int fineness, double inputWeight) {
        if (!Double.isFinite(usdPerTroyOz) || usdPerTroyOz <= 0 ||
                !Double.isFinite(usdToEur) || usdToEur <= 0 ||
                fineness < 0 || fineness > 1000 ||
                !Double.isFinite(inputWeight) || inputWeight < 0 || inputWeight > 1e9) {
            throw new IllegalArgumentException("Invalid metal inputs");
        }
        final double troyOunces = european ? inputWeight / GRAMS_PER_TROY_OUNCE : inputWeight;
        return troyOunces * usdPerTroyOz * (fineness / 1000.0) * (european ? usdToEur : 1.0);
    }

    public static double indicativePayout(double spotValue, double fraction) {
        if (!Double.isFinite(spotValue) || spotValue < 0 || !Double.isFinite(fraction) || fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("Invalid payout");
        }
        return spotValue * fraction;
    }
}
