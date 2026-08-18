# Price Monitor Android

Open-source Android app for live prices and historical charts in euros.

## Assets

- Gold in EUR/kg
- Silver in EUR/kg
- Bitcoin in EUR
- PUMP in EUR (PUMPUSDT converted with EURUSDT)

Tap an asset for a large single chart, or use **ALLE 4 CHARTS** to compare all four charts vertically. Available periods: 1 day, 1 week, 1 month, 1 year, and 3 years. The 1-day, 1-week, and 1-month views keep at least 90 days loaded: drag the chart left to inspect older prices and tap the active period again to return to the latest data.

Current prices and a merged rolling chart history are cached locally, so newly downloaded points remain available during temporary API outages instead of replacing older history.

## Data sources

- Coinbase: current Bitcoin price
- Stooq: current gold, silver, and EUR/USD quotes
- Yahoo Finance: Bitcoin and precious-metal charts
- Binance public market-data API: PUMP price and chart
- Google Apps Script: fallback for current gold, silver, and Bitcoin prices

## Build

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on Android 7.0 (API 24) or newer.

Version: **3.1**

Contact: [roserge.nbg@gmail.com](mailto:roserge.nbg@gmail.com)
