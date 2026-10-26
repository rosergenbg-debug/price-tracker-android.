# Gold & Silver Price Checker v1.1.1

Android app (`com.rosergenbg.goldchecker`, versionCode 3), no login or API key.

## Features
- German (default) and English (DE/EN buttons at the top); GOLD / SILVER headings always English.
- Gold: 999, 986, 875, 835, 750, 585, 375, 333.
- Silver: 999, 925, 900, 835, 800, 750.
- Weights for **every** fineness row, supports `12,5` and `12.5`, sum gold and silver together.
- Separate display language, currency (EUR / USD), and weight (g / troy oz / dwt) options. Default DE/EUR/g; EN/USD/g unless user explicitly overrides currency.
- Each-row spot material price; all-metal spot total, indicative dealer payout, and difference. Reset removes all entered weights.
- Baseline indicative payout ratios: gold 98.3% and silver 80.8%, **estimates** calibrated to publicly posted Heubach Edelmetalle scrap buyback rates on 26 October 2026 and contemporaneous gold.de spot prices. This is not a live Heubach quote. User may adjust percentages.
- Gold API and XAUS free keyless price sources, automatic fallback, and Frankfurter public EUR/USD exchange data. Show stale indication for cached quotes.
- App icon shows gold and silver bars, with original Price Monitor gold and silver graphics retained within the app.

## Build & update
GitHub Actions `Build Gold & Silver Checker` builds debug and unsigned release APKs.
For **seamless updates**, sign release APK with the original owner's **private v1.0.0 signing keystore**. Keystore MUST NOT be stored in the public project. Package `com.rosergenbg.goldchecker`, versionCode 3.

This project is on a separate branch, never overwrite Price Monitor's main branch.
## Direct Android download

- [Download the signed updateable APK v1.1.1](releases/GoldSilverChecker-v1.1.1-SIGNED.apk)
- This binary uses the same Android signing certificate as v1.0.0 and v1.1.0. Install over the previous version; do not uninstall first.
- v1.1.1: decimal input accepts comma or dot, with decimal keyboard; version is displayed in the app; About / Report bug / PayPal support actions are available in DE and EN.
- PayPal recipient email is a developer contact; donations work only if that email is associated with an eligible PayPal receiving account.
- **Never commit the keystore/signing secret to this public repository.**
