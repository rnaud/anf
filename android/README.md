# anf games for Android

A native Android library for the ten projects on anf.io. Eight browser games download as complete ZIP packages and play inside an accelerated WebView. Jumpman and Alexandria open their installed native apps, or their APK release pages. Android 8.0+; a current Android System WebView and a WebGL-capable device are needed for the 3D games.

[Download the signed APK](https://github.com/rnaud/anf/releases/download/game-library/anf-games.apk) · [Release and game packages](https://github.com/rnaud/anf/releases/tag/game-library)

## Build

Install JDK 21 and Android SDK 36, set `JAVA_HOME` and `ANDROID_HOME`, then:

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`. Open the `android/` directory in Android Studio for development. The website remains a standalone static site.

## Build game downloads

From the repository root, with Node 22+, Python 3.9+, npm and Git:

```sh
python3 android/tools/package-games.py --bundle-catalog
# Or reuse clean HEAD snapshots and installed dependencies from sibling repositories:
python3 android/tools/package-games.py --local /path/to/projects --bundle-catalog
```

The script uses temporary checkouts, builds Vite projects with root-relative paths, and packages OpenKart's static runtime files. Hiraeth uses its public build. It never modifies the game sources. Native apps and ROM data are not packaged. Missing local repositories are cloned from `rnaud` on GitHub. `--only dust-valley` builds a single game for development.

Outputs are in `android/packages/`: eight content-addressed ZIPs and `catalog.json`. `--bundle-catalog` also updates the app's initial catalog with package sizes, commit IDs, HTTPS download URLs and SHA-256 hashes. Rebuild the APK afterward.

For local testing before publication, transfer a ZIP to the device's Downloads folder, then use **Import game ZIP** on its card. The file must match the bundled catalog's size and SHA-256. No broad storage permission is needed. The online Download buttons work after the release assets are published.

## Publish

The **Android library** GitHub Actions workflow builds and checks APKs on changes. A manual run also builds all eight browser packages. Set its `publish` input to publish packages, the catalog and `anf-games.apk` to the `game-library` release.

Publishing requires a stable signing key in repository secrets:

- `ANF_KEYSTORE_B64`: base64-encoded Java keystore, alias `anf`.
- `ANF_KEYSTORE_PASSWORD`: store and key password.

The release build intentionally has no debug-signing fallback. Keep the key for future updates. For a local signed build, set `ANF_KEYSTORE` to its path and `ANF_KEYSTORE_PASSWORD`, then run `./gradlew assembleRelease`. Never commit the key.

Packages upload before the catalog changes, and old content-addressed packages remain available for installed catalogs. **Check for game updates** fetches the release catalog; the app's existing catalog and downloads remain available if that request fails. Publishing the Android release does not publish the website.

## Runtime behavior

- The catalog ships inside the APK, with an optional last-successful downloaded catalog.
- Downloads run one at a time while the app is open. Progress and cancellation are visible. A killed/interrupted download can be retried; it does not resume from partial bytes.
- ZIPs are checked against their declared size and SHA-256, then extracted with traversal, duplicate-path, entry-count and expanded-size limits. A new version becomes current only after extraction and entrypoint validation succeed. Failed updates preserve the old install.
- Each game has a stable, separate HTTPS origin under `<game>.games.anf.invalid`, served from private app storage. Saves in localStorage/IndexedDB survive package updates and removal. Uninstalling the app clears saves. There is no native JavaScript bridge or file-URL access.
- Service worker installation is blocked: the launcher owns offline files and updates, avoiding stale service-worker caches. Optional remote features still need internet; packaged core assets do not.
- External HTTPS navigation opens the browser. Native-app installs use the browser and Android's normal package installer; the launcher never installs apps silently.
- Game controls, controller support and graphics performance come from each game. This launcher does not convert keyboard-only interactions to touch or guarantee every game mode on every device.

## Verification

`PackageStoreTest` covers verified updates, checksum failure, traversal, extraction limits, missing entrypoints, invalid IDs and cancellation. Run the Gradle checks above. For device verification, install the APK, import packages, disable connectivity, launch a game, return to the library, rotate the screen, relaunch, and check save persistence. Check a failed/cancelled download and native-app handoff too.

The WebView local-content approach follows [Android's local content guidance](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content), using per-game HTTPS request interception instead of permissive `file://` access.

Initial verification: all eight packaged games loaded with external requests blocked in Chromium; no uncaught JavaScript errors or missing local startup assets. Dust Valley was imported through Android’s file picker and launched in the emulator with connectivity disabled. Full playthroughs and physical-device performance remain unverified.
