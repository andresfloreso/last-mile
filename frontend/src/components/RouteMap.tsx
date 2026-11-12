import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { MapContainer, Marker, Polyline, TileLayer } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { TimeWindow } from "@/types";
import type { StopView } from "@/lib/routeView";
import { DEPARTURE_TIME, DEPOT, TILE_ATTRIBUTION, TILE_MAX_NATIVE_ZOOM, TILE_URL } from "@/lib/config";
import { routeSegments } from "@/lib/routeEdit";
import { BANDS } from "@/lib/timeWindows";
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
  /** HU-15: vuelta a la bodega desde la última parada, si se conoce (armado manual). */
  returnPath?: [number, number][];
  /** HU-15: la ruta antes de los cambios sin guardar, en gris punteado. */
  ghostLine?: [number, number][] | null;
  /** HU-15: la línea mientras se arrastra una parada (orden provisional, aún sin horas). */
  draftLine?: [number, number][] | null;
}

/** Margen alrededor de la tarjeta dentro del cual un marcador se considera tapado. */
const COVER_MARGIN = 24;

/**
 * Tolerancia de simplificación de la polilínea, en píxeles de pantalla (HU-32). Leaflet la aplica
 * de nuevo en cada zoom (Douglas-Peucker): alejado dibuja pocos puntos; cerca, el detalle de la calle.
 */
const ROUTE_SMOOTH_FACTOR = 1.5;

/**
 * Margen que Leaflet dibuja fuera de la pantalla, como fracción del mapa visible (#86). Leaflet solo
 * vuelve a dibujar las líneas al soltar el mapa: con el margen por defecto (0,1), al arrastrar más de un
 * 10 % la línea aparece cortada y se completa al soltar. Con 1, cubre un arrastre de una pantalla entera.
 */
const ROUTE_PADDING = 1;

/** Valor de un token CSS del tema (por ejemplo --color-route o --band-morning). */
function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/** Colores de la línea: la ruta (mientras se arrastra), cada franja (HU-15) y la ruta original en gris. */
function lineColors() {
  return {
    route: cssVar("--color-route"),
    ghost: cssVar("--color-muted"),
    bands: Object.fromEntries(BANDS.map((b) => [b.id, cssVar(`--band-${b.id}`)])) as Record<string, string>,
  };
}

export default function RouteMap(props: Props) {
  const { stops, theme, selectedId, centerKey, relocatingId, timeWindow, onSelect, onCloseCard } = props;
  const [map, setMap] = useState<L.Map | null>(null);
  const wrapper = useRef<HTMLDivElement>(null);
  const card = useRef<HTMLDivElement>(null);
  const [colors, setColors] = useState(lineColors);
  // La polilínea va en SVG y no en el canvas de preferCanvas (#86): con un margen de una pantalla, un canvas
  // tendría 9 veces los píxeles del mapa; el SVG solo guarda el trazado, ya simplificado por zoom (HU-32).
  const routeRenderer = useMemo(() => L.svg({ padding: ROUTE_PADDING }), []);

  const selected = stops.find((s) => s.shipmentId === selectedId) ?? null;
  const ordered = useMemo(() => [...stops].sort((a, b) => a.position - b.position), [stops]);
  // HU-15: la línea va por tramos, del color de la hora a la que el camión los maneja.
  const segments = useMemo(() => routeSegments(ordered, DEPARTURE_TIME, props.returnPath), [ordered, props.returnPath]);
  const located = useMemo(
    () => ordered.filter((s) => s.latitude != null && s.longitude != null).map((s) => [s.latitude!, s.longitude!] as [number, number]),
    [ordered]
  );
  // Encuadra cuando cambian las paradas, no su orden (HU-15: al editar, el mapa no salta en cada cambio).
  const stopsKey = ordered.map((s) => s.shipmentId).sort().join(",");

  // El color de la polilínea sigue al tema (los tokens cambian al cambiar la clase "dark").
  useEffect(() => setColors(lineColors()), [theme]);

  // Encuadra la ruta completa (con la bodega) al cargar una ruta nueva.
  useEffect(() => {
    if (!map || located.length === 0) return;
    map.fitBounds([[DEPOT.lat, DEPOT.lng], ...located], { paddingTopLeft: [40, 80], paddingBottomRight: [360, 40] });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, stopsKey]);

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

  return (
    <div ref={wrapper} className="relative h-full w-full" role="region" aria-label="Mapa de la ruta del día">
      <MapContainer
        ref={setMap}
        center={[DEPOT.lat, DEPOT.lng]}
        zoom={13}
        zoomControl={false}
        preferCanvas
        className="h-full w-full"
      >
        <TileLayer
          key={theme}
          url={TILE_URL[theme]}
          attribution={TILE_ATTRIBUTION}
          subdomains="abcd"
          maxZoom={19}
          maxNativeZoom={TILE_MAX_NATIVE_ZOOM}
        />
        {props.ghostLine && props.ghostLine.length > 1 && (
          <Polyline
            positions={props.ghostLine}
            renderer={routeRenderer}
            pathOptions={{ color: colors.ghost, weight: 4, opacity: 0.8, dashArray: "6 8" }}
            smoothFactor={ROUTE_SMOOTH_FACTOR}
          />
        )}
        {props.draftLine && props.draftLine.length > 1 ? (
          <Polyline
            positions={props.draftLine}
            renderer={routeRenderer}
            pathOptions={{ color: colors.route, weight: 5, opacity: 0.9 }}
            smoothFactor={ROUTE_SMOOTH_FACTOR}
          />
        ) : (
          segments.map((seg, i) => (
            <Polyline
              key={`${i}-${seg.band}-${seg.positions.length}`}
              positions={seg.positions}
              renderer={routeRenderer}
              pathOptions={{ color: colors.bands[seg.band], weight: 5, opacity: 0.9 }}
              smoothFactor={ROUTE_SMOOTH_FACTOR}
            />
          ))
        )}
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
          <WindowLegend showGhost={!!props.ghostLine} />
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

        {map && selected && selected.latitude != null && selected.longitude != null && (
          <GuideLine map={map} lat={selected.latitude} lng={selected.longitude} card={card} wrapper={wrapper} />
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

interface GuideProps {
  map: L.Map;
  lat: number;
  lng: number;
  card: React.RefObject<HTMLDivElement>;
  wrapper: React.RefObject<HTMLDivElement>;
}

/**
 * Línea punteada de la tarjeta al marcador seleccionado. Sigue al mapa mientras se mueve: es lo único
 * que se vuelve a dibujar en cada cuadro (HU-32), no los marcadores ni la polilínea.
 */
function GuideLine({ map, lat, lng, card, wrapper }: GuideProps) {
  const [, setTick] = useState(0);
  useEffect(() => {
    const bump = () => setTick((t) => t + 1);
    map.on("move zoom resize", bump);
    return () => {
      map.off("move zoom resize", bump);
    };
  }, [map]);

  if (!card.current || !wrapper.current) return null;
  const p = map.latLngToContainerPoint([lat, lng]);
  const r = card.current.getBoundingClientRect();
  const w = wrapper.current.getBoundingClientRect();
  if (p.x < 0 || p.y < 0 || p.x > w.width || p.y > w.height) return null;
  const x1 = r.left - w.left;
  const y1 = Math.min(Math.max(p.y, r.top - w.top + 24), r.bottom - w.top - 24);
  return (
    <svg className="absolute inset-0 h-full w-full" aria-hidden="true">
      <line x1={x1} y1={y1} x2={p.x} y2={p.y} stroke="var(--color-muted)" strokeWidth={1} strokeDasharray="3 3" />
    </svg>
  );
}
