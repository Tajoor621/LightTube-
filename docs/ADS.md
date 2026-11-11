# Ads and telemetry

The ads, push, analytics and OEM modules reserve the requested boundaries. This source has no advertising, tracking, crash reporting or remote configuration SDK. It has no application IDs or test ad tokens.

| Integration | Status |
|---|---|
| Google UMP and AdMob | Pending consent configuration and mediation setup |
| Pangle, InMobi, Mintegral, Vungle, Huawei Ads and Meta Audience Network | Pending vendor configuration and adapter compatibility checks |
| FCM, Firebase Analytics, Crashlytics, SensorsData and AppsFlyer | Pending configuration, opt-in wiring and event verification |
| Firebase Dynamic Links | Service retired. See the [Firebase deprecation FAQ](https://firebase.google.com/support/dynamic-links-faq). |

The requested sequencing is consent before ad SDK initialisation and explicit opt-in for analytics. The current build starts no ad-keeper or process resurrection jobs.
