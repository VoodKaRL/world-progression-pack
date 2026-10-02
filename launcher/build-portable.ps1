$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $here 'release\win-unpacked'
$target = Join-Path $here 'release\World-Progression-Launcher-portable.zip'
if (-not (Test-Path (Join-Path $source 'World Progression Launcher.exe'))) {
  throw 'Primero ejecuta npm run dist:dir para generar la aplicación Windows.'
}
if (Test-Path $target) { Remove-Item -LiteralPath $target }
Compress-Archive -Path (Join-Path $source '*') -DestinationPath $target -CompressionLevel Optimal
Write-Output "Portable ZIP: $target"
