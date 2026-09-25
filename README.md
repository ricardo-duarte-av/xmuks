# xmuks

A native Android frontend for [gomuks](https://github.com/gomuks/gomuks), the always-on Matrix client.
gomuks does the Matrix work (sync, encryption, push); xmuks talks to its HTTP API:

- `GET /_gomuks/sse` (jsonl + zstd) for live data while the app is open, resuming with `last_server_ts`
- `POST /_gomuks/exec/{command}` for everything outbound, with `txn_id` idempotency
- FCM pushes (via the gomuks push gateway) while it is closed

## Building

Requires JDK 21 and the Android SDK with `platforms;android-37.1` and `build-tools;37.0.0`.

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew testDebugUnitTest           # JVM unit tests (Robolectric, no emulator)
./gradlew verifyRoborazziDebug        # screenshot tests against committed goldens
./gradlew recordRoborazziDebug        # re-record goldens after an intended UI change
./gradlew spotlessApply detekt lintDebug
```

CI (`.github/workflows/ci.yml`) runs the same checks with `-PwarningsAsErrors=true` on every push. A `vX.Y.Z` tag
matching `versionName` also uploads the AAB to the Play internal track and cuts a GitHub Release.

Release signing reads `keystore.properties` (gitignored) or the `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD` environment variables; without them the release build is unsigned.

## Modules

| Module | Purpose |
|---|---|
| `app` | Single activity, navigation, DI entry point |
| `core:designsystem` | M3 Expressive theme (Google Sans Flex, dynamic colour), shared components |
| `build-logic` | Convention plugins (`xmuks.android.*`, `xmuks.hilt`, `xmuks.screenshots`) |

## Licences

Google Sans Flex is bundled under the SIL Open Font License (`core/designsystem/src/main/assets/licenses`).
