# Cloud Build Setup for Kotlin Multiplatform (Android/iOS)

## Purpose
This guide ensures that Gradle builds run correctly in a clean cloud CI environment where Android SDK and command-line tools are not preinstalled.

---

## Prerequisites
- JDK 17 installed and available on PATH
- Unix-like shell (Linux or macOS)
- Internet access to download Android SDK components

---

## Environment Setup Script

Save the following as `setup-android-env.sh` and execute before running Gradle tasks.

```bash
#!/usr/bin/env bash
set -euo pipefail

# Verify JDK 17
java -version 2>&1 | grep 'version "17' >/dev/null || { echo "JDK 17 required."; exit 1; }

# SDK root
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

# Install command-line tools if missing
if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading Android command-line tools..."
  curl -sSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/clt.zip
  unzip -q /tmp/clt.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
fi

# Update PATH
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Accept licenses and install required packages
yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

# Write local.properties
printf "sdk.dir=%s\n" "$ANDROID_SDK_ROOT" > local.properties

# Ensure gradlew is executable
chmod +x gradlew

echo "Android SDK environment ready."
```

---

## Build Commands

### Format and Build
```bash
./gradlew spotlessCheck build
```

### Lint and Static Analysis
```bash
./gradlew lint detekt
```

### Unit Tests
```bash
./gradlew test
```

### Full Android Verification
```bash
./gradlew spotlessCheck lint detekt test build
```

---

## Notes
- Execute from repository root.
- macOS required for iOS targets.
- Do not commit `local.properties`.
- Prefer cached Gradle dependencies to reduce CI time.
