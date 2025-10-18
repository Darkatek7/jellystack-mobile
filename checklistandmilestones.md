# Feature Checklists

All items assume the Kotlin Multiplatform stack with shared Compose UI and native service bridges.

## Jellyfin Browse and Play
- [ ] View libraries and filter  
- [ ] Search by name  
- [ ] Item detail with versions and tracks  
- [ ] Direct play and HLS play  
- [ ] Subtitles on/off and track select  
- [ ] Resume points sync  

## Downloads
- [ ] Add to queue with quality choice  
- [ ] Background transfer and retry  
- [ ] Storage quota and purge rules  
- [ ] Offline list and playback  
- [ ] Delete and reclaim space  

## Requests via Jellyseerr
- [ ] Search titles  
- [ ] Create request with options  
- [ ] View status and history  
- [ ] Cancel own request if pending  
- [ ] Admin approve/deny if permitted  

## Sonarr
- [ ] List and filter series  
- [ ] Add new series
- [ ] Delete series
- [ ] Remove Episode files
- [ ] Edit monitored, profile, path, tags  
- [ ] Manual search and queue  
- [ ] Refresh and rescan triggers  

## Radarr
- [ ] List and filter movies  
- [ ] Add new movie
- [ ] Remove Movie files
- [ ] Delete movies  
- [ ] Edit monitored, profile, path, tags  
- [ ] Manual search and queue  
- [ ] Refresh and rename triggers  

## Settings
- [ ] Manage servers and credentials  
- [ ] Playback defaults and network limits  
- [ ] Theme, language, telemetry, security  
- [ ] Export diagnostics  

## Work Package Checklists

### 0. Repo bootstrap
- [x] README explains goals plus Android and iOS build steps.
- [x] Static analysis reports zero outstanding issues.

### 1. CI setup
- [x] PRs trigger Gradle build, Android instrumentation smoke, and iOS simulator tests.
- [x] Status badges for build and lint in README.
- [x] Dependency bot PRs auto-label and request reviewers.

### 2. App shell and theming
- [x] Navigation graph verified on Android.
- [x] Theme toggle updates instantly across shared screens.
- [x] Accessibility roles assigned for primary components.

### 3. DI, config, and secure storage
- [x] Secrets redacted in logs on both platforms.
- [x] Unit tests cover secure storage abstraction and error handling.

### 4. API clients codegen
- [x] `./gradlew :tools:generateApis` regenerates clients deterministically.
- [x] Sample request per service passes against local or staging servers.

### 5. Onboarding and server management
- [x] Add, edit, remove servers on both platforms.
- [x] Connectivity tests show success or actionable error text.
- [x] Validation prevents duplicate servers and invalid URLs.

### 6. Jellyfin browse MVP
> data layer for libraries/items now persisted via SQLDelight with Compose browse UI and detail layouts in place; needs wiring to live server selection before marking complete.
- [x] Library fetch stays under 1.5 s on local network baseline.
- [x] Paging works with smooth scroll on Android and iOS.
- [x] Detail screen renders metadata, artwork, and actions.

### 7. Playback engine v1
- [x] direct play stable on Android.
- [x] Subtitle rendering handles SRT and VTT on Android.
- [x] Progress resumes after closing and reopening the Android app.
- [ ] Port the Android playback to iOS.

### 8. Offline downloads v1
- [x] Pause and resume supported on Android.
- [x] Corrupt files detected using checksum or byte count validation on Android.
- [x] Offline playback succeeds with radios disabled on Android.
- [ ] Port the Android offline download stack to iOS.

### 9. Library sync, download improvements and caching
- [ ] Cold start reads from cache instantly.
- [ ] Sync resumes after app restart.
- [ ] Database migrations covered by unit tests.
- [ ] Download TV Show function
- [ ] Download TV Show Season function 
- [ ] Auto Download Subitles when downloading any video
- [ ] Sync watched status and playback time for videos watched offline to jellyfin
- [ ] Phone Back button functionality

### 10. Jellyseerr requests
- [ ] Create request and observe status transitions.
- [ ] Duplicate request surfaces friendly error.

### 11. Sonarr management
- [ ] Update quality profile and persist via API.
- [ ] Add a series from search dialog.
- [ ] View and refresh import queue state.

### 12. Radarr management
- [ ] Update movie profile and persist via API.
- [ ] Add a movie from search dialog.
- [ ] View and refresh download queue state.

### 13. Security hardening
- [ ] HTTP add flow warns and requires confirm.
- [ ] Pinning blocks altered certificate in tests.
- [ ] Biometric lock guards entry on both targets.

### 14. Error states and UX polish
- [ ] Lists show placeholders before data loads.
- [ ] VoiceOver and TalkBack announce key controls.
- [ ] Landscape tablet layout reviewed.

### 15. Telemetry and diagnostics (opt-in)
- [ ] Crash event captured in test build.
- [ ] Exported bundle omits sensitive data.

### 16. Localization and formatting
- [ ] Language switch at runtime across both targets.
- [ ] No hardcoded strings in Compose or SwiftUI host layers.

### 17. Casting (optional)
- [ ] Cast HLS stream to target device.
- [ ] Transport controls map to receiver commands.

### 18. Advanced playback (optional)
- [ ] PiP works on Android and iOS builds.
- [ ] Engine selection toggle switches between native and alternate player.

### 19. Release engineering
- [ ] Signed builds produced from CI workflows.
- [ ] Store listings drafted and linked to artifacts.
- [ ] Testers can install on both stores.

### 20. Testing
- [ ] Coverage target defined and met via Kover.
- [ ] Emulator and physical device runs documented.

### 21. Docs
- [ ] New contributor can build in under 15 minutes.
- [ ] Architecture diagram committed.

---

# Labels and Milestones

## Labels
- `area:jellyfin`
- `area:downloads`
- `area:requests`
- `area:sonarr`
- `area:radarr`
- `area:player`
- `type:bug`
- `type:feature`
- `type:dependencies`
- `good first issue`

## Milestones
| Version | Scope | Includes |
|----------|--------|-----------|
| **v0.1 Foundations** | Base setup | Packages 0–6 |
| **v0.2 Playback + Downloads** | Player, offline | Packages 7–9 |
| **v0.3 Requests** | Jellyseerr | Package 10 |
| **v0.4 Sonarr/Radarr** | Management | Packages 11–12 |
| **v0.x Casting Experiments** | Optional playback enhancements | Packages 17–18 (optional) |
| **v1.0 Polish + Release** | Finalization | Packages 13–16, 19–21 |
