# Side-by-side Artemis testing

## Why a different application ID is necessary

Android identifies installed apps by their **application ID**, not merely by
the signing certificate. Two APKs with the same application ID cannot be
installed side by side, regardless of their signatures. A different signing
key with the same application ID instead causes an update-signature mismatch.

The existing Artemis Stable release uses `com.limelight.noir`. The AnyLabVN
test channel uses a *separate build type* and package identity:

| Build variant | Application ID | Launcher label |
|---|---|---|
| `nonRoot_gameRelease` (unchanged) | `com.limelight.noir` | Artemis |
| `nonRoot_gameReleaseTest` | `com.limelight.noir.anylabtest` | Artemis AnyLab Test |
| `nonRoot_gameDebug` (unchanged) | `com.limelight.noirdebug` | Diana |

The `releaseTest` build type inherits release R8 shrinking, resources and
configuration, but changes `applicationIdSuffix` and app labels. Its
`matchingFallbacks=['release']` allows release-only dependencies/resources
to resolve without changing production code. The Java source package
(`com.limelight`) and JNI symbols are intentionally unchanged.

Android storage, pairing credentials, preferences, app permissions, shortcuts
and notifications are separated by the package/UID. Each app must be paired
with Apollo independently. Installation of the test APK does **not** uninstall
or migrate the Stable package's data.

## Integration details

The merged manifest generates content authorities dynamically:
`poster.${applicationId}` and `${applicationId}.fileprovider`, so both
installed apps register distinct providers. The foreground notification uses
the build-type-specific app label. The workflow checks the releaseTest APK's
package ID and provider authorities with Android Build Tools, then verifies
the APK signatures.

## Installing and upgrading

Use the signed ARM64 artifact from the
`artemis-anylabvn-parallel-test-release-signed-apks` CI artifact.
It is a production-style **Release Test** APK (R8 enabled), not the
`Diana` debug APK.

Android requires the *same* test-channel signing certificate on subsequent
upgrades. Maintain the organization release signing key in Actions secrets
`ANYLABVN_ARTEMIS_RELEASE_KEYSTORE_BASE64` and
`ANYLABVN_ARTEMIS_RELEASE_PASSWORD`. If those are unavailable, the workflow
bootstraps a temporary release key and exports encrypted recovery material:
a future build with a different bootstrap key will not install as an update
until that original key is configured. Do not expose a raw signing key in the
repository.

Having both apps installed does not mean both can stream the same Apollo host
session simultaneously: the host's session behavior is independent of
Android package identity. Test one stream at a time unless specifically
testing multi-client host behavior.

## Acceptance criteria

- Stable `com.limelight.noir` remains installed and launches normally.
- `com.limelight.noir.anylabtest` installs as a second app without a
  signature conflict and displays `Artemis AnyLab Test`.
- Stable's saved hosts and settings are unchanged; Test has isolated data.
- Test can independently pair with Apollo and test Home/Recent Apps, Surface
  reattachment and reconnect after network loss.
- CI must pass all Debug, Release and Release Test unit tests; build and
  signing failures must fail the job.
