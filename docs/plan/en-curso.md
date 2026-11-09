# En curso (Sprint 3)

- **Rama:** `feature/HU-27-pantalla-rutas` · PR #56 · issue #42.
- **Hecho:** HU-27 completa, con 35 tests de Vitest y E2E 32/32. PRs abiertos: #48, #49, #50, #51, #53, #55 y #56.

## Falta
1. **Punto 4 del dueño.** Cuando se mergee #48:
   - Actualizar #49 con `main` y dejar un solo `BusinessType`. La resolución ya está hecha en el commit `90a4426` de esta rama: `git checkout 90a4426 -- frontend/src/types/index.ts frontend/src/api/endpoints.ts frontend/src/App.tsx docs/api/README.md`.
   - Luego hacer lo mismo con #50 después de #49. La resolución está en el commit `bab3563` (`DeliveryDtos.java`, `docs/api/README.md`).
2. **Decisión del dueño:** el mapa base. CARTO exige API key, y por ahora se usa Esri (ver `docs/design/README.md`).
3. **Cierre del sprint:** `docs/plan/sprint-3-cierre.md` y `hallazgos.md` al día:
   - Filas 5 (#29) y 7 (#36): corregidas.
   - Fila 9 (#44): corregida en #51.
   - Hallazgo nuevo: CARTO exige API key.
   - Hallazgo nuevo: `/api/routes/build` se llama al abrir la pantalla, porque no hay un `GET` de la ruta.
   - Hallazgo nuevo: la primera visita a 30 días no tiene backend.
4. Al cerrar el ítem, borrar este archivo en el mismo PR.

**Siguiente paso exacto:** `gh pr view 48 --json state`. Si está mergeado: `git checkout feature/HU-11-ventana-real && git merge origin/main` y aplicar la resolución del punto 1.
