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

## Focus session persistence and unlock timing

The focus session is stored in a Preferences DataStore (`infrastructure/persistence`), so it
survives app kills and reboots. The app re-evaluates it (`tick`: unlock if the countdown has
ended, otherwise re-schedule the unlock) on app start, boot, app update, screen-on /
user-present, shutdown, and when the unlock alarm fires. The alarm is an exact
`ELAPSED_REALTIME_WAKEUP` alarm (`USE_EXACT_ALARM`); if exact alarms are not allowed it falls
back to an inexact one, which can only make the unlock a little late, never early.

### Clock tampering (v1)

While an exit countdown runs, time is measured with the monotonic clock
(`SystemClock.elapsedRealtime()`) from a persisted checkpoint, not with the system clock
(decision in #4, see `application/TrustedTime.kt`):

- Changing the system time does not shorten (or lengthen) a running countdown.
- After a reboot, only the time since boot is added to the last checkpoint: **the countdown
  pauses while the phone is off**, so it can end later than the shown time, never earlier.

Residual risks in v1: the shown unlock time can be off by the time the phone was switched
off; screen-on is only observed while the app process is alive (boot and the alarm cover the
rest); and like everything in v1 the app itself can still be escaped (uninstall, clear data).
Device Owner (#14) locks this down.

Debug builds show a small debug panel with a one-minute exit delay
(`ExitDelayOptions.DEBUG_ONE_MINUTE`) to test the countdown and reboots on the phone.

## Project layout

Single `app` module, Kotlin + Jetpack Compose, minSdk 34. Code lives under
`app/src/main/kotlin/dev/phonefocus/` in DDD layers: `domain`, `application`,
`infrastructure`, `ui`. See [CLAUDE.md](CLAUDE.md) for conventions.
