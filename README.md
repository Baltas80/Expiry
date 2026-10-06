# Expiry

Expiry es una app sencilla para controlar fechas de caducidad y recibir avisos antes de que un producto caduque.

## Objetivo

- Registrar productos rápidamente.
- Guardar fecha de caducidad.
- Mostrar qué caduca hoy, pronto y más adelante.
- Recibir recordatorios configurables.
- Escanear códigos de barras y completar información del producto cuando exista.
- Mantener los datos localmente y con una interfaz simple.

## MVP

1. Lista de productos con estado visual.
2. Alta, edición y eliminación.
3. Fecha de caducidad.
4. Categorías opcionales.
5. Notificaciones locales.
6. Búsqueda y filtros.
7. Persistencia local.
8. Escáner de códigos de barras.
9. Consulta opcional de información pública de producto.
10. Registro local de consumo/desperdicio.

## Consulta global de códigos de barras

El escáner acepta los formatos de código soportados por ML Kit y la capa de consulta está preparada para identificadores de producto internacionales.

La consulta remota utiliza el endpoint universal de Open Food Facts con \`product_type=all\`, que puede resolver registros de Open Food Facts, Open Beauty Facts, Open Pet Food Facts y Open Products Facts. También se envían el idioma y país del dispositivo para localizar la respuesta cuando el servicio dispone de esa información.

Antes de consultar la red, Expiry revisa su catálogo local. Después aplica normalizaciones conservadoras para códigos GTIN habituales (por ejemplo, UPC-A/EAN-13, GTIN-14) y extrae GTIN desde GS1 AI (01) cuando el escáner devuelve una cadena GS1 estructurada.

La cobertura no puede garantizar que exista información para el 100 % de los códigos del mundo: un código puede ser válido y no tener todavía un registro público de producto. En esos casos el usuario puede introducir los datos manualmente.

## Arquitectura preparada para evolución

La aplicación contiene infraestructura futura desactivada para permitir una evolución sin rediseñar el almacenamiento local. Incluye contratos para sincronización, perfil extensible y un sistema interno de recompensas basado en puntos.

Estas funciones no están activas ni visibles en la versión actual. No se realiza ninguna transmisión de datos por estas capacidades.

### Privacidad y retención

- El historial de consumo y desperdicio permanece local y está excluido del contrato de nube.
- La sincronización futura requerirá consentimiento específico.
- Cuando se acepte la sincronización, el modelo registra el instante de aceptación y un periodo de retención de cinco años.
- La retirada del consentimiento elimina del perfil local las fechas de consentimiento y retención.
- Las finalidades futuras de analítica, sincronización, marketing o recompensas deberán activarse de forma separada y comunicarse claramente antes de su utilización.

### Rewards

El módulo interno \`Expiry Rewards\` está preparado para gestionar:

- Expiry Points (EP).
- Libro mayor de puntos.
- Campañas.
- Catálogo de recompensas.
- Patrocinadores.
- Canje futuro de puntos.

Rewards permanece desactivado (\`REWARDS_ENABLED = false\`) y no aparece en la interfaz actual.

## Principios

- Rápida de usar.
- Sin registro obligatorio.
- Privacidad por defecto.
- Sin funciones innecesarias en la interfaz actual.
- Arquitectura modular para futuras actualizaciones.
- El consumo individual no forma parte del intercambio de datos con servidor.

## Localización

Expiry está preparado para una cobertura internacional amplia mediante recursos Android por locale. Las traducciones específicas se validan mediante la comprobación automática de claves de recursos.

## Privacidad

Consulta la [Política de Privacidad de Expiry](PRIVACY_POLICY.md).


## Monetización preparada para la próxima actualización

La arquitectura de monetización queda preparada para dos niveles:

- **Gratis**: versión con publicidad mediante Google Mobile Ads/AdMob, con consentimiento gestionado mediante UMP.
- **Expiry Premium**: suscripción de Google Play que elimina la publicidad.

### Identificadores de Google Play

Crear en Play Console una suscripción con:

- Product ID: `premium_no_ads`
- Base plan mensual: `monthly`
- Base plan anual: `annual`

La aplicación consulta las ofertas disponibles directamente desde Google Play y utiliza el precio localizado que devuelve Play. No se deben introducir precios fijos en el código.

### AdMob

Antes de activar anuncios en producción hay que registrar la aplicación en AdMob y completar:

- `app/src/main/res/values/monetization.xml` → `admob_app_id`
- `app/src/main/res/values/monetization.xml` → `admob_banner_ad_unit_id`

Mientras esos valores estén vacíos, el SDK permanece sin solicitar anuncios. Esto evita publicar accidentalmente anuncios con identificadores de prueba o inventados.

### Seguridad de la suscripción

La aplicación actualiza el entitlement Premium consultando las suscripciones activas de Google Play y reconoce las compras pendientes de reconocimiento. Para una futura evolución con backend, el token de compra puede verificarse servidor a servidor mediante las APIs de Google Play Developer.

La dependencia usada es Play Billing 9.1.0, versión soportada actualmente para nuevas actualizaciones. La integración sigue el flujo moderno de `ProductDetails` y ofertas de suscripción.
