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

La configuración de firma de producción está preparada en `app/build.gradle.kts`: solo activa la firma cuando recibe las cuatro credenciales necesarias mediante variables de entorno. Sin esas credenciales, CI puede seguir construyendo y validando el AAB, pero ese AAB no debe considerarse listo para subir a Google Play.

El workflow de GitHub Actions puede recibir el keystore como Base64 mediante un secreto, reconstruirlo temporalmente dentro del runner y pasar su ruta a Gradle. El archivo temporal queda fuera del repositorio y no se publica como artefacto.

No se deben guardar en Git el keystore, su contraseña, la contraseña de la clave ni ningún fichero que contenga secretos.

### Contrato de secretos para CI

Configurar en GitHub Actions los valores equivalentes a:

- `EXPIRY_KEYSTORE_BASE64`: contenido Base64 del keystore de producción.
- `EXPIRY_KEYSTORE_PASSWORD`: contraseña del keystore.
- `EXPIRY_KEY_ALIAS`: alias de la clave de publicación.
- `EXPIRY_KEY_PASSWORD`: contraseña de la clave.

El workflow reconstruye el archivo en `$RUNNER_TEMP/expiry-upload-key.jks` cuando existe `EXPIRY_KEYSTORE_BASE64`. Gradle utiliza esa ruta mediante `EXPIRY_KEYSTORE_PATH`.

### Estado actual

- [x] Configuración `signingConfigs.production` preparada y condicionada a credenciales completas.
- [x] `release` usa la configuración de producción cuando las cuatro variables están presentes.
- [x] Workflow preparado para recibir el keystore mediante secreto Base64 y reconstruirlo temporalmente.
- [x] Keystores, contraseñas y ficheros de firma excluidos del repositorio.
- [x] CI valida `bundleRelease` sin requerir secretos.
- [x] Crear/confirmar la clave de firma de producción.
- [x] Guardar el keystore en un lugar seguro fuera del repositorio.
- [ ] Configurar los cuatro secretos de CI y proporcionar el keystore de forma segura al job.
- [ ] Ejecutar `bundleRelease` con las credenciales de producción.
- [ ] Verificar criptográficamente que el AAB generado está firmado con la clave esperada.
- [ ] Conservar de forma segura la clave de firma; no hay que perderla.

## Google Play

- [ ] Crear/confirmar la aplicación en Play Console.
- [ ] Completar ficha de tienda, privacidad, contenido y declaraciones de datos.
- [ ] Subir primero a prueba interna.
- [ ] Validar instalación, actualización, notificaciones, escáner y exportación/importación.
- [ ] Pasar a producción cuando la prueba interna sea estable.

## Regla de seguridad

Nunca imprimir secretos en logs de CI ni introducir contraseñas, claves privadas o keystores en commits.
