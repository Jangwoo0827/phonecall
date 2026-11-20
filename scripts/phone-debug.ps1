# Builds the "SuperDialer Dev" debug APK (installs next to the real app) and copies it to the Desktop.
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
Set-Location (Split-Path $PSScriptRoot -Parent)
.\gradlew.bat assembleDebug -PphoneDebug
if ($LASTEXITCODE -eq 0) {
    Copy-Item appuild\outputspk\debugpp-debug.apk "$env:USERPROFILE\Desktop\SuperDialer-dev.apk" -Force
    Write-Host "Done: Desktop\SuperDialer-dev.apk"
}
