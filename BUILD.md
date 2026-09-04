# Building Lumo (this fork)

There are two ways to build the APK:

## A. GitHub Actions (no local setup required)

1. Push the project to a GitHub repo — see [`scripts/push-to-github.sh`](scripts/push-to-github.sh)
   for a one-liner that handles auth + commit + push.
2. The `.github/workflows/build-apk.yml` workflow starts automatically on every push.
3. After ~10-15 minutes, open the workflow run → **Artifacts** → download `lumo-debug-apks`.
4. The debug APK is signed with the standard Android debug key, ready to sideload.

For tagged releases (e.g. `v2.1.0`), the `release-apk.yml` workflow also fires and attaches
the unsigned release APK to the GitHub release page automatically.

## B. Local build

```bash
# Prereqs: JDK 17, Android SDK with platform-tools / android-36 / build-tools 36.0.0

# Debug APK (noGms — degoogled, suitable for F-Droid / sideloading)
./gradlew :app:assembleNobleNoGmsDebug

# Release APK (noGms, unsigned — you'll need to sign it yourself)
./gradlew :app:assembleProductionStandardNoGmsRelease
```

The APK lands in `app/build/outputs/apk/nobleNoGms/debug/lumo-v*.apk`.

If you need the GMS variant (with billing + Sentry), use `:app:assembleNobleGmsDebug` instead.

## Baseline profile (optional, advanced)

The `:baselineprofile` module is disabled by default — it requires a Gradle-managed
emulator and is only useful for production release builds. Enable it with:

```bash
./gradlew :app:assembleProductionStandardNoGmsRelease \
    -PincludeBaselineProfile=true \
    :app:generateBaselineProfile
```

## Pushing to GitHub (one command)

```bash
# Public repo (less recommended — your chats stay on device anyway, but the repo itself is visible)
GH_REPO_VISIBILITY=public ./scripts/push-to-github.sh <github-user>/lumo-llm "Initial commit"

# Private repo (default)
./scripts/push-to-github.sh <github-user>/lumo-llm "Initial commit"

# Subsequent pushes
./scripts/push-to-github.sh <github-user>/lumo-llm "Add feature X"
```

The script will:

1. Check that `gh` CLI is installed and authenticated. If not, it launches `gh auth login`
   (browser flow — paste the one-time code it shows you).
2. Initialise a git repo if missing (otherwise just commits the diff).
3. Creates the GitHub repo via `gh repo create` if it doesn't exist yet.
4. Pushes to `main`.

After the push, watch the Actions tab — the **Build APK** workflow should turn green
within ~15 minutes and produce the artifact.

## Troubleshooting

### `Could not resolve gradle plugin androidx.room`

Your Gradle version is too old. The project pins Gradle 8.13 via `gradle-wrapper.properties`,
so this shouldn't happen unless you've replaced the wrapper. Run `./gradlew --version`
to verify.

### `SDK location not found`

Create a `local.properties` file in the repo root with:

```
sdk.dir=/path/to/your/Android/Sdk
```

### `Keystore file … not set for signing config release`

You didn't provide signing keys. The release APK will be **unsigned** — sign it with:

```bash
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
    --out lumo-signed.apk lumo-v*-nogms-release-unsigned.apk
```

Or just use the debug variant (`:app:assembleNobleNoGmsDebug`), which is already signed.

### Build fails with OOM

Bump the heap in `gradle.properties`:

```
org.gradle.jvmargs=-Xmx8192m -XX:MaxMetaspaceSize=2048m …
```
