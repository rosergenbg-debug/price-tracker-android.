# Price Monitor Android

Open-source Android app for live prices and historical charts in euros.

## Assets

- Gold in EUR/kg
- Silver in EUR/kg
- Bitcoin in EUR
- PUMP in EUR (PUMPUSDT converted with EURUSDT)

Tap an asset for a large single chart, or use **ALLE 4 CHARTS** to compare all four charts vertically. Available periods: 1 day, 1 week, 1 month, 1 year, and 3 years.

Current prices and charts are cached locally so the latest available data remains visible during temporary API outages.

## Data sources

- Coinbase: current Bitcoin price
- Stooq: current gold, silver, and EUR/USD quotes
- Yahoo Finance: Bitcoin and precious-metal charts
- Binance public market-data API: PUMP price and chart
- Google Apps Script: fallback for current gold, silver, and Bitcoin prices

## Build

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on Android 7.0 (API 24) or newer.

Version: **3.0**

Contact: [roserge.nbg@gmail.com](mailto:roserge.nbg@gmail.com)
