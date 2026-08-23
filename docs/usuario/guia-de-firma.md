# Guía para firmar un PDF

Esta guía describe el flujo actual de FirmaUNA para firmar un documento PDF con un token y un certificado de firma disponible.

## Pasos principales

1. Seleccione **Seleccionar PDF...** y abra el documento.
2. Revise la vista previa y navegue hasta la página donde desea colocar el sello.
3. Elija el formato **horizontal** o **vertical** y arrastre el recuadro del sello a la posición deseada.
4. Si corresponde, active **Firmar todas las páginas**.
5. Seleccione **Firmar PDF**, ingrese el PIN y elija un certificado válido.

## Antes de firmar

| Acción | Qué ocurre |
| --- | --- |
| Seleccionar o reemplazar el PDF | Use **Seleccionar PDF...** al inicio o **Abrir otro PDF...** cuando ya exista un documento cargado. |
| Posicionar el sello | Arrastre el recuadro visible dentro de la página mostrada. La aplicación limita la posición a un área segura. |
| Elegir formato | El formato horizontal y el vertical cambian las dimensiones del sello. Revise la posición después de cambiar el formato. |
| Firmar todas las páginas | Repite el sello visible en todas las páginas. La operación sigue realizando una sola firma digital criptográfica por cada acción de firma. |

## PIN y certificado

El PIN autoriza el uso del token. Después de validarlo, seleccione obligatoriamente un certificado válido para continuar. Los certificados vencidos pueden aparecer en la lista, pero no se pueden seleccionar.

Si cancela la ventana del PIN o del certificado, no se firma el documento y puede volver a intentarlo.

Consulte [Certificados, PIN y token](certificados.md) para conocer el significado de los estados mostrados.

## Resultado y nueva firma

Al terminar, el archivo se guarda automáticamente en la misma carpeta del documento abierto:

| Si el nombre disponible es | El resultado será |
| --- | --- |
| `documento.pdf` | `documento [FU].pdf` |
| `documento [FU].pdf` ya existe | `documento 2 [FU].pdf` |
| Ya existen resultados anteriores | Se usa el siguiente número disponible, sin reemplazar archivos existentes. |

El PDF guardado se abre nuevamente en la aplicación. Puede elegir **Volver a firmar** para agregar otra firma. Seleccione **Ver ubicación** para mostrar el archivo guardado en Finder en macOS.

## Expectativas importantes

- Verifique que el PDF correcto esté cargado antes de ingresar el PIN.
- Revise la vista previa; el sello se coloca según la posición elegida y el formato activo.
- Para varias páginas, la posición se conserva de forma relativa al área visible de cada página. Si una página no tiene espacio suficiente para el formato seleccionado, la firma no se inicia.
- La aplicación guarda un resultado nuevo; no reemplaza el PDF de entrada ni un resultado firmado existente.

## Siguiente paso

Si no puede continuar, consulte las [preguntas frecuentes](preguntas-frecuentes.md).
