<#
Builds ACH Studio (PowerShell version of build.sh).

  .\build.ps1                 runnable jar           -> target\ach-studio.jar   (needs Java 21+ to run)
  .\build.ps1 app             self-contained app     -> dist\ach-studio\        (bundles its own Java runtime)
  .\build.ps1 installer       native installer       -> dist\                   (.msi on Windows, .dmg on macOS, .deb/.rpm on Linux)

Options:
  -SkipTests                  skip the unit tests

The jar contains JavaFX for the platform it was built on, so build on each OS you want to ship for.
If scripts are blocked, run:  powershell -ExecutionPolicy Bypass -File .\build.ps1 [args]
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('jar', 'app', 'installer')]
    [string]$Target = 'jar',
    [switch]$SkipTests,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Content $PSCommandPath | Select-Object -Skip 1 -First 11 | Write-Host
    exit 0
}

Set-Location $PSScriptRoot

# Windows PowerShell 5.1 has no $IsWindows/$IsMacOS; it only runs on Windows.
$onWindows = ($PSVersionTable.PSEdition -eq 'Desktop') -or $IsWindows
$onMac = (-not $onWindows) -and $IsMacOS

function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        Write-Error "$Exe failed with exit code $LASTEXITCODE"
    }
}

$name = 'ach-studio'
$jar = "target/$name.jar"
$version = ([xml](Get-Content pom.xml -Raw)).project.version
$appVersion = ($version -split '-')[0]   # jpackage needs a plain numeric version (1.0-SNAPSHOT -> 1.0)

Write-Host "==> Building $name $version ($Target)"
$mvnArgs = @('-B', 'clean', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }
$mvnw = if ($onWindows) { '.\mvnw.cmd' } else { './mvnw' }
Invoke-Native $mvnw $mvnArgs

if (-not (Test-Path $jar)) {
    Write-Error "Build did not produce $jar"
}

if ($Target -eq 'jar') {
    Write-Host ''
    Write-Host "Done: $jar"
    Write-Host "Run it with:  java -jar $jar [file.ach ...]"
    exit 0
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Write-Error "jpackage not found. It ships with JDK 14+; put the JDK's bin directory on PATH."
}

# jpackage bundles everything in --input: the fat jar plus license files.
$inputDir = 'target/jpackage-input'
if (Test-Path $inputDir) { Remove-Item $inputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $inputDir, 'dist' | Out-Null
Copy-Item $jar $inputDir
# license and notices travel with the app (they end up next to the jar in the app image)
Copy-Item -Recurse 'LICENSE', 'NOTICE', 'THIRD-PARTY-NOTICES.md', 'licenses' $inputDir

# Java modules JavaFX and the app need when run from the classpath.
$modules = 'java.base,java.desktop,java.logging,java.prefs,java.scripting,java.xml,jdk.unsupported,jdk.charsets'

$common = @(
    '--name', $name,
    '--app-version', $appVersion,
    '--vendor', 'ACH Studio',
    '--description', 'Read, build and present NACHA ACH files',
    '--input', $inputDir,
    '--main-jar', "$name.jar",
    '--main-class', 'com.fx.ach.Launcher',
    '--add-modules', $modules,
    '--jlink-options', '--strip-debug --no-man-pages --no-header-files',
    '--dest', 'dist'
)

if ($Target -eq 'app') {
    if (Test-Path "dist/$name") { Remove-Item "dist/$name" -Recurse -Force }
    if (Test-Path "dist/$name.app") { Remove-Item "dist/$name.app" -Recurse -Force }
    Invoke-Native 'jpackage' (@('--type', 'app-image') + $common)
    Write-Host ''
    if ($onWindows) { Write-Host "Done: dist\$name\$name.exe" }
    elseif ($onMac) { Write-Host "Done: dist/$name.app" }
    else { Write-Host "Done: dist/$name/bin/$name" }
    exit 0
}

# installer
if ($onWindows) {
    $type = 'msi'   # needs WiX Toolset on PATH
    $extra = @('--win-menu', '--win-shortcut', '--win-dir-chooser')
} elseif ($onMac) {
    $type = 'dmg'
    $extra = @()
} else {
    if (Get-Command dpkg-deb -ErrorAction SilentlyContinue) { $type = 'deb' }
    elseif (Get-Command rpmbuild -ErrorAction SilentlyContinue) { $type = 'rpm' }
    else { Write-Error 'Need dpkg-deb (for .deb) or rpmbuild (for .rpm) to build a Linux installer.' }
    $extra = @('--linux-shortcut', '--linux-menu-group', 'Office')
}
Invoke-Native 'jpackage' (@('--type', $type) + $common + $extra + @('--license-file', 'LICENSE'))
Write-Host ''
$installer = Get-ChildItem "dist/*.$type" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host "Done: $($installer.FullName)"
