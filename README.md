# Gold & Silver Price Checker — independent Android app

This branch is a **separate prototype**, not a modification of the existing Price Monitor app.

Package: `com.rosergenbg.goldchecker` • Version 1.0.0.

The first commit contains `project-source.txz`, a complete native Android project. GitHub Actions extracts the archive to readable files, builds a prototype APK and uploads it as a workflow artifact.

Features: gold/silver fineness charts, per-metal weight fields with decimal comma, estimated spot value versus configurable pawn dealer payout, EUR/gram or USD/troy-ounce, Gold API main, XAUS backup, Frankfurter forex, and no accounts or API keys.

**Signing:** The prototype debug APK is not update-safe across GitHub runners. Stable updates require signing each release with the *same private keystore* (GitHub Actions repository secrets) and incrementing `versionCode` for every update. Never publish signing keys.

Full instructions in `SIGNING.md` once the archive is extracted. This branch exists only because this GitHub connection currently does not permit creation of new repositories.

Data sources: https://gold-api.com/docs • https://xaus.com/api/ • https://frankfurter.dev/.
