# AGENTS.md

## Overview
This repository is a multi-module Kotlin project targeting Android and iOS clients plus shared libraries.  
All automation, CI, and AI actions must run from the repository root and invoke the Gradle Wrapper (`./gradlew`).

---

## Module map
- `app-android`: Android application module.
- `app-ios`: Shared Kotlin code prepared for the iOS target.
- `shared-core`, `shared-database`, `shared-network`: Kotlin Multiplatform shared logic.
- `players`: Sample feature module used in multiple apps.

---

## Environment
- Use **JDK 17**.
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

---

## Setup
```bash
set -euo pipefail

# Verify JDK 17
java -version 2>&1 | grep 'version "17' >/dev/null || {
  echo "JDK 17 required."
  exit 1
}

# Define SDK root
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
mkdir -p "$ANDROID_SDK_ROOT"

# Ensure PATH includes SDK tools
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Check for sdkmanager
if ! command -v sdkmanager >/dev/null; then
  echo "Android cmdline-tools missing under $ANDROID_SDK_ROOT/cmdline-tools/latest"
  exit 1
fi

# Accept licenses and install required packages
yes | sdkmanager --licenses
sdkmanager "cmdline-tools;latest" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Write local.properties bound to this workspace
printf "sdk.dir=%s\n" "$ANDROID_SDK_ROOT" > local.properties
```

---

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

---

## Java toolchain
The build must use Java 17. Enforce via Gradle toolchains:

**`build.gradle.kts` (root)**
```kotlin
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}
```

---

## Rules
- Run `./gradlew spotlessCheck build` before any commit, merge, or automated change.
- Always execute the **Setup** section before any Gradle command.
- Use the full verification command before tagging a release.
- If any Gradle task fails, stop immediately and surface the Gradle log.
- Do not bypass the Gradle Wrapper or edit SDK paths.
- Generate or refresh `local.properties` on every run; do not commit it.
- Prefer cached Gradle dependencies to reduce build time.
- If `sdkmanager` is unavailable, fail fast with a clear message.

---

## Git hygiene
Add this to `.gitignore`:
```
local.properties
```

---

## Notes
Assume a Unix-like shell environment.  
Always execute commands from the repository root unless a module readme specifies otherwise.
