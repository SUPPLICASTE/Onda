# Onda

> Tu música, sin ruido.

**Onda** es un reproductor de música para Android con interfaz oscura inspirada en los patrones habituales de los reproductores modernos, sin anuncios. Permite buscar canciones, marcar **Me gusta**, crear álbumes y playlists, compartirlos por QR, reproducir en segundo plano con controles multimedia, activar un DJ que sugiere la siguiente canción e instalarse a sí misma desde GitHub Releases sin borrar tus datos.

- **Paquete:** `com.supplicaste.onda`
- **Versión:** 1.1.0 (`versionCode 4`)
- **Stack:** Kotlin + Jetpack Compose + Material 3 + MVVM + Room + Media3 + NewPipeExtractor

---

## Funcionalidades

| Función | Estado | Detalle |
|---|---|---|
| Interfaz tipo reproductor moderno | ✅ | Tema oscuro con acento verde, navegación inferior, tarjetas, mini-reproductor |
| Sin anuncios | ✅ | Ningún SDK publicitario |
| Registro con correo | ✅ | Firebase Auth si hay credenciales; modo local si no las hay |
| Búsqueda de canciones | ✅ | NewPipeExtractor (YouTube), búsquedas recientes |
| Reproducción | ✅ | Media3/ExoPlayer, servicio en segundo plano y notificación multimedia |
| **Me gusta** | ✅ | Botón en cada canción y playlist especial "Me gusta" |
| **Pantalla de reproducción** | ✅ | Carátula, progreso, aleatorio, anterior/siguiente, repetir, cola editable |
| Menú de cada canción | ✅ | Reproducir, reproducir siguiente, Me gusta, añadir a álbum o playlist |
| Álbumes y playlists | ✅ | Crear, renombrar, borrar, añadir y quitar canciones |
| Compartir con amigos | ✅ | Código QR (ZXing) + campo para importar o escanear |
| DJ opcional | ✅ | Sugiere la siguiente canción según Me gusta, historial y artistas |
| Perfil | ✅ | Correo, estadísticas, tema oscuro/claro, comprobación de updates |
| Auto-actualizador | ✅ | Consulta `/releases/latest`, descarga el APK y lanza la instalación |
| Persistencia al actualizar | ✅ | Mismo `applicationId` y misma clave de firma; migración Room v1→v2 |

---

## Decisiones

| Decisión | Elección | Motivo |
|---|---|---|
| Nombre | **Onda** | Propio; no usa nombre, logo ni recursos de Spotify |
| Catálogo | NewPipeExtractor / YouTube | Grandes listas sin descargar archivos |
| Registro | Firebase Auth + fallback local | Verificación real sin bloquear el build |
| Compartir | QR / texto | Sin backend propio |
| DJ | Local, sin IA externa | Basado en Me gusta e historial |
| Reproductor | `MediaSessionService` | Música en segundo plano y controles en notificación |
| Actualizaciones | Misma firma | Conserva Room (álbumes, Me gusta, listas e historial) al sobrescribir |

---

## Configuración de Firebase (opcional pero recomendada)

Sin credenciales la app funciona en **modo local**: el correo se guarda solo en SharedPreferences. Para activar el registro real:

1. Crea un proyecto en Firebase y añade una app Android con paquete `com.supplicaste.onda`.
2. Copia `google-services.json` a `app/` (ignorado por Git; **no subirlo**).
3. Añade a `local.properties`:

```properties
onda.firebase.apiKey=AIza...
onda.firebase.projectId=tu-proyecto
onda.firebase.appId=1:123456789:android:abcdef
```

También sirven las variables `ONDA_FIREBASE_API_KEY`, `ONDA_FIREBASE_PROJECT_ID` y `ONDA_FIREBASE_APP_ID`.

---

## Firma y actualizaciones

El APK de release se firma con `app/onda-release.keystore` (alias `onda`), que **no se sube a GitHub**. Guárdalo junto a la contraseña:

```properties
onda.storePassword=...
onda.keyPassword=...
onda.keyAlias=onda
```

O las variables `ONDA_STORE_PASSWORD`, `ONDA_KEY_PASSWORD`, `ONDA_KEY_ALIAS`.

**Si se pierde el keystore, las futuras versiones no podrán actualizar la instalada.**

Flujo: `/releases/latest` → compara tag con `VERSION_NAME` → descarga el primer `.apk` → `FileProvider` + `ACTION_INSTALL_PACKAGE`. Como coinciden paquete y firma, Android conserva la base de datos.

La base de datos pasa de versión 1 a 2 con `MIGRATION_1_2`, que **añade** tablas (playlists, Me gusta, búsquedas) sin borrar las existentes.

---

## Compilar

Requiere JDK 17 y Android SDK 34.

```powershell
.\gradlew assembleDebug
.\gradlew assembleRelease
```

- Debug: `app\build\outputs\apk\debug\app-debug.apk`
- Release: `app\build\outputs\apk\release\app-release.apk`

La ruta contiene `PROGRAMACIÓN` (acento), por eso `gradle.properties` incluye `android.overridePathCheck=true`.

---

## GitHub Actions

`.github/workflows/android.yml` ejecuta: checkout, JDK 17, Gradle, decodificar keystore, `assembleDebug`, `assembleRelease` firmado, verificar firma, subir artefactos y crear Release en tags `v*`. En PR solo compila el debug.

Secrets: `ONDA_KEYSTORE_BASE64`, `ONDA_STORE_PASSWORD`, `ONDA_KEY_PASSWORD`, `ONDA_KEY_ALIAS` (y los opcionales de Firebase).

Nueva versión:

```powershell
git tag v1.1.1
git push origin v1.1.1
```

---

## Aviso legal

La interfaz se inspira en patrones habituales de reproductores de música, pero **no usa** el nombre, el logo ni los recursos gráficos de Spotify. El catálogo se obtiene mediante NewPipeExtractor desde fuentes públicas; el usuario es responsable del uso de esas fuentes y de sus condiciones.

---

## Estado

- ✅ Compile debug y release de v1.1.0.
- ✅ Release firmada y generada por GitHub Actions.
- ⏳ Pruebas en dispositivo físico: streams reales, notificación en segundo plano y permiso de instalación.
