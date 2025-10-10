# 0. Repo bootstrap

## Tasks
- Create public repo: `jellystack-mobile`.
- Add APGL-3.0  license, README, CODE_OF_CONDUCT, CONTRIBUTING, issue templates.
- Init .NET MAUI project.
- Add solution folders: `App`, `Core`, `Apis`, `Players`, `Platform`, `Storage`, `Design`, `Tests`, `UiTests`, `tools`, `docs`.
- Add packages: `CommunityToolkit.Mvvm`, `CommunityToolkit.Maui`, `Refit`, `Polly`, `Refit.HttpClientFactory`, `SQLite-net-pcl` or `EFCore.Sqlite`, `FluentValidation`, `Mapperly` or `AutoMapper`, optional `LibVLCSharp`, `Shiny`.
- Add EditorConfig, analyzers, dotnet format.

## Checklist
- README explains goals, build, run.
- Build succeeds on Android and iOS.
- Lint passes with zero warnings.

**Exit:** First tag: `v0.0.1`.

---

# 1. CI setup

## Tasks
- GitHub Actions: build matrix for Android+iOS, run tests, cache NuGet.
- Dependabot for NuGet and Actions.
- CodeQL workflow.

## Checklist
- PRs run CI.
- Status badges in README.
- Dependabot PR auto-labels.

**Exit:** CI green on a sample PR.

---

# 2. App shell and theming

## Tasks
- MAUI Shell navigation.
- Light/Dark themes, typography, icons.
- Base pages: Home, Libraries, Detail, Player, Downloads, Requests, Settings, Onboarding.

## Checklist
- Shell routes registered.
- All pages render on device.
- Theme switch works.

**Exit:** Screenshot set in README.

---

# 3. DI, config, and secure storage

## Tasks
- Microsoft.Extensions.DependencyInjection setup.
- App settings model.
- SecureStorage wrapper for tokens and API keys.
- Logging with redaction.

## Checklist
- Secrets never logged.
- Unit tests for redaction.

**Exit:** Manual test shows keys stored and retrieved.

---

# 4. API clients codegen

## Tasks
- Collect OpenAPI/Swagger for Jellyfin, Sonarr, Radarr, Jellyseerr.
- NSwag or Kiota script in `/tools` to generate Refit interfaces and DTOs.
- Typed HttpClients with Polly timeouts and retry.
- Auth handlers per service.

## Checklist
- `dotnet tool restore && ./tools/codegen.ps1` regenerates clients.
- Sample call per service succeeds against a test server.

**Exit:** Clients versioned in repo with regen instructions.

---

# 5. Onboarding and server management

## Tasks
- Add server: name, base URL, auth method.
- Jellyfin login flow to get access token.
- Sonarr/Radarr/Jellyseerr API key entry.
- Connectivity test button and result.
- Multi-server support.

## Checklist
- Add, edit, remove servers.
- All tests show green connectivity or clear error.
- Inputs validated.

**Exit:** At least one of each service saved and usable.

---

# 6. Jellyfin browse MVP

## Tasks
- Fetch libraries.
- List views with paging and search.
- Item detail: title, art, streams, tracks, runtime, resume state.
- Continue Watching on Home.

## Checklist
- Libraries load under 1.5 s on local network.
- Infinite scroll or paging works.
- Detail page shows primary metadata.

**Exit:** Demo: browse and open an item detail.

---

# 7. Playback engine v1

## Tasks
- Player abstraction.
- Platform default players: ExoPlayer (Android) and AVPlayer (iOS).
- Play direct stream and HLS from Jellyfin.
- Controls: play/pause, seek, next/prev, audio/subtitle picker.
- Track progress and send updates to server.

## Checklist
- 1080p H264 plays direct.
- Subtitles display (SRT/VTT).
- Progress sync works on stop and on interval.

**Exit:** Watch a file for 2 minutes, relaunch, resume works.

---

# 8. Offline downloads v1

## Tasks
- Choose quality or original.
- Background transfer: WorkManager (Android), NSURLSession background (iOS).
- Download queue UI: states, cancel, retry.
- File storage layout per user.
- Space checks and purge rules.

## Checklist
- Pause/resume works.
- Corrupt file detection with checksum or size check.
- Offline playback with network off.

**Exit:** Download two items, play both offline.

---

# 9. Library sync and caching

## Tasks
- SQLite schema for items, users, servers, progress, downloads.
- Fast sync: resume points and continue watching.
- Slow sync: library deltas by updated timestamp.
- Conflict policy: local progress wins then PATCH.

## Checklist
- Cold start shows cached lists.
- Sync jobs resumable on app restart.
- DB migrations tested.

**Exit:** Airplane mode shows cached data with badges.

---

# 10. Jellyseerr requests

## Tasks
- Search titles from Jellyseerr.
- Request flow for movie and series.
- Status list: requested, approved, processing, available.
- Permissions check for admin vs user.

## Checklist
- Create request and see status change over time.
- Errors surfaced when title already requested.

**Exit:** End-to-end request recorded on server.

---

# 11. Sonarr management

## Tasks
- List series with filters.
- Series detail: monitored, quality profile, root path, tags.
- Add new series and season options.
- Manual search and queue view.

## Checklist
- Edit quality profile and save.
- Add a series from search.
- View import queue.

**Exit:** Change one series to a new profile and confirm in Sonarr UI.

---

# 12. Radarr management

## Tasks
- List movies with filters.
- Movie detail: monitored, profile, path, tags.
- Add new movie.
- Manual search and queue view.

## Checklist
- Edit movie profile and save.
- Add a movie from search.
- View download queue.

**Exit:** Change one movie to a new profile and confirm in Radarr UI.

---

# 13. Security hardening

## Tasks
- Enforce HTTPS by default. Warn on HTTP.
- Optional certificate pinning toggle.
- Biometric lock for the app.
- Sensitive logs redacted.

## Checklist
- HTTP add flow shows warning with require-confirm.
- Pinning blocks altered cert in test.
- Biometric lock gates entry.

**Exit:** Threat notes in `/docs/security.md`.

---

# 14. Error states and UX polish

## Tasks
- Empty states, skeleton loaders, retry actions.
- Global toast/snackbar for common errors.
- Accessibility labels and larger text support.
- Tablet layouts.

## Checklist
- All list screens have placeholder states.
- VoiceOver/TalkBack reads controls.
- Landscape tablet layout verified.

**Exit:** UX review doc with screenshots.

---

# 15. Telemetry and diagnostics (opt-in)

## Tasks
- Sentry or App Center with user consent.
- In-app diagnostic bundle export with redaction.
- Toggle in Settings.

## Checklist
- Crash captured in test.
- Export produces a zip without secrets.

**Exit:** Telemetry disabled by default, works when enabled.

---

# 16. Localization and formatting

## Tasks
- Resource files for strings.
- Date, time, and number formatting.
- German and English packs to start.

## Checklist
- Language switch at runtime.
- No hardcoded strings in UI.

**Exit:** Two languages shippable.

---

# 17. Casting (optional)

## Tasks
- Google Cast on Android.
- AirPlay on iOS.
- Session controls and route picker.

## Checklist
- Cast HLS to a target device.
- Transport controls map to receiver.

**Exit:** One test device confirmed.

---

# 18. Advanced playback (optional)

## Tasks
- LibVLCSharp integration path.
- Picture-in-Picture on both platforms.
- Background audio for music.

## Checklist
- PiP works on Android and iOS.
- Switch engine flag between native and VLC.

**Exit:** Playback parity maintained.

---

# 19. Release engineering

## Tasks
- App icons, splash, versioning.
- Fastlane or MAUI publish tasks.
- Google Play Internal Testing track.
- TestFlight build.
- Privacy policy and data safety forms.

## Checklist
- Signed builds produced from CI.
- Store listings drafted.
- Testers can install.

**Exit:** `v0.1` to test tracks.

---

# 20. Testing

## Tasks
- Unit tests for repositories, auth handlers, and mappers.
- API wrappers tested with WireMock.Net.
- Snapshot tests for viewmodels.
- Device tests for player and downloads.

## Checklist
- Coverage target set and met.
- Emulator and physical device runs logged.

**Exit:** CI gate enforces tests on PRs.

---

# 21. Docs

## Tasks
- `/docs/setup.md` for servers and keys.
- `/docs/apis.md` for endpoints used.
- `/docs/architecture.md` diagram.
- `/docs/contrib.md` flow and code style.

## Checklist
- New dev can build in under 15 minutes.
- Architecture diagram in repo.

**Exit:** Docs linked from README.
