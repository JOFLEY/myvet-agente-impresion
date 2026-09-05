# Empaqueta el agente de impresion como un INSTALADOR de Windows de un solo
# archivo.
#
# El resultado incluye su propio runtime de Java: la clinica no instala Java ni
# tiene que actualizarlo nunca, que es justamente parte de por que elegimos esta
# arquitectura (ver docs/plan-impresion-directa-tickets.md).
#
# Un solo .exe y no un zip: descomprimir era el paso donde se perdia la gente, y
# ademas tiene una trampa silenciosa -- si alguien hace doble clic en el .exe
# DENTRO del zip, Windows lo extrae a una carpeta temporal y el arranque
# automatico queda apuntando a una carpeta que se borra sola.
#
# El instalador es GENERICO: el mismo archivo para todas las clinicas. La PC se
# vincula despues, sola, abriendo MyVet en el navegador.
#
# Uso:
#   .\empaquetar.ps1              -> instalador .exe (y app-image para probar)
#   .\empaquetar.ps1 -SoloImagen  -> solo el app-image, sin instalador
#
# Requisito del instalador: WiX 3 (candle.exe/light.exe) en el PATH. No hace
# falta instalarlo en el sistema: alcanza con descomprimir wix314-binaries.zip
# (https://github.com/wixtoolset/wix3/releases) y apuntar -Wix a esa carpeta.

param(
    [switch]$SoloImagen,
    [string]$Wix = "C:\Tools\wix314"
)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot
$version = "0.1.0"
$nombre = "MyVetAgente"

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
        --vendor "MyVet" `
        --description "Agente de impresion de tickets de MyVet"
    if ($LASTEXITCODE -ne 0) { throw "jpackage fallo (exit $LASTEXITCODE)" }

    if ($SoloImagen) {
        Write-Host "`nListo (solo imagen). Ejecutable: $dist\$nombre\$nombre.exe" -ForegroundColor Green
        return
    }

    Write-Host "`n== 4/4 Generando el instalador ==" -ForegroundColor Cyan
    if (Test-Path $Wix) { $env:PATH = "$Wix;$env:PATH" }
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        throw ("WiX 3 no esta disponible. Descarga wix314-binaries.zip de " +
            "https://github.com/wixtoolset/wix3/releases, descomprimilo en $Wix " +
            "y volve a correr. O usa -SoloImagen si solo queres probar el agente.")
    }

    $instalador = "$dist\$nombre-$version.exe"
    Remove-Item $instalador -Force -ErrorAction SilentlyContinue

    # --win-per-user-install: instala en el perfil del usuario y por eso NO pide
    # permisos de administrador. Es deliberado: quien atiende un mostrador rara
    # vez es administrador de esa PC, y ademas el agente TIENE que correr como el
    # usuario logueado (un servicio en la Sesion 0 no ve las impresoras de red
    # mapeadas por usuario).
    #
    # Sin --win-dir-chooser: elegir carpeta es una decision que el usuario no
    # tiene con que tomar. Se instala donde corresponde y listo.
    & $jpackage `
        --type exe `
        --name $nombre `
        --app-version $version `
        --app-image "$dist\$nombre" `
        --dest $dist `
        --vendor "MyVet" `
        --description "Agente de impresion de tickets de MyVet" `
        --win-per-user-install `
        --win-shortcut `
        --win-menu --win-menu-group "MyVet"
    if ($LASTEXITCODE -ne 0) { throw "jpackage fallo generando el instalador (exit $LASTEXITCODE)" }
    if (-not (Test-Path $instalador)) { throw "No se genero $instalador" }

    Write-Host ("   {0}  ({1:N1} MB)" -f $instalador, ((Get-Item $instalador).Length / 1MB))
    Write-Host "`nListo. Instalador: $instalador" -ForegroundColor Green
    Write-Host "Subilo al VPS como AGENTE_INSTALADOR_PATH (ver docs/runbook-agente-impresion.md)."
} finally {
    Pop-Location
}
