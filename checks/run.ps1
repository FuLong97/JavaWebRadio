$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot
$javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
$javacCommand = Join-Path (Split-Path $javaCommand) 'javac.exe'
$jarCommand = Join-Path (Split-Path $javaCommand) 'jar.exe'
& .\mvnw.cmd '-DskipTests' compile dependency:copy-dependencies '-DoutputDirectory=target/check-dependencies'
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
$checkRoot = Join-Path $repoRoot 'target/verification'
New-Item -ItemType Directory -Force $checkRoot | Out-Null
& $javacCommand -encoding UTF-8 -cp 'target/classes;target/check-dependencies/*' -d "$checkRoot/classes" checks/Verification.java
if ($LASTEXITCODE -ne 0) { throw 'Verification compilation failed' }
& $jarCommand --create --file "$checkRoot/verification.jar" -C "$checkRoot/classes" . -C target/classes .
if ($LASTEXITCODE -ne 0) { throw 'Verification packaging failed' }
$runRoot = Join-Path $checkRoot ([Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force "$runRoot/home" | Out-Null
Set-Content -LiteralPath "$runRoot/favorites.txt" -Value 'Jazz [MP3] - https://example.com/live' -Encoding Ascii
Push-Location $runRoot
try {
    & $javaCommand "-Duser.home=$runRoot/home" -cp "$checkRoot/verification.jar;$repoRoot/target/check-dependencies/*" org.example.Verification
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed' }
} finally { Pop-Location }
