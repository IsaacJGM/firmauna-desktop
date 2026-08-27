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

The output is written to `target/windows-package/FirmaUNA`.

## Release installer (recommended): Inno Setup wizard

The distributed installer is built with **Inno Setup 6**, which provides a
user-friendly wizard in Spanish (Bienvenida → Carpeta de destino → Menú
Inicio/escritorio → Instalar → Finalizar).

The application is installed for **all users** at **`C:\FirmaUNA`** — a
location easy to find on the `C:` drive and **not** inside the user's
AppData folder. Because it writes to the root of the system drive, the
installer requests **administrator privileges (UAC)**.

> **Why not jpackage `--type exe`?** The jpackage/`exe` installer uses the
> minimal single-dialog WiX UI and cannot be customized into a wizard because
> `jpackage` does not pass the WiX `WixUIExtension` to `light.exe`. Inno Setup
> is therefore used to wrap the `app-image` produced by `jpackage`.

### Requirements

- JDK 21 and Maven (as above).
- Portable WiX 3.14 binaries (only needed to build the `app-image`, not for
  Inno). The [release archive](https://github.com/wixtoolset/wix3/releases)
  `wix314-binaries.zip` provides `candle.exe`/`light.exe` without an
  installation. The script below expects them under `%TEMP%\opencode\wix\bin`.
- Inno Setup 6. It can be installed per-user without administrator rights:

  ```powershell
  innosetup-6.7.3.exe /VERYSILENT /SUPPRESSMSGBOXES /CURRENTUSER /NORESTART
  ```

  This places `ISCC.exe` in `%LOCALAPPDATA%\Programs\Inno Setup 6`.

### Build the installer

```powershell
.\packaging\windows\installer-inno.ps1
```

The script:

1. Runs `package.ps1 -Type app-image` to build the self-contained `FirmaUNA\`
   image at `target/windows-package/FirmaUNA` (with its bundled Java 21 runtime).
2. Compiles `packaging/windows/firmauna.iss` with `ISCC.exe`.
3. Writes the installer to `target\FirmaUNA-2.0.0-setup.exe` and prints its
   SHA-256 checksum.

To skip regenerating the app image between iterations:

```powershell
.\packaging\windows\installer-inno.ps1 -SkipAppImage
```

If Inno Setup is installed elsewhere, pass its directory:

```powershell
.\packaging\windows\installer-inno.ps1 -InnoDir "C:\Path\To\Inno Setup 6"
```

### Publish checklist

- [ ] Upload `target\FirmaUNA-2.0.0-setup.exe` and publish its SHA-256 next to
      the download link so users can verify integrity.
- [ ] Note that the package is **not code-signed**: Windows SmartScreen will
      show "Windows protegió su equipo". Instruct users to choose
      *Más información → Ejecutar de todas formas*.
- [ ] Acquire and apply a code-signing certificate (`signtool.exe`) before an
      institutional release to remove the SmartScreen warning and improve
      reputation. The resulting installer should be re-tested end-to-end.

## What is not yet established

- A tested signing and distribution process for a production release.
- Windows code-signing and reputation behavior (the current installer is unsigned).
- End-to-end native authorization with representative software keys and smartcards.
- Equivalent behavior for macOS-specific actions such as revealing a saved document in Finder.

Do not infer Windows support from the presence of Java, JavaFX, PDFBox, or PKCS#11-related code. Those components require target-platform validation.

## Validation checklist before a release

- [ ] Define the supported Windows versions and processor architectures.
- [x] Build a Windows-native package in a controlled Windows environment.
- [x] Verify JavaFX runtime inclusion and application startup.
- [x] Verify Java 21 can enumerate a `PrivateKeyEntry` in `Windows-MY` through `SunMSCAPI`.
- [ ] Test valid, expired, not-yet-valid, public-only, and unsupported certificate discovery.
- [ ] Test native Windows authorization success, cancellation, incorrect PIN, absent card, and card removal.
- [ ] Test PDF loading, page navigation, both stamp layouts, all-pages placement, and automatic `[FU]` output naming.
- [ ] Validate `explorer.exe /select,` for the document-location action on supported Windows versions.
- [ ] Define code-signing, checksum, distribution, update, and support processes.
- [ ] Record the tested commands and evidence before publishing installation instructions.

## Related documentation

The [architecture overview](arquitectura.md) identifies the platform-specific components that require validation. The [macOS packaging guide](empaquetado-macos-arm64.md) is macOS-specific and must not be used as a Windows release procedure.
