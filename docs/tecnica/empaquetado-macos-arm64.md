# macOS ARM64 packaging

Build the ARM64 package on an Apple Silicon Mac. Do not build the ARM64 runtime on an Intel Mac.

> **Current release caveat:** this procedure uses local ad-hoc signing. It is not Apple Developer ID signing and does not notarize the application.

## Requirements

- macOS on Apple Silicon
- Git
- Maven
- JDK 25 for ARM64
- JavaFX 21.0.6 ARM64 artifacts

Verify the machine and Java architecture:

```bash
uname -m
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
java -version
file "$JAVA_HOME/bin/java"
```

`uname -m` and the Java executable must report `arm64`.

## Build the application

Clone the repository:

```bash
cd ~/Documents
git clone git@github.com:IsaacJGM/firmauna-desktop.git
cd firmauna-desktop
```

Close any running FirmaUNA instance before cleaning the build:

```bash
mvn clean package -DskipTests
```

Expected result:

```text
BUILD SUCCESS
```

## Create the ARM64 runtime

Download JavaFX ARM64 artifacts:

```bash
for module in javafx-base javafx-controls javafx-graphics javafx-swing; do
  mvn dependency:get \
    -Dartifact="org.openjfx:${module}:21.0.6:jar:mac-aarch64"
done
```

Create the runtime:

```bash
JAVAFX_JARS=$(find "$HOME/.m2/repository/org/openjfx" \
  -name "*-21.0.6-mac-aarch64.jar" | tr '\n' ':')

printf '%s\n' "$JAVAFX_JARS"

rm -rf target/runtime-fx target/app target/dmg-staging

"$JAVA_HOME/bin/jlink" \
  --module-path "$JAVAFX_JARS" \
  --add-modules java.base,java.desktop,java.logging,java.naming,javafx.base,javafx.controls,javafx.graphics,javafx.swing,jdk.crypto.cryptoki \
  --output target/runtime-fx \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress zip-6
```

## Create the application image

The repository icon is `packaging/macos/FirmaUNA.icns`.

```bash
jpackage \
  --type app-image \
  --name FirmaUNA \
  --app-version 2.1.0 \
  --input target \
  --main-jar firmauna-desktop-2.0.0.jar \
  --main-class unap.oti.firmauna.Launcher \
  --vendor "OTI UNAP" \
  --description "Firma digital PDF - UNA Puno" \
  --runtime-image "$(pwd)/target/runtime-fx" \
  --java-options "--add-modules=jdk.crypto.cryptoki" \
  --java-options "--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11=ALL-UNNAMED" \
  --icon packaging/macos/FirmaUNA.icns \
  --mac-package-name FirmaUNA \
  --mac-app-category public.app-category.utilities \
  --dest target/app
```

Install the launcher:

```bash
printf '%s\n' \
'#!/bin/bash' \
'DIR="$(cd "$(dirname "$0")/.." && pwd)"' \
'RUNTIME="$DIR/app/runtime-fx"' \
'APP="$DIR/app/firmauna-desktop-2.0.0.jar"' \
'exec "$RUNTIME/bin/java" --enable-native-access=javafx.graphics --add-modules=jdk.crypto.cryptoki --add-exports=jdk.crypto.cryptoki/sun.security.pkcs11=ALL-UNNAMED -cp "$APP" unap.oti.firmauna.Launcher' \
> target/app/FirmaUNA.app/Contents/MacOS/FirmaUNA

chmod 755 target/app/FirmaUNA.app/Contents/MacOS/FirmaUNA
```

Verify the bundled runtime:

```bash
file target/app/FirmaUNA.app/Contents/app/runtime-fx/bin/java
```

Expected output includes:

```text
Mach-O 64-bit executable arm64
```

## Sign, test, and package

Apply and verify local ad-hoc signing:

```bash
codesign --force --deep --sign - target/app/FirmaUNA.app
codesign --verify --deep --strict --verbose=2 target/app/FirmaUNA.app
```

Open the application:

```bash
open target/app/FirmaUNA.app
```

Before creating the DMG, test PDF loading, page navigation, horizontal and vertical layouts, preview positioning, token authentication, signing, and saving.

Create the DMG:

```bash
mkdir -p target/dmg-staging
cp -R target/app/FirmaUNA.app target/dmg-staging/
ln -s /Applications target/dmg-staging/Applications

hdiutil create \
  -volname "FirmaUNA" \
  -srcfolder target/dmg-staging \
  -ov \
  -format UDZO \
  target/FirmaUNA-v01-arm64.dmg
```

Generate the SHA-256 checksum:

```bash
shasum -a 256 target/FirmaUNA-v01-arm64.dmg \
  | tee target/FirmaUNA-v01-arm64.dmg.sha256
```

Publish both the DMG and its checksum only after the validation above. The DMG targets Apple Silicon Macs (M1/M2/M3); macOS may show a Gatekeeper warning because the application is not notarized.
