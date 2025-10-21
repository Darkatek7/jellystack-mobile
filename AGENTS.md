# AGENTS.md

## Overview
This repository is a Kotlin Multiplatform project with Android and iOS modules.  
All CI, automation, or Codex builds must be executed from the repository root using the Gradle Wrapper.

---

## Requirements
- JDK 17 on PATH  
- Unix-like shell (Linux or macOS)  
- Internet access for SDK downloads  
- macOS host required for iOS targets  
- Linux runners build only JVM + Android modules

---


## Developer Setup (local)
./gradlew assembleDebug
```

---

## Verification Rule
Every task, commit, or automation step must succeed with:
```bash
./gradlew build
```
If this command fails, the task is considered incomplete.

---

## Validation
```bash
java -version            # must show 17
sdkmanager --list | grep android-34
test -x ./gradlew && echo OK
```

---

## Notes
- Run all Gradle tasks from repository root.  
- Never commit `local.properties`.  
- Keep `gradlew` executable.  
- Cache Gradle dependencies to reduce CI time.  
- Do not invoke Gradle inside submodules; always use the root wrapper.  
