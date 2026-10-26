# Release signing and seamless updates

Android will only install a new APK *over* an existing installation if the package id and app signing certificate match, and the next APK has a higher `versionCode`.

1. Generate one owner-controlled signing keystore on a trusted computer. Example:
   `keytool -genkeypair -v -keystore release.jks -alias goldchecker -keyalg RSA -keysize 3072 -validity 10000`
2. Keep multiple encrypted offline backups. **Never place the keystore or passwords in this public repository.**
3. GitHub repository → Settings → Secrets and variables → Actions → New repository secret:
   - `ANDROID_KEYSTORE_BASE64` = base64 encoding of `release.jks` (one line)
   - `ANDROID_KEYSTORE_PASSWORD` = keystore password
   - `ANDROID_KEY_ALIAS` = key alias, e.g. goldchecker
   - `ANDROID_KEY_PASSWORD` = key password
4. Release workflow then signs `assembleRelease` and uploads `GoldSilverChecker-signed.apk`. Sign the very first publicly distributed APK with this key.
5. Each update: increment `versionCode` in `app/build.gradle`, keep the `applicationId`, package name and keystore exactly the same. Android will install the new signed APK as an update without losing app preferences.

If users install a prototype **debug** APK first, they must uninstall it before the first differently-signed **release** APK. Thereafter signed release APKs update normally.

The GitHub Actions workflow includes optional signing. A deploy workflow with automatic version checking can be added once a dedicated public repository is created and signed releases exist. This project contains no API keys or secrets.
