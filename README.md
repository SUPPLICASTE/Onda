# Onda

> Tu música, sin ruido.

**Onda** es una app de música para Android, inspirada en la interfaz de Spotify, sin anuncios. Permite buscar canciones, crear álbumes, compartirlos con amigos mediante QR, activar un DJ que sugiere la siguiente canción según lo que escuchas e instalarse a sí misma desde GitHub Releases sin borrar tus datos.

- **Paquete:** `com.supplicaste.onda`
- **Versión:** 1.0.0 (`versionCode 2`)
- **Stack:** Kotlin + Jetpack Compose + Material 3 + MVVM
- **Distribución:** GitHub Releases (APK firmado)

---

## Funcionalidades

| Función | Estado | Detalle |
|---|---|---|
| Interfaz tipo Spotify | ✅ | Barra superior, navegación inferior (Inicio / Buscar / Biblioteca), mini-reproductor |
| Sin anuncios | ✅ | Ningún SDK publicitario |
| Registro con correo | ✅ | Firebase Auth si hay credenciales; modo local si no las hay |
| Búsqueda de canciones | ✅ | NewPipeExtractor (YouTube), resultados en tiempo real |
| Reproducción | ✅ | Media3/ExoPlayer con streams de audio |
| Álbumes | ✅ | Room: crear, añadir, quitar y borrar álbumes |
| Compartir con amigos | ✅ | Código QR (ZXing) + campo para importar código o escanearlo |
| DJ opcional | ✅ | Sugiere la siguiente canción según reproducciones y artistas guardados |
| Auto-actualizador | ✅ | Consulta `/releases/latest`, descarga el APK y lanza la instalación |
| Persistencia al actualizar | ✅ | Mismo `applicationId` y misma clave de firma |

---

## Decisiones

| Decisión | Elección | Motivo |
|---|---|---|
| Nombre | **Onda** | Propio y corto; evita conflictos de marca con Spotify |
| Catálogo | NewPipeExtractor / YouTube | Grandes listas, sin descargar archivos |
| Registro | Firebase Auth (correo) | Verificación real; con fallback local para poder compilar sin credenciales |
| Compartir | QR / texto | Sin backend propio; el otro usuario importa el álbum |
| DJ | Local, sin IA externa | Basado en historial y artistas favoritos |
| Actualizaciones | Misma firma | Conserva la base de datos al sobrescribir la app |

---

## Configuración de Firebase (opcional pero recomendada)

La app compila y funciona sin credenciales, en **modo local**: el correo se guarda solo en SharedPreferences y no se verifica. Para activar el registro real:

1. Crea un proyecto en Firebase y añade una app Android con paquete `com.supplicaste.onda`.
2. Copia el `google-services.json` a `app/` (está en `.gitignore`; **no subirlo**).
3. Añade a `local.properties` (también ignorado):

```properties
onda.firebase.apiKey=AIza...
onda.firebase.projectId=tu-proyecto
onda.firebase.appId=1:123456789:android:abcdef
```

También puedes usar las variables de entorno `ONDA_FIREBASE_API_KEY`, `ONDA_FIREBASE_PROJECT_ID` y `ONDA_FIREBASE_APP_ID`.

Si los tres valores están presentes, la pantalla de acceso usará Firebase Auth de verdad.

---

## Firma y actualizaciones

El APK de release está firmado con `app/onda-release.keystore` (alias `onda`). Ese archivo **no se sube a GitHub**. Guárdalo junto con la contraseña de `local.properties`:

```properties
onda.storePassword=...
onda.keyPassword=...
onda.keyAlias=onda
```

O las variables `ONDA_STORE_PASSWORD`, `ONDA_KEY_PASSWORD` y `ONDA_KEY_ALIAS`.

**Si se pierde el keystore, las futuras versiones ya no podrán actualizar la instalada.** Se generó con validez de 10.000 días.

El flujo de actualización es:

1. `Updater.latest()` consulta `https://api.github.com/repos/SUPPLICASTE/Onda/releases/latest`.
2. Compara el `tag_name` con `BuildConfig.VERSION_NAME`.
3. Descarga el primer asset `.apk` a `filesDir`.
4. Lo expone con `FileProvider` y lanza `ACTION_INSTALL_PACKAGE`.
5. El usuario debe conceder permiso de "instalar aplicaciones desconocidas" una vez.

Como el paquete y la firma coinciden, Android sobrescribe la app y **Room conserva álbumes e historial**.

---

## Compilar

Requiere JDK 17 y Android SDK 34. El wrapper Gradle está incluido.

```powershell
.\gradlew assembleDebug     # com.supplicaste.onda.debug
.\gradlew assembleRelease   # com.supplicaste.onda, firmado
```

APKs:

- Debug: `app\build\outputs\apk\debug\app-debug.apk`
- Release: `app\build\outputs\apk\release\app-release.apk`

**Nota:** la ruta contiene `PROGRAMACIÓN` (acento), por lo que `gradle.properties` incluye `android.overridePathCheck=true`.

Verificación de firma:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

---

## Aviso legal

La interfaz se inspira en patrones habituales de reproductores de música, pero **no usa** el nombre, el logo ni los recursos gráficos de Spotify. El catálogo se obtiene mediante NewPipeExtractor desde fuentes públicas; el usuario es responsable del uso que haga de esas fuentes y de las condiciones del proveedor.

---

## GitHub Actions

`.github/workflows/android.yml` automatiza todos los pasos:

1. Checkout + JDK 17 + Gradle 8.7.
2. Decodifica el keystore desde el secreto `ONDA_KEYSTORE_BASE64`.
3. `assembleDebug` y `assembleRelease` firmado.
4. Verifica la firma con `apksigner`.
5. Sube ambos APK como artefactos (`onda-debug`, `onda-release`).
6. Si el push es un tag `v*`, crea el Release y adjunta el APK firmado.

Se ejecuta en `push` a `main`, en tags `v*`, en `pull_request` y manualmente (`workflow_dispatch`). En los PR no se usa el keystore: solo se compila el debug.

**Secrets configurados en el repo:** `ONDA_KEYSTORE_BASE64`, `ONDA_STORE_PASSWORD`, `ONDA_KEY_PASSWORD`, `ONDA_KEY_ALIAS`. Opcionales para Firebase: `ONDA_FIREBASE_API_KEY`, `ONDA_FIREBASE_PROJECT_ID`, `ONDA_FIREBASE_APP_ID`.

Para publicar una versión nueva:

```powershell
git tag v1.0.1
git push origin v1.0.1
```

El workflow compilará y creará el Release automáticamente.

---

## Estado

- ✅ Debug y release compilados correctamente (20/11/2026).
- ✅ Release firmada y verificada con `apksigner`.
- ✅ Workflow de GitHub Actions configurado con secretos de firma.
- ⏳ Pruebas en dispositivo físico pendientes (búsqueda real, streams, permiso de instalación).
