; FirmaUNA Desktop - Inno Setup installer script
;
; Compile with ISCC.exe from the Inno Setup 6 command-line compiler.
;
;   ISCC.exe firmauna.iss /DVERSION=2.0.0 \
;     /DAPP_IMG="D:\repo\firmauna-desktop\target\windows-package\FirmaUNA" \
;     /DOUTPUT="D:\repo\firmauna-desktop\target"

#ifndef VERSION
    #define VERSION "2.0.0"
#endif

#ifndef APP_IMG
    #error "APP_IMG define must point to the jpackage app-image directory (FirmaUNA\)."
#endif

#ifndef OUTPUT
    #define OUTPUT "."
#endif

#define APP_NAME "FirmaUNA"
#define APP_EXE_NAME "FirmaUNA.exe"
#define PUBLISHER "Universidad Nacional del Altiplano"
#define APP_DESCRIPTION "Firma digital de documentos PDF"

[Setup]
AppId={{7E2F9C0B-3A1A-4C5E-9B7A-0D1F2E3A4B5C}
AppName={#APP_NAME}
AppVersion={#VERSION}
AppVerName={#APP_NAME} {#VERSION}
AppPublisher={#PUBLISHER}
AppPublisherURL=https://www.unap.edu.pe
AppComments={#APP_DESCRIPTION}
DefaultDirName=C:\{#APP_NAME}
DefaultGroupName={#APP_NAME}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#APP_NAME}\{#APP_EXE_NAME}
UninstallDisplayName={#APP_NAME}
OutputBaseFilename=FirmaUNA-{#VERSION}-setup
OutputDir={#OUTPUT}
SetupIconFile={#SOURCEPATH}..\macos\IconGroup32512.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CreateUninstallRegKey=yes
PrivilegesRequired=admin
LanguageDetectionMethod=uilanguage
CloseApplications=yes

; Persist the previous install directory across upgrades.
UsePreviousAppDir=yes

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Copy the whole jpackage app-image folder recursively into {app}\FirmaUNA
Source: "{#APP_IMG}\*"; DestDir: "{app}\{#APP_NAME}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#APP_NAME}"; Filename: "{app}\{#APP_NAME}\{#APP_EXE_NAME}"
Name: "{group}\{cm:UninstallProgram,{#APP_NAME}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#APP_NAME}"; Filename: "{app}\{#APP_NAME}\{#APP_EXE_NAME}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#APP_NAME}\{#APP_EXE_NAME}"; Description: "{cm:LaunchProgram,{#APP_NAME}}"; Flags: nowait postinstall skipifsilent
