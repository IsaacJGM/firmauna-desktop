# Architecture overview

FirmaUNA is a Java desktop application that signs PDF documents through a JavaFX workflow, PDFBox detached signing, and a PKCS#11 token session.

## Runtime flow

1. `MainApp` starts JavaFX and opens `MainWindow`.
2. `MainWindow` loads the selected PDF, renders its preview, and captures the stamp position and layout.
3. The user enters a token PIN and must select a discovered valid certificate.
4. `TokenProvider` provides the selected certificate, private key, and certificate chain to `PDFSigner`.
5. `PDFSigner` adds the visible stamp or stamps, then writes one detached PKCS#7 signature for the signing action.
6. The signed temporary output is moved beside the input PDF using the next available `[FU]` name and reloaded into the UI.

## Main components

| Component | Responsibility | Platform scope |
| --- | --- | --- |
| `MainApp` | JavaFX application entry point. | Cross-platform Java code. |
| `ui/MainWindow` | PDF selection and preview, draggable stamp, page navigation, PIN and certificate modals, automatic output reload. | JavaFX UI; `Ver ubicación` is currently macOS Finder-specific. |
| `signer/PDFSigner` | PDFBox document processing, visible stamp drawing, detached PKCS#7 signing through Bouncy Castle. | Cross-platform Java code, subject to runtime validation. |
| `pkcs11/TokenProvider` | One PKCS#11 login session, signing-certificate discovery, selected alias, private-key access, logout. | Current library path is macOS-specific. |
| `packaging/macos` | macOS icon and packaging inputs. | macOS-specific. |

## Signing and stamp behavior

`PDFSigner` creates a detached `adbe.pkcs7.detached` signature. For a single signing action, it adds visible stamps to every requested page before saving incrementally, while retaining one cryptographic detached-signature operation.

The horizontal and vertical stamp layouts have different dimensions. `MainWindow` converts the preview rectangle to PDF coordinates using the active page CropBox. For all-pages signing, the selected position is normalized within the first page's safe placement area and resolved relative to each target page's CropBox. The operation is rejected before token interaction when any target page is too small for the selected layout.

## Certificate selection

After PIN login, `TokenProvider` discovers key entries with X.509 certificates. `MainWindow` displays discovered signing certificates, orders valid choices before expired ones, and requires a selection before signing. Validity is currently determined by whether the certificate expiration time is after the current system time; expired entries remain visible but disabled.

`TokenProvider` validates the PIN by accessing a private key in the same PKCS#11 session, rather than relying only on keystore load. It reuses that session for certificate selection and signing, then logs out and clears its active provider, keystore, and selected-alias state.

## Automatic output workflow

The signer writes to a temporary file in the input PDF's directory. `MainWindow` then moves it without replacement to:

1. `base [FU].pdf`
2. `base 2 [FU].pdf`
3. The next available sequential name

The saved PDF is automatically reloaded so it can be signed again. On macOS, `Ver ubicación` uses `open -R` to reveal the saved file in Finder.

## Packaging artifacts

The documented macOS artifact is an Apple Silicon (ARM64) application image wrapped in a DMG, using a custom Java runtime and the `packaging/macos/FirmaUNA.icns` icon. It is ad-hoc signed and not notarized. See [macOS ARM64 packaging](empaquetado-macos-arm64.md) for the exact procedure; Windows packaging remains unverified.
