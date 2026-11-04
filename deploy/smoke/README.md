# Despliegue HTTPS del servicio de humo

1. Copie este archivo como `.env` en la raíz del repositorio y ajuste las rutas PEM.
2. Coloque el certificado y su clave privada en rutas locales que no se versionen.
   El certificado debe poder leerse en el contenedor; para la clave, use permisos `0400` y
   propietario UID `10001` en despliegues con Docker rootful.
3. Ejecute `docker compose up --build -d`.
4. Compruebe `https://localhost:8443/api/health` usando el certificado correspondiente.

Compose monta el certificado y la clave como secretos de solo lectura y activa TLS en Spring
Boot. La clave privada no debe incluirse en la imagen ni en el repositorio. El contenedor limita
la memoria a 512 MiB y reserva como máximo el 75 % del límite para el heap. Al iniciar, la
aplicación registra el uso/compromiso/máximo del heap y el uso de memoria de la JVM.
