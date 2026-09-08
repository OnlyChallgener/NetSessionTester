[Setup]
AppName=NetSessionTester
AppVersion=1.0.22
AppPublisher=OnlyChallgener
AppPublisherURL=https://github.com/OnlyChallgener/NetSessionTester
DefaultDirName={autopf}\NetSessionTester
DefaultGroupName=NetSessionTester
UninstallDisplayIcon={app}\NetSessionTester.exe
SetupIconFile=package-windows\icon.ico
Compression=lzma2
SolidCompression=yes
OutputDir=.
OutputBaseFilename=NetSessionTester-Windows-x64-Setup
ArchitecturesInstallIn64BitMode=x64
WizardStyle=modern

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "package-windows\NetSessionTester.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "package-windows\README.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "package-windows\icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\NetSessionTester"; Filename: "{app}\NetSessionTester.exe"; IconFilename: "{app}\icon.ico"
Name: "{group}\Uninstall NetSessionTester"; Filename: "{uninstallexe}"
Name: "{autodesktop}\NetSessionTester"; Filename: "{app}\NetSessionTester.exe"; IconFilename: "{app}\icon.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\NetSessionTester.exe"; Description: "Launch NetSessionTester"; Flags: postinstall nowait skipifsilent
