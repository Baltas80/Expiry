# Expiry — Release checklist

## Estado de la versión

- [x] `versionCode = 6`
- [x] `versionName = "1.0.0"`
- [x] `gradle clean test`
- [x] `gradle lint`
- [x] `gradle assembleDebug`
- [x] `gradle bundleRelease`
- [x] Generación del AAB de release en CI

## Firma de producción

La firma de producción de Expiry se creó previamente y se conserva fuera del repositorio. CI recibe el keystore de forma segura mediante secretos y lo reconstruye temporalmente dentro del runner. El keystore y las credenciales no se publican como artefactos ni se almacenan en Git.

### Contrato de secretos para CI

- `EXPIRY_KEYSTORE_BASE64`: contenido Base64 del keystore de producción.
- `EXPIRY_KEYSTORE_PASSWORD`: contraseña del keystore.
- `EXPIRY_KEY_ALIAS`: alias de la clave de publicación.
- `EXPIRY_KEY_PASSWORD`: contraseña de la clave.

El workflow reconstruye el archivo en `$RUNNER_TEMP/expiry-upload-key.jks` y Gradle utiliza esa ruta mediante `EXPIRY_KEYSTORE_PATH`.

### Estado actual

- [x] Configuración `signingConfigs.production` preparada y condicionada a credenciales completas.
- [x] `release` usa la configuración de producción cuando las cuatro variables están presentes.
- [x] Workflow preparado para recibir el keystore mediante secreto Base64 y reconstruirlo temporalmente.
- [x] Keystores, contraseñas y ficheros de firma excluidos del repositorio.
- [x] CI valida `bundleRelease` sin requerir secretos.
- [x] Crear/confirmar la clave de firma de producción.
- [x] Guardar el keystore en un lugar seguro fuera del repositorio.
- [x] Configuración de los secretos de CI y entrega segura del keystore al job.
- [x] Ejecución de `bundleRelease` con la configuración de firma de producción.
- [x] Verificación criptográfica de que el AAB generado está firmado.
- [x] Conservación segura de la clave de firma.

## Google Play

- [ ] Crear/confirmar la aplicación en Play Console.
- [ ] Completar ficha de tienda, privacidad, contenido y declaraciones de datos.
- [ ] Subir primero a prueba interna.
- [ ] Validar instalación, actualización, notificaciones, escáner y exportación/importación.
- [ ] Pasar a producción cuando la prueba interna sea estable.

## Regla de seguridad

Nunca imprimir secretos en logs de CI ni introducir contraseñas, claves privadas o keystores en commits.
