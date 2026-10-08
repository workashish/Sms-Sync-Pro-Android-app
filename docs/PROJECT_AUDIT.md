# SMS Sync Pro — audit closure and acceptance status

The repositories were reviewed together, starting at Android `c7e2638` and dashboard `27698e5`. The implementation fixes below were tested locally. Production Supabase/Vercel migration and deployment remain unverified because those credentials are unavailable. GitHub publication status is shown by this repository and its releases.

## Every original remaining-work item

| Original area | Resolution | Verification / limit |
| --- | --- | --- |
| Live Supabase security | Server-only service-role access, RLS/table/view/function privileges, single-transaction schema migration and read-only security-audit script prepared. Public inherited table privileges are also revoked. | Real PostgreSQL 17 checks pass. Production credentials absent; live policies and legacy SECURITY DEFINER RPCs remain unverified. |
| SMS commands | STATUS replies require an authorized-sender list; empty list allows no replies. Unsupported LOCATION/REBOOT behavior removed. The requested enableSmsCommands default remains true. | Device test verifies an unauthorized STATUS creates no SMS reply. |
| Durable queue | Encrypted receipt/outbox tables, ID-only WorkManager inputs, stable per-rule delivery IDs, startup/boot/periodic recovery. | Device test delivers a 20,000-character message and verifies encrypted storage. Text capture is bounded at 256 KB; the server accepts 1 MB requests. |
| Regex | RE2/J replaces blocking backtracking regex. Unsupported expressions and >512-character filters are rejected. | Pathological 100,000-character input regression passes with a time bound. |
| RCS capture | Notification summaries, ongoing/self-sent entries skipped; stable event identities, persistent fallback cache and cross-source deduplication implemented. | Real messaging-account/provider behavior needs a physical-phone test. Providers without timestamps cannot guarantee distinction between identical repeated content and updates. Android content redaction is respected. |
| Background/status | Indefinite dataSync foreground service removed. WorkManager recovery replaces service-based claims. UI shows enabled/paused/no-rule states and real queue count; permission/SIM status is visible. | Emulator startup/recovery passes. Force-stop and OEM/platform restrictions cannot be bypassed or represented as guarantees. |
| SMS delivery | Explicit multipart sent/delivery PendingIntents and protected receiver; SENDING/SENT/DELIVERED states; no ready/default SIM errors; selected-SIM UI; uncertain carrier results are not resent automatically. | Code and API compatibility checks pass. Actual carrier/dual-SIM outcomes require a physical phone. SENT is not presented as proof of delivery. |
| Import integrity | Complete prevalidation, atomic Room settings/rules commit, idempotent merge. Secrets can be imported explicitly; exports exclude secrets/internal keys/message history. | Unit and device tests verify invalid imports do not partially apply and repeat imports do not duplicate rules. |
| Pause behavior | Pause retains queued work and skips new forwarding; resume schedules pending items. Explicit cancel marks unsent receipts/outbox cancelled. HTTP retries preserve IDs; uncertain SMS is not auto-resubmitted. | Device pause/resume test verifies no consumed network attempt while paused and successful same-ID delivery after resume. In-flight work may finish. |
| Login/session | Random signed seven-day sessions, database expiry/revocation checks, password/secret rotation invalidation, distributed PostgreSQL login counters, origin/body limits. | Real SQL-backed HTTP tests verify auth, logout revocation, expired/tampered token rejection, CSRF and rate limiting. Real-browser testing also caught/fixed reverse-proxy Host/origin handling. |
| Payload protocol | Explicit schema_version/encryption/id/timestamp contract. Legacy messages without adequate identity get independent IDs rather than collapsing distinct arrivals. | Version/decryption/HMAC/invalid-batch tests pass. Legacy senders without stable identity cannot offer exact retry idempotency. |
| Category/OTP parsing | Context-aware OTP extraction and currency amounts; dates, account numbers, balance amounts and substring false positives handled. Server-derived fields override untrusted extracted metadata. | Android/TypeScript fixtures cover these cases. Heuristics are not a universal parser for every language/provider. |
| Data retention/privacy | Device-bound encrypted settings, queue and complete log fields; keyed local fingerprints; 1,000-log limit and configurable retention; cloud/device backups disabled. Server content cleanup runs on ingestion/authenticated use; permanent minimal ID tombstones preserve deduplication. | Android Keystore/device encryption test passes; migration preserves rules/log records while encrypting legacy contents. Provider-managed database backup retention is outside this repository. |
| Dashboard limits | Database pagination, search and flags; receipt versus arrival ordering; full timezone aggregation; authenticated snapshot-ticket streaming export in pages of 100. | SQL tests cover 600 general records plus OTP and export 601 records. No 500-row sample limits remain in these flows. |
| Notifications | Opt-in browser-permission flow, private notification contents, incoming-message polling and stored preference. | Implementation is present; actual OS/browser notification delivery depends on user-granted permission and provider support. |
| Transaction reliability | Message writes, audit logging, global ID ledger and purge are database transactions. Export snapshot is consistent and owned by a session. | Invalid mid-batch writes roll back; duplicate retries leave one message; RLS/pagination/export/analytics tests pass in PostgreSQL. |
| Updates | SHA-256, package ID, increasing versionCode and signer checks; bounded verification; app-private download storage; failure feedback; explicit installer permission/action; compatible signature history. | Emulator rejects a bad checksum and successfully downloads/verifies/installs a same-key update from build 4 to 5. A Keystore test passes after upgrade. Local fixtures required temporary DownloadProvider LAN permission on API 37; restored after testing. |
| Target SDK/distribution | Compile/target 35, min 21, guarded platform APIs, direct APK distribution, manual signed GitHub release workflow. No Google Play publication plan. | Build/unit/lint checks pass. API 21 legacy Keystore wrapping is implemented; device verification uses API 37 rather than every supported Android release. |
| UX | Rule editing, target-specific permissions, saved drafts, explicit secret/template saves, update URL/current version, queue controls, import/export feedback, persistent battery-prompt dismissal, collapse/expand messages, responsive navigation/keyboard/labels, corrected device fields and documentation. | Emulator and real browser checks performed. New edge cases found during review were fixed rather than relying only on compilation. |

## Requested permanent installation defaults

`app/src/main/assets/default_config.json` remains exactly as requested, with the active `sms sync dashboard` rule and supplied HMAC/AES strings. The final APK is checked for that packaged asset. Stored edits survive compatible upgrades; defaults do not overwrite existing user configuration.

Those literal strings are public placeholders because the owner explicitly required them. They are not unique secret credentials. A private production deployment must use unique matching overrides in app Settings and server environment variables. This is an accepted owner-default constraint, not a claim of secret per-device provisioning.

## Signing and release artifacts

A new persistent local PKCS12 signing key was generated because no original Google AI Studio key exists locally or in the current repository. It is kept outside both repositories in the protected `.local-signing` folder. No signing password/private key is printed or published. Future releases must reuse it and increase versionCode. The old differently signed APK cannot be upgraded with this key; it needs a one-time configuration migration/reinstall.

The release artifact is `artifacts/SmsSyncPro-2.2-release.apk`, versionCode 4, minSdk 21, targetSdk 35. APK ZIP integrity and Android signature verification are performed, with checksum and public signer fingerprint alongside it. Old incomplete repository APKs are replaced/removed so the current download path contains a valid build.

## Evidence and execution environment

- Android: 16 unit tests pass; assembly, Android-test assembly and lint pass without an ignored-error baseline.
- Device: five instrumentation tests (four core scenarios plus package context) pass on API 37; an additional Keystore test passes after the signed in-place upgrade.
- Update flow: bad SHA-256 download rejected; same-key build 4→5 downloaded, verified and installed through Android's installer. Package state confirms versionCode 5 afterward.
- Dashboard: nine unit/SQL tests pass, plus SQL-backed HTTP integration, production build/typecheck and zero reported npm vulnerabilities.
- Local PostgreSQL 17 verifies transaction rollback, public-role denial, complete pagination/export, timezone analytics and distributed login limits. The test gateway is PostgREST-compatible, not a live Supabase deployment.
- Real browser checks cover sign-in, long-body layout, desktop/mobile navigation and responsive behavior; screenshots are saved under artifacts.
- Emulator networking initially used an unavailable proxy at 192.168.1.102:8080. It was temporarily cleared for tests, then restored. API-37 DownloadProvider LAN permission was temporarily granted only for the local update fixture, then revoked.

## What cannot be certified from repo-only access

Production migration/deployment requires Supabase and Vercel account access. Original-key compatibility cannot be recovered without the original key. Physical carrier, dual-SIM, real RCS/OTP redaction and OEM/reboot/Doze acceptance cannot be proven using only this emulator. Browser OS notifications require the user's permission. These limits are not marked as completed external verification.

The implemented repository gaps have concrete remedies and local evidence. “Perfect on every phone and production deployment” is not asserted.

## References

- https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration
- https://developer.android.com/reference/android/telephony/SmsManager
- https://github.com/google/re2j
- https://supabase.com/docs/guides/database/secure-data
- https://supabase.com/docs/guides/database/postgres/row-level-security
- https://vercel.com/docs/headers/request-headers
- https://docs.github.com/en/rest/releases/releases
