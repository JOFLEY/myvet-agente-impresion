# Politica de privacidad

> **In English:** the agent stores a per-workstation token and the printing
> queue locally, and sends only the computer name, its printer names and the
> agent version to the MyVet server of the clinic that installed it. It does not
> read user files, does not open inbound ports, has no telemetry and sends
> nothing to the authors of this software or to any third party.

Ultima actualizacion: 2026-09-07.

## Que guarda en la PC

En `%LOCALAPPDATA%\MyVetAgente`:

- El **token del puesto**, que identifica a esa PC ante el servidor de su
  clinica.
- El **identificador y el nombre del puesto**, y la impresora elegida.
- Una **bitacora** (`agente.log`) con la actividad reciente: vinculacion,
  trabajos impresos y errores.
- Los **PDF de los tickets** mientras se imprimen; no quedan almacenados.

## Que envia, y a donde

Unicamente al servidor de MyVet de **la clinica que lo instalo** (por defecto
`https://myvet.serfley.com`, configurable), siempre por HTTPS:

- El **nombre de la PC** (hostname) y la **version del agente**.
- La **lista de nombres de impresoras** instaladas, para que el usuario pueda
  elegir cual es la de tickets.
- El **resultado de cada impresion** (exito, o el mensaje de error).

## Que NO hace

- No lee archivos ni documentos del usuario.
- No abre puertos entrantes: toda la comunicacion es saliente.
- No tiene telemetria, analitica ni publicidad.
- **No envia nada a los autores de este software** ni a ningun tercero: el
  unico destino es el servidor de la propia clinica.

## Datos personales

El agente no pide ni almacena datos personales de quien lo usa. Los tickets que
imprime pueden contener datos del cliente de la clinica (nombre, documento);
esos datos vienen del servidor de la clinica, se usan para imprimir y no se
guardan.

## Como borrar todo

Desinstalar "MyVetAgente" desde "Agregar o quitar programas" y borrar la carpeta
`%LOCALAPPDATA%\MyVetAgente`. Conviene ademas eliminar el puesto desde
Configuraciones > Impresion en MyVet, para invalidar su token.

## Contacto

Por cualquier consulta sobre esta politica: <jofleyns@gmail.com>
