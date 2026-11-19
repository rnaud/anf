# anf games for Android

A native Android library for the ten projects on anf.io. Eight browser games download as complete ZIP packages and play inside an accelerated WebView. Jumpman and Alexandria open their installed native apps, or their APK release pages. Android 8.0+; a current Android System WebView and a WebGL-capable device are needed for the 3D games.

[Download the signed APK](https://github.com/rnaud/anf/releases/download/game-library/anf-games.apk) · [Release and game packages](https://github.com/rnaud/anf/releases/tag/game-library)

## Game shelf

The library uses a responsive grid of illustrated game cartridges, with ready, download, update and native-app badges. Tap a cartridge to open its details and Play, Download, Import or Remove controls. **Downloaded** filters the shelf to offline web games. Short landscape screens use a compact header so cartridges stay visible.

While playing, the **Library** control, a hardware Back button, or the system Back gesture offers **Keep playing** and **Exit game**. Keeping the game resumes the same WebView session. Exiting destroys that WebView and returns to the shelf without clearing saved data. Modern Back callbacks run above WebView navigation; a key-dispatch fallback handles physical Back and older Android versions.

## Build

Install JDK 21 and Android SDK 36, set `JAVA_HOME` and `ANDROID_HOME`, then:

```sh
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`. Debug builds use `io.anf.games.debug`, separate from the signed release app and its saves. Open the `android/` directory in Android Studio for development. The website remains a standalone static site.

## Build game downloads

From the repository root, with Node 22+, Python 3.9+, npm and Git:

```sh
python3 android/tools/package-games.py --bundle-catalog
# Or reuse clean HEAD snapshots and installed dependencies from sibling repositories:
python3 android/tools/package-games.py --local /path/to/projects --bundle-catalog
```

The script uses temporary checkouts, builds Vite projects with root-relative paths, and packages OpenKart's static runtime files. Local checkouts must match the remote default-branch HEAD; stale ones fail unless `--allow-stale-local` explicitly requests a historical build. Snapshots stream directly into extraction to limit temporary disk use. Hiraeth uses its public build. It never modifies the game sources. Native apps and ROM data are not packaged. Missing local repositories are cloned from `rnaud` on GitHub. `--only dust-valley` builds a single game for development.

Outputs are in `android/packages/`: eight content-addressed ZIPs, a matching `.manifest.json` for each game, and `catalog.json`. Every ZIP also contains `package-manifest.json`: the complete built file inventory with paths, byte sizes, and SHA-256 hashes. The inventory excludes only itself to avoid a recursive hash. The build rejects missing, extra, duplicate, or corrupt files. These are Android download manifests; website install metadata (`manifest.webmanifest`) and service-worker caching are separate. `--bundle-catalog` also updates the app's initial catalog with download and installed sizes, file counts, commit IDs, HTTPS package/manifest URLs and SHA-256 hashes. Rebuild the APK afterward.

For local testing before publication, transfer a ZIP to the device's Downloads folder, then use **Import game ZIP** on its card. The file must match the bundled catalog's size and SHA-256. No broad storage permission is needed. The online Download buttons work after the release assets are published.

## Publish

Game versions are not tied to the APK. Every hour, the **Android library** workflow's `update-games` job compares each game repo's `HEAD` with the revision in the published `catalog.json`. It rebuilds only games that have new commits, uploads their packages, and then switches the catalog. A game that fails to build keeps its previous package, and the run is marked failed. To do this right away, run the workflow manually with `update_games` set.

```sh
python3 android/tools/package-games.py --published-catalog --changed-only   # what the scheduled job runs
```

The same workflow builds and checks APKs on changes. Set its `publish` input to rebuild changed games and publish them with `anf-games.apk` to the `game-library` release. Only publish a new APK when the app itself changes. GitHub pauses scheduled workflows after 60 days without repository activity; re-enable it in the Actions tab if that happens.

Publishing requires a stable signing key in repository secrets:

- `ANF_KEYSTORE_B64`: base64-encoded Java keystore, alias `anf`.
- `ANF_KEYSTORE_PASSWORD`: store and key password.

The release build intentionally has no debug-signing fallback. Keep the key for future updates. For a local signed build, set `ANF_KEYSTORE` to its path and `ANF_KEYSTORE_PASSWORD`, then run `./gradlew assembleRelease`. Never commit the key.

Packages upload before the catalog changes (`android/tools/publish-release.sh`), and old content-addressed packages remain available for installed catalogs. Publishing the Android release does not publish the website.

## Package verification

```sh
python3 -m unittest discover -s android/tools -p 'test_*.py'
```

Use `--repack-existing android/packages --output android/packages-new --bundle-catalog` to add inventories to previously published ZIPs without upgrading the game code. Existing ZIP checksums are verified first. A package inventory proves what was built and downloaded; it does not prove that every possible game mode has been played through.

Dust Valley’s current package includes MCM preview: all 33 terrain binaries, original bike/rider models and skins, recovered scene data, and all bundled audio. The builder checks those source assets against the output by content, including lazy-loaded files. The old ~2 MB package was a stale Classic-only snapshot; verifying its file inventory did not establish that it included the current game. The corrected package is roughly 118 MB compressed / 121 MB installed. Its exact revision, sizes and per-file hashes are in the catalog and package manifest.

## Runtime behavior

- The app checks the published catalog on launch and when it returns to the foreground (at most every 15 minutes). **↻ Updates** checks immediately. Ready games with a newer published version show **UPDATE AVAILABLE**.
- **Download** always fetches the latest catalog first, so it installs the newest published version even if the shelf was loaded earlier. Offline, it falls back to the last known catalog.
- The catalog bundled in the APK is only a first-launch/offline fallback. The last successfully fetched catalog is kept and used offline.
- Downloads run one at a time while the app is open. Progress and cancellation are visible. A killed/interrupted download can be retried; it does not resume from partial bytes.
- ZIPs are checked against their declared size and SHA-256, then extracted with traversal, duplicate-path, entry-count and expanded-size limits. A new version becomes current only after extraction and entrypoint validation succeed. Failed updates preserve the old install.
- Each game has a stable, separate HTTPS origin under `<game>.games.anf.invalid`, served from private app storage. Saves in localStorage/IndexedDB survive package updates and removal. Uninstalling the app clears saves. There is no native JavaScript bridge or file-URL access.
- Service worker installation is blocked: the launcher owns offline files and updates, avoiding stale service-worker caches. Optional remote features still need internet; packaged core assets do not.
- External HTTPS navigation opens the browser. Native-app installs use the browser and Android's normal package installer; the launcher never installs apps silently.
- Game controls, controller support and graphics performance come from each game. This launcher does not convert keyboard-only interactions to touch or guarantee every game mode on every device.

## Verification

`PackageStoreTest` covers verified updates, checksum failure, traversal, extraction limits, missing entrypoints, invalid IDs and cancellation. Run the Gradle checks above. For device verification, install the APK, import packages, disable connectivity, launch a game, return to the library, rotate the screen, relaunch, and check save persistence. Check a failed/cancelled download and native-app handoff too.

The WebView local-content approach follows [Android's local content guidance](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content), using per-game HTTPS request interception instead of permissive `file://` access.

Initial verification: all eight packaged games loaded with external requests blocked in Chromium; no uncaught JavaScript errors or missing local startup assets. Dust Valley was imported through Android’s file picker and launched in the emulator with connectivity disabled. Its Classic race and free-ride modes were exercised with network requests blocked in Chromium. Those initial checks covered an older Classic-only revision and did not cover MCM preview. The corrected package has separate source-asset completeness checks and MCM offline startup coverage. Full playthroughs and physical-device performance remain unverified.

### Cartridge and Back navigation device check

With the debug APK installed on an Android emulator using gesture navigation:

```sh
python3 android/tools/test-device-navigation.py --adb "$ANDROID_HOME/platform-tools/adb"
```

This test only operates on an `emulator-*` serial and the separate debug app. It seeds a small game fixture, checks cartridge details and filtering, physical/system Back with a focused WebView and history, keep-playing session preservation, explicit exit, edge Back gestures when the emulator’s SystemUI handler is attached, and portrait/landscape shelves. A detached system gesture handler is reported as skipped rather than passed. Screenshots go to `.local/cartridge-qa/`. It does not touch release-app downloads or saves.
