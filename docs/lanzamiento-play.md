# Lanzamiento en Google Play (cuenta personal)

*Plan del 2026-11-04. Las reglas de Google cambian: contrasta cada punto con la ayuda de la Play Console. Lo marcado «sin confirmar» no se pudo verificar en una fuente oficial.*

Enlaces: alta de la cuenta <https://play.google.com/console/signup> · consola <https://play.google.com/console>

## Decisión tomada
- **Cuenta personal.** Si se creó después del 13 de noviembre de 2023, exige una **prueba cerrada con al menos 12 testers, inscritos sin interrupción durante 14 días**, antes de poder pedir el acceso a producción (sin confirmar en la página oficial de Google). Cumplir el mínimo no garantiza que lo aprueben.
- **Nombre en la tienda:** «Mado · Director's Viewfinder» (28 de 30 caracteres). El ícono del teléfono se llama «Mado». El identificador interno `app.mado` no cambia después de publicar, y tampoco se ve.
- **App de pago.** El precio se declara al crear la app; una app gratuita no puede pasar a pago después (sin confirmar). Necesita un **perfil de pagos (comerciante)**; no se pudo confirmar que Chile esté entre los países admitidos para vender: revisar la lista oficial de Google antes de empezar.

## Pasos
1. **Crear la cuenta de desarrollador** (25 USD, pago único) y verificar identidad, teléfono y dirección.
2. **Perfil de pagos** para vender (vínculo único con la consola) y datos fiscales.
3. **Clave de subida** (una sola vez, fuera del repositorio):
   `keytool -genkeypair -v -keystore ~/mado-upload.jks -alias mado -keyalg RSA -keysize 2048 -validity 10000`
   Guardar archivo y contraseñas en dos lugares. Crear `keystore.properties` en la raíz con `storeFile`, `storePassword`, `keyAlias` y `keyPassword` (ya está ignorado por git).
4. **Paquete de tienda:** `gradle :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` (firmado si existe `keystore.properties`). Activar **Play App Signing**.
5. **Crear la app** en la consola (nombre, idioma, aplicación, de pago).
6. **Ficha:** textos en `docs/ficha-tienda.md`; ícono `docs/play/icon-512.png`; imagen destacada y 8 capturas por idioma en `docs/play/<es|en|ja>/` (las capturas actuales son del emulador: conviene reemplazarlas por capturas reales del S26 con lentes calibrados, sin el aviso de calibración). Falta el **correo de contacto** y una **dirección pública** para la política de privacidad (`docs/politica-privacidad.md`).
7. **Contenido de la app:** política de privacidad; sin anuncios; sin acceso con cuenta; clasificación de edad; **seguridad de los datos** (Mado no envía datos a ningún lado; la ubicación opcional se guarda solo en la foto); permisos de cámara y ubicación.
8. **Prueba interna** (hasta 100 testers) para comprobar que el paquete instala.
9. **Prueba cerrada** con **12 o más testers** por correo de Google, **14 días seguidos**. Ver el plan de testers abajo.
10. **Solicitar acceso a producción**, elegir precio y países, y publicar. Calcular **tres a cuatro semanas** de punta a punta.

## Plan de testers (los 12 de la prueba cerrada)
- Reclutar **al menos 14** personas con cuenta de Google (margen por si alguien se baja): Edo, Felipe, otros videógrafos y los que ayudan a calibrar con teléfonos distintos.
- Registrar a cada uno en `privado/registro-probadores.csv` (nombre o correo, país, teléfono, cámaras, aporte, licencia gratis).
- Pedirles que **no se desinscriban** durante los 14 días y que abran la app al menos una vez; si alguien se sale antes, no cuenta.
- Mantener la prueba cerrada abierta después del lanzamiento: sirve para ofrecer a los probadores la app **gratis de por vida** (otra opción: códigos promocionales de la consola; sin confirmar sus límites).

## Qué ya está listo en este repositorio
Licencia propietaria (`LICENSE`), política de privacidad (borrador), configuración de firma, nombre y textos de la ficha en tres idiomas (el japonés sin revisar), ícono 512, imagen destacada y capturas con título, y los manuales de uso.
