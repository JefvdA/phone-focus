# CLAUDE.md

Conventions for working on phone-focus. Issue #1 is the authoritative description of the
concept, domain model, invariants and architecture; read it before changing the domain.

## Architecture (DDD)

Single Gradle module `app`, Kotlin + Jetpack Compose, minSdk 33 (Android 13). The target
device is a Galaxy Z Flip 6; the current test device is a Galaxy Tab S7 on Android 13 (#25),
so any Android API newer than 33 needs a `Build.VERSION.SDK_INT` check (`lintDebug` reports
`NewApi`). Package root `dev.phonefocus`, split into layers:

- `domain`: the `FocusSession` aggregate, value objects (`AppRule`, `AppPolicy`,
  `ExitDelay`), commands and invariants. **Pure Kotlin: no `android.*` or `androidx.*`
  imports**, no I/O, no system clock (time is passed in, e.g. `now`). The single source
  of truth for focus state. Launcher and accessibility service check apps through the
  `AppAccess` domain service (session + device `EssentialApps`, which are never blocked),
  not the policy directly.
- `application`: use cases that load the aggregate, call domain commands and persist the
  result. Depends on `domain` only; defines ports (interfaces) for what it needs.
- `infrastructure`: implementations of those ports: persistence, Android services
  (launcher, accessibility service, cover widget, clock). May use Android APIs.
- `ui`: Compose screens and activities. Reads state and sends commands through
  `application`; never mutates domain state directly or holds business rules.

Dependency direction: `ui` / `infrastructure` -> `application` -> `domain`. Never the reverse.

## Tests

- Every domain change needs unit tests covering the rule or transition it touches,
  including the invariants from #1 (policy/delay can only get stricter while focused, a
  running countdown keeps its `unlockAt`, only the countdown ending leaves focus).
- Unit tests live in `app/src/test/kotlin/...` (JUnit 4, plain JVM; no Robolectric needed
  for the domain).
- Before pushing: `./gradlew testDebugUnitTest assembleDebug` must pass (add `lintDebug`
  when touching Android APIs or the manifest).

## Build

- Gradle Kotlin DSL with the version catalog in `gradle/libs.versions.toml`; add
  dependencies there, not inline. Use the Gradle wrapper (`./gradlew`).
- CI (`.github/workflows/build.yml`) runs unit tests and uploads a debug APK on every push
  and PR. Keep it green.
- Never commit keystores or secrets (`*.jks`, `*.keystore` are git-ignored).

## Git workflow

- Branch per issue: `<type>/<issue-number>-<short-kebab-description>`, e.g.
  `feat/3-focus-session-aggregate`. Types: feat, fix, chore, refactor, docs, test, ci, build.
- Commits: Conventional Commits with a scope, e.g. `feat(domain): add FocusSession transitions`.
- PR title in the same format with the issue number, e.g.
  `feat(domain): FocusSession aggregate (#3)`. PR body: summary, what was tested,
  decisions, and `Closes #<n>`.
- Keep each PR scoped to its issue; no unrelated changes.
