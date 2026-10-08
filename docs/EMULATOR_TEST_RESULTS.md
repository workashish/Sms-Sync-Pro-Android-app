# Final local acceptance results

Device: emulator-5554, sdk_gphone16k_arm64, Android API 37. Client date: 8 October 2026, Asia/Kolkata. Final release: SMS Sync Pro 2.2, versionCode 4, minSdk 21, targetSdk 35.

## Android

Assembly, Android-test assembly, lint and 16 unit tests pass. No ignored-error baseline was introduced. Tests cover templates/Unicode, PBKDF2 interoperability, numeric/build-tag updates, metadata/checksum requirements, invalid imports, default seeding, data-preserving migration, safe regex and contextual message parsing.

Five instrumentation tests pass and cover these scenarios:

1. Android Keystore encryption/decryption and randomized ciphertext.
2. Atomic import and idempotent rule merge; invalid configuration rejected before change.
3. 20,000-character message committed encrypted, routed through ID-only WorkManager data and delivered/decrypted by the local SQL-backed dashboard.
4. Paused delivery consumes no network attempt; resume retains the same ID and succeeds.
5. Unauthorized STATUS does not create an SMS reply; repeated event ID does not create a new receipt.

The generated package-context test also passes within that five-test suite count (the core class contains four methods and the package test supplies the fifth).

The final release is ZIP/signature verified, installed fresh and shows exactly one active `sms sync dashboard` rule. Its packaged default_config.json exactly matches the owner's nine settings and rule fields. It is non-debuggable. The old incomplete repository APK was replaced and the corrupt historical build2 file removed.

## Signed update flow

A signed-debug build 4 and release candidate build 5 use the same newly generated signing key. The actual app UI checks local metadata, uses DownloadManager to fetch a 9 MB APK, verifies checksum/package/version/signer, opens Android's installer and completes the update. Package state confirms versionCode 5, versionName 2.2.1, non-debuggable afterward. A Keystore test passes after that in-place upgrade.

A download whose metadata deliberately supplies an all-zero checksum is rejected; no install-ready URI is retained, and failure feedback is persisted. The valid download then produces an install-ready URI/version and succeeds. This proves new-key continuity, not compatibility with a missing old Google AI Studio key.

The updater fixture used debug-only HTTP on 10.0.2.2. Release update URLs require HTTPS. Android 17's DownloadProvider had LAN access denied and could not reach the fixture; LAN permission was temporarily granted to that system provider for the local test and revoked afterward. Production public GitHub HTTPS downloads do not require that local fixture exception.

## Emulator network environment

The emulator had an unavailable global proxy at 192.168.1.102:8080, causing the new total-call timeout to return RETRYING while retaining the outbox row. It was temporarily cleared for local tests, which then passed. The original proxy was restored. The earlier captive portal override was also restored/unset. Wi-Fi/mobile data remain enabled. No app production TLS/network constraint was weakened.

## Dashboard and database

Nine unit/SQL tests, production build, TypeScript check and SQL-backed HTTP integration pass. npm audit reports zero vulnerabilities. PostgreSQL 17 verifies anon/authenticated read/write denial, mid-batch rollback, retry deduplication, 600-message pagination/aggregation, 601-record export, timezone grouping and atomic login counters.

HTTP checks cover authentication, middleware bypass attempt, logout revocation, origin protection, encrypted ingestion, malformed requests, global retry IDs, redaction, pagination, analytics and streamed export. A local PostgREST-compatible gateway is used; no live Supabase/Vercel account was accessed.

Real browser checks verify login under the actual Host/origin, long-message expand controls, desktop layout, phone-sized layout, mobile menu focus/labels/keyboard Escape, and hiding closed navigation from accessibility traversal. Screenshots are in artifacts/dashboard-final-desktop.jpg and artifacts/dashboard-final-mobile.jpg.

## Final state and limits

The emulator is left on the final signed 2.2 release with clean default configuration and no temporary forwarding rule. Temporary update/network test permissions were restored; local fixtures and test database processes were stopped. Build/test logs, checksums, public signing certificate fingerprint, screenshots and audit closure documents are retained.

Production database migration and deployment remain blocked by absent Supabase/Vercel credentials. The original signing key is absent. A physical phone, real carrier/dual-SIM and real messaging/RCS account are unavailable. OS notification permission/provider redaction cannot be guaranteed. Those external conditions are explicitly not reported as tested or perfect.

## 2.2.1 fixed-setting regression

Assembly, lint and 18 unit tests pass. Three targeted emulator instrumentation tests pass: immutable settings/legacy override reset, atomic imports and Keystore encryption. UI inspection confirms HMAC/AES use non-editable status text and Updates contains the official-release description and Check for Updates button, with no URL editor. Signed release 2.2.1 (versionCode 5) installs in place with the same certificate; packaged defaults and ZIP/signature integrity are verified.
