# Price Monitor release signing (version 3.4+)

Android application ID: `com.example.pricetracker`
Key alias: `pricemonitor-release`
Keystore type: PKCS12 (RSA-3072)
Release certificate SHA-256: `C5:14:66:B6:4C:1F:83:52:DE:CE:2F:62:0B:2C:6C:C7:1A:16:69:0A:48:D0:BD:D7:38:E1:23:CD:69:0F:F0:E2`

## Permanent recovery

- The private backup archive `PriceMonitor-SIGNING-RECOVERY-BUNDLE.zip` is stored in the owner's **private Google Drive** folder `PumpSignal-Signing-Backup` (alongside the unrelated PumpSignal signing backup).
- Folder: https://drive.google.com/drive/folders/1v4_9elHaUqYODphFk5v6A_ZGxATw-NE7
- The backup contains the PKCS12 keystore, the strong random password and recovery instructions.
- The public GitHub repository may store only a **password-protected** PKCS12 file, `.github/signing/PriceMonitor-Update-Key.p12.b64`. Never commit the password or an unencrypted private key. The public repository by itself is not the full signing backup.
- Add GitHub Actions repository secret `PRICE_MONITOR_KEYSTORE_PASSWORD` with the exact password from the private Google Drive archive. This secret must be configured by an account administrator, not placed in source code.
- For future releases, always reuse the same keystore and alias. Check the signing certificate SHA-256 before distributing any APK.
- Do **not** reuse the PumpSignal key: its certificate is different.

## Compatibility warning

Price Monitor **3.3** was signed with a temporary Android debug key (SHA-256:
`D7:E8:39:5B:8D:06:4C:D7:01:F4:C6:F5:15:84:88:66:7D:1D:6E:30:07:2B:7A:9D:12:3D:A2:5D:E1:60:0F:D5`).

That private debug key was not retained by GitHub Actions. Hence the new permanent release certificate **cannot update the installed 3.3 APK in place**. Android will reject the signature mismatch. A one-time uninstall/reinstall is required to switch to the permanent release certificate unless the matching original debug keystore is later recovered. Afterwards, versions signed with this permanent key can update in place.

## Build process

The GitHub Actions Android workflow will build `app-debug.apk` for CI, but such a file should never be distributed as a release. When `PRICE_MONITOR_KEYSTORE_PASSWORD` is configured, the workflow will also assemble and upload a signed `app-release.apk`. Only distribute the **signed release** artifact for version 3.4 onward.

Never create a replacement release signing key when making a new app version.
