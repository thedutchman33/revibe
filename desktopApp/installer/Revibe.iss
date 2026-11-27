; Revibe per-user Windows installer.
; Build the app image first:
;   ./gradlew :desktopApp:createDistributable
; Then compile with:
;   ISCC.exe /DAppVersion=0.10.0 Revibe.iss

#ifndef AppVersion
  #define AppVersion "0.10.0"
#endif

#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\Revibe"
#endif

#ifndef OutputDir
  #define OutputDir "..\build\installer"
#endif

#ifndef OutputBaseFilename
  #define OutputBaseFilename "Revibe-" + AppVersion + "-Setup"
#endif

[Setup]
AppId={{96ABEE54-A741-4118-B1CD-23080DB60D33}
AppName=Revibe
AppVersion={#AppVersion}
AppVerName=Revibe {#AppVersion}
AppPublisher=Revibe
AppPublisherURL=https://github.com/thedutchman33/revibe
AppSupportURL=https://github.com/thedutchman33/revibe/issues
AppUpdatesURL=https://github.com/thedutchman33/revibe/releases
DefaultDirName={localappdata}\Programs\Revibe
DefaultGroupName=Revibe
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir={#OutputDir}
OutputBaseFilename={#OutputBaseFilename}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
SetupIconFile=..\icons\revibe.ico
UninstallDisplayName=Revibe
UninstallDisplayIcon={app}\Revibe.exe
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
VersionInfoVersion={#AppVersion}
VersionInfoDescription=Revibe installer
VersionInfoProductName=Revibe
VersionInfoProductVersion={#AppVersion}
VersionInfoCompany=Revibe

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Revibe"; Filename: "{app}\Revibe.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\Revibe"; Filename: "{app}\Revibe.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\Revibe.exe"; Description: "{cm:LaunchProgram,Revibe}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; JNativeHook and other libraries can extract files beside the app at runtime.
Type: filesandordirs; Name: "{app}"
