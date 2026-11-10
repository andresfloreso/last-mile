import { useMemo } from "react";
import L from "leaflet";
import { Marker } from "react-leaflet";
import type { StopView } from "@/lib/routeView";
import { stopIcon } from "@/lib/markerIcons";

interface Props {
  stop: StopView;
  selected: boolean;
  draggable: boolean;
  onSelect: () => void;
  onDragEnd: (lat: number, lng: number) => void;
}

export default function StopMarker({ stop, selected, draggable, onSelect, onDragEnd }: Props) {
  const icon = useMemo(() => stopIcon(stop, selected), [stop, selected]);
  if (stop.latitude == null || stop.longitude == null) return null;
  const label = `Parada ${stop.position}: ${stop.name}${
    stop.shipmentCount > 1 ? ` (${stop.shipmentCount} envíos)` : ""
  }, ${stop.eta}${stop.outsideWindow ? ", fuera de ventana" : ""}${
    stop.needsReview ? ", ubicación por revisar" : ""
  }`;
  return (
    <Marker
      position={[stop.latitude, stop.longitude]}
      icon={icon}
      title={label}
      alt={label}
      keyboard
      draggable={draggable}
      zIndexOffset={selected ? 1000 : 0}
      eventHandlers={{
        click: onSelect,
        dragend: (e) => {
          const p = (e.target as L.Marker).getLatLng();
          onDragEnd(p.lat, p.lng);
        },
      }}
    />
  );
}
