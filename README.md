# Onda

> Tu música, sin ruido.

**Onda** es un reproductor de música para Android con interfaz oscura inspirada en los patrones habituales de los reproductores modernos, sin anuncios. Permite buscar canciones, marcar **Me gusta**, crear álbumes y playlists, compartirlos por QR, reproducir en segundo plano con controles multimedia, activar un DJ que sugiere la siguiente canción e instalarse a sí misma desde GitHub Releases sin borrar tus datos.

- **Paquete:** `com.supplicaste.onda`
- **Versión:** 1.1.7 (`versionCode 11`)
- **Stack:** Kotlin + Jetpack Compose + Material 3 + MVVM + Room + Media3 + NewPipeExtractor

---

## Funcionalidades

| Función | Estado | Detalle |
|---|---|---|
| Interfaz tipo reproductor moderno | ✅ | Tema oscuro con acento verde, navegación inferior, biblioteca con carátulas reales, mini-reproductor flotante y barra superior con perfil |
| Sin anuncios | ✅ | Ningún SDK publicitario |
| Registro con correo / Gmail | ✅ | Modo local: el correo se guarda solo en este dispositivo |
| Acceso con Spotify | ✅ | OAuth 2.0 con PKCE: registra o vincula después desde Tu perfil e importa tus playlists con carátula, título, artista y duración |
| Audio de Spotify | ✅ | Se buscan en YouTube las canciones importadas para poder reproducirlas sin Premium; el token se renueva solo |
| Búsqueda de canciones | ✅ | NewPipeExtractor (YouTube); **tocar un resultado lo reproduce**; "Mejor resultado", filtros Canciones/Artistas e **historial visible con borrado individual** |
| Reproducción | ✅ | Media3/ExoPlayer, servicio en segundo plano, notificación multimedia, **avance automático al terminar** y **pausa/reanude por foco de audio** |
| **Me gusta** | ✅ | Botón en cada canción, playlist especial "Me gusta" y **"Reproducir todo" reproduce la playlist completa** |
| **Pantalla de reproducción** | ✅ | Fondo difuminado, progreso, aleatorio, repetir, cola editable y menú "..." de la canción para añadirla a álbum o playlist |
| Menú de cada canción | ✅ | Reproducir, reproducir siguiente, Me gusta, añadir a álbum o playlist |
| Álbumes y playlists | ✅ | Biblioteca en cuadrícula con menú "+", crear, renombrar, borrar, añadir y quitar canciones; **cada canción muestra carátula, título, artista y duración** |
| Compartir con amigos | ✅ | Código QR (ZXing) **y código de texto copiable/compartible** + campo para importar o escanear |
| DJ opcional | ✅ | Al activarlo encadena músicas afines a la canción actual, tus listas/Me gusta y el artista que suenas |
| Perfil | ✅ | Correo, estadísticas, tema oscuro/claro, comprobación de updates |
| Auto-actualizador | ✅ | Consulta `/releases/latest`, descarga el APK y lanza la instalación |
| Persistencia al actualizar | ✅ | Mismo `applicationId` y misma clave de firma; migración Room v1→v2 |

---

## Decisiones

| Decisión | Elección | Motivo |
|---|---|---|
| Nombre | **Onda** | Propio; no usa nombre, logo ni recursos de Spotify |
| Catálogo | NewPipeExtractor / YouTube | Grandes listas sin descargar archivos |
| Registro | Modo local + Spotify OAuth | Sin credenciales de Google/Firebase: solo hace falta el Client ID de Spotify |
| Compartir | QR / texto | Sin backend propio |
| DJ | Local, sin IA externa | Basado en canción actual, colecciones, Me gusta e historial |
| Reproductor | `MediaSessionService` | Música en segundo plano y controles en notificación |
| Actualizaciones | Misma firma | Conserva Room (álbumes, Me gusta, listas e historial) al sobrescribir |

---

## Configuración de Spotify (única necesaria)

Esta build usa **solo Spotify** como acceso social. El registro con correo/Gmail funciona en modo local sin credenciales. Para entrar con Spotify e importar playlists:

1. Crea una app en el [Dashboard de desarrolladores de Spotify](https://developer.spotify.com/dashboard).
2. Añade `ondaoauth://spotify/callback` en *Redirect URIs*.
3. Configura:

```properties
onda.spotify.clientId=tu-client-id
onda.spotify.redirectUri=ondaoauth://spotify/callback
```

O las variables `ONDA_SPOTIFY_CLIENT_ID` / `ONDA_SPOTIFY_REDIRECT_URI`. Se usa PKCE, así que **no hace falta Client Secret**. También puedes pegar el Client ID directamente en **Tu perfil → Guardar Client ID**, sin recompilar ni dármelo a mí. Sin Client ID, el botón explica qué configurar.

GitHub Actions lee estos mismos valores desde secrets: `ONDA_SPOTIFY_CLIENT_ID` y `ONDA_SPOTIFY_REDIRECT_URI`.

### Configuración en un paso

Ejecuta el asistente; te pide el Client ID y el redirect, escribe `local.properties` y crea los GitHub Secrets:

```powershell
.\scripts\configure-onda.ps1
```

Opciones: `-SkipGithub` (solo local) y `-Build` (compila release y verifica firma al terminar). Requiere `gh auth login` previo. Después crea un tag `v1.1.7` para publicar el APK con Spotify activado.

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

Secrets: `ONDA_KEYSTORE_BASE64`, `ONDA_STORE_PASSWORD`, `ONDA_KEY_PASSWORD`, `ONDA_KEY_ALIAS`, `ONDA_SPOTIFY_CLIENT_ID` y `ONDA_SPOTIFY_REDIRECT_URI`.

Nueva versión:

```powershell
git tag v1.1.7
git push origin v1.1.7
```

---

## Aviso legal

La interfaz se inspira en patrones habituales de reproductores de música, pero **no usa** el nombre, el logo ni los recursos gráficos de Spotify. El catálogo se obtiene mediante NewPipeExtractor desde fuentes públicas; el usuario es responsable del uso de esas fuentes y de sus condiciones.

---

## Estado

- ✅ v1.1.7: acceso social reducido a Spotify; registro por correo/Gmail en modo local; asistente compatible con PowerShell 5.1 y 7.
- ✅ Compilan debug y release de v1.1.6 (YouTube, refresh de Spotify, secretos en CI y registro completo).
- ✅ Release firmada y generada por GitHub Actions.
- ⏳ Pruebas en dispositivo físico: streams reales, notificación en segundo plano y permiso de instalación.
