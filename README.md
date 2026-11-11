# LightTube 1.0.0

<img src="docs/assets/lighttube-logo.png" alt="LightTube play and download logo" width="96" height="96">

LightTube is an original Android media utility written in Kotlin with Android Views and Material 3. This source package implements a subset of the master specification. See [feature status](docs/FEATURES.md) for the remaining acceptance criteria.

| Setting | Value |
|---|---|
| Production application ID | `com.lighttube.app` |
| Version | `1.0.0` (10000) |
| Android SDK | Minimum 21, target and compile 35 |
| Build | Gradle 8.11.1, Android Gradle Plugin 8.9.2, JDK 17 |
| Variants | `dev`, `beta`, `prod`; debug, internal and release |

## Brand assets

The LightTube mark combines playback and download in a single mint silhouette on deep teal. The shared path and palette are defined in [lighttube_brand.xml](core/ui/src/main/res/values/lighttube_brand.xml). The Android resources use the mark for the launcher, system splash on Android 12+, Home and Settings, and the playback notification icon. The launcher includes adaptive, legacy and Android 13+ themed variants. Android DownloadManager supplies its own transfer notification icon.

[512 × 512 logo PNG](docs/assets/lighttube-logo.png)

## Build

Install Android SDK Platform 35 and Build Tools 35.0.0, then run from the project root.

```sh
./gradlew :app:assembleDevDebug :app:lintDevDebug :app:assembleProdRelease
```

The same commands run in the **Verify LightTube** GitHub Actions workflow. A successful run attaches the debug APK under **Artifacts**. Use `gradlew.bat` on Windows. The production release uses R8 and resource shrinking. It is unsigned unless `LIGHTTUBE_RELEASE_KEYSTORE`, `LIGHTTUBE_RELEASE_STORE_PASSWORD`, `LIGHTTUBE_RELEASE_KEY_ALIAS` and `LIGHTTUBE_RELEASE_KEY_PASSWORD` are configured together. The internal build uses debug signing and an `.internal` suffix.

## Manifest components

| Feature | Component | Manifest module | Implementation |
|---|---|---|---|
| Application | `androidx.multidex.MultiDexApplication` | `:app` | AndroidX Multidex bootstrap |
| Home and utilities | `ExploreActivity` | `:app` | Launch surface and navigation |
| Launcher | `MainExploreAlias` | `:app` | Launcher alias for `ExploreActivity` |
| Link and media sharing | `DeepLinkActivity` | `:app` | Validates incoming HTTPS text and forwards granted media URIs |
| Playback | `VideoPlaybackActivity` | `:feature:player` | Media3 player controls and Android PiP |
| Background playback | `PlayerService` | `:feature:player` | Media3 session and media playback foreground service |
| Private vault | `VaultActivity` | `:feature:vault` | Credential gate, encrypted copies, search and preview |

AndroidX dependencies contribute their own manifest entries during merging. Open the app manifest's **Merged Manifest** view in Android Studio to inspect the result for a particular variant.

## Verification

The source package passed XML and TOML parsing, checks for module and component declarations, and archive integrity checks. Gradle could not bootstrap in the source environment because `services.gradle.org` could not be resolved. Android compilation, lint, APK installation and device behaviour are pending.

- [Features and acceptance status](docs/FEATURES.md)
- [Link routing](docs/DEEPLINKS.md)
- [Ads and telemetry](docs/ADS.md)
- [Platform and security decisions](docs/COMPLIANCE.md)
