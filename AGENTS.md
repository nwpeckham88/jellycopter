# Jellycopter Development Guidelines & Guardrails

## Jellyfin API Integration (12.x+)
- **Dual Authorization Headers**: Jellyfin 12.1.0+ requires client identification in the HTTP `Authorization` header (`MediaBrowser Client="...", Device="...", DeviceId="...", Version="..."`) in addition to legacy `X-Emby-Authorization`. All OkHttp/networking interceptors and image loaders must ensure both headers or appropriate authentication tokens are present on all outgoing API calls.
- **Cleartext HTTP Support**: Because local Jellyfin instances may run over plain HTTP on LAN ports (e.g., `:8096`, `:8098`), ensure `android:usesCleartextTraffic="true"` remains enabled in `AndroidManifest.xml`.

## Jetpack Compose for Android TV
- **Coroutine Scope Survival**: Avoid unmounting composables while their local `rememberCoroutineScope()` has pending asynchronous network operations. Do not promote intermediate transient loading states to root navigation switches if it causes the calling screen to be destroyed mid-call.
- **10-Foot UI Sizing**: Design TV dialogs and screens to fit comfortably within a 1920x1080 viewport without scrolling where possible. Form elements must always have visible focus indicators (`FocusCard`, custom border glow) and handle `ImeAction.Next`/`ImeAction.Done`.
- **D-Pad & Remote Navigation**: Every actionable control must be reachable via remote D-pad without requiring touch or mouse emulation. Ensure `focusable()` and focus styling are applied.

## Workflow & Scope
- When instructed to work on a specific component (e.g., Android app vs Jellyfin plugin), restrict all changes and tests to that component until explicitly instructed to proceed to others.
