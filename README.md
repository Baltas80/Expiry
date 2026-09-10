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

El módulo interno `Expiry Rewards` está preparado para gestionar:

- Expiry Points (EP).
- Libro mayor de puntos.
- Campañas.
- Catálogo de recompensas.
- Patrocinadores.
- Canje futuro de puntos.

Rewards permanece desactivado (`REWARDS_ENABLED = false`) y no aparece en la interfaz actual.

## Principios

- Rápida de usar.
- Sin registro obligatorio.
- Privacidad por defecto.
- Sin funciones innecesarias en la interfaz actual.
- Arquitectura modular para futuras actualizaciones.
- El consumo individual no forma parte del intercambio de datos con servidor.

## Localización

Expiry está preparado para una cobertura internacional amplia mediante recursos Android por locale. Las traducciones específicas se validan mediante la comprobación automática de claves de recursos.
