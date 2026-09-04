# Hace que el agente arranque solo al iniciar sesion en esta PC.
#
# Se instala en el arranque del USUARIO (carpeta Inicio) y NO como servicio de
# Windows, a proposito: un servicio corre en la Sesion 0 y desde ahi las
# impresoras de red mapeadas por usuario NO son visibles. Es un clasico que
# hace perder horas cuando la termica es de red.
#
# Uso (en la PC del mostrador, sin permisos de administrador):
#   .\instalar-inicio-automatico.ps1
#   .\instalar-inicio-automatico.ps1 -Quitar

param([switch]$Quitar)

$ErrorActionPreference = "Stop"

$exe = Join-Path $PSScriptRoot "VetControlAgente.exe"
$inicio = [Environment]::GetFolderPath('Startup')
$acceso = Join-Path $inicio "VetControl Agente de Impresion.lnk"

if ($Quitar) {
    if (Test-Path $acceso) {
        Remove-Item $acceso -Force
        Write-Host "Listo: el agente ya no arranca solo." -ForegroundColor Green
    } else {
        Write-Host "No estaba configurado para arrancar solo."
    }
    return
}

if (-not (Test-Path $exe)) {
    throw "No se encontro $exe. Corre este script desde la carpeta del agente."
}

$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut($acceso)
$lnk.TargetPath = $exe
$lnk.WorkingDirectory = $PSScriptRoot
$lnk.Description = "Agente de impresion de tickets de VetControl"
$lnk.Save()

Write-Host "Listo: el agente va a arrancar solo cada vez que inicies sesion." -ForegroundColor Green
Write-Host "Acceso directo: $acceso"
Write-Host ""
Write-Host "Si todavia no lo vinculaste a un puesto, hacelo una sola vez con:"
Write-Host "  .\VetControlAgente.exe --emparejar CODIGO"
