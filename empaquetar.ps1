# Empaqueta el agente de impresion como una aplicacion autocontenida de Windows.
#
# El resultado incluye su propio runtime de Java: la clinica no instala Java ni
# tiene que actualizarlo nunca, que es justamente parte de por que elegimos esta
# arquitectura (ver docs/plan-impresion-directa-tickets.md).
#
# Uso:
#   .\empaquetar.ps1              -> app-image + zip (no necesita nada extra)
#   .\empaquetar.ps1 -Msi         -> ademas un instalador .msi (requiere WiX 3)

param([switch]$Msi)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot
$version = "0.1.0"
$nombre = "VetControlAgente"

Push-Location $raiz
try {
    Write-Host "== 1/4 Compilando y testeando ==" -ForegroundColor Cyan
    & "$raiz\mvnw.cmd" -q clean package
    if ($LASTEXITCODE -ne 0) { throw "El build de Maven fallo (exit $LASTEXITCODE)" }

    $jar = "$raiz\target\vetcontrol-agente.jar"
    if (-not (Test-Path $jar)) { throw "No se genero $jar" }
    Write-Host ("   JAR: {0:N1} MB" -f ((Get-Item $jar).Length / 1MB))

    # jpackage copia TODO lo que encuentra en --input, asi que se le pasa un
    # directorio limpio con el jar shadeado y nada mas.
    Write-Host "`n== 2/4 Preparando entrada limpia ==" -ForegroundColor Cyan
    $staging = "$raiz\target\jpackage-input"
    $dist = "$raiz\dist"
    Remove-Item $staging, "$dist\$nombre" -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Path $staging -Force | Out-Null
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    Copy-Item $jar $staging

    $jpackage = Join-Path (Split-Path (Get-Command javac).Source) "jpackage.exe"
    if (-not (Test-Path $jpackage)) { throw "jpackage no esta en el JDK ($jpackage)" }

    # SIN --win-console: el agente ya no necesita consola. Si no esta vinculado
    # abre su ventana, y mientras corre vive en el icono de la bandeja. Una
    # consola negra abierta todo el dia en el mostrador solo asusta.
    Write-Host "`n== 3/4 Generando la aplicacion ==" -ForegroundColor Cyan
    & $jpackage `
        --type app-image `
        --name $nombre `
        --app-version $version `
        --input $staging `
        --main-jar (Split-Path $jar -Leaf) `
        --main-class py.com.vetcontrol.agente.AgenteMain `
        --dest $dist `
        --vendor "VetControl" `
        --description "Agente de impresion de tickets de VetControl"
    if ($LASTEXITCODE -ne 0) { throw "jpackage fallo (exit $LASTEXITCODE)" }

    Write-Host "`n== 4/4 Comprimiendo ==" -ForegroundColor Cyan
    $zip = "$dist\$nombre-$version.zip"
    Remove-Item $zip -Force -ErrorAction SilentlyContinue
    Compress-Archive -Path "$dist\$nombre" -DestinationPath $zip
    Write-Host ("   {0}  ({1:N1} MB)" -f $zip, ((Get-Item $zip).Length / 1MB))

    if ($Msi) {
        if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
            Write-Warning "WiX 3 no esta instalado: se omite el .msi. Instalalo desde https://wixtoolset.org/"
        } else {
            Write-Host "`n== extra: instalador MSI ==" -ForegroundColor Cyan
            & $jpackage `
                --type msi `
                --name $nombre `
                --app-version $version `
                --input $staging `
                --main-jar (Split-Path $jar -Leaf) `
                --main-class py.com.vetcontrol.agente.AgenteMain `
                --dest $dist `
                --win-dir-chooser --win-menu `
                --vendor "VetControl"
        }
    }

    Write-Host "`nListo. Ejecutable: $dist\$nombre\$nombre.exe" -ForegroundColor Green
    Write-Host "Runbook de instalacion: docs/runbook-agente-impresion.md"
} finally {
    Pop-Location
}
