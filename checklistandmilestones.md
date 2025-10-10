# Feature Checklists

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
- [ ] Edit monitored, profile, path, tags  
- [ ] Manual search and queue  
- [ ] Refresh and rescan triggers  

## Radarr
- [ ] List and filter movies  
- [ ] Add new movie  
- [ ] Edit monitored, profile, path, tags  
- [ ] Manual search and queue  
- [ ] Refresh and rename triggers  

## Settings
- [ ] Manage servers and credentials  
- [ ] Playback defaults and network limits  
- [ ] Theme, language, telemetry, security  
- [ ] Export diagnostics  

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
- `good first issue`

## Milestones
| Version | Scope | Includes |
|----------|--------|-----------|
| **v0.1 Foundations** | Base setup | Packages 0–6 |
| **v0.2 Playback + Downloads** | Player, offline | Packages 7–9 |
| **v0.3 Requests** | Jellyseerr | Package 10 |
| **v0.4 Sonarr/Radarr** | Management | Packages 11–12 |
| **v1.0 Polish + Release** | Finalization | Packages 13–16, 19–21 |
