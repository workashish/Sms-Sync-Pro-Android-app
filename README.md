# SMS Sync Pro

Android SMS and messaging-notification forwarding, with a companion dashboard. The app captures text, stores it in an encrypted durable queue, and schedules delivery with WorkManager. Rules and settings remain on the phone. The dashboard receives signed requests directly from the phone.

## Install and configure

Android 5.0/API 21 or newer is supported. This release compiles and targets API 35; emulator verification uses API 37. Direct APK distribution is supported; Google Play publishing is not part of this project.

The installation defaults are in `app/src/main/assets/default_config.json`. They match the owner's requested configuration: forwarding, device model, webhook retries and SMS commands enabled; timeout 8 seconds; HMAC `YOUR_HMAC_SECRET_KEY`; AES `YOUR_AES_PASSWORD`; screenshots allowed; no template; one active `sms sync dashboard` rule targeting `https://thesms.vercel.app/api/webhooks/incoming`. Editable defaults are created once, and edits survive restarts and compatible upgrades. HMAC, AES and the official GitHub update URL are fixed by the APK; saved legacy overrides are reset and imported overrides are ignored.

The supplied HMAC/AES strings are public placeholders, not unique production credentials. For a private deployment, configure unique values in the packaged default_config.json, rebuild the APK and set identical values on the dashboard server. Keeping the specified defaults was an explicit owner requirement; they are still embedded unchanged.

Grant SMS receipt permission to capture SMS. Send SMS permission is requested only for SMS targets. Notification access is required for messaging-notification capture. Outgoing SMS uses the selected SIM or Android's default SMS SIM; no ready/default SIM produces an actionable failure.

## Rules, commands and notifications

Create, edit, enable or remove SMS/Webhook rules. Literal filters match sender or body without case sensitivity. `/pattern/` and `/pattern/i` use RE2/J with a 512-character limit. Lookaround and backreferences are unsupported and rejected instead of running unsafe backtracking expressions.

SMS Commands remains enabled by default, but remote replies require an authorized sender list. An empty list allows no remote replies. Only `STATUS` is supported. LOCATION and REBOOT are not implemented and do not trigger privileged actions.

Messaging-notification capture can include SMS and RCS. Group summaries, ongoing notifications and self-sent MessagingStyle entries are ignored. Event identity is persisted, and matching SMS/notification bodies are deduplicated across capture sources. Providers without stable timestamps cannot always distinguish an identical repeated message from a notification update. Android may hide sensitive notification contents; the app does not bypass that protection.

SMS forwarding adds `[SMS Sync Pro forwarded]` to prevent loops between instances. SMS logs distinguish SENDING, SENT and DELIVERED using carrier callbacks. SENT does not prove delivery. Missing results become UNKNOWN and are not automatically resent, preventing duplicate charges. Physical carrier/dual-SIM tests remain necessary.

## Queue and privacy

Message receipts are committed to Room before receipt processing. WorkManager inputs contain record IDs, not full message bodies. New receipts, outbox payloads, settings and log contents are encrypted with device-bound Android Keystore keys; local deduplication fingerprints are keyed. API 21/22 use an RSA-wrapped random AES key; newer Android uses Keystore AES directly.

Queue processing recovers at app startup, boot and periodically. No indefinite foreground service is used. Pause retains queued messages and stops new forwarding; an in-flight request may finish. Resume schedules pending work. Cancel Unsent Queue explicitly cancels unsent items; failed webhooks can be retried with their original delivery IDs.

Logs are limited to 1,000 entries and a configurable 1–365-day retention period (default 30). Pending items are retained until completed/cancelled. Completed receipts are pruned after two days; completed outbox items follow retention. Cloud backup and device transfer are disabled because encrypted data depends on the original device's keys. Uninstalling clears local data; export configuration first if migrating.

Imports validate the complete file before an atomic Room transaction merges settings and matching rules. Re-importing the same rules does not duplicate them. Exports omit HMAC/AES secrets, internal fingerprint keys and message history. Imports cannot override the fixed HMAC, AES or update URL.

Normal app closure is supported. Force-stop, revoked permissions, OEM battery restrictions, unvalidated/offline networks and platform OTP restrictions can delay or prevent capture/delivery. WorkManager does not guarantee instantaneous delivery under every device condition.

## Webhook contract

Release builds require HTTPS. Debug builds allow HTTP for local testing. Standard payload:

```json
{
  "schema_version": 1,
  "id": "stable-delivery-uuid",
  "encryption": "aes-256-gcm-pbkdf2-sha256-v1",
  "type": "otp",
  "sender": "BANK",
  "body": "saltHex:ivHex:ciphertextAndTagHex",
  "timestamp": 1791417600000,
  "time": "2026-10-08T00:00:00.000Z",
  "metadata": { "device_model": "Pixel" }
}
```

HMAC-SHA256 is sent as `x-hmac-signature` over the exact UTF-8 JSON body. `Idempotency-Key` also carries the stable delivery ID. Encryption uses PBKDF2-HMAC-SHA256, 10,000 iterations, a 32-byte key, a 16-byte salt, a 12-byte GCM IV and a 16-byte authentication tag. When encryption is disabled, `encryption` is `none` and `body` is plain text over HTTPS. Extracted OTP/amount metadata is omitted from the encrypted request; the dashboard extracts it after authenticated decryption.

Templates support `{sender}`, `{message}`, `{body}`, `{device_model}`, `{id}`, `{timestamp}` and `{encryption}` through nested objects/arrays. A standalone `{timestamp}` becomes a JSON number. The standard dashboard endpoint requires sender/body placeholders and a valid versioned envelope. Other integrations must implement their own decryption/idempotency, or be configured for plaintext HTTPS payloads.

The companion server accepts up to 100 messages and 1 MB of request data. Rejects malformed JSON, unsupported versions, invalid signatures, decryption failures and malformed batches before writing. The server records transactional batches with global delivery-ID tombstones so retries do not duplicate or restore deleted messages.

## Direct APK updates and signing

Updates are checked against the fixed official GitHub Releases URL. The URL cannot be edited or changed through configuration imports. Metadata must include a SHA-256 digest (`digest` on GitHub assets). The app verifies checksum, package ID, increasing versionCode and compatible signer before offering installation. Downloaded files use app-specific storage. Installation starts from the explicit Settings button and Android may request approval for this app to install packages.

A new persistent signing key was created locally because the original Google AI Studio key is unavailable. Future releases must reuse it. A differently signed older installation requires a one-time migration/reinstall; its original signing key cannot be recovered from the repository alone. Keep an encrypted offline backup of the protected signing folder. Never commit keystores or passwords.

## Build and checks

Use JDK 17 and Android SDK 35:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Device reliability tests expect the companion local test server on emulator host `10.0.2.2:4320`; see the dashboard's tests/emulator-server.mjs. Use synthetic messages and disable production-target rules during tests.

Build a release with `scripts/build_release.py --keystore <path> --password-file <path>`. Signing values are passed without printing them. `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` are supported at build time. Every release must increase versionCode. GitHub verification and manual signed-release workflows are provided; publishing requires repository authentication and signing secrets.
