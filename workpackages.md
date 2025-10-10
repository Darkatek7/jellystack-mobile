# 0. Repo bootstrap

## Tasks
- Create public repo: `jellystack-mobile`.
- Add APGL-3.0 license, README, CODE_OF_CONDUCT, CONTRIBUTING, issue templates.
- Init Kotlin Multiplatform project with Gradle Kotlin DSL, Compose Multiplatform UI, and shared `expect`/`actual` structure.
- Create modules: `:app-android`, `:app-ios`, `:shared-core`, `:shared-network`, `:shared-database`, `:players`, `:design`, `:testing`, `:tools`.
- Add libraries: `kotlinx.coroutines`, `kotlinx.serialization`, `Ktor` (client, logging, auth), `SQLDelight`, `Koin`, `Napier`, `Voyager` or similar navigation, optional `MediaKit` for experiments.
- Configure `spotless` with `ktlint`, Detekt, and shared compiler options.

## Checklist
- README explains goals plus Android and iOS build steps.
- `./gradlew :app-android:assembleDebug` and `./gradlew :shared-core:iosArm64Test` succeed on CI runners.
- Static analysis reports zero outstanding issues.

**Exit:** First tag: `v0.0.1`.

---

# 1. CI setup

## Tasks
- GitHub Actions matrix with Android and iOS simulators, run `./gradlew build` and unit tests, cache Gradle and Kotlin compiler outputs.
- Dependabot or Renovate for Gradle, Kotlin, GitHub Actions.
- Kotlin static analysis workflow (Detekt, ktlint).

## Checklist
- PRs trigger Gradle build, Android instrumentation smoke, and iOS simulator tests.
- Status badges for build and lint in README.
- Dependency bot PRs auto-label and request reviewers.

**Exit:** CI green on a sample PR.

---

# 2. App shell and theming

## Tasks
- Compose Multiplatform navigation scaffold with shared screen models.
- Light and dark palettes, typography, and icon pack from shared design tokens.
- Base screens: Home, Libraries, Detail, Player shell, Downloads, Requests, Settings, Onboarding.
- iOS host uses SwiftUI wrapper around Compose hierarchy.

## Checklist
- Navigation graph verified on Android emulator and iOS simulator.
- Theme toggle updates instantly across shared screens.
- Accessibility roles assigned for primary components.

**Exit:** Screenshot set in README from both platforms.

---

# 3. DI, config, and secure storage

## Tasks
- Configure Koin modules for shared services with platform bootstrap for Android and iOS.
- Define settings model persisted with `Settings` multiplatform library or custom expect/actual store.
- Secure token vault using Android EncryptedSharedPreferences and iOS Keychain through shared abstraction.
- Structured logging with Napier plus redaction filters.

## Checklist
- Secrets redacted in logs on both platforms.
- Unit tests cover secure storage abstraction and error handling.

**Exit:** Manual test stores and retrieves tokens through shared API.

---

# 4. API clients codegen

## Tasks
- Collect OpenAPI or Swagger for Jellyfin, Sonarr, Radarr, Jellyseerr.
- Create Kotlin script in `/tools` using `openapi-generator` or `ktorfit` to emit multiplatform DTOs and Ktor client interfaces.
- Configure Ktor client with CIO or OkHttp engines, retry policy with `ktor-client-plugins`, and auth interceptors per service.
- Provide Gradle task to refresh generated sources.

## Checklist
- `./gradlew :tools:generateApis` regenerates clients deterministically.
- Sample request per service passes against local or staging servers.

**Exit:** Generated sources committed with usage notes.

---

# 5. Onboarding and server management

## Tasks
- Add server flow: name, base URL, auth method selection.
- Jellyfin login sequence obtaining token via shared Ktor client.
- Sonarr, Radarr, Jellyseerr API key entry with validation.
- Connectivity test surfaced with Compose snackbar and SwiftUI sheet wrappers.
- Multi-server profiles stored in shared database.

## Checklist
- Add, edit, remove servers on both platforms.
- Connectivity tests show success or actionable error text.
- Validation prevents duplicate servers and invalid URLs.

**Exit:** At least one of each service saved and reusable after restart.

---

# 6. Jellyfin browse MVP

## Tasks
- Fetch libraries via generated client and cache in SQLDelight.
- Compose lazy lists with paging on Android; reuse shared presenter logic for iOS.
- Item detail screen shows metadata, media sources, track listings, resume state.
- Continue Watching rail on Home using cached progress.

## Checklist
- Library fetch stays under 1.5 s on local network baseline.
- Paging works with smooth scroll on Android and iOS.
- Detail screen renders metadata, artwork, and actions.

**Exit:** Demo covers browsing and opening an item detail on both platforms.

---

# 7. Playback engine v1

## Tasks
- Shared player facade with `expect`/`actual` bridging to ExoPlayer on Android and AVPlayer on iOS.
- Support direct play and HLS from Jellyfin.
- Controls: play, pause, seek, next, previous, audio and subtitle selection.
- Progress tracking pushes updates through Ktor client on interval.

## Checklist
- 1080p H264 direct play stable on both platforms.
- Subtitle rendering handles SRT and VTT.
- Progress resumes after closing and reopening the app.

**Exit:** Watch for two minutes, close, reopen, resume succeeds.

---

# 8. Offline downloads v1

## Tasks
- Quality selector with bitrate presets.
- Background transfer: WorkManager on Android, NSURLSession background tasks on iOS bridged through shared abstraction.
- Download queue UI with status chips, cancel, retry.
- File layout namespacing per user and server.
- Space monitoring and purge rules in shared service.

## Checklist
- Pause and resume supported on both platforms.
- Corrupt files detected using checksum or byte count validation.
- Offline playback succeeds with radios disabled.

**Exit:** Download two items and play both offline.

---

# 9. Library sync and caching

## Tasks
- SQLDelight schema for items, users, servers, progress, downloads.
- Fast sync: resume points and Continue Watching rails.
- Slow sync: delta pulls keyed by updated timestamp.
- Conflict handling: prefer local progress then patch upstream.

## Checklist
- Cold start reads from cache instantly.
- Sync resumes after app restart.
- Database migrations covered by unit tests.

**Exit:** Airplane mode shows cached data and sync badges.

---

# 10. Jellyseerr requests

## Tasks
- Search titles through Jellyseerr client with pagination.
- Request flow for movie and series using shared form logic.
- Status list: requested, approved, processing, available, including polling cadence.
- Permission gate for admin versus standard user roles.

## Checklist
- Create request and observe status transitions.
- Duplicate request surfaces friendly error.

**Exit:** End-to-end request visible on server timeline.

---

# 11. Sonarr management

## Tasks
- List series with filters and fast search.
- Series detail edits: monitored flag, quality profile, root path, tags.
- Add new series with season monitoring presets.
- Manual search and queue monitoring screen.

## Checklist
- Update quality profile and persist via API.
- Add a series from search dialog.
- View and refresh import queue state.

**Exit:** Change one series profile and verify in Sonarr UI.

---

# 12. Radarr management

## Tasks
- List movies with filters and quick search.
- Movie detail edits: monitored flag, quality profile, root path, tags.
- Add new movie from Jellyseerr or direct search.
- Manual search and queue monitoring screen.

## Checklist
- Update movie profile and persist via API.
- Add a movie from search dialog.
- View and refresh download queue state.

**Exit:** Change one movie profile and confirm in Radarr UI.

---

# 13. Security hardening

## Tasks
- Enforce HTTPS default with warning on HTTP.
- Optional certificate pinning toggle using Ktor features on both engines.
- Biometric lock using Android BiometricPrompt and iOS LocalAuthentication bridged to shared auth gate.
- Sensitive logs redacted before emission.

## Checklist
- HTTP add flow warns and requires confirm.
- Pinning blocks altered certificate in tests.
- Biometric lock guards entry on both targets.

**Exit:** Threat notes in `/docs/security.md`.

---

# 14. Error states and UX polish

## Tasks
- Empty states, skeleton loaders, retry affordances.
- Global snackbar or toast messaging wired through shared state.
- Accessibility labels and dynamic type scaling.
- Tablet and desktop Compose layout variants.

## Checklist
- Lists show placeholders before data loads.
- VoiceOver and TalkBack announce key controls.
- Landscape tablet layout reviewed.

**Exit:** UX review doc with screenshots.

---

# 15. Telemetry and diagnostics (opt-in)

## Tasks
- Hook Sentry, Firebase Crashlytics, or Kermit remote logging with user consent gate.
- In-app diagnostic bundle export with redaction of secrets.
- Toggle in Settings stored in shared preferences abstraction.

## Checklist
- Crash event captured in test build.
- Exported bundle omits sensitive data.

**Exit:** Telemetry off by default, user can opt in.

---

# 16. Localization and formatting

## Tasks
- Shared string resources with moko-resources or custom expect/actual loader.
- Date, time, number formatting through kotlinx-datetime and platform formatters.
- German and English packs to start.

## Checklist
- Language switch at runtime across both targets.
- No hardcoded strings in Compose or SwiftUI host layers.

**Exit:** Two languages ready for release.

---

# 17. Casting (optional)

## Tasks
- Google Cast on Android with shared command channel.
- AirPlay on iOS using AVRoutePicker and now playing info bridge.
- Session controls and route picker UI.

## Checklist
- Cast HLS stream to target device.
- Transport controls map to receiver commands.

**Exit:** One cast target validated end to end.

---

# 18. Advanced playback (optional)

## Tasks
- MediaKit or VLC-based fallback engine experiment.
- Picture-in-Picture on Android and iOS.
- Background audio service for music playback.

## Checklist
- PiP works on Android and iOS builds.
- Engine selection toggle switches between native and alternate player.

**Exit:** Playback parity maintained.

---

# 19. Release engineering

## Tasks
- App icons, splash, semantic versioning.
- Fastlane lanes for Android and iOS builds, Gradle tasks for bundles.
- Google Play internal testing track.
- TestFlight distribution via `fastlane pilot`.
- Privacy policy and data safety forms updated for Kotlin Multiplatform stack.

## Checklist
- Signed builds produced from CI workflows.
- Store listings drafted and linked to artifacts.
- Testers can install on both stores.

**Exit:** `v0.1` distributed to test tracks.

---

# 20. Testing

## Tasks
- Unit tests for repositories, auth handlers, mappers using Kotlin test frameworks.
- API wrappers tested with Ktor mock engine and MockServer.
- Snapshot tests for screen models via Compose testing APIs.
- Device tests for player and downloads on Android instrumentation and iOS UITests.

## Checklist
- Coverage target defined and met via Kover.
- Emulator and physical device runs documented.

**Exit:** CI gate enforces tests on PRs.

---

# 21. Docs

## Tasks
- `/docs/setup.md` for servers and keys.
- `/docs/apis.md` for endpoints used.
- `/docs/architecture.md` diagram showing Kotlin Multiplatform layers.
- `/docs/contrib.md` flow, code style, tooling commands.

## Checklist
- New contributor can build in under 15 minutes.
- Architecture diagram committed.

**Exit:** Docs linked from README.
