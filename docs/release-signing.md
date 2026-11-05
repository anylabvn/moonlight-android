# Signed Android release workflow

The release variant is `:app:assembleNonRoot_gameRelease` (package
`com.limelight.noir`), **not** the `Diana` debug variant. The GitHub Actions
workflow runs debug and release unit tests as required checks, builds the
release ABI splits with code shrinking, and verifies signatures using Android
Build Tools `apksigner`. The workflow must be green before a release artifact
is distributed.

## Signing identity

Android only accepts an in-place APK upgrade when the package name and signing
identity match. **A newly generated key cannot update an installation signed by
the original Artemis developer.** Back up relevant user data and be prepared
to uninstall that existing package before installing the AnyLabVN-signed
release. The debug variant has a distinct package name.

If the organization has its own release key, configure the repository Actions
secrets `ANYLABVN_ARTEMIS_RELEASE_KEYSTORE_BASE64` (base64-encoded PKCS12
keystore) and `ANYLABVN_ARTEMIS_RELEASE_PASSWORD` (its keystore password).
The key must contain alias `anylabvn-artemis`. The workflow will then reuse
that key on every push and will not export it.

**First-run bootstrap only:** when both secrets are absent, CI generates a new
RSA signing key. The public release APKs are signed and verified, and the
keystore plus password is archived and encrypted for the repository's public
recovery certificate `.github/release-signing-recipient.crt`. Only the
corresponding private key holder can decrypt that Actions artifact. The
private recovery key MUST NOT be committed or uploaded to public CI artifacts.
The maintainer must promptly recover the PKCS12 and configure the two secrets.
Without doing so, later bootstrap builds will create *different* signing keys,
breaking upgrades.

Never commit a keystore or password to Git, check them into a public CI
artifact unencrypted, or substitute the Android debug keystore for the release
signer.

## Verification scope

A successful build confirms the release APK is compiled, code-shrunk,
signed using modern APK signature schemes, and passes the checked unit
tests. It **does not** independently prove background streaming and automatic
reconnect on an Android tablet with an Apollo host. Those scenarios require
device acceptance testing described in `persistent-stream-recovery.md`.
