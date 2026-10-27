# Gold & Silver Price Checker — v1.2.1

Android (`com.rosergenbg.goldchecker`, versionCode 6). Keyless, ad-free, no registration and no custom backend.

## Calculator

- GOLD purities: 999, 986, 750, 585, 375, 333; SILVER: 999, 925, 900, 835, 800, 750.
- Per-metal `Eigene Feinheit` / `Custom Fineness` row accepts a fineness in thousandths (`1..1000`) plus an independently editable weight. Custom fineness persists after `Zurücksetzen` / `Reset`, which clears all weights including custom weight.
- Gold and silver totals combine standard and custom rows; each fineness is multiplied by purity/1000. Comma and period decimal weights, e.g. `22,5` or `22.5`.
- German or English, EUR or USD, and grams, troy ounces or dwt separately configurable.
- Indicative dealer bid is a user-editable percentage of spot, *not* a live dealer bid.

## Expandable chart monitor

- Top `Kursverlauf anzeigen` / `Show price charts` toggle opens two historical curves, one each for GOLD and SILVER. The *same button at the top* collapses them.
- Chart periods 24h / 7d / 1mo / 1y / 3y, with percent change from first to last actual point and a cached indication.
- Gold and silver history are read from XAUS keyless public historical endpoints: `/api/v1/intraday` for 24h when available, otherwise `/api/v1/chart`. For 3 years, request 5 years and filter locally. 7 days are filtered from a 1-month range. No price history is fabricated; missing data is displayed as unavailable.
- Graph units intentionally fixed at USD per troy ounce; changing calculator currency/weight does **not** convert historical data using the latest FX rate. XAUS chart is sourced via Yahoo Finance proxy and historical quotes can differ from live spot rates.
- PriceMonitor's simple line-chart concept and original gold/silver images are reused, but only precious metals are queried: **no Bitcoin, crypto or trading modules**.
- Historical responses cached locally; no owner-operated servers, credentials or database.

## Building and distribution

GitHub Actions builds a debug APK and an unsigned release. To install over v1.1.2, use the **same private release signing key** stored outside the public repo, with versionCode 6. Never upload the signing keystore or credentials to this public branch.

The `main` branch still contains the old Price Monitor and must not be overwritten.


## v1.2.1 chart presentation
- Price Monitor-inspired full grids with both X and Y axes, time labels, and MIN/MAX/NOW statistics for each metal.
- Separated gold and silver chart panels, 249dp high each.
- Settings: independent chart currency (EUR/USD) and chart unit (kg, g, oz t, dwt). Default EUR/kg. Calculations for weights remain independent.
- Historical USD/troy oz data are converted at **current** USD/EUR spot rate when EUR charts are selected, so historic currency movements are not reconstructed.
- Dedicated refresh button in expanded charts, with loading spinner; auto-refreshes when panel opens and app resumes.
- Removed company-specific name from dealer-estimate disclaimer; actual payout can differ, particularly be lower, sometimes higher.
- Core history data still sourced keylessly from XAUS (real data only). Cached history is only used if fresh retrieval fails.