# Refactor roadmap: architecture, separation of concerns and DDD

Status: proposal (from an architecture review, 2026-11-27). Nothing here has been started.

This roadmap lists architectural improvements for phone-focus, ordered by value and risk.
Each item says what is wrong now, why it matters, what to change, and how to tell it is done.
Pick items one at a time; each phase is meant to be one or a few PRs (one issue each, per
CLAUDE.md).

---

## What already works well (do not lose this)

- **The domain is pure.** `domain` and `application` import nothing from `android.*` or
  `androidx.*` (checked with grep). Time is passed in.
- **`FocusSession` is a proper aggregate root.** It has a private constructor, is
  immutable, and its invariants are documented next to the transitions that enforce them.
- **Errors are values:** `FocusResult`, `FocusError`, `Loosening`. There is no
  exception-driven control flow.
- **Defence in depth.** `MakeStricterForm` and `AppRuleOptions.stricterThan` only offer
  stricter choices, and `FocusSession.tighten` still rejects any loosening.
- **Ports and adapters.** The ports are in `application/Ports.kt`; the Android
  implementations are in `infrastructure`.
- **Persistence fails closed.** `FocusRecordCodec.decode` and `corruptionFallback` mean
  corrupt data never unlocks the phone.
- **Anti-corruption layer.** `ScreenNode` keeps `AccessibilityNodeInfo` out of short-video
  detection.
- **Use cases are small and named:** `CheckAppLaunch`, `StartFocusFromSetup`,
  `ObserveFocusDesktop`, …

Every refactor below must keep these properties and the existing tests green
(`./gradlew testDebugUnitTest assembleDebug`).

---

## Overview

| Phase | Item | Value | Effort | Risk |
|---|---|---|---|---|
| 1 | [R1: Move the composition root out of `infrastructure`](#r1) | High | S | Low |
| 1 | [R2: Enforce the layer rules automatically](#r2) | High | S–M | Low |
| 2 | [R3: Trusted time and countdown in the domain](#r3) | High | L | Medium |
| 3 | [R4: One domain service for the foreground-app verdict](#r4) | Medium | M | Low |
| 3 | [R5: `PackageName` value object](#r5) | Medium | M | Low |
| 4 | [R6: Display text and formatting out of `application`](#r6) | Medium | S–M | Low |
| 4 | [R7: Domain events instead of comparing states](#r7) | Medium | M | Medium |
| 5 | [R8: Read models instead of exposing the aggregate to the UI](#r8) | Low–Medium | M | Low |
| 5 | [R9: Device-specific data out of the domain](#r9) | Low–Medium | M | Low |
| 5 | [R10: Small cleanups](#r10) | Low | S | Low |

Suggested order: R1 + R2 first, because they are cheap and stop further drift. Then R3,
which brings the biggest gain for the integrity of the aggregate. Then the rest as time
allows. R5 gets more expensive the longer it waits.

---

## Phase 1: protect the boundaries

<a id="r1"></a>
### R1: Move the composition root out of `infrastructure`

**Problem.** CLAUDE.md says the dependency direction is
`ui` / `infrastructure` → `application` → `domain`. But `ui` imports concrete
infrastructure classes:

| UI file | Infrastructure used |
|---|---|
| `ui/launcher/LauncherActivity.kt` | `AndroidStartPrerequisites.settingsIntent`, `FocusAccessibilityService.EXTRA_BLOCKED` |
| `ui/MainActivity.kt` | `AndroidStartPrerequisites`, `NotificationPermission` |
| `ui/cover/CoverActivity.kt` | `CoverDisplay`, `CoverIntents` |
| `ui/AppIcon.kt` | `AppIconLoader` |
| `ui/DebugScreenTreePanel.kt` | `ScreenTreeDebug` |
| all ViewModel factories, `DebugFocusPanel` | `infrastructure.AppContainer` |

The root cause: `PhoneFocusApp` and `AppContainer` (the composition root) live in
`infrastructure`, so every screen reaches into infrastructure to get its dependencies, and
from there into concrete adapters.

**Change.**
1. Move `PhoneFocusApp` and `AppContainer` to a new package, `dev.phonefocus.app` (or `di`).
   This is the only package allowed to depend on every layer. Update the manifest's
   `android:name`.
2. Type the container's public properties with application-level types (ports and use
   cases), not adapters. For example, `startPrerequisites: StartPrerequisites` is fine;
   exposing `AndroidStartPrerequisites` is not.
3. Replace the Android glue the UI uses with one of two options:
   - **A port:** e.g. `SystemSettingsNavigator` (open the settings page for a
     prerequisite), `NotificationPermissionSource`, `AppIcons` (an interface implemented
     by `AppIconLoader`).
   - **Or move it into `ui`** when it is pure UI glue, e.g. settings intents in
     `ui/system/SettingsIntents.kt`.
4. Move `EXTRA_BLOCKED` to a small contract object that both the service and the
   launcher depend on, e.g. `app/DesktopIntents.kt`, next to `desktopIntent()`.
   `FocusAccessibilityService.desktopIntent` already avoids depending on `ui`; keep that.
5. The same applies to the receivers and services in `infrastructure`
   (`FocusEventReceiver`, `FocusAccessibilityService`, `CoverWidgetProvider`) that cast to
   `PhoneFocusApp`: they then depend on `app`, which is acceptable for Android entry points.

**Done when.** `grep -rn "import dev.phonefocus.infrastructure" app/src/main/kotlin/dev/phonefocus/ui`
returns nothing, and the R2 test enforces it.

---

<a id="r2"></a>
### R2: Enforce the layer rules automatically

**Problem.** The layering relies on convention, `package-info.kt` comments and review.
R1 shows it has already slipped. In a single module, `FocusSession.restore` cannot be
package-private, so any code can bypass the transitions.

**Change. Pick one, or both:**

- **Option A: Gradle modules (strongest).** Extract `:domain`, and ideally
  `:application`, into `kotlin("jvm")` modules. "No Android in the domain" is then enforced
  by the compiler, and domain tests run faster without AGP. `kotlinx-coroutines-core` is
  the only dependency `application` needs.
- **Option B: architecture tests (cheapest).** Add [Konsist](https://docs.konsist.lemonappdev.com/)
  (or ArchUnit) to the version catalog and write tests in
  `app/src/test/kotlin/dev/phonefocus/ArchitectureTest.kt`:
  - `domain` imports nothing from `android..`, `androidx..`, `dev.phonefocus.application..`,
    `..infrastructure..` or `..ui..`;
  - `application` imports nothing from `android..`, `androidx..`, `..infrastructure..` or
    `..ui..`;
  - `ui` does not import `..infrastructure..` (only `..app..`, `..application..`, `..domain..`);
  - `infrastructure` does not import `..ui..`;
  - `FocusSession.restore(` is only called from `infrastructure.persistence` (and tests).

**Done when.** CI fails on any of the violations above. Add a line to CLAUDE.md pointing to
the test or module setup.

---

## Phase 2: make the aggregate own its invariants

<a id="r3"></a>
### R3: Trusted time and countdown in the domain

**Problem.** According to the README, these are core invariants: "the countdown can only end
later, never earlier" and "changing the system time does not shorten a countdown". But the
code that guarantees them is in `application`:

- `TrustedTime`, `TimeCheckpoint` and `ClockReading` are in `application/TrustedTime.kt`
  and `application/FocusRecord.kt`.
- `FocusRecord` adds state to the aggregate from outside (`checkpoint`,
  `countdownStartedAt`). The persisted unit of consistency is therefore `FocusRecord`, not
  the aggregate root.
- `countdownStartedAt` is nullable only because it was added later. A countdown's start
  really belongs to `ExitPending`.
- Countdown calculations sit in application `companion object`s:
  `ExitCountdown.remaining` and `status`, and `CoverScreen.nextMinuteChange`.
- There are two clock abstractions. `domain.Clock`, with the `requestExit(clock)` and
  `tick(clock)` overloads, is only used in tests. Production code uses
  `application.DeviceClock` plus `TrustedTime`.

So `FocusSession.tick(now)` is only correct if the caller passes a trusted `now`, and the
aggregate cannot protect that itself.

**Change.**
1. Create a `domain/time` package and move `ClockReading`, `TimeCheckpoint` and
   `TrustedTime` into it. They are pure already, so this is a move plus a re-import.
2. Model the countdown in the state, e.g.

   ```kotlin
   data class ExitPending(val countdown: ExitCountdown) : FocusState

   data class ExitCountdown(
       val startedAt: Instant,
       val unlockAt: Instant,
       val checkpoint: TimeCheckpoint,
   ) {
       fun trustedNow(reading: ClockReading): Instant = TrustedTime.now(reading, checkpoint)
       fun remaining(reading: ClockReading): Duration = …   // never negative
       fun total(): Duration = Duration.between(startedAt, unlockAt)
       fun refreshed(reading: ClockReading): ExitCountdown = …  // new checkpoint, same unlockAt
   }
   ```

3. Make the aggregate's time-dependent commands take a `ClockReading` instead of an
   `Instant`: `requestExit(reading)` and `tick(reading)`. The aggregate then works out the
   trusted time itself.
4. `FocusRecord` disappears; the repository stores `FocusSession` directly. Or keep
   `FocusRecord` as a pure persistence DTO inside `infrastructure.persistence`.
5. Move `FocusCommands.nextRecord` (the checkpoint handling) into the aggregate.
6. Delete `domain.Clock` and the unused `Clock` overloads.
7. Migrate the persistence format. Bump `SCHEMA_VERSION`. Data with a missing
   `countdown_started_at` can fall back to `unlockAt - exitDelay` (today's behaviour in
   `ExitCountdown.status`). Keep decoding fail-closed.

**Tests to add or move.** Move `TrustedTimeTest` and the `ExitCountdownTest` calculations
to the domain tests. Add a codec test for migrating from schema 1 to schema 2, and an
invariant test: a `ClockReading` with an earlier wall time never moves `unlockAt` and never
unlocks early.

**Done when.** `FocusSession` alone guarantees the trusted-time invariants, and `application`
only reads the clock and passes the reading on.

---

## Phase 3: sharpen the model

<a id="r4"></a>
### R4: One domain service for the foreground-app verdict

**Problem.** `AppAccess` (domain) knows about `EssentialApps`. But the real decision, "may
this foreground app stay?", is made in `application/AppBlocking.kt`
(`AppBlockingContext.verdict`). That method combines `NeverBlockedPackages`, the device's
`systemPackages`, the launcher-entry check and `AppAccess`. `ForegroundVerdict` is an
application type. So there are two separate ideas of "never blocked" (essential apps versus
system packages), merged only in `application`. Code that uses `AppAccess` directly ignores
the system packages without any warning.

**Change.**
1. Add a domain value object, e.g. `ProtectedPackages(essential: EssentialApps, system: Set<PackageName>)`,
   or extend `AppAccess` to take both.
2. Move `verdict(packageName, hasLauncherEntry)` and `ForegroundVerdict` into the domain
   service.
3. Keep only the process concerns in `application`: debouncing, `RecheckIn`, the
   picture-in-picture handling (`EvaluateForegroundApp`, `EvaluateShortVideoScreen`), and
   combining the flows (`ObserveAppBlocking`).

**Done when.** Every "is this package blocked?" question is answered by a single domain
service, with domain tests covering every `ForegroundVerdict`.

---

<a id="r5"></a>
### R5: `PackageName` value object

**Problem.** `packageName: String` is used everywhere: `Map<String, AppRule>` in `AppPolicy`
and `Tightening`, `Set<String>` for system packages, and in `InstalledApp`,
`ForegroundWindow`, `QuickButton` and the short-video detectors. Labels, view ids and
package names are all `String`s and can be mixed up silently.

**Change.**
```kotlin
@JvmInline
value class PackageName(val value: String) : Comparable<PackageName> {
    init { require(value.isNotBlank()) }
    override fun compareTo(other: PackageName) = value.compareTo(other.value)
    override fun toString() = value
}
```
Introduce it bottom-up: domain first (`AppPolicy`, `Tightening`, `EssentialApps`,
`ShortVideoApps`, `Loosening`, `UnsupportedRule`), then application, then the adapters,
which convert at the boundary (`PackageManager`, accessibility events, the codec).

**Done when.** No domain or application signature takes a raw `String` package name. A
value class has no runtime cost, so no behaviour changes.

---

## Phase 4: clean responsibilities

<a id="r6"></a>
### R6: Display text and formatting out of `application`

**Problem.** The application layer contains text and formatting for the screens:
- `CoverStatus.text` ("Unlocked", "Exit in …") and `QuickAction.label` ("Call") in
  `application/CoverScreen.kt`;
- `ExitCountdown.format` and `formatWithSeconds` (`Locale`-based formatting) in
  `application/ExitCountdown.kt`;
- `ExitCountdownStatus.elapsedFraction: Float`, which exists only for the progress bar;
- `ScreenTreeDump`, debug rendering in `application/ShortVideoBlocking.kt`.

Meanwhile `ui` already has `LauncherLabels` and `SetupLabels`, so text is split across two
layers.

**Change.**
1. `application` returns semantic values only, e.g. `CoverStatus.ExitPending(remaining)`
   and `QuickAction.Call`.
2. Move all strings to `ui` (`CoverLabels`, plus the existing `LauncherLabels` and
   `SetupLabels`), or to Android string resources if localisation is planned. The cover
   widget (`infrastructure/cover/CoverWidget.kt`) needs the labels too: put them in a
   shared presentation package, or have the widget use string resources.
3. Keep the rounding rule ("never show less time than is left") as a function that returns
   a `Duration` or a number of whole minutes, in the domain (see R3). Only the string
   formatting moves.
4. Move `ScreenTreeDump` to `infrastructure/system` (next to `ScreenTreeDebug`) or to a
   `debug` package.

**Done when.** `application` contains no user-facing strings and no `Locale`.

---

<a id="r7"></a>
### R7: Domain events instead of comparing states

**Problem.** `FocusCommands` works out "focus ended" by comparing the states before and
after (`focusEnded(before, after)`). It then triggers the notifier and the alarm sync. The
cover widget is kept up to date separately, with `observeSession().collect { coverWidget.refresh() }`
in `PhoneFocusApp` and an explicit refresh in `FocusEventReceiver`. Each side effect is
re-derived in a different place.

**Change.**
1. Add `sealed interface FocusEvent { FocusStarted; CountdownStarted(unlockAt); CountdownCancelled; FocusEnded; Tightened(changes) }`.
2. The commands return events with the new session:
   `FocusResult.Success(session, events)`, and `tick` returns `TickResult(session, events)`.
3. `FocusCommands` sends the events to handlers:
   - unlock scheduling on `CountdownStarted`, `CountdownCancelled` and `FocusEnded`;
   - the notifier on `FocusEnded`;
   - the cover-widget refresh on any event.

   Handlers are plain application-level interfaces, wired in the composition root.
4. Remove the separate widget-refresh code in `PhoneFocusApp` and `FocusEventReceiver`, or
   keep the flow-based refresh as the only mechanism. Choose one.

**Note.** The current `syncUnlockAlarm` is idempotent and also re-schedules after a reboot
(`tick` with no change). Keep a "re-sync on tick" path so alarms survive reboots.

**Done when.** No code compares two sessions to find out what happened.

---

## Phase 5: polish

<a id="r8"></a>
### R8: Read models instead of exposing the aggregate to the UI

**Problem.** `FocusDesktop.session: FocusSession` is passed to Compose. `DesktopScreen`,
`FocusSetupScreen` and `MakeStricterDialog` branch on `FocusState` or build sessions. The
UI could call `session.tighten(…)`, which is harmless because sessions are immutable and
the UI never persists them, but it ties the screens to the shape of the aggregate.
`DebugFocusPanel` calls `container.commands` directly from a Composable.

**Change.**
1. Use cases return read models, e.g.
   `FocusStatusView(mode: Mode, exitDelay, unlockAt?, canRequestExit, canCancelExit, canTighten)`.
2. The UI only reads these views. Matching on `FocusError` stays allowed, because errors
   are part of the published language.
3. Optionally add a `DebugViewModel` for `DebugFocusPanel` (debug builds only).

**Done when.** `ui` no longer imports `FocusSession`.

---

<a id="r9"></a>
### R9: Device-specific data out of the domain

**Problem.** Pure Kotlin, but this is really adapter configuration that changes for
reasons outside the domain:
- `domain/NeverBlockedPackages.kt`: hard-coded Samsung and Google package names;
- `domain/shortvideo/ShortVideoRules.kt`: Instagram and YouTube view ids. Its own comment
  says they "break when an app changes its UI".

Also, `ShortVideoApps` is a global singleton that `AppPolicy.unsupportedRulesIn` and
`AppRuleOptions` reach for directly, while `EssentialApps` is passed in. That inconsistency
makes the set of supported apps hard to replace in tests.

**Change (a judgement call, discuss before doing).**
1. Keep the domain concepts in the domain: `ShortVideoDetector`, `NodeMatcher`,
   `ScreenNode`, `ShortVideoRule`, and "a package can be protected".
2. Move the concrete data (`ShortVideoRules.BY_PACKAGE`, the `NeverBlockedPackages` lists)
   to `infrastructure/shortvideo` and `infrastructure/system`. The composition root passes
   it in.
3. Replace the global `ShortVideoApps` with a value object, `ShortVideoCapableApps`, passed
   to `AppPolicy` and `AppRuleOptions` the same way as `EssentialApps`. It could also be
   derived from the registered detectors (`ShortVideoDetectors.packages`).

**Done when.** Domain code does not hard-code any third-party package names or view ids.

---

<a id="r10"></a>
### R10: Small cleanups

- [ ] **`domain/AppVersion.kt`**: a leftover placeholder ("the real model arrives with
      later issues"), now used by `MainActivity`. It isn't domain. Move it to `ui` or
      `app`, or delete it.
- [ ] **Handwritten `equals`, `hashCode` and `toString`** in `FocusSession`, `AppPolicy`
      and `EssentialApps`. For `FocusSession`, use `@ConsistentCopyVisibility data class`
      with a private constructor. For the value objects, use a `data class` that makes a
      defensive copy in `init`, or wrap them in a value class.
- [ ] **`MakeStricterForm.create`** rebuilds `EssentialApps` from the catalogue's
      `essential` flags (`application/MakeStricter.kt:71`), recreating a domain value
      object from a read model. Pass `EssentialApps` in, as `FocusSetupForm` and
      `CoverScreen` do.
- [ ] **`StartPrerequisites`** is a port interface whose default methods (`statuses()`,
      `missing()`) contain application logic. Reduce the port to `status(…)` and put the
      aggregation in a use case. `FocusSetupUiState.missingPrerequisites` repeats the same
      filter; reuse the use case there.
- [ ] **The `BY_LABEL` comparator** is copied in `application/AppCatalogue.kt` and
      `application/FocusDesktop.kt`. Make it a single `InstalledApp.BY_LABEL`.
- [ ] **`FocusSetupViewModel`** has hard-coded error strings ("Could not start focus
      mode.") and shows `${result.error}` with `toString()`. Move them to `SetupLabels`
      with a proper `when` over `FocusError`, as `LauncherLabels` does.

---

## Checklist

- [ ] R1: Move the composition root out of `infrastructure`
- [ ] R2: Enforce the layer rules (Konsist test or Gradle modules)
- [ ] R3: Trusted time and countdown in the domain (plus schema migration)
- [ ] R4: One domain service for the foreground-app verdict
- [ ] R5: `PackageName` value object
- [ ] R6: Display text and formatting out of `application`
- [ ] R7: Domain events instead of comparing states
- [ ] R8: Read models instead of exposing the aggregate to the UI
- [ ] R9: Device-specific data out of the domain
- [ ] R10: Small cleanups
