# MyVet Agente de impresion

Agente de escritorio para Windows que imprime tickets de venta en la impresora
termica del mostrador, sin dialogos ni pasos manuales.

> **In English:** desktop agent for Windows that prints sales receipts on the
> counter's thermal printer. It polls a REST API over an outbound HTTPS
> connection, downloads the receipt as a PDF and sends it to the printer the
> user chose. No inbound ports, no browser print dialog. Built with Java 17 and
> packaged with `jpackage` into a self-contained per-user installer.

## Que problema resuelve

Un navegador no puede imprimir en una impresora concreta sin abrir el dialogo
de impresion, y menos elegir la termica de 80 mm en vez de la laser. En un
mostrador que cobra decenas de ventas por dia, ese dialogo es un estorbo.

Este agente corre en la PC del mostrador y hace de puente: la aplicacion web
encola el ticket, el agente lo baja y lo manda a la impresora elegida. El
usuario solo toca "Imprimir ticket".

## Como funciona

1. **Vinculacion.** Al instalarse, el agente levanta un servidor local efimero
   y abre el navegador para que el usuario elija a que puesto pertenece esa PC,
   usando la sesion que ya tiene abierta. Recibe un token propio del puesto.
2. **Espera.** Mantiene una peticion HTTPS **saliente** en long-poll contra la
   API. No abre puertos ni requiere configurar el router o el firewall.
3. **Impresion.** Cuando hay un trabajo, baja el PDF ya renderizado con el
   formato correcto (80 mm, 58 mm o A4) y lo imprime en la impresora que el
   usuario eligio para tickets. Despues reporta si salio bien o que fallo.
4. **Bandeja.** Vive en el area de notificacion. Se registra en el arranque
   automatico del usuario (clave `Run` de `HKCU`), no como servicio de Windows:
   un servicio corre en la Sesion 0 y desde ahi **no ve** las impresoras de red
   mapeadas por el usuario.

## Que datos maneja

- Un token del puesto y el PDF de cada ticket, guardados en
  `%LOCALAPPDATA%\MyVetAgente`.
- La lista de nombres de impresoras de la PC, que reporta para que el usuario
  pueda elegir cual es la de tickets.
- No lee archivos del usuario, no abre puertos entrantes y no envia nada a
  ningun destino fuera del servidor de su propia clinica.

## Instalacion

Descarga `MyVetAgente-<version>.exe` desde
[Releases](../../releases) y ejecutalo. Se instala **en el perfil del usuario**
(`%LOCALAPPDATA%\MyVetAgente`) y por eso **no pide permisos de administrador**.
Incluye su propio runtime de Java: no hay que instalar Java ni mantenerlo.

Al terminar, el agente abre el navegador para vincular la PC a un puesto.

## Compilar desde el codigo

Requisitos:

- JDK 17 o superior (aporta `jpackage`).
- [WiX Toolset 3.14](https://github.com/wixtoolset/wix3/releases) para generar
  el instalador. No hace falta instalarlo: alcanza con descomprimir
  `wix314-binaries.zip` en una carpeta y pasarla por parametro.

```powershell
.\empaquetar.ps1                       # instalador .exe en dist\
.\empaquetar.ps1 -SoloImagen           # solo la app, sin instalador
.\empaquetar.ps1 -Wix C:\Tools\wix314 -Version 0.2.0
```

El mismo script es el que corre la CI, asi que el binario publicado se
reproduce con un comando.

## Builds y firma de codigo

Cada release se compila en GitHub Actions desde este repositorio
(`.github/workflows/build.yml`) y el artefacto se envia a firmar sin pasar por
ninguna maquina personal.

**Code signing policy:** free code signing provided by
[SignPath.io](https://signpath.io/), certificate by
[SignPath Foundation](https://signpath.org/).

> Estado: la solicitud a SignPath Foundation esta en curso. Hasta que se
> apruebe, los instaladores publicados **no estan firmados** y Windows los va a
> tratar como de origen desconocido. Se puede verificar cual es cual con
> `Get-AuthenticodeSignature`.

Para comprobar que un instalador corresponde a este codigo, comparar su SHA-256
con el que imprime el log del build en GitHub Actions. **No** coincide con el de
una compilacion local: cada maquina empaqueta su propio JDK.

## Privacidad

Que guarda en la PC, que envia y a donde: [PRIVACY.md](PRIVACY.md). Resumen: el
unico destino de los datos es el servidor de la propia clinica; no hay
telemetria y los autores de este software no reciben nada.

## Licencia

MIT. Ver [LICENSE](LICENSE).
