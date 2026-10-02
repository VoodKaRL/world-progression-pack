$ErrorActionPreference = 'Stop'
# Configure CSC_LINK and CSC_KEY_PASSWORD in the build environment. Never
# put certificates or passwords in this repository or release assets.
if (-not $env:CSC_LINK) {
  throw 'Falta un certificado de firma de código confiable. Configura CSC_LINK antes de construir. No se generará otro instalador sin firma.'
}
Push-Location $PSScriptRoot
try {
  & node 'node_modules/electron-builder/cli.js' --win nsis '--config.forceCodeSigning=true' '--config.directories.output=release-signed'
  if ($LASTEXITCODE -ne 0) { throw 'La compilación firmada falló.' }
  $version = (Get-Content package.json -Raw | ConvertFrom-Json).version
  $installer = Join-Path $PSScriptRoot "release-signed/Verdania-Launcher-$version-Setup.exe"
  $signature = Get-AuthenticodeSignature -LiteralPath $installer
  if ($signature.Status -ne 'Valid') { throw "El instalador no tiene una firma confiable válida: $($signature.Status)" }
  Write-Output "Instalador firmado: $installer"
  Write-Output "Editor: $($signature.SignerCertificate.Subject)"
} finally { Pop-Location }
