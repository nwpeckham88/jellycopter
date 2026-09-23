# Original User Request

## Initial Request — 2026-09-16T04:32:35Z

Build Jellycopter, a native Android TV and Android client for Jellyfin and Jellyseerr featuring a first-class Smart Shuffle engine (exhaustion decks, anti-clustering, multi-part grouping, single and multi-show mix channels), optimized playback (Dolby Vision Profile 7 to 8.1 auto-conversion, Audio Night Mode DRC and force stereo), rich Jellyseerr discovery and 1-click requests, and a dynamic Jetpack Compose UI.

Working directory: /home/nathan/Development/jellycopter
Integrity mode: development

## Reference Implementations & Specifications
- Shuffle algorithm reference: /home/nathan/Pane/pane_backup/Documents/antigravity/proud-hertz/main.go
- Shuffle entropy & distribution test: /home/nathan/Pane/pane_backup/Documents/antigravity/proud-hertz/test_shuffle_entropy.py
- Architectural plan: /home/nathan/.gemini/antigravity/brain/6b6d21c5-a40f-421b-9e76-11d6f27c06b1/implementation_plan.md

## Requirements

### R1. Smart Shuffle Engine & First-Class Deck Management
- Port and enhance the exhaustion deck algorithm from the reference Go implementation into native Kotlin.
- Implement multi-part episode regex grouping so two-part and multi-part episodes always remain contiguous.
- Implement season anti-clustering round-robin to ensure balanced distribution without adjacent season repeats.
- Support both single-show decks and multi-show mix channels (e.g., "Saturday Morning Cartoons", "Animation Block").
- Separate sequential watch history from shuffle deck state so shuffling a show never clobbers sequential "Next Up" progress.
- Maintain continuous autoplay background TV progression, saving midway resume per episode and recycling skipped episodes into the unexhausted pool.

### R2. Playback Subsystem & Audio/Video Quality of Life
- Implement Media3 (ExoPlayer) playback engine with seamless fallback to `libmpv`.
- Handle Dolby Vision Profile 7 (dual-layer MEL/FEL) MKVs by rewriting/extracting RPU metadata to Profile 8.1 on-the-fly or cleanly falling back to HDR10 base layer, eliminating purple/green tint on Shield TV and Onn 4K Pro.
- Implement Audio Night Mode with Dynamic Range Compression (dialogue floor boost + loud transient clamping) and speech clarity vocal formant EQ.
- Implement ITU-R BS.775 Force Stereo downmixing with configurable center-dialogue boost.
- Provide smart passthrough handoff: full bitstream passthrough during normal playback; transparently hand off to PCM DSP when Night Mode or Force Stereo is active.
- Include a time-based auto-night mode scheduler and subtitle luminance dimmer.

### R3. Jellyseerr Discovery & 1-Click Request Integration
- Dedicated Discover tab featuring Trending, Popular, Upcoming, and Studio/Network carousels via Jellyseerr REST API.
- Instant 1-Click Request flow using smart defaults with on-screen animated confirmation, plus a long-press menu for season selection and quality profile overrides.
- In-library missing season/episode badges with direct request triggers.

### R4. Adaptive Android TV & Mobile UI
- 10-foot D-pad focus engine using Jetpack Compose for TV (focus scaling, smooth carousel snapping, focus restoration) with responsive touch layouts for phone/tablet.
- Dynamic Adaptive Palette: extract dominant colors from focused posters in real-time to dynamically tint accents and ambient glow over master base themes (OLED Pure Black, Jellyfin Purple, Cyberpunk).
- Top-level navigation: Home (Continue Watching + Active Shuffles shelf), Shuffle Channels tab, Discover tab, and Library tab.

### R5. Telemetry, Post-Playback Achievements & Companion Plugin
- Telemetry evaluation engine for quirky milestones ("Trip to the Moon", "Binge Voyager", "Shuffle Centurion").
- Non-intrusive presentation: display milestone summaries on post-playback end screens and inside a dedicated Trophy Room tab.
- Companion Jellyfin Server Plugin (`Jellyfin.Plugin.Jellycopter` in C# .NET 8/9) providing `/Jellycopter/Decks` and `/Jellycopter/Achievements` for cross-device sync, with graceful local Room DB fallback if the plugin is absent.

## Acceptance Criteria

### Smart Shuffle Verification
- [ ] Programmatic entropy & distribution test suite verifies that shuffled decks achieve Shannon entropy comparable to the reference implementation without season clustering.
- [ ] Multi-part episode test confirms episodes titled "Part 1" and "Part 2" are sequenced adjacently 100% of the time.
- [ ] Shuffle playback state verification confirms sequential "Next Up" pointers in Jellyfin are not altered by shuffle deck playback.

### Playback & Audio/Video Verification
- [ ] Media3 track selection verifies DV Profile 7 streams are mapped to Profile 8.1 or HDR10 base layer on devices lacking Profile 7 hardware decoding.
- [ ] Audio DSP unit tests verify ITU-R BS.775 downmix coefficients and DRC compression ratios on simulated multi-channel audio buffers.

### Discover & Request Verification
- [ ] Mocked Jellyseerr API integration tests verify payload construction for 1-click requests and missing season detection.

### Build & Compilation
- [ ] Gradle build succeeds for the Android application (`./gradlew assembleDebug` or test passes).
- [ ] .NET build succeeds for the companion plugin (`dotnet build`).

## Follow-up — 2026-09-16T22:23:44Z

Build the core Jellyfin client functionality for Jellycopter, an Android TV and mobile media player featuring standalone TV show smart shuffle, custom multi-show broadcast channels with independent exhaustion decks, and full Jellyfin media library browsing and playback.

Working directory: /home/nathan/Development/jellycopter
Integrity mode: development

## Requirements

### R1. Design System & Icon Overhaul
Replace all raw emoji strings in navigation and screens with standard `Icons.Rounded` Material Icons at a consistent 24dp viewport. Support proper 3-state focus tinting (idle muted gray, focused white, selected accent) across TV leanback and mobile scaffolds to ensure typographic baseline stability and theme harmony.

### R2. Jellyfin SDK Integration, Authentication & Settings
Integrate the official `jellyfin-sdk-kotlin` and Koin dependency injection. Implement single-server connection probing (`/System/Info`), credential authentication, encrypted local session storage, and an authentication gate on app launch. Provide a comprehensive Settings screen supporting theme selection, audio DSP defaults, silent companion plugin sync detection, and optional Jellyseerr URL configuration (hiding the Discover tab when unconfigured).

### R3. Media Library Browsing & Series Details
Implement real library browsing querying Jellyfin virtual folders, series, seasons, and episodes with Coil image loading for posters and backdrops. Provide a rich Series Detail screen featuring normal playback from resume/next-up points, a "Restart Show" action (S1E1), a standalone "Shuffle" launcher, and an "Add to Channel" action.

### R4. Fullscreen Playback & Coordination Subsystem
Implement a dedicated fullscreen `PlaybackActivity` with D-pad accessible player controls (scrubbing, audio track selection, subtitle selection, and audio night mode toggles). Implement a `PlaybackCoordinator` that routes normal playback to report scrobbling and playback progress to the Jellyfin server, while strictly suppressing server-side scrobbling for shuffle and channel sessions.

### R5. Smart Show Shuffle & Multi-Show Custom Channels
Support 1:1 standalone show exhaustion shuffle decks and custom multi-show virtual Channels. Channel playback must maintain independent sub-deck progress for each participating show (isolated from standalone show shuffle state) and support Round-Robin, Random, and Weighted interleaving. When shows are added or removed from an active channel, future queue items must safely re-interleave while preserving existing sub-deck exhaustion progress.

## Acceptance Criteria

### Build & Toolchain
- [ ] `./gradlew assembleDebug` compiles cleanly with zero unresolved dependencies (Jellyfin SDK, Koin, Material Icons Extended).
- [ ] `./gradlew test` passes all existing and newly added unit tests without regressions.

### Authentication & Server Connection
- [ ] App launches into `LoginScreen` when unauthenticated; entering valid Jellyfin credentials navigates to the main navigation scaffold.
- [ ] Saved authentication tokens persist across app restarts via secure local session storage.

### UI & Navigation Standards
- [ ] Zero emoji strings remain in navigation or screen components; all icons render via vector `Icon` composables with verified 24dp bounding boxes and correct focus tint states.
- [ ] Settings screen correctly displays server details, theme options, and companion plugin probe status.
- [ ] Discover tab is hidden if Jellyseerr is not configured.

### Playback & Scrobble Decoupling
- [ ] Normal episode playback reports playback progress and completion to the Jellyfin server.
- [ ] Standalone show shuffle and channel playback suppress Jellyfin scrobble calls (`/Sessions/Playing/*`) while maintaining local Room DB progress.

### Channel & Shuffle Isolation
- [ ] Shuffling a show standalone and playing the same show within a Channel maintain completely independent exhaustion decks in Room DB.
- [ ] Adding a show to an active channel interleaves the new show into upcoming playback without resetting cycle progression of existing shows.
- [ ] Removing a show removes upcoming cards for that show and prunes orphaned channel-scoped deck state.

