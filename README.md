# Jellystack Mobile

[![CI](https://github.com/jellystack/jellystack-mobile/actions/workflows/ci.yml/badge.svg)](https://github.com/jellystack/jellystack-mobile/actions/workflows/ci.yml)
[![Static Analysis](https://github.com/jellystack/jellystack-mobile/actions/workflows/static-analysis.yml/badge.svg)](https://github.com/jellystack/jellystack-mobile/actions/workflows/static-analysis.yml)

Jellystack Mobile is a Kotlin Multiplatform application that targets Android and iOS. It uses Compose Multiplatform for UI, shared expect/actual implementations for platform logic, and modular Gradle configuration to enable focused workstreams across networking, database, playback, and tooling.

## Project layout

- `app-android`: Android host application using Compose.
- `app-ios`: Kotlin/Native framework exposing the shared Compose hierarchy to SwiftUI.
- `shared-core`: Core utilities, platform detection, and foundation services.
- `shared-network`: Ktor client factory and networking helpers.
- `shared-database`: SQLDelight database schema and driver abstractions.
- `players`: Playback state management.
- `design`: Theming, root Compose tree, and shared visual components.
- `testing`: Shared testing utilities for multiplatform modules.
- `tools`: JVM utilities and future API generation scripts.

## Build prerequisites

- JDK 21+
- Android SDK with API level 35 platform tools
- Xcode 15+ for iOS builds

Gradle Wrapper is committed, so no manual installation is required beyond a working Java runtime.

## Building

```bash
./gradlew build
```

### Android

Assemble a debug build:

```bash
./gradlew :app-android:assembleDebug
```

### iOS

Produce the iOS framework for arm64 devices:

```bash
./gradlew :app-ios:linkReleaseFrameworkIosArm64
```

The generated framework can be consumed from `app-ios/build/bin/iosArm64/releaseFramework/App.framework`.

Run the shared iOS test suite on simulators:

```bash
./gradlew :shared-core:iosSimulatorArm64Test
```

## Static analysis

Run the configured format and lint checks:

```bash
./gradlew spotlessCheck detekt
```

## Contributing

We welcome contributions! Review the [Code of Conduct](./CODE_OF_CONDUCT.md) and [Contributing guide](./CONTRIBUTING.md), then open an issue or pull request. Roadmap details live in [`workpackages.md`](./workpackages.md) and progress tracking in [`checklistandmilestones.md`](./checklistandmilestones.md).

## Next steps

Refer to `workpackages.md` and `checklistandmilestones.md` for roadmap guidance, including CI enablement, navigation scaffolding, DI wiring, and feature implementation.
