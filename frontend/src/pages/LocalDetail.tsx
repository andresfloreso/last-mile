import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Layout from "@/components/Layout";
import TimeWindowChart from "@/components/TimeWindowChart";
import { customersApi } from "@/api/endpoints";
import { windowLine } from "@/lib/timeWindow";
import type { BusinessType, Customer, TimeWindow } from "@/types";

const BUSINESS_LABEL: Record<BusinessType, string> = {
  BOTILLERIA: "Botillería",
  MINIMARKET: "Minimarket",
  BAR: "Bar",
  RESTAURANT: "Restaurant",
  OTRO: "Otro",
};

/** Detalle del local (HU-11): modulación declarada vs. ventana real aprendida. */
export default function LocalDetail() {
  const { customerId = "" } = useParams();
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [tw, setTw] = useState<TimeWindow | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    Promise.all([customersApi.get(customerId), customersApi.timeWindow(customerId)])
      .then(([c, t]) => {
        if (cancelled) return;
        setCustomer(c);
        setTw(t);
      })
      .catch((e) => {
        if (cancelled) return;
        setError(e?.response?.status === 404 ? "El local no existe" : "No se pudo cargar el local");
      });
    return () => {
      cancelled = true;
    };
  }, [customerId]);

  return (
    <Layout>
      <div className="max-w-3xl mx-auto space-y-4">
        <Link to="/" className="text-sm text-primary hover:underline">
          ← Volver a rutas
        </Link>

        {error && <p className="rounded-lg bg-tile p-4 text-sm text-reject">{error}</p>}
        {!error && (!customer || !tw) && <p className="text-sm text-muted">Cargando…</p>}

        {customer && tw && (
          <>
            <header>
              <h1 className="text-xl font-semibold text-ink">{customer.fullName}</h1>
              <p className="text-sm text-muted">
                {customer.businessType ? `${BUSINESS_LABEL[customer.businessType]} · ` : ""}
                {customer.address}
                {customer.commune ? `, ${customer.commune}` : ""}
              </p>
            </header>

            <section className="rounded-lg border border-line bg-panel p-5 space-y-4">
              <div>
                <h2 className="text-xs font-semibold uppercase tracking-wide text-muted">Horario de recepción</h2>
                <p className="mt-1 text-lg font-semibold text-ink" data-testid="window-line">
                  {windowLine(tw)}
                </p>
                <p className="text-sm text-muted">{tw.summary}.</p>
              </div>

              <dl className="grid grid-cols-3 gap-3 text-sm">
                <div className="rounded-md bg-tile p-3">
                  <dt className="text-xs text-muted">Intentos registrados</dt>
                  <dd className="text-lg font-semibold tabular-nums text-ink">{tw.totalAttempts}</dd>
                </div>
                <div className="rounded-md bg-tile p-3">
                  <dt className="text-xs text-muted">Recibió</dt>
                  <dd className="text-lg font-semibold tabular-nums text-ink">{tw.acceptedAttempts}</dd>
                </div>
                <div className="rounded-md bg-tile p-3">
                  <dt className="text-xs text-muted">No recibió</dt>
                  <dd className="text-lg font-semibold tabular-nums text-ink">{tw.rejectedAttempts}</dd>
                </div>
              </dl>

              <TimeWindowChart tw={tw} />

              {tw.hoursToAvoid.length > 0 && (
                <p className="text-sm text-muted">
                  <span className="font-medium text-ink">Horas a evitar:</span>{" "}
                  {tw.hoursToAvoid.map((h) => `${String(h).padStart(2, "0")}:00`).join(", ")} (más rechazos que
                  entregas).
                </p>
              )}
            </section>
          </>
        )}
      </div>
    </Layout>
  );
}
