# Link routing

| Entry point | Accepted input | Destination |
|---|---|---|
| Launcher | MAIN/LAUNCHER | Explore |
| Android Share | Plain text containing an HTTPS URL | Link actions |
| Android Share | Single content URI with audio, video or image MIME | Shared media actions |
| Android Share | Multiple content URIs with image or video MIME | Shared media actions, up to 30 files |
| Custom scheme | `lighttube://open?url=<percent-encoded HTTPS URL>` | Link actions |
| Paste or clipboard | HTTPS URL | Link actions |

An HTTPS URL with a recognised media extension exposes playback. A direct audio or video file also exposes original-file download. Explicit HLS and DASH manifests expose streaming. Page URLs from YouTube, Facebook, Instagram, TikTok, X, WhatsApp, Snapchat and other hosts open through Custom Tabs.

Automatic HTTPS opening depends on domain-owner Digital Asset Links. This package registers only the custom scheme and Android Share filters. See [Add Android App Links](https://developer.android.com/training/app-links/add-applinks). The custom scheme is an unverified entry point. It opens a confirmation screen and accepts only HTTPS links as payloads.
