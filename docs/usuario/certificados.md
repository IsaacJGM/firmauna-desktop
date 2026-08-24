# Certificados y autorización

FirmaUNA obtiene certificados desde el token PKCS#11 en macOS o desde el almacén Personal de Windows. Siempre debe seleccionar un certificado utilizable; la forma de autorización depende del proveedor de su clave privada.

## Flujo seguro

1. En macOS, conecte el token y confirme que su middleware esté instalado. En Windows, confirme que el certificado esté en Personal y tenga clave privada.
2. En macOS, ingrese el PIN únicamente en FirmaUNA. En Windows, no ingrese el PIN en FirmaUNA.
3. Seleccione un certificado con estado **Válido**.
4. Confirme con **Firmar con este certificado** y complete la autorización nativa si Windows la muestra.

Puede cancelar en la ventana del PIN, del certificado o en la autorización de Windows. La cancelación no firma el PDF y cierra el proveedor utilizado por la aplicación.

## Estados de certificado

| Estado mostrado | Significado en la aplicación | Acción disponible |
| --- | --- | --- |
| **Válido** | Está dentro de su vigencia, usa RSA y permite firma digital. | Puede seleccionarse para firmar. |
| **Certificado vencido** | La fecha de vencimiento ya pasó según la hora actual del equipo. | Se muestra como referencia, pero está deshabilitado. |
| **Certificado todavía no válido** | Su fecha inicial de vigencia aún no llegó. | Está deshabilitado. |
| **Algoritmo no soportado** | No usa RSA, que es el algoritmo admitido actualmente. | Está deshabilitado. |
| **No permite firma digital** | La extensión de uso de clave no autoriza firma digital o compromiso de contenido. | Está deshabilitado. |

Cada opción incluye el titular, el emisor y la fecha/hora de vencimiento en la zona horaria del equipo. Revise estos datos antes de confirmar: la aplicación no elige un certificado distinto en su nombre.

## Si la autorización no funciona

- Verifique que el token esté conectado y que el PIN sea correcto.
- En Windows, verifique el certificado en `certmgr.msc`, dentro de **Personal > Certificados**, y confirme que indique que dispone de la clave privada correspondiente.
- Una smartcard visible en Windows sigue necesitando el dispositivo conectado y su controlador.
- Cancele y vuelva a intentar si seleccionó el documento o el certificado incorrecto.
- Si no aparecen certificados válidos, no se podrá firmar hasta disponer de uno vigente.
- Use únicamente middleware o controladores instalados y validados por el proveedor o por el área responsable.

## Siguiente paso

Para el proceso completo, consulte la [guía para firmar un PDF](guia-de-firma.md). Para incidencias frecuentes, consulte las [preguntas frecuentes](preguntas-frecuentes.md).
