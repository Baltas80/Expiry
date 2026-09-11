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

La compilación actual genera el AAB correctamente, pero la firma de publicación debe configurarse antes de subir la aplicación a Google Play.

No se deben guardar en Git el keystore, su contraseña, la contraseña de la clave ni ningún fichero que contenga secretos.

### Secretos previstos para CI

Configurar en GitHub Actions los valores equivalentes a:

- `EXPIRY_KEYSTORE_BASE64`: keystore de producción codificado en Base64.
- `EXPIRY_KEYSTORE_PASSWORD`: contraseña del keystore.
- `EXPIRY_KEY_ALIAS`: alias de la clave de publicación.
- `EXPIRY_KEY_PASSWORD`: contraseña de la clave.

Los nombres anteriores son una propuesta de contrato para la automatización; no contienen valores reales.

### Antes de publicar

- [ ] Crear/confirmar la clave de firma de producción.
- [ ] Guardar el keystore en un lugar seguro fuera del repositorio.
- [ ] Configurar los cuatro secretos de CI.
- [ ] Añadir la configuración de `signingConfigs.release` usando únicamente secretos/variables de CI.
- [ ] Verificar que `bundleRelease` produce un AAB firmado.
- [ ] Conservar de forma segura la clave de firma; no hay que perderla.
- [ ] Crear la aplicación en Play Console.
- [ ] Completar ficha de tienda, privacidad, contenido y declaraciones de datos.
- [ ] Subir primero a prueba interna.
- [ ] Validar instalación, actualización, notificaciones, escáner y exportación/importación.
- [ ] Pasar a producción cuando la prueba interna sea estable.

## Regla de seguridad

Nunca imprimir secretos en logs de CI ni introducir contraseñas, claves privadas o keystores en commits.
