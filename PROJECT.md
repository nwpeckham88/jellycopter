# Project: Jellycopter

## Architecture
Jellycopter is a native Android TV and Android mobile client for Jellyfin and Jellyseerr, paired with a C# .NET companion server plugin.

- **Presentation Layer**: Jetpack Compose for TV (`androidx.tv.material3`) and Android Mobile. 10-foot D-pad focus engine, focus scaling (`1.08f`), glowing borders, focus restoration, responsive layouts (Leanback / Mobile / Tablet), dynamic adaptive palette with Coil downsampled poster extraction, and OLED Pure Black, Jellyfin Purple, and Cyberpunk base themes.
- **Shuffle Engine**: Native Kotlin exhaustion deck state machine, tightness-aware anti-clustering round-robin, atomic multi-part episode grouping with 4-point invariant checks, multi-show mix channels, continuous background autoplay with midway resume and skip recycling, completely decoupled from Jellyfin's sequential watch history.
- **Playback & DSP Subsystem**: Media3 (ExoPlayer) primary player with automatic fallback to `libmpv`. Dynamic Dolby Vision Profile 7 to 8.1 metadata rewriting and HDR10 base layer fallback. Real-time audio DSP pipeline: ITU-R BS.775 Force Stereo downmixer with center dialogue boost and dynamic normalization, dual-envelope Audio Night Mode DRC (+6dB floor, -12dB clamp), 2.5kHz vocal formant peaking EQ (+3.5dB), smart bitstream-to-PCM passthrough handoff, and subtitle dimmer.
- **Discovery & Request Layer**: Retrofit/OkHttp client interfacing with Jellyseerr REST API (v2.7.3). Carousels for Trending, Popular, Upcoming, and Studio/Network. 1-Click Request flow with smart defaults, long-press configuration modal, and missing season in-library diff detection.
- **Telemetry & Achievements**: Asynchronous milestone evaluation engine tracking viewing habits and quirky milestones ('Trip to the Moon', 'Binge Voyager', 'Shuffle Centurion'). Zero mid-playback interruptions; summaries appear on post-playback end screens and in the Trophy Room.
- **Data & Synchronization Layer**: Local Android Room DB (`jellycopter.db`) as authoritative state store for standalone operation. Companion C# .NET 8/9 Jellyfin server plugin (`Jellyfin.Plugin.Jellycopter`) providing REST endpoints (`/Jellycopter/Decks`, `/Jellycopter/Achievements`) with set-union cross-device sync and graceful local Room DB fallback.

---

## Feature Inventory

Every feature from the Survey phase is enumerated below with its assigned milestone.

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Host Toolchain & Scaffolding | Bootstrap OpenJDK 21, Android SDK (cmdline-tools, platform 34, build-tools), Gradle wrapper, .NET SDK, and multi-project structure | M1 | Survey Explorer |
| 2 | Core Domain Models & Room DB | Unified Kotlin data models, Room database schema, DAOs, and repository contracts for decks, history, channels, and achievements | M1 | Survey Explorer & Spec Miners |
| 3 | Exhaustion Deck Engine | Stateful exhaustion deck algorithm with cycle reset at threshold, boundary repeat protection, and dynamic shuffling | M2 | Survey Spec Miner (Shuffle) |
| 4 | Multi-Part Episode Grouping | 4-point invariant regex grouping (same season, sequential index, matching base title, sequential part) | M2 | Survey Spec Miner (Shuffle) |
| 5 | Season Anti-Clustering | Tightness-aware weighted round-robin bucket selection guaranteeing 0 adjacent season collisions | M2 | Survey Spec Miner (Shuffle) |
| 6 | Multi-Show Mix Channels | Interleaved multi-series virtual channels with balanced show rotation and independent sub-deck recycling | M2 | Survey Spec Miner (Shuffle) |
| 7 | Decoupled Watch History | Isolated Room DB playback state preventing shuffle scrobbling from clobbering Jellyfin "Next Up" | M2 | Survey Spec Miner (Shuffle) |
| 8 | Autoplay Progression & Recycling | Continuous autoplay with midway resume (<80%) and unexhausted skip recycling into pool | M2 | Survey Spec Miner (Shuffle) |
| 9 | Dual Playback Engine | Media3 (ExoPlayer) playback engine with seamless automatic fallback to `libmpv` preserving position | M3 | Survey Spec Miner (Playback) |
| 10 | Dolby Vision Profile 7 Rewriter | On-the-fly Profile 7 to Profile 8.1 metadata rewriting / clean HDR10 base layer fallback (fixing green/purple tint) | M3 | Survey Spec Miner (Playback) |
| 11 | Audio Night Mode DRC | Dual-envelope DRC with +6dB dialogue floor boost and -12dB loud transient clamping | M3 | Survey Spec Miner (Playback) |
| 12 | Speech Formant Peaking EQ | 2nd-order Biquad peaking filter at 2.5kHz (+3.5dB, Q=1.0) for dialogue intelligibility | M3 | Survey Spec Miner (Playback) |
| 13 | ITU-R BS.775 Force Stereo | 5.1/7.1 downmixing with parameterized center dialogue boost and dynamic zero-clipping normalization | M3 | Survey Spec Miner (Playback) |
| 14 | Smart Passthrough Handoff | Transparent state transition between HDMI bitstream passthrough and decoded multi-channel PCM DSP | M3 | Survey Spec Miner (Playback) |
| 15 | Night Scheduler & Subtitle Dimmer | Time-based auto-night mode scheduler and 50% luminance subtitle dimmer | M3 | Survey Spec Miner (Playback) |
| 16 | Jellyseerr Discovery Carousels | Trending, Popular, Upcoming, and Studio/Network carousels via Jellyseerr REST API | M4 | Survey Spec Miner (Discovery) |
| 17 | 1-Click Request & Long Press | Instant 1-Click request with smart defaults and animated confirmation, plus long-press season/quality modal | M4 | Survey Spec Miner (Discovery) |
| 18 | In-Library Missing Season Badges | Jellyfin library diff against TMDB/Jellyseerr with direct request trigger badges | M4 | Survey Spec Miner (Discovery) |
| 19 | 10-Foot D-Pad Focus Engine | Jetpack Compose for TV D-pad navigation, focus scaling (1.08f), glow borders, carousel snapping, focus restoration | M5 | Survey Explorer (UI) |
| 20 | Dynamic Adaptive Palette | Downsampled poster palette extraction with smooth animated theme blending over OLED/Purple/Cyberpunk | M5 | Survey Explorer (UI) |
| 21 | Navigation Architecture | Top-level Home (Continue Watching + Active Shuffles shelf), Shuffle Channels, Discover, and Library | M5 | Survey Explorer (UI) |
| 22 | Telemetry & Achievements | Milestone evaluator ('Trip to the Moon', 'Binge Voyager', etc.), post-playback screen, Trophy Room | M5 | Survey Explorer (Achievements) |
| 23 | Companion Jellyfin Plugin | C# .NET 8/9 plugin (`Jellyfin.Plugin.Jellycopter`) with `/Jellycopter/Decks` and `/Jellycopter/Achievements` | M5 | Survey Explorer (Plugin) |
| 24 | Graceful Local Fallback | Handshake probe enabling server sync if plugin present, defaulting to local Room DB if absent | M5 | Survey Explorer (Plugin) |
| 25 | E2E Test Suite (Tiers 1-4) | Opaque-box requirement-driven test suite covering entropy, multi-part, isolation, DV, DSP, Jellyseerr, builds | E2E Track | Survey Spec Miners & Explorer |
| 26 | Adversarial Hardening (Tier 5) | White-box coverage analysis, edge case generation, and stress verification | Final Milestone | Survey Spec Miners & Explorer |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Track | Independent requirement-driven test infrastructure & test cases (Tiers 1-4); produces `TEST_READY.md` | none | IN_PROGRESS |
| M1 | Toolchain, Scaffolding & Shared Contracts | Install JDK 21, Android SDK, Gradle, .NET SDK; create project structure, Room entities, and interfaces | none | IN_PROGRESS |
| M2 | Smart Shuffle Engine & Deck Management | Exhaustion decks, 4-point multi-part grouping, tightness-aware anti-clustering, mix channels, decoupled history, autoplay & recycling | M1 | PLANNED |
| M3 | Playback Subsystem & Audio/Video QoL | Media3 + libmpv fallback, Dolby Vision Profile 7 to 8.1 / HDR10 fallback, Audio Night Mode DRC, Formant EQ, ITU-R BS.775, passthrough handoff | M1 | PLANNED |
| M4 | Jellyseerr Discovery & 1-Click Requests | Retrofit Jellyseerr client, Discovery carousels, 1-Click requests with smart defaults, long-press menu, missing season diff & badges | M1 | PLANNED |
| M5 | Adaptive UI, Telemetry & Companion Plugin | Compose for TV 10-ft focus engine, adaptive palette, navigation, achievement evaluator, Trophy Room, C# .NET plugin & Room fallback | M1, M2, M3, M4 | PLANNED |
| Final | 100% E2E Pass & Adversarial Hardening | Phase 1: Pass 100% of E2E test suite (Tiers 1-4). Phase 2: Tier 5 adversarial stress testing | E2E, M5 | PLANNED |

---

## Interface Contracts

### M1/Shared ↔ M2 (Shuffle Engine)
- `SmartShuffleEngine`:
  - `fun generateDeck(seriesId: String, episodes: List<EpisodeItem>, config: DeckConfig): ShuffledDeck`
  - `fun generateMixChannelDeck(channelId: String, shows: List<ShowConfig>, episodeProvider: (String) -> List<EpisodeItem>): ShuffledDeck`
  - `fun advancePlayback(deckId: String, currentCardId: String, positionMs: Long, durationMs: Long): DeckProgressionEvent`
  - `fun recycleSkipped(deckId: String, cardId: String)`
- `MediaCard`: `id: String`, `seriesId: String`, `seasonId: String`, `categoryKey: String`, `items: List<EpisodeItem>`, `isMultiPart: Boolean`
- `DeckState`: `deckId: String`, `cards: List<MediaCard>`, `currentIndex: Int`, `exhaustedCardIds: Set<String>`, `cooldownHistory: List<String>`

### M1/Shared ↔ M3 (Playback & DSP)
- `PlaybackEngine`:
  - `fun play(item: MediaCard, startPositionMs: Long)`
  - `fun setAudioNightMode(enabled: Boolean, config: DrcConfig)`
  - `fun setForceStereo(enabled: Boolean, centerBoostDb: Float)`
  - `fun setSubtitleDimmer(enabled: Boolean, dimRatio: Float)`
  - `val playerState: StateFlow<PlayerState>`
- `TrackSelectionOverrides`:
  - `fun resolveDolbyVisionTrack(format: Format, deviceCapabilities: DeviceCapabilities): TrackOverride`

### M1/Shared ↔ M4 (Jellyseerr)
- `JellyseerrClient`:
  - `suspend fun getTrending(page: Int): List<MediaDiscoverItem>`
  - `suspend fun getPopular(page: Int): List<MediaDiscoverItem>`
  - `suspend fun getUpcoming(mediaType: String, page: Int): List<MediaDiscoverItem>`
  - `suspend fun requestMedia(request: MediaRequestPayload): RequestResult`
  - `suspend fun checkMissingSeasons(seriesId: String, tmdbId: Int): List<SeasonStatus>`

### M1/M2/M3/M4 ↔ M5 (UI, Achievements, Plugin)
- `AchievementEngine`:
  - `fun onPlaybackSessionEnded(session: PlaybackSessionStats): List<UnlockedAchievement>`
  - `val unlockedAchievements: StateFlow<List<AchievementEntity>>`
- `PluginSyncService`:
  - `suspend fun syncDecks(localDecks: List<DeckEntity>): SyncResult`
  - `suspend fun syncAchievements(localAchievements: List<AchievementEntity>): SyncResult`

---

## Code Layout

```
jellycopter/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/org/jellycopter/
│       │       ├── core/
│       │       │   ├── model/
│       │       │   ├── database/
│       │       │   └── repository/
│       │       ├── shuffle/
│       │       ├── playback/
│       │       │   ├── dsp/
│       │       │   └── dv/
│       │       ├── discovery/
│       │       ├── ui/
│       │       │   ├── theme/
│       │       │   ├── tv/
│       │       │   └── mobile/
│       │       └── telemetry/
│       └── test/
│           └── java/org/jellycopter/
│               ├── shuffle/
│               ├── playback/
│               └── discovery/
├── plugin/
│   ├── Jellyfin.Plugin.Jellycopter.sln
│   └── Jellyfin.Plugin.Jellycopter/
│       ├── Jellyfin.Plugin.Jellycopter.csproj
│       ├── Plugin.cs
│       ├── Api/
│       │   ├── DecksController.cs
│       │   └── AchievementsController.cs
│       └── Configuration/
└── test/
    └── e2e/
        ├── run_e2e_tests.sh
        ├── entropy/
        ├── playback/
        ├── discovery/
        └── build/
```
