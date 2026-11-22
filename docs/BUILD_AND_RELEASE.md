# BUILD_AND_RELEASE

Everything here is reproducible without Android Studio. The app targets **compileSdk 37.2**
(Android 17 minor version), **minSdk 26**, AGP **9.5.1** with **Gradle 9.9.2** and **JDK 21**.

## 1. One-time toolchain bootstrap (fresh machine / sandbox)

```bash
tools/bootstrap-env.sh      # JDK 21 + Gradle 9.9.2 + Android SDK 37.2 + build-tools 37 (~3 GB)
source tools/env.sh
```

The script installs everything under `.toolchain/` (git-ignored) and writes `local.properties` with
`sdk.dir`. Behind a TLS-inspecting proxy it also imports `/etc/mitm/ca.crt` into a JVM truststore and
exports `JAVA_TOOL_OPTIONS`, which is required for Gradle to download dependencies.

## 2. Build

```bash
./gradlew :app:assembleDebug            # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest        # JVM tests (parsers, analytics, CSV, catalog)
./gradlew :app:assembleDebugAndroidTest # compiles Compose/instrumented tests (no device needed)
./gradlew :app:lintDebug
```

This sandbox has no KVM, so instrumented tests are **compiled** here and must be **run on a device**
(`:app:connectedDebugAndroidTest`). Never claim a device run that did not happen.

Long builds must be detached in this environment; the agent tooling terminates foreground commands
after ~30 s:

```bash
setsid nohup ./gradlew :app:assembleDebug > /tmp/build.log 2>&1 &
```

## 3. Version pins that matter

| Piece | Version | Why |
| --- | --- | --- |
| AGP | 9.5.1 | Built-in Kotlin: **never add `org.jetbrains.kotlin.android`**. Only `android.application`, `kotlin.plugin.compose`, `kotlin.plugin.serialization` are applied. |
| Kotlin | 2.4.21 | Compose compiler plugin version comes from the same alias. |
| Compose BOM | 2026.11.01 | Compose 1.13 AARs declare `compileSdk 37.1+` metadata, so `compileSdkMinor = 2` is set next to `compileSdk = 37`. |
| compileSdk | 37 + minor 2 | `platforms;android-37.2` must be installed. |
| JDK | 21 | Gradle 9.9.2 requires 17+, AGP 9 is happiest on 21. |
| Gradle wrapper | 9.9.2 | `gradle/wrapper` is committed; `--no-validate-url` was needed to generate it behind the proxy. |

## 4. Signing

Never commit secrets. Release signing is opt-in through Gradle properties:

```bash
./gradlew :app:assembleRelease \
  -PMYHEALTH_KEYSTORE=/abs/keystore.jks -PMYHEALTH_KEYSTORE_PASSWORD=… \
  -PMYHEALTH_KEY_ALIAS=… -PMYHEALTH_KEY_PASSWORD=…
```

Without those properties the release variant still assembles (unsigned).

## 5. Optional Google sign-in

```bash
./gradlew :app:assembleDebug -PMYHEALTH_GOOGLE_WEB_CLIENT_ID=<id>.apps.googleusercontent.com
```

When the id is absent, `BuildConfig.GOOGLE_WEB_CLIENT_ID` is empty, the "Continue with Google" button is
not shown, and the offline path remains fully functional.

## 6. Verification loop used by agents

1. `:app:assembleDebug` — must succeed.
2. `:app:testDebugUnitTest` — must be green.
3. `:app:assembleDebugAndroidTest` — compiles UI/instrumented tests.
4. `:app:lintDebug` — warnings allowed, errors not.

Record the actual result in `HANDOFF.md`; do not write "should build".
