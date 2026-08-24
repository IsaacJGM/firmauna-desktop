# Windows installation and validation

> **Current status:** access to `Windows-MY` is implemented and has been verified with Java 21 x64 against a private-key entry. A release installer and an end-to-end production PDF signature have not yet been validated.

## Certificate source

FirmaUNA opens the current user's Personal certificate store with `KeyStore.getInstance("Windows-MY")`. Java exposes it through `SunMSCAPI`. Only X.509 entries backed by a private key are offered for signing.

The private key may be:

- A software key installed in Windows. Windows displays authorization only when the key was created with a protection policy that requires it.
- A smartcard-backed key. The card, Windows driver, and native authorization remain required even though FirmaUNA accesses it through `Windows-MY` rather than a vendor PKCS#11 library.

FirmaUNA never requests a Windows certificate password or smartcard PIN in its own dialog. Authorization is delegated to the Windows cryptographic provider.

## Java coexistence

RENIEC DCDelivery documents Java 1.8.0_351 or newer for certificate provisioning and requires 32-bit Java for token or smartcard workflows. FirmaUNA itself requires Java 21 and should be distributed with a private runtime image. Java 8 can remain installed for RENIEC tooling; the FirmaUNA package must not replace it or change its file associations.

For development, verify that Maven uses Java 21:

```powershell
mvn -version
mvn clean package
```

Create a self-contained application image for validation:

```powershell
.\packaging\windows\package.ps1
```

The output is written to `target/windows-package/FirmaUNA`. The script can receive `-Type exe` or `-Type msi` after the required WiX tooling and release-signing process have been established. An unsigned local package is a test artifact, not an institutional release.

## What is not yet established

- A Windows runtime image or installer artifact.
- A tested signing and distribution process.
- Windows code-signing and reputation behavior.
- End-to-end native authorization with representative software keys and smartcards.
- Equivalent behavior for macOS-specific actions such as revealing a saved document in Finder.

Do not infer Windows support from the presence of Java, JavaFX, PDFBox, or PKCS#11-related code. Those components require target-platform validation.

## Validation checklist before a release

- [ ] Define the supported Windows versions and processor architectures.
- [ ] Build a Windows-native package in a controlled Windows environment.
- [ ] Verify JavaFX runtime inclusion and application startup.
- [x] Verify Java 21 can enumerate a `PrivateKeyEntry` in `Windows-MY` through `SunMSCAPI`.
- [ ] Test valid, expired, not-yet-valid, public-only, and unsupported certificate discovery.
- [ ] Test native Windows authorization success, cancellation, incorrect PIN, absent card, and card removal.
- [ ] Test PDF loading, page navigation, both stamp layouts, all-pages placement, and automatic `[FU]` output naming.
- [ ] Validate `explorer.exe /select,` for the document-location action on supported Windows versions.
- [ ] Define code-signing, checksum, distribution, update, and support processes.
- [ ] Record the tested commands and evidence before publishing installation instructions.

## Related documentation

The [architecture overview](arquitectura.md) identifies the platform-specific components that require validation. The [macOS packaging guide](empaquetado-macos-arm64.md) is macOS-specific and must not be used as a Windows release procedure.
