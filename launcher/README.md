# Verdania Launcher 0.7.0

Inicio directo con botón único: morado para verificar, verde para lanzar.
Cada nuevo proceso exige verificar los archivos; cambiar de perfil también.
El menú nativo File/Edit está eliminado. Icono TNT/V con transparencia.
Versiones y perfiles en tarjetas, selector junto al botón principal.
Los perfiles personales usan Minecraft 1.21.1 Fabric y el pack Verdania;
cada uno conserva sus propios mundos, mods y opciones.
Ajustes de memoria: 2 a 16 GB. Inicio: 4 GB.
Amigos: interfaz en desarrollo, sin servicio social conectado.
Avisos breves explican Prism antes de autenticar y del primer lanzamiento.

Autenticación y preparación del juego mediante Prism Launcher oficial.
Las cuentas existentes se conservan en los mismos datos de la aplicación.

Instalador: Verdania-Launcher-0.7.0-Setup.exe.
Construir: npm ci, npm run dist (Node.js 22+).
Pruebas: node --test test/*.test.cjs.
Perfil de archivos: 0.31.1 (31 mods, 3 resourcepacks, 12 archivos FancyMenu).
Manifiesto: node create-client-manifest.mjs.
Validación del pack: node verify-release.mjs.

GitHub sigue usando VoodKaRL/world-progression-pack hasta que el propietario
indique la URL del repositorio nuevo. Publicar los assets individualmente
en el release estable v0.31.1 y marcarlo como Latest.
El launcher no publica archivos automáticamente.

Logo: fondo eliminado con la herramienta integrada imagegen.
Instrucción: eliminar solo el fondo y la sombra externa; conservar la TNT,
la V, los colores y la perspectiva; generar transparencia real.
Asset: src/assets/verdania-logo.png y exportación Windows verdania.ico.
