SMS Sync Pro 2.2.1 (versionCode 5) fixes the update source, HMAC secret and AES password to the APK configuration. Their editable settings fields are removed, imports cannot override them, and legacy saved overrides reset to the packaged values. Other settings remain editable.

Updates come from this repository's GitHub Releases. Reuse the persistent signing key for future releases. The packaged HMAC/AES values remain the owner's previously requested strings; the dashboard must use the same values.
