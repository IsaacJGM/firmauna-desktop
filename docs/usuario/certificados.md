# Certificados, PIN y token

FirmaUNA usa el token conectado para descubrir certificados de firma disponibles. El PIN y la selección de un certificado válido son pasos obligatorios antes de firmar.

## Flujo seguro

1. Conecte el token y confirme que su middleware esté instalado y validado por su proveedor.
2. Ingrese el PIN únicamente en la ventana de FirmaUNA.
3. Seleccione un certificado con estado **Válido**.
4. Confirme con **Firmar con este certificado**.

Puede cancelar en la ventana del PIN o del certificado. La cancelación no firma el PDF y cierra la sesión del token utilizada por la aplicación.

## Estados de certificado

| Estado mostrado | Significado en la aplicación | Acción disponible |
| --- | --- | --- |
| **Válido** | La fecha de vencimiento del certificado es posterior a la hora actual del equipo. | Puede seleccionarse para firmar. |
| **Certificado vencido** | La fecha de vencimiento ya pasó según la hora actual del equipo. | Se muestra como referencia, pero está deshabilitado. |

Cada opción incluye el titular, el emisor y la fecha/hora de vencimiento en la zona horaria del equipo. Revise estos datos antes de confirmar: la aplicación no elige un certificado distinto en su nombre.

## Si el PIN o el token no funcionan

- Verifique que el token esté conectado y que el PIN sea correcto.
- Cancele y vuelva a intentar si seleccionó el documento o el certificado incorrecto.
- Si no aparecen certificados válidos, no se podrá firmar hasta disponer de uno vigente.
- Use únicamente middleware de token que haya sido instalado y validado por el proveedor o por el área responsable. Esta guía no confirma compatibilidad con middleware no verificado.

## Siguiente paso

Para el proceso completo, consulte la [guía para firmar un PDF](guia-de-firma.md). Para incidencias frecuentes, consulte las [preguntas frecuentes](preguntas-frecuentes.md).
