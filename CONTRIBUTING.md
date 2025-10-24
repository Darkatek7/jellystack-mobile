# Contributing to Jellystack Mobile

Thanks for taking the time to contribute! This guide walks through tooling, coding standards, and the general process for proposing changes.

## Getting Started

1. **Fork and clone** the repository.
2. Ensure you have the required tooling:
   - JDK 17
   - Android SDK with API level 35 platform tools
   - Xcode 15+ (for iOS builds)
3. Install the Kotlin Multiplatform Mobile prerequisites documented in the [JetBrains guide](https://www.jetbrains.com/help/kotlin-multiplatform-dev/setup.html).

Run the full build once to seed caches:

```bash
./gradlew build
```

## Branching and Commits

- Start from the latest `main`.
- Use feature branches: `feature/<summary>` or `fix/<summary>`.
- Keep commits focused. Squash before merging if the history becomes noisy.

## Formatting and Linting

We use Spotless with ktlint and Detekt. Before pushing:

```bash
./gradlew spotlessApply
./gradlew detekt
```

CI runs these checks automatically; running them locally avoids churn.

## Tests

- JVM/unit tests: `./gradlew allTests`
- Android debug build smoke: `./gradlew :app-android:assembleDebug`
- iOS simulator tests (if applicable): `./gradlew :shared-core:iosSimulatorArm64Test`

Please add or update tests alongside code changes.

## Submitting Changes

1. Open a pull request describing the change, referencing issues as appropriate.
2. Include screenshots or screen recordings for UI updates.
3. Ensure the checklist in `checklistandmilestones.md` reflects any newly completed work.
4. Expect review for architecture, testability, and user impact.

## Reporting Issues

- Use the issue templates under `.github/ISSUE_TEMPLATE`.
- Provide reproduction steps, logs, and environment details where possible.

## Community Expectations

Participation is governed by the [Code of Conduct](./CODE_OF_CONDUCT.md). Please review it before engaging with the community.
