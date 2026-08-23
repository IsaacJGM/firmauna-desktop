# macOS installation and deployment

This document describes the current macOS deployment boundary. It is not a substitute for the packaging procedure.

## Current status

| Area | Current repository status |
| --- | --- |
| Target architecture | Apple Silicon (ARM64) package is documented. |
| Package trust | Local ad-hoc signing only; not Apple Developer ID signed or notarized. |
| User warning | Gatekeeper may warn when opening the application. |
| Token middleware | Not bundled with the application. |

## Deployment prerequisites

- Use a validated ARM64 DMG produced on an Apple Silicon Mac.
- Provide the matching SHA-256 checksum with the artifact and verify it before institutional distribution.
- Install and validate vendor-provided token middleware separately.
- Test PDF loading, stamp layouts, PIN authentication, certificate selection, signing, and automatic save on the target environment.

The repository currently configures PKCS#11 for the Bit4id tokenME FIPS v3 library path on macOS. This is an implementation boundary, not a guarantee that other token middleware or libraries will work. Do not distribute unverified PKCS#11 libraries or libraries without confirmed redistribution rights.

## Installation expectations

The DMG is intended for Apple Silicon Macs. Dragging the application to `/Applications` follows the standard DMG layout, but it does not remove the ad-hoc signing limitation. Because the application is not notarized, users may encounter Gatekeeper warnings. Distribution owners must decide and document the approved institutional handling for those warnings.

## Packaging procedure

Use the [macOS ARM64 packaging guide](empaquetado-macos-arm64.md) for exact commands, icon usage, signing verification, DMG generation, and SHA-256 generation. Do not duplicate or improvise those commands here.
