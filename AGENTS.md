# AGENTS.md

## Overview
This repository is a multi-module Kotlin project targeting Android and iOS clients plus shared libraries. All automation, CI, and AI actions must run from the repository root and invoke the Gradle Wrapper (`./gradlew`).

## Module map
- `app-android`: Android application module.
- `app-ios`: Shared Kotlin code prepared for the iOS target.
- `shared-core`, `shared-database`, `shared-network`: Kotlin Multiplatform shared logic.
- `players`: Sample feature module used in multiple apps.

## Environment
- Use JDK 17.
- Android SDK must be available through `ANDROID_SDK_ROOT` or `ANDROID_HOME`.
- Required SDK packages:
  - `platforms;android-34`
  - `build-tools;34.0.0`
  - `cmdline-tools;latest`
- Required command-line tools: `sdkmanager`, `avdmanager`.
- Kotlin native targets rely on a macOS host when assembling iOS binaries; Linux agents may build only JVM and Android variants.

### Validation
```bash
java -version   # must report version 17
sdkmanager --list | grep "android-34"
```

## Commands

### Format and build
```bash
./gradlew spotlessCheck build
```

### Lint and static analysis
```bash
./gradlew lint detekt
```

### Unit tests
```bash
./gradlew test
```

### Instrumented tests
```bash
./gradlew connectedAndroidTest
```

### Full verification (Android only on non-macOS hosts)
```bash
./gradlew spotlessCheck lint detekt test build
```

## Rules
- Run `./gradlew spotlessCheck build` before any commit, merge, or automated change.
- Use the full verification command before tagging a release.
- If any Gradle task fails, stop immediately and surface the Gradle log.
- Do not bypass the Gradle Wrapper or edit SDK paths.
- Prefer cached Gradle dependencies to reduce build time.
- Keep module-specific instructions inside their directories if additional guidance is needed.

## Notes
Assume a Unix-like shell environment.
Always execute commands from the repository root unless a module readme states otherwise.
