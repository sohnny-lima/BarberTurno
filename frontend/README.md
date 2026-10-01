# Frontend de BarberTurno

Esqueleto Angular 22 con componentes standalone, signals, OnPush y Vitest, sin zone.js. Angular Material usa el tema M3 de navy y teal del prototipo.

Active Node 24.21.0 mediante fnm y el archivo `.node-version` de la raíz, como se explica en el [README del proyecto](../README.md#arranque-y-pruebas-del-frontend). Desde esta carpeta:

```powershell
npm ci
npm start
```

Abra `http://localhost:4200`. El proxy de desarrollo reenvía `/api` a `http://localhost:8080`; arranque el backend en dev para usarlo. Ctrl+C detiene el servidor.

```powershell
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
```

`npm run format` aplica Prettier solo a este frontend. El build de producción se genera en `dist/frontend/browser`.

`src/app/core` contendrá servicios de API, sesión, modelos y tiempo; `shared`, componentes compartidos; `layout`, la navegación; y `features`, las pantallas por funcionalidad. Su implementación corresponde a tareas posteriores. Toda regla de negocio se resuelve en el backend.

El tema se generó con el schematic oficial `@angular/material:theme-color`, usando `#173c4d` como primario y `#087f8c` como secundario y terciario. Las paletas están en `src/theme-colors.scss`; los estilos globales usan una fuente del sistema sin descargas externas.
