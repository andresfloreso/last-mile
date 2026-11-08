# Hallazgos fuera de alcance

Bugs y deuda técnica detectados durante los sprints que **no** se corrigieron
porque estaban fuera del alcance de la historia en curso. Cada hallazgo debe
convertirse en una historia o tarea del backlog.

| # | Sprint | Servicio | Hallazgo | Impacto | Sugerencia |
|---|---|---|---|---|---|
| 1 | S1 (HU-05) | api-gateway | `JwtAuthenticationFilter` no elimina los headers `X-User-Id` / `X-User-Role` que envía el cliente: en rutas públicas pasan intactos y en las protegidas se agrega un valor en vez de reemplazarlo. ([#9](https://github.com/andresfloreso/last-mile/issues/9)) | Bajo hoy (ningún servicio confía en esos headers), alto si se empiezan a usar | Quitar ambos headers de la request entrante antes de setearlos desde el JWT |
