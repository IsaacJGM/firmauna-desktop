# Windows installation and validation

> **Current status:** no Windows package has been built or verified by this repository. This document is a planning and validation checklist, not a release or installation instruction.

## What is not yet established

- A Windows runtime image or installer artifact.
- A tested signing and distribution process.
- Windows code-signing and reputation behavior.
- Verified PKCS#11 token middleware compatibility on Windows.
- Equivalent behavior for macOS-specific actions such as revealing a saved document in Finder.

Do not infer Windows support from the presence of Java, JavaFX, PDFBox, or PKCS#11-related code. Those components require target-platform validation.

## Validation checklist before a release

- [ ] Define the supported Windows versions and processor architectures.
- [ ] Build a Windows-native package in a controlled Windows environment.
- [ ] Verify JavaFX runtime inclusion and application startup.
- [ ] Validate the selected vendor token middleware and PKCS#11 library path.
- [ ] Test valid and expired certificate discovery, PIN failures, cancellation, and token session cleanup.
- [ ] Test PDF loading, page navigation, both stamp layouts, all-pages placement, and automatic `[FU]` output naming.
- [ ] Decide and validate the document-location experience for Windows.
- [ ] Define code-signing, checksum, distribution, update, and support processes.
- [ ] Record the tested commands and evidence before publishing installation instructions.

## Related documentation

The [architecture overview](arquitectura.md) identifies the platform-specific components that require validation. The [macOS packaging guide](empaquetado-macos-arm64.md) is macOS-specific and must not be used as a Windows release procedure.
