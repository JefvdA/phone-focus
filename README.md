# phone-focus

A "dumb phone" mode for a Galaxy Z Flip 6 on stock One UI. Unlocked is the normal phone;
Focus is a retro home screen that shows only allowed apps, with short-video feeds blocked,
and the only way out is a delayed exit. See issue #1 for the concept and domain model.

## Install the app on your phone (no laptop needed)

Every push and pull request runs the **Build** workflow, which runs the unit tests and
builds a debug APK.

1. On the phone, open GitHub in the browser (Chrome or Samsung Internet) and sign in.
   Artifacts can only be downloaded when signed in. Use "Desktop site" if the mobile
   page hides the Actions tab.
2. Go to the repository, **Actions** tab, **Build** workflow, and open the latest green run
   (or open a pull request, **Checks** tab, then the run).
3. Scroll to **Artifacts** at the bottom of the run summary and tap
   `phone-focus-debug-<run number>.apk` to download it.
   (If the download arrives as a `.zip`, open it in **My Files** and tap the `.apk` inside.)
4. Tap the downloaded file in the browser's download bar or in **My Files > Downloads**.
5. The first time, Android asks to allow installs from this source: tap **Settings**,
   enable **Allow from this source** for your browser / My Files, go back, tap **Install**.
6. If Play Protect warns about an unknown app, choose **Install anyway**.
7. Open **Phone Focus** from the app drawer.

### Updates

Newer builds install **over** the old one (no uninstall, data kept) because:

- every CI build is signed with the same fixed debug key (repository secrets, see below), and
- the `versionCode` is the workflow run number, so it always increases.

If Android says "App not installed" / "package conflicts", the installed app was signed
with a different key (e.g. a build from before the secrets were set up). Uninstall it once,
then install the new build.

Artifacts are kept for 30 days.

## Signing (repository secrets)

The workflow signs debug builds with a fixed keystore when these repository secrets exist
(Settings > Secrets and variables > Actions):

| Secret | Value |
|---|---|
| `DEBUG_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `DEBUG_KEYSTORE_PASSWORD` | keystore password |
| `DEBUG_KEY_ALIAS` | key alias |
| `DEBUG_KEY_PASSWORD` | key password |

Without them the build still succeeds but uses a throwaway debug key, so it will not update
a previously installed build. Locally, set the same names as environment variables, with
`DEBUG_KEYSTORE_PATH` pointing to the keystore file instead of the base64 value.

## Building locally

Requires JDK 17+ and the Android SDK (`ANDROID_HOME`).

```sh
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

Single `app` module, Kotlin + Jetpack Compose, minSdk 34. Code lives under
`app/src/main/kotlin/dev/phonefocus/` in DDD layers: `domain`, `application`,
`infrastructure`, `ui`. See [CLAUDE.md](CLAUDE.md) for conventions.
