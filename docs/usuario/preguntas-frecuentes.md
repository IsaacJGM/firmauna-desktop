# Preguntas frecuentes

Use estas respuestas para resolver situaciones habituales del flujo actual de FirmaUNA.

## No hay un PDF seleccionado

Seleccione **Seleccionar PDF...** y elija un archivo PDF. Si ya hay un documento abierto, use **Abrir otro PDF...** para reemplazarlo antes de firmar.

## No aparecen certificados válidos

En Windows, compruebe en `certmgr.msc` que el certificado esté en **Personal > Certificados** y tenga clave privada. Para una smartcard, confirme que esté conectada y que su controlador funcione. En macOS, compruebe el token, PIN y middleware. Los certificados vencidos, todavía no válidos o incompatibles aparecen deshabilitados.

## El PIN no funciona o el token no responde

Revise el PIN, la conexión física del token y el middleware instalado. Cancele el proceso y vuelva a intentarlo. No comparta el PIN ni lo registre en documentos de soporte.

## Windows solicita autorización o PIN

La ventana pertenece al proveedor criptográfico de Windows, no a FirmaUNA. Puede aparecer para claves protegidas o smartcards. Si usa un certificado software sin protección adicional, es normal que Windows no muestre una ventana.

## ¿Dónde se guarda el PDF firmado?

Se guarda en la misma carpeta del PDF abierto. La aplicación usa `base [FU].pdf`; al firmar ese resultado usa `base [FFU].pdf`, luego `base [FFFU].pdf`, y así sucesivamente. No reemplaza archivos existentes.

**Ver ubicación** muestra el archivo en Finder o en el Explorador de Windows. Si no funciona, abra manualmente la carpeta del PDF original y localice el nombre con `[FU]`.

## ¿Puedo firmar el mismo documento otra vez?

Sí. Después de guardar, el resultado se vuelve a abrir en la aplicación. Seleccione **Volver a firmar** y luego **Firmar PDF** para iniciar otra acción. Cada firma con token solicita el PIN nuevamente y genera el siguiente nombre encadenado disponible.

## ¿Es seguro usar “Firmar todas las páginas”?

La opción repite el sello visible en todas las páginas y conserva su posición relativa al área visible de cada una. Una única operación de firma digital criptográfica se realiza por cada acción de firma; no se crea una operación criptográfica adicional por página. Si alguna página no tiene espacio suficiente para el formato elegido, la aplicación detiene el inicio de la firma.

## macOS muestra una advertencia al abrir la aplicación

El paquete macOS actual está firmado de forma ad-hoc y no está notarizado por Apple. Por ello, Gatekeeper puede mostrar una advertencia. No omita controles institucionales ni de seguridad: use únicamente artefactos publicados por el canal oficial y solicite orientación al área responsable cuando corresponda.

## Siguiente paso

Revise la [guía para firmar un PDF](guia-de-firma.md) y la guía de [certificados y autorización](certificados.md).
