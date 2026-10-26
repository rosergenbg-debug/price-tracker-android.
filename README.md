# Gold & Silver Price Checker — Android 1.0.0

Standalone Android project. Separate package `com.rosergenbg.goldchecker`; does **not** modify or share installation data with Price Monitor.

## What it does
- Gold purities 999 through 333 and silver purities 999 through 500, side by side.
- Tap a purity to select it. Enter a weight in each column, e.g. `12,5`.
- Calculate **spot metal value** and an **indicative pawn / scrap dealer payout** using configurable percentages.
- EU: EUR and grams. USA: USD and **troy ounces** (31.1034768 grams), not ordinary ounces.
- Gold API (`api.gold-api.com`) is default; XAUS (`xaus.com`) is the no-key fallback or manually selected provider.
- EUR foreign-exchange reference rate is sourced from Frankfurter (`frankfurter.dev`) for Gold API; XAUS embeds an FX rate when used.
- Current prices only, no API keys, no accounts, no app-specific server. Requests go directly from the user's device over HTTPS.
- Offline: last saved price can be displayed with an explicit **OUTDATED** warning; no invented prices.
- No ads, analytics, accounts or background polling.

## Getting the APK
GitHub Actions → `Build Gold & Silver Checker` → most recent run → Artifacts → `GoldSilverChecker-prototype-debug-APK`.

**Important:** Prototype debug APK may use a different ephemeral debug signing key on each CI runner and is **not** guaranteed to update over a prior debug install. For seamless future updates, distribute a release APK signed with **one private keystore**. Keep the keystore private; never commit it or passwords. Increase `versionCode` for each new release and never change the `applicationId` or release signing key. See `SIGNING.md`.

## Data sources
- [Gold API](https://gold-api.com/docs): `https://api.gold-api.com/price/XAU` and `/price/XAG`. Spot USD per troy ounce.
- [XAUS](https://xaus.com/api/): `https://xaus.com/api/v1/spot?currency=EUR&compact=1`, uses `spot_usd_oz`, `silver_usd_oz` and `fx_rate`.
- [Frankfurter](https://frankfurter.dev/): `https://api.frankfurter.dev/v1/latest?base=USD&symbols=EUR`, daily FX reference rate.

**Important limitations:** Dealer payout percentages are **user-adjustable assumptions, not live offers**. Scrap dealers may test metal purity, deduct fees and offer different bids. Metal spot prices are indicative and can differ between sources. Data feeds are supplied by third parties and can change or be discontinued. With no personal backend, internet access is still needed to retrieve current prices.
