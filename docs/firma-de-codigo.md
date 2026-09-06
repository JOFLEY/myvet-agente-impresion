# Firma de codigo

## Por que

El instalador sin firmar lo trata como sospechoso cualquier antivirus por pura
falta de reputacion. Sintomas reales medidos el 2026-09-06 en una PC con
Kaspersky activo (Defender en modo pasivo):

- El antivirus lo marca como archivo malicioso al descargarlo.
- El ejecutable instalado **no arranca** con doble clic, y si arranca al
  elegir "Ejecutar como administrador".

Ese segundo sintoma **no es un problema de permisos de Windows**, y conviene no
confundirse: el instalador usa `--win-per-user-install` (instala en
`%LOCALAPPDATA%`, sin UAC) y el ejecutable declara `requestedExecutionLevel =
asInvoker`, o sea que nunca le pide elevacion al sistema. Lo comprobamos ademas
sin Mark-of-the-Web y sin flags de compatibilidad. Lo que queda es el
antivirus: bloquea por reputacion, y la reputacion arranca con la firma.

## Que se eligio y por que

| Opcion | Estado |
| --- | --- |
| **SignPath Foundation** (gratis, OSS) | **Elegida.** |
| Microsoft Artifact Signing (USD 9,99/mes) | Descartada: no esta disponible para Paraguay (organizaciones solo en EEUU, Canada, UE, UK, AU, NZ, JP, KR, SG, CH, NO, IL; individuos solo EEUU/Canada). |
| Certum Open Source (~USD 50-70) | Descartada: prohibe firmar software distribuido comercialmente y revoca el certificado si se hace. |
| Certificado OV comercial (~USD 200-400/ano) | Alternativa si SignPath rechaza el proyecto. |
| Certificado EV (~USD 400-600/ano) | Lo unico que limpia SmartScreen desde la primera descarga. |

Con SignPath el certificado es de la fundacion, asi que **Windows muestra
"SignPath Foundation" como editor**, no "MyVet". Y ojo: un certificado OV
mejora mucho el trato de los antivirus, pero SmartScreen puede seguir avisando
hasta que el binario acumule reputacion. Solo un EV lo evita desde el dia uno.

## Requisitos que impone SignPath (y como los cumple este repo)

| Requisito | Como se cumple |
| --- | --- |
| Licencia OSI, sin dual-licensing comercial | MIT ([LICENSE](../LICENSE)) |
| Ningun componente propietario | Dependencias: PDFBox y Jackson, ambas Apache-2.0 |
| Codigo publico y proyecto activo | Este repositorio |
| Funcionalidad documentada en la pagina de descarga | [README](../README.md) y las notas de cada release |
| Build automatizada y verificable desde el codigo | `.github/workflows/build.yml`, que corre el mismo `empaquetar.ps1` que se usa a mano |
| Ya publicado en la forma que se va a firmar | Release del instalador sin firmar |
| MFA en GitHub para todo el equipo | A cargo del duenio del repositorio |

## Como aplicar

1. Publicar el repositorio y un primer release con el instalador sin firmar.
2. Activar MFA en la cuenta de GitHub.
3. Solicitar el alta en <https://signpath.org/apply> con la URL del repositorio,
   la licencia, la descripcion del agente y el enlace al workflow de build.
4. Al aprobar, SignPath entrega el `organization-id`, el `project-slug` y el
   `signing-policy-slug`, y se genera un API token.

## Que cargar en GitHub cuando aprueben

En **Settings > Secrets and variables > Actions**:

- Variables: `SIGNPATH_ORGANIZATION_ID`, `SIGNPATH_PROJECT_SLUG`,
  `SIGNPATH_SIGNING_POLICY_SLUG`.
- Secreto: `SIGNPATH_API_TOKEN`.

El job `firmar` se activa solo cuando `SIGNPATH_PROJECT_SLUG` existe. Hasta
entonces el release sale sin firmar en vez de fallar.

## Publicar una version

```powershell
git tag v0.2.0
git push origin v0.2.0
```

La CI compila, firma (si ya esta aprobado) y publica el release. Despues hay
que subir ese `.exe` al VPS como `AGENTE_INSTALADOR_PATH` para que MyVet lo
entregue desde "Descargar agente".

## Verificar la firma

```powershell
Get-AuthenticodeSignature .\MyVetAgente-0.2.0.exe | Format-List Status, SignerCertificate
```

`Status` tiene que decir `Valid`. Sin firma dice `NotSigned`.

## Mientras tanto: reportar el falso positivo

Es gratis y ayuda aunque despues se firme. Kaspersky recibe muestras en
<https://opentip.kaspersky.com/> y Microsoft en
<https://www.microsoft.com/en-us/wdsi/filesubmission>.
