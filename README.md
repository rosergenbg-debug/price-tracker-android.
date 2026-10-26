# Price Monitor Android

Open-source Android app for live prices and historical charts in euros.

## Assets

- Gold in EUR/kg
- Silver in EUR/kg
- Bitcoin in EUR
- PUMP in EUR (PUMPUSDT converted with EURUSDT)

Tap an asset for a large single chart, or use **ALLE 4 CHARTS** to compare all four charts vertically. Available periods: 1 day, 1 week, 1 month, 1 year, and 3 years.

Current prices and charts are cached locally so the latest available data remains visible during temporary API outages.

## Shared chart time axis (v3.3+)

All four charts share one rolling absolute time window for the selected period: latest 24 hours (1D), 7 days, 30 days, 365 days or 1,095 days. Every point is plotted at its real timestamp, rather than at the point's index. Fixed X-axis endpoints and equal plot margins keep grid lines and time labels aligned in the **ALLE 4 CHARTS** view, even when market data has different intervals.

The same time window is reset on period changes, chart mode changes and refresh. Metals may have gaps when their markets are closed; no values are invented for missing trades. The locally cached history remains visible during API outages and long-range history is refreshed more regularly.

Charts stay fixed to the selected period. A compact panel in the upper-left corner shows the percentage change from the first point to the latest point plus the exact minimum and maximum values for that period.

## Rolling 24-hour history (v3.4)

The 1D Bitcoin series uses actual BTC-EUR five-minute candles from Coinbase Exchange.
Yahoo Finance is a fallback for Bitcoin, and supplies gold and silver charts.
For all 1D Yahoo requests the app asks for five days of history, **then clips
points to the shared most recent 24 hours**. Yahoo's 1d request represents a
market/calendar session, not necessarily the last 24 clock hours.
Historic chart caches use a new v3.4 namespace to avoid stale short history.

**Market hours still apply:** gold and silver futures can have no trades over
parts of Sunday or other closed sessions. Such gaps represent real missing market
observations and are not silently filled with invented prices.

## Stable signing and updates

See [.github/signing/SIGNING.md](.github/signing/SIGNING.md) for the permanent
signing key fingerprint and recovery location. The signing key and password are
privately backed up on Google Drive; only a password-protected copy of the
keystore is kept in this public repository.

Warning: v3.3 APK uses an earlier temporary debug signature. Switching to
the permanent v3.4 release certificate may require a **one-time reinstall**.
All subsequent signed releases should reuse this permanent key so they install
on top of v3.4 without deleting home-screen shortcuts.

## Data sources

- Coinbase: current Bitcoin price
- Stooq: current gold, silver, and EUR/USD quotes
- Yahoo Finance: Bitcoin and precious-metal charts
- Binance public market-data API: PUMP price and chart
- Google Apps Script: fallback for current gold, silver, and Bitcoin prices

## Build

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on Android 7.0 (API 24) or newer.

Version: **3.4**

Contact: [roserge.nbg@gmail.com](mailto:roserge.nbg@gmail.com)
