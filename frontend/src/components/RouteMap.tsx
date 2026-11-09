import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { MapContainer, Marker, Polyline, TileLayer } from "react-leaflet";
import type L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { TimeWindow } from "@/types";
import type { StopView } from "@/lib/routeView";
import { DEPOT, TILE_ATTRIBUTION, TILE_URL } from "@/lib/config";
import type { Theme } from "@/store/themeStore";
import StopMarker from "@/components/StopMarker";
import { depotIcon } from "@/lib/markerIcons";
import StopDetailCard from "@/components/StopDetailCard";
import WindowLegend from "@/components/WindowLegend";
import type { DeclaredWindowPayload } from "@/lib/declaredWindow";
import { MinusIcon, PlusIcon } from "@/components/icons";

interface Props {
  stops: StopView[];
  theme: Theme;
  selectedId: string | null;
  /** Cambia cada vez que la lista pide centrar la parada seleccionada. */
  centerKey: number;
  relocatingId: string | null;
  timeWindow: TimeWindow | undefined;
  onSelect: (shipmentId: string) => void;
  onCloseCard: () => void;
  onSaveDeclared: (payload: DeclaredWindowPayload) => Promise<void>;
  onStartRelocate: () => void;
  onRelocated: (lat: number, lng: number) => void;
  /** Botón para abrir el panel en tablet (se dibuja bajo la leyenda). */
  leftAction?: React.ReactNode;
}

/** Margen alrededor de la tarjeta dentro del cual un marcador se considera tapado. */
const COVER_MARGIN = 24;

/** Ruta en el color de la polilínea del tema (token --color-route). */
function routeColor(): string {
  return getComputedStyle(document.documentElement).getPropertyValue("--color-route").trim();
}

export default function RouteMap(props: Props) {
  const { stops, theme, selectedId, centerKey, relocatingId, timeWindow, onSelect, onCloseCard } = props;
  const [map, setMap] = useState<L.Map | null>(null);
  const wrapper = useRef<HTMLDivElement>(null);
  const card = useRef<HTMLDivElement>(null);
  const [, setTick] = useState(0);
  const [color, setColor] = useState(routeColor);

  const selected = stops.find((s) => s.shipmentId === selectedId) ?? null;
  const ordered = useMemo(() => [...stops].sort((a, b) => a.position - b.position), [stops]);
  const polyline = useMemo(() => ordered.flatMap((s) => s.path), [ordered]);
  const located = useMemo(
    () => ordered.filter((s) => s.latitude != null && s.longitude != null).map((s) => [s.latitude!, s.longitude!] as [number, number]),
    [ordered]
  );
  const stopsKey = ordered.map((s) => s.shipmentId).join(",");

  // El color de la polilínea sigue al tema (los tokens cambian al cambiar la clase "dark").
  useEffect(() => setColor(routeColor()), [theme]);

  // Encuadra la ruta completa (con la bodega) al cargar una ruta nueva.
  useEffect(() => {
    if (!map || located.length === 0) return;
    map.fitBounds([[DEPOT.lat, DEPOT.lng], ...located], { paddingTopLeft: [40, 80], paddingBottomRight: [360, 40] });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, stopsKey]);

  // Redibuja la línea guía cuando el mapa se mueve.
  useEffect(() => {
    if (!map) return;
    const bump = () => setTick((t) => t + 1);
    map.on("move zoom resize", bump);
    return () => {
      map.off("move zoom resize", bump);
    };
  }, [map]);

  // Desde la lista: centrar la parada seleccionada.
  useEffect(() => {
    if (!map || !selected || selected.latitude == null || selected.longitude == null || centerKey === 0) return;
    map.panTo([selected.latitude, selected.longitude]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, centerKey]);

  // La tarjeta nunca tapa el marcador seleccionado: si lo haría, el mapa se corre a la izquierda.
  useLayoutEffect(() => {
    if (!map || !selected || !card.current || selected.latitude == null || selected.longitude == null) return;
    const check = () => {
      if (!card.current) return;
      const p = map.latLngToContainerPoint([selected.latitude!, selected.longitude!]);
      const r = card.current.getBoundingClientRect();
      const w = wrapper.current!.getBoundingClientRect();
      const left = r.left - w.left - COVER_MARGIN;
      const bottom = r.bottom - w.top + COVER_MARGIN;
      if (p.x > left && p.y < bottom) map.panBy([p.x - left + 32, 0], { animate: true });
    };
    map.once("moveend", check);
    check();
    return () => {
      map.off("moveend", check);
    };
  }, [map, selected]);

  const guide = (() => {
    if (!map || !selected || !card.current || !wrapper.current || selected.latitude == null) return null;
    const p = map.latLngToContainerPoint([selected.latitude, selected.longitude!]);
    const r = card.current.getBoundingClientRect();
    const w = wrapper.current.getBoundingClientRect();
    const x1 = r.left - w.left;
    const y1 = Math.min(Math.max(p.y, r.top - w.top + 24), r.bottom - w.top - 24);
    if (p.x < 0 || p.y < 0 || p.x > w.width || p.y > w.height) return null;
    return { x1, y1, x2: p.x, y2: p.y };
  })();

  return (
    <div ref={wrapper} className="relative h-full w-full" role="region" aria-label="Mapa de la ruta del día">
      <MapContainer
        ref={setMap}
        center={[DEPOT.lat, DEPOT.lng]}
        zoom={13}
        zoomControl={false}
        className="h-full w-full"
      >
        <TileLayer key={theme} url={TILE_URL[theme]} attribution={TILE_ATTRIBUTION} subdomains="abcd" maxZoom={19} />
        {polyline.length > 1 && <Polyline positions={polyline} pathOptions={{ color, weight: 5, opacity: 0.9 }} />}
        <Marker position={[DEPOT.lat, DEPOT.lng]} icon={depotIcon()} title="Bodega" alt="Bodega" keyboard={false} />
        {ordered.map((s) => (
          <StopMarker
            key={s.shipmentId}
            stop={s}
            selected={s.shipmentId === selectedId}
            draggable={s.shipmentId === relocatingId}
            onSelect={() => onSelect(s.shipmentId)}
            onDragEnd={props.onRelocated}
          />
        ))}
      </MapContainer>

      {/* Capas sobre el mapa. El mapa tiene su propio contexto de apilamiento (index.css). */}
      <div className="pointer-events-none absolute inset-0 z-10">
        <div className="pointer-events-auto absolute left-4 top-4 flex max-w-[calc(100%-120px)] flex-col items-start gap-2">
          <WindowLegend />
          {props.leftAction}
        </div>

        <div className="pointer-events-auto absolute right-4 top-4 flex flex-col gap-2">
          <button
            type="button"
            aria-label="Acercar el mapa"
            onClick={() => map?.zoomIn()}
            className="flex h-10 w-10 items-center justify-center rounded-lg border border-line bg-float text-ink hover:bg-tile"
          >
            <PlusIcon />
          </button>
          <button
            type="button"
            aria-label="Alejar el mapa"
            onClick={() => map?.zoomOut()}
            className="flex h-10 w-10 items-center justify-center rounded-lg border border-line bg-float text-ink hover:bg-tile"
          >
            <MinusIcon />
          </button>
        </div>

        {guide && (
          <svg className="absolute inset-0 h-full w-full" aria-hidden="true">
            <line x1={guide.x1} y1={guide.y1} x2={guide.x2} y2={guide.y2} stroke="var(--color-muted)" strokeWidth={1} strokeDasharray="3 3" />
          </svg>
        )}

        {selected && (
          <div className="pointer-events-auto absolute right-4 top-[120px] max-h-[calc(100%-136px)] overflow-y-auto">
            <StopDetailCard
              ref={card}
              key={selected.shipmentId}
              stop={selected}
              timeWindow={timeWindow}
              relocating={relocatingId === selected.shipmentId}
              onClose={onCloseCard}
              onSaveDeclared={props.onSaveDeclared}
              onStartRelocate={props.onStartRelocate}
            />
          </div>
        )}
      </div>
    </div>
  );
}
