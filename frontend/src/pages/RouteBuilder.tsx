import { useEffect, useState } from "react";
import Layout from "@/components/Layout";
import AddressSearch from "@/components/AddressSearch";
import StopEditor from "@/components/StopEditor";
import RouteMap, { REVIEW_COLOR } from "@/components/RouteMap";
import RouteSchedule from "@/components/RouteSchedule";
import { routesApi, shipmentsApi } from "@/api/endpoints";
import type { DraftStop, GeocodeResult, GeocodeStatus, OrderPreference, RouteStop, StopType } from "@/types";

/** Envío tal como lo devuelve GET /api/shipments (solo los campos que usa la pantalla). */
interface ShipmentListItem {
  id: string;
  stopType: StopType;
  packageCount: number;
  orderPreference: OrderPreference;
  label?: string | null;
  notes?: string | null;
  customer: {
    id: string;
    fullName: string;
    address: string;
    commune?: string | null;
    phone?: string | null;
    latitude?: number | null;
    longitude?: number | null;
    geocodeStatus?: GeocodeStatus;
  };
}

function toDraftStop(s: ShipmentListItem): DraftStop {
  return {
    tempId: s.id,
    shipmentId: s.id,
    customerId: s.customer.id,
    customerName: s.customer.fullName,
    address: s.customer.address,
    commune: s.customer.commune ?? undefined,
    phone: s.customer.phone ?? undefined,
    latitude: s.customer.latitude ?? undefined,
    longitude: s.customer.longitude ?? undefined,
    stopType: s.stopType,
    packageCount: s.packageCount,
    orderPreference: s.orderPreference,
    label: s.label ?? "Default",
    notes: s.notes ?? "",
    needsReview: s.customer.geocodeStatus === "REVIEW",
  };
}

function newTempId() {
  return `tmp_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
}

export default function RouteBuilder() {
  const [routeName, setRouteName] = useState("Mi primera ruta");
  const [scheduledDate, setScheduledDate] = useState(new Date().toISOString().substring(0, 10));
  const [roundTrip, setRoundTrip] = useState(true);

  const [stops, setStops] = useState<DraftStop[]>([]);
  const [editingStopId, setEditingStopId] = useState<string | null>(null);

  const [currentLocation, setCurrentLocation] = useState<{ lat: number; lng: number; accuracyMeters: number } | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);

  const [optimizedRoute, setOptimizedRoute] = useState<RouteStop[]>([]);
  const [departureTime, setDepartureTime] = useState("08:00");
  const [optimizing, setOptimizing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // "Empezar desde la ubicación actual" — usa GPS de alta precisión (igual
  // que la app de referencia), sin caché (maximumAge: 0) para asegurar la
  // coordenada más exacta disponible en el dispositivo.
  useEffect(() => {
    if (!navigator.geolocation) {
      setLocationError("Este dispositivo no soporta geolocalización");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCurrentLocation({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          accuracyMeters: pos.coords.accuracy,
        });
      },
      () => setLocationError("No se pudo obtener tu ubicación exacta — revisa los permisos de GPS"),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  }, []);

  // Envíos ya cargados para la fecha (por ejemplo, desde una importación): se
  // muestran en la lista y en el mapa, con los locales "por revisar" marcados (HU-09).
  // Las paradas aún no guardadas se conservan al cambiar de fecha.
  useEffect(() => {
    let cancelled = false;
    shipmentsApi
      .listByDate(scheduledDate)
      .then((data: ShipmentListItem[]) => {
        if (cancelled) return;
        setOptimizedRoute([]);
        // Los locales por revisar van primero para que el despachador los vea sin buscarlos.
        const loaded = data.map(toDraftStop).sort((a, b) => Number(!!b.needsReview) - Number(!!a.needsReview));
        setStops((prev) => [...loaded, ...prev.filter((s) => !s.shipmentId)]);
      })
      .catch(() => !cancelled && setError("No se pudieron cargar los envíos de la fecha"));
    return () => {
      cancelled = true;
    };
  }, [scheduledDate]);

  function addStopFromSearch(address: string, geocode: GeocodeResult | null) {
    const stop: DraftStop = {
      tempId: newTempId(),
      customerName: address.split(",")[0] || address,
      address,
      latitude: geocode?.latitude,
      longitude: geocode?.longitude,
      needsReview: geocode?.needsReview ?? false,
      stopType: "DELIVERY",
      packageCount: 1,
      orderPreference: "AUTO",
      label: "Default",
      notes: "",
    };
    setStops((prev) => [...prev, stop]);
    setEditingStopId(stop.tempId);
  }

  function patchStop(id: string, patch: Partial<DraftStop>) {
    setStops((prev) =>
      prev.map((s) => ((s.shipmentId ?? s.tempId) === id ? { ...s, ...patch } : s))
    );
  }

  async function persistStopIfNeeded(stop: DraftStop): Promise<DraftStop> {
    if (stop.shipmentId) {
      // ya existe -> sólo sincroniza los campos editables
      await shipmentsApi.update(stop.shipmentId, {
        stopType: stop.stopType,
        packageCount: stop.packageCount,
        orderPreference: stop.orderPreference,
        label: stop.label,
        notes: stop.notes,
      });
      return stop;
    }
    const created = await shipmentsApi.createStop(stop, scheduledDate);
    return { ...stop, shipmentId: created.id };
  }

  async function handleDuplicate(id: string) {
    const stop = stops.find((s) => (s.shipmentId ?? s.tempId) === id);
    if (!stop) return;
    const persisted = await persistStopIfNeeded(stop);
    if (persisted.shipmentId) {
      const dup = await shipmentsApi.duplicate(persisted.shipmentId);
      setStops((prev) => [
        ...prev.map((s) => ((s.shipmentId ?? s.tempId) === id ? persisted : s)),
        { ...persisted, tempId: newTempId(), shipmentId: dup.id },
      ]);
    }
  }

  async function handleDelete(id: string) {
    const stop = stops.find((s) => (s.shipmentId ?? s.tempId) === id);
    if (stop?.shipmentId) {
      await shipmentsApi.remove(stop.shipmentId).catch(() => {});
    }
    setStops((prev) => prev.filter((s) => (s.shipmentId ?? s.tempId) !== id));
    setEditingStopId(null);
  }

  async function handleOptimize() {
    if (!currentLocation) {
      setError("Necesitamos tu ubicación actual (GPS) para calcular la ruta desde ahí");
      return;
    }
    if (stops.length === 0) {
      setError("Añade al menos una parada");
      return;
    }

    setOptimizing(true);
    setError(null);
    try {
      // 1) Asegura que todas las paradas existan en el backend (con coordenadas
      //    geocodificadas de forma precisa si el usuario no fijó lat/lng manual).
      const persisted: DraftStop[] = [];
      for (const stop of stops) {
        persisted.push(await persistStopIfNeeded(stop));
      }
      setStops(persisted);

      // 2) Calcula el orden óptimo (ventana horaria aprendida + distancia real por calle).
      const route = await routesApi.build(scheduledDate, currentLocation.lat, currentLocation.lng, departureTime);
      setOptimizedRoute(route);
    } catch (err: any) {
      setError(err?.response?.data?.message || "No se pudo optimizar la ruta");
    } finally {
      setOptimizing(false);
    }
  }

  const orderedPositions: Record<string, number> = {};
  optimizedRoute.forEach((r) => (orderedPositions[r.shipmentId] = r.position));

  const editingStop = stops.find((s) => (s.shipmentId ?? s.tempId) === editingStopId) ?? null;

  return (
    <Layout>
      <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Panel izquierdo: configuración + lista de paradas (réplica de la app de referencia) */}
        <div className="space-y-4">
          <div className="bg-slate-900 text-slate-100 rounded-2xl overflow-hidden">
            <div className="px-5 py-4 space-y-1 border-b border-slate-800">
              <p className="text-xs text-slate-500">{stops.length} parada{stops.length === 1 ? "" : "s"}</p>
              <input
                value={routeName}
                onChange={(e) => setRouteName(e.target.value)}
                className="bg-transparent text-xl font-bold outline-none w-full"
              />
            </div>

            <div className="px-5 py-3">
              <AddressSearch onSelect={addStopFromSearch} />
            </div>

            <div className="px-5 pb-2">
              <p className="text-xs uppercase text-slate-500 mb-2">Configuración de ruta</p>

              <div className="flex items-center justify-between py-2 border-t border-slate-800">
                <div>
                  <p className="text-sm font-medium">Empezar desde la ubicación actual</p>
                  <p className="text-xs text-slate-500">
                    {currentLocation
                      ? `Precisión GPS: ±${Math.round(currentLocation.accuracyMeters)} m`
                      : locationError || "Obteniendo ubicación…"}
                  </p>
                </div>
                <span className="text-brand-500">📍</span>
              </div>

              <div className="flex items-center justify-between py-2 border-t border-slate-800">
                <div>
                  <p className="text-sm font-medium">Viaje de ida y vuelta</p>
                  <p className="text-xs text-slate-500">Volver al punto de partida</p>
                </div>
                <button
                  onClick={() => setRoundTrip((v) => !v)}
                  className={`w-10 h-6 rounded-full flex items-center px-0.5 transition-colors ${
                    roundTrip ? "bg-brand-600 justify-end" : "bg-slate-700 justify-start"
                  }`}
                >
                  <span className="w-5 h-5 bg-white rounded-full" />
                </button>
              </div>

              <div className="flex items-center justify-between py-2 border-t border-slate-800">
                <div>
                  <p className="text-sm font-medium">Fecha de entrega</p>
                </div>
                <input
                  type="date"
                  value={scheduledDate}
                  onChange={(e) => setScheduledDate(e.target.value)}
                  className="bg-slate-800 rounded-md px-2 py-1 text-sm"
                />
              </div>

              <div className="flex items-center justify-between py-2 border-t border-slate-800">
                <div>
                  <p className="text-sm font-medium">Hora de salida</p>
                  <p className="text-xs text-slate-500">Desde el punto de partida; con ella se estima la hora de cada parada</p>
                </div>
                <input
                  type="time"
                  value={departureTime}
                  onChange={(e) => setDepartureTime(e.target.value)}
                  className="bg-slate-800 rounded-md px-2 py-1 text-sm"
                  aria-label="Hora de salida"
                />
              </div>
            </div>

            <div className="px-5 pt-2">
              <p className="text-xs uppercase text-slate-500 mb-2 border-t border-slate-800 pt-3">Paradas</p>
              <div className="space-y-2 max-h-80 overflow-y-auto">
                {stops.map((stop) => {
                  const id = stop.shipmentId ?? stop.tempId;
                  const position = orderedPositions[stop.shipmentId ?? ""];
                  return (
                    <button
                      key={id}
                      onClick={() => setEditingStopId(id)}
                      className="w-full flex items-center justify-between bg-slate-800 hover:bg-slate-700 rounded-lg px-3 py-2 text-left"
                    >
                      <div className="flex items-center gap-3">
                        <span className="w-6 h-6 rounded-full bg-brand-600 text-white text-xs flex items-center justify-center font-bold">
                          {position ?? "•"}
                        </span>
                        <div>
                          <p className="text-sm font-medium">
                            {stop.customerName}
                            {stop.needsReview && (
                              <span
                                className="ml-2 rounded px-1.5 py-0.5 text-[10px] font-semibold text-white align-middle"
                                style={{ backgroundColor: REVIEW_COLOR }}
                                title="Ubicación aproximada: confirma la dirección"
                              >
                                Revisar
                              </span>
                            )}
                          </p>
                          <p className="text-xs text-slate-400">{stop.address}</p>
                        </div>
                      </div>
                      <span
                        className="w-2.5 h-2.5 rounded-full shrink-0"
                        style={{ backgroundColor: stop.stopType === "PICKUP" ? "#f59e0b" : "#3b82f6" }}
                      />
                    </button>
                  );
                })}
                {stops.length === 0 && (
                  <p className="text-sm text-slate-500 py-4 text-center">
                    Usa el buscador de arriba para añadir tu primera parada
                  </p>
                )}
              </div>
            </div>

            <div className="p-5">
              {error && <p className="text-sm text-red-400 mb-3">{error}</p>}
              <button
                onClick={handleOptimize}
                disabled={optimizing}
                className="w-full bg-brand-600 hover:bg-brand-700 text-white rounded-xl py-3 text-sm font-semibold disabled:opacity-60 flex items-center justify-center gap-2"
              >
                {optimizing ? "Optimizando…" : "↻ Optimizar la ruta"}
              </button>
              <p className="text-xs text-slate-500 mt-2 text-center">
                Combina la ventana horaria aprendida por cliente con la distancia real por calle (no línea recta)
              </p>
            </div>
          </div>
        </div>

        {/* HU-14: horario estimado de la ruta optimizada */}
        {optimizedRoute.length > 0 && (
          <div className="lg:col-span-2 lg:order-last">
            <RouteSchedule route={optimizedRoute} departureTime={departureTime} />
          </div>
        )}

        {/* Panel derecho: mapa Leaflet */}
        <div className="h-[600px] rounded-2xl overflow-hidden border border-slate-200">
          <RouteMap
            stops={stops}
            orderedPositions={orderedPositions}
            plannedRoute={optimizedRoute}
            currentLocation={currentLocation ? { lat: currentLocation.lat, lng: currentLocation.lng } : null}
          />
        </div>
      </div>

      {editingStop && (
        <StopEditor
          stop={editingStop}
          onChange={(patch) => patchStop(editingStop.shipmentId ?? editingStop.tempId, patch)}
          onDuplicate={() => handleDuplicate(editingStop.shipmentId ?? editingStop.tempId)}
          onDelete={() => handleDelete(editingStop.shipmentId ?? editingStop.tempId)}
          onClose={() => setEditingStopId(null)}
        />
      )}
    </Layout>
  );
}
