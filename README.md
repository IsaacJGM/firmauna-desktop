# FirmaUNA Desktop

FirmaUNA is a desktop application for applying a digital signature and a visible stamp to PDF documents for UNA Puno.

> **Current status:** the repository currently documents and packages macOS ARM64 (Apple Silicon) builds. That package uses ad-hoc signing and is **not notarized**. A Windows package has not been built or verified by this repository.

## Quick start

1. Open a PDF.
2. Choose the stamp format and drag the preview to its position.
3. Select **Firmar PDF**, enter the token PIN, and choose a valid certificate.
4. The signed PDF is saved beside the original with the `[FU]` suffix.

## Documentation

| Audience | Documentation |
| --- | --- |
| End users | [Signing guide](docs/usuario/guia-de-firma.md), [Certificates](docs/usuario/certificados.md), [FAQ](docs/usuario/preguntas-frecuentes.md) |
| macOS deployment | [macOS installation](docs/tecnica/instalacion-macos.md) |
| Apple Silicon packaging | [ARM64 macOS packaging guide](docs/tecnica/empaquetado-macos-arm64.md) |
| Windows planning | [Windows installation and validation](docs/tecnica/instalacion-windows.md) |
| Maintainers | [Architecture overview](docs/tecnica/arquitectura.md) |

## Release-artifact caution

Do not treat a locally generated DMG as an institutional release artifact without validation. The current macOS procedure produces an ARM64 DMG for Apple Silicon and does not include Apple Developer ID signing or notarization. Follow the [ARM64 macOS packaging guide](docs/tecnica/empaquetado-macos-arm64.md) for the exact build, signing, test, and SHA-256 steps.

## Support boundaries

- The application does not bundle token middleware. Install and validate the appropriate vendor middleware separately.
- Do not redistribute PKCS#11 libraries whose provenance or redistribution rights have not been confirmed.
- The documented `Ver ubicación` action is currently implemented for macOS Finder; verify platform behavior before promising equivalent support elsewhere.
