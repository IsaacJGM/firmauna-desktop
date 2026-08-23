# Preguntas frecuentes

Use estas respuestas para resolver situaciones habituales del flujo actual de FirmaUNA.

## No hay un PDF seleccionado

Seleccione **Seleccionar PDF...** y elija un archivo PDF. Si ya hay un documento abierto, use **Abrir otro PDF...** para reemplazarlo antes de firmar.

## No aparecen certificados válidos

Compruebe que el token esté conectado, que el PIN sea correcto y que el middleware del proveedor esté instalado y validado. Los certificados vencidos se muestran, pero están deshabilitados. Si no existe un certificado vigente, FirmaUNA no puede completar la firma.

## El PIN no funciona o el token no responde

Revise el PIN, la conexión física del token y el middleware instalado. Cancele el proceso y vuelva a intentarlo. No comparta el PIN ni lo registre en documentos de soporte.

## ¿Dónde se guarda el PDF firmado?

Se guarda en la misma carpeta del PDF abierto. La aplicación usa `base [FU].pdf`; si ese archivo ya existe, usa `base 2 [FU].pdf` y continúa con el siguiente número disponible. No reemplaza archivos existentes.

En macOS, **Ver ubicación** muestra el archivo en Finder. Si no funciona, abra manualmente la carpeta del PDF original y localice el nombre con `[FU]`.

## ¿Puedo firmar el mismo documento otra vez?

Sí. Después de guardar, el resultado se vuelve a abrir en la aplicación. Seleccione **Volver a firmar** para iniciar otra acción de firma. Cada acción de firma genera un archivo nuevo con el siguiente nombre disponible.

## ¿Es seguro usar “Firmar todas las páginas”?

La opción repite el sello visible en todas las páginas y conserva su posición relativa al área visible de cada una. Una única operación de firma digital criptográfica se realiza por cada acción de firma; no se crea una operación criptográfica adicional por página. Si alguna página no tiene espacio suficiente para el formato elegido, la aplicación detiene el inicio de la firma.

## macOS muestra una advertencia al abrir la aplicación

El paquete macOS actual está firmado de forma ad-hoc y no está notarizado por Apple. Por ello, Gatekeeper puede mostrar una advertencia. No omita controles institucionales ni de seguridad: use únicamente artefactos publicados por el canal oficial y solicite orientación al área responsable cuando corresponda.

## Siguiente paso

Revise la [guía para firmar un PDF](guia-de-firma.md) y la guía de [certificados, PIN y token](certificados.md).
