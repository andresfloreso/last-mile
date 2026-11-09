# ---- Etapa de build ----
FROM node:20-alpine AS build
WORKDIR /app
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
# Mapa base (HU-27). Vacías = Esri Gray Canvas (ver src/lib/config.ts).
ARG VITE_TILE_URL_LIGHT=
ARG VITE_TILE_URL_DARK=
ARG VITE_TILE_ATTRIBUTION=
ARG VITE_TILE_MAX_NATIVE_ZOOM=
ENV VITE_TILE_URL_LIGHT=$VITE_TILE_URL_LIGHT     VITE_TILE_URL_DARK=$VITE_TILE_URL_DARK     VITE_TILE_ATTRIBUTION=$VITE_TILE_ATTRIBUTION     VITE_TILE_MAX_NATIVE_ZOOM=$VITE_TILE_MAX_NATIVE_ZOOM
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Etapa de runtime ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
