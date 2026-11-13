# Features and acceptance status

“Implemented in source” describes code present in this repository. See the GitHub Actions run for build and emulator results.

| Area | Implemented in source | Remaining |
|---|---|---|
| Explore and share | Link entry, explicit clipboard read, Android share target for text, image, video and single audio files | Trending feeds, categories and discovery recommendations |
| Download | Direct HTTPS media URL with a supported filename extension; original file, Android DownloadManager and completion notifications | Provider-specific format extraction, in-page scanning, audio transcoding, subtitles, adaptive stream downloads and storage selection before download |
| Playback | Local documents, progressive HTTPS and explicit HLS/DASH manifest links, Media3 session, standard speed and track controls, PiP on supported Android 8+ devices | Separate floating window service, custom gestures, account login, embedded web player and media browse service |
| Task manager | Download status and progress on refresh, name search, play, export, retry failed transfers, cancel and remove | Manual pause/resume, multi-select, dedicated toolbar, storage switcher and mount scanning |
| Vault | Android 6+, device credential gate, encrypted photo/video copies, name search, preview and deletion. Imports are limited to 32 MB per file | Vault-specific PIN or biometric prompt, export, moves, drag/drop, and bulk management |
| Cleaner | User-selected folder, exact duplicate photo matching and confirmed deletion. The scan checks up to 400 photos, each at most 50 MB | APK, empty folder and cache scans, plus similar-photo detection |
| Battery and memory | Device metrics, battery saver status and a shortcut to Android battery settings | Third-party process killing, booster widget and foreground app detection |
| WhatsApp statuses | List media from a granted folder or select saved image and video files individually. Export through the system file dialog or copy to the vault. | Devices whose picker hides the status directory require a user-visible copy. Integrated gallery and auto-discovery are pending |
| Search | Download name search and vault name search | Unified online search, voice and Process Text |
| Browser | Android Custom Tabs and local bookmarks | Persistent multi-tab WebView, speed dial customisation and TTS |
| Settings | System/light/dark appearance, version and notification permission | Language, account, policy screens, AMOLED and accent settings |
| Updates | Build version and release signing configuration | Scheduled checks, optional and forced updates, and install result handling |
| Integrations | Module boundaries for future work | Ad mediation and UMP, Firebase push/Crashlytics, SensorsData, AppsFlyer, HMS and Honor bridges |
| Manifest parity | Launcher alias, internal playback and vault components, explicit external share and custom scheme entry | Third-party handlers, legacy SDK and OEM entries, all-host App Links and binary manifest parity |

Source folders with an empty manifest reserve the requested module boundary. The feature table identifies which modules have an implementation. Downloads use app-specific storage and can be exported to locations available through Android’s file picker.

## Device verification checklist

- Launch the dev APK on API 21 and recent physical Android devices. Check navigation, large text and light/dark appearance.
- Share HTTPS page and media links. Try a direct file, a bad address, a cancelled transfer and a failed transfer followed by retry.
- Pick and play local audio and video. Background the player, open notification controls and enter PiP on a compatible device. Test HTTPS HLS and DASH manifests.
- Grant a test status folder, export media, then revoke access and retry. Confirm the UI recovers.
- Scan a disposable folder containing identical photos and different photos. Confirm only explicitly selected duplicates are removed.
- Verify vault credential cancellation, lock on backgrounding, image/video preview, file-size limits and deletion. Confirm the source copies remain in place.
