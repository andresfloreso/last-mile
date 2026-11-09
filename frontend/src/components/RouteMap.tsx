import { useEffect } from "react";
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { DraftStop } from "@/types";

// Los iconos por defecto de Leaflet no cargan bien con bundlers (Vite) porque
// referencian rutas relativas a los assets del paquete; se reconstruyen
// explícitamente aquí para que los pines se vean correctamente.
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
});

/** Color de los locales con ubicación aproximada (HU-09: "revisar"). */
export const REVIEW_COLOR = "#7c3aed";

function numberedIcon(position: number, color: string, needsReview = false) {
  // Un local "por revisar" lleva borde punteado y un "!" para distinguirlo sin depender solo del color.
  const border = needsReview ? "2px dashed white" : "2px solid white";
  const badge = needsReview
    ? `<span style="position:absolute;top:-6px;right:-6px;background:#fff;color:${REVIEW_COLOR};width:14px;height:14px;border-radius:50%;font-size:11px;line-height:14px;text-align:center;font-weight:800">!</span>`
    : "";
  return L.divIcon({
    className: "custom-numbered-marker",
    html: `<div style="position:relative;background:${color};width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:white;font-weight:700;font-size:13px;border:${border};box-shadow:0 1px 4px rgba(0,0,0,0.4)">${position}${badge}</div>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

function currentLocationIcon() {
  return L.divIcon({
    className: "current-location-marker",
    html: `<div style="background:#0d6efd;width:18px;height:18px;border-radius:50%;border:3px solid white;box-shadow:0 0 0 4px rgba(13,110,253,0.25)"></div>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  });
}

/** Recentra el mapa automáticamente cuando cambia el conjunto de puntos a mostrar. */
function FitBounds({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 0) return;
    if (points.length === 1) {
      map.setView(points[0], 15);
    } else {
      map.fitBounds(points, { padding: [40, 40] });
    }
  }, [points, map]);
  return null;
}

interface RouteMapProps {
  stops: DraftStop[];
  orderedPositions?: Record<string, number>; // tempId/shipmentId -> posición óptima (post-optimización)
  currentLocation?: { lat: number; lng: number } | null;
}

export default function RouteMap({ stops, orderedPositions, currentLocation }: RouteMapProps) {
  const geolocated = stops.filter((s) => s.latitude != null && s.longitude != null);

  const points: [number, number][] = geolocated.map((s) => [s.latitude!, s.longitude!]);
  if (currentLocation) points.push([currentLocation.lat, currentLocation.lng]);

  const defaultCenter: [number, number] = points[0] ?? [-33.4489, -70.6693]; // Santiago, Chile

  // Si hay orden calculado, se dibuja la polilínea siguiendo ese orden.
  // Sin orden calculado no se dibuja la línea: unir las paradas en el orden de la lista no representa una ruta.
  const hasOrder = orderedPositions != null && Object.keys(orderedPositions).length > 0;
  const orderedGeolocated = hasOrder
    ? [...geolocated].sort(
        (a, b) =>
          (orderedPositions[a.shipmentId ?? a.tempId] ?? 999) -
          (orderedPositions[b.shipmentId ?? b.tempId] ?? 999)
      )
    : [];

  const polylinePoints: [number, number][] = [];
  if (currentLocation) polylinePoints.push([currentLocation.lat, currentLocation.lng]);
  orderedGeolocated.forEach((s) => polylinePoints.push([s.latitude!, s.longitude!]));

  return (
    <MapContainer center={defaultCenter} zoom={13} style={{ height: "100%", width: "100%" }}>
      {/* Tiles de OpenStreetMap — mismo dataset que usa Nominatim para geocodificar,
          así el pin y el mapa base siempre están alineados a la misma fuente. */}
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      <FitBounds points={points} />

      {currentLocation && (
        <Marker position={[currentLocation.lat, currentLocation.lng]} icon={currentLocationIcon()}>
          <Popup>Tu ubicación actual</Popup>
        </Marker>
      )}

      {geolocated.map((stop) => {
        const key = stop.shipmentId ?? stop.tempId;
        const position = orderedPositions?.[key];
        const color = stop.needsReview ? REVIEW_COLOR : stop.stopType === "PICKUP" ? "#f59e0b" : "#0d6efd";
        return (
          <Marker
            key={key}
            position={[stop.latitude!, stop.longitude!]}
            icon={numberedIcon(position ?? 0, color, stop.needsReview)}
          >
            <Popup>
              <strong>{stop.customerName}</strong>
              <br />
              {stop.address}
              <br />
              <span className="text-xs text-slate-500">
                {stop.stopType === "PICKUP" ? "Recogida" : "Entrega"} · {stop.packageCount} paquete(s)
              </span>
              {stop.needsReview && (
                <>
                  <br />
                  <span style={{ color: REVIEW_COLOR, fontWeight: 600 }}>
                    Revisar: ubicación aproximada, confirma la dirección
                  </span>
                </>
              )}
            </Popup>
          </Marker>
        );
      })}

      {polylinePoints.length > 1 && (
        <Polyline positions={polylinePoints} pathOptions={{ color: "#0d6efd", weight: 4, opacity: 0.7 }} />
      )}
    </MapContainer>
  );
}
