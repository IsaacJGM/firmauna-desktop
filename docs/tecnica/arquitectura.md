# Architecture overview

FirmaUNA is a Java 21 desktop application that signs PDF documents through a JavaFX workflow, PDFBox detached signing, and a platform-specific credential provider.

## Runtime flow

1. `Launcher` starts the packaged classpath application and delegates JavaFX startup to `MainApp`, which opens `MainWindow`.
2. `MainWindow` loads the selected PDF, renders its preview, and captures the stamp position and layout.
3. On macOS, the user enters a token PIN and `TokenProvider` opens PKCS#11. On Windows, `WindowsCertificateProvider` opens the current user's `Windows-MY` store without requesting a PIN in JavaFX.
4. The user selects a compatible certificate. The active `SigningProvider` supplies its certificate, private key, certificate chain, and optional JCA signature provider to `PDFSigner`.
5. `PDFSigner` adds the visible stamp or stamps, then writes one detached PKCS#7 signature for the signing action.
6. The signed temporary output is moved beside the input PDF using the next available `[FU]` name and reloaded into the UI.

## Main components

| Component | Responsibility | Platform scope |
| --- | --- | --- |
| `Launcher` / `MainApp` | Plain packaged entry point and JavaFX application startup. | Cross-platform Java code. |
| `ui/MainWindow` | PDF selection and preview, draggable stamp, page navigation, provider-specific authorization, certificate modal, and automatic output reload. | Cross-platform JavaFX UI. |
| `signer/PDFSigner` | PDFBox document processing, visible stamp drawing, detached PKCS#7 signing through Bouncy Castle. | Cross-platform Java code, subject to runtime validation. |
| `certificate/SigningProvider` | Common contract for certificate discovery and private-key access. | Cross-platform abstraction. |
| `pkcs11/TokenProvider` | One PKCS#11 login session, signing-certificate discovery, selected alias, private-key access, logout. | Current library path is macOS-specific. |
| `windows/WindowsCertificateProvider` | Reads private-key entries from `Windows-MY` and routes RSA signatures through `SunMSCAPI`. | Windows-specific. |
| `packaging/macos` | macOS icon and packaging inputs. | macOS-specific. |

## Signing and stamp behavior

`PDFSigner` creates a detached `adbe.pkcs7.detached` signature. For a single signing action, it adds visible stamps to every requested page before saving incrementally, while retaining one cryptographic detached-signature operation. The signing provider can explicitly select `SunMSCAPI` for a Windows private key while preserving the existing PKCS#11 behavior.

The horizontal and vertical stamp layouts have different dimensions. `MainWindow` converts the preview rectangle to PDF coordinates using the active page CropBox. For all-pages signing, the selected position is normalized within the first page's safe placement area and resolved relative to each target page's CropBox. The operation is rejected before token interaction when any target page is too small for the selected layout.

## Certificate selection

Both credential providers expose only key entries with X.509 certificates. `MainWindow` displays discovered signing certificates, orders usable choices first, and requires a selection before signing. A certificate is usable when it is currently valid, has an RSA public key, and permits digital signature or content commitment when a key-usage extension is present. Expired, not-yet-valid, unsupported, and non-signing entries remain visible but disabled.

`TokenProvider` validates the PIN by accessing a private key in the same PKCS#11 session, rather than relying only on keystore load. It reuses that session for certificate selection and signing, then logs out and clears its active provider, keystore, and selected-alias state.

On Windows, `WindowsCertificateProvider` loads `KeyStore.getInstance("Windows-MY")` with `SunMSCAPI`. It does not collect a PIN. Access to a protected software key or smartcard is delegated to Windows and its CAPI/CNG provider, which may display a native authorization window during private-key use. Software keys without an authorization policy may sign without a prompt.

## Automatic output workflow

The signer writes to a temporary file in the input PDF's directory and closes it before moving it, which is required on Windows. `MainWindow` then moves it without replacement to:

1. `base [FU].pdf`
2. `base [FFU].pdf` when the input is `base [FU].pdf`
3. `base [FFFU].pdf` when the input is `base [FFU].pdf`, continuing the marker chain

The saved PDF is automatically reloaded so it can be signed again. `Ver ubicación` uses `open -R` on macOS and `explorer.exe /select,` on Windows.

## Packaging artifacts

The documented macOS artifact is an Apple Silicon (ARM64) application image wrapped in a DMG, using a custom Java runtime and the `packaging/macos/FirmaUNA.icns` icon. It is ad-hoc signed and not notarized. See [macOS ARM64 packaging](empaquetado-macos-arm64.md) for the exact procedure. Windows must bundle its own Java 21 runtime so it can coexist with Java 8 installations required by RENIEC tools; release packaging remains unverified.
