[Setup]
AppName=NetSessionTester
AppVersion=1.0.22
AppPublisher=OnlyChallgener
AppPublisherURL=https://github.com/OnlyChallgener/NetSessionTester
DefaultDirName={autopf}\NetSessionTester
DefaultGroupName=NetSessionTester
UninstallDisplayIcon={app}\NetSessionTester.exe
SetupIconFile=..\assets\icon.ico
Compression=lzma2
SolidCompression=yes
OutputDir=.
OutputBaseFilename=NetSessionTester-Windows-x64-Setup
ArchitecturesInstallIn64BitMode=x64
WizardStyle=modern

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加快捷方式:"

[Files]
Source: "package-windows\NetSessionTester.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "package-windows\NetSessionTester.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "package-windows\README.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\assets\icon.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\NetSessionTester"; Filename: "{app}\NetSessionTester.exe"; IconFilename: "{app}\icon.ico"
Name: "{group}\卸载 NetSessionTester"; Filename: "{uninstallexe}"
Name: "{autodesktop}\NetSessionTester"; Filename: "{app}\NetSessionTester.exe"; IconFilename: "{app}\icon.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\NetSessionTester.exe"; Description: "启动 NetSessionTester"; Flags: postinstall nowait skipifsilent
