import { useEffect, useRef, useState } from "react";
import type { StopView } from "@/lib/routeView";
import StopRow from "@/components/StopRow";

interface Props {
  stops: StopView[];
  selectedId: string | null;
  onSelect: (shipmentId: string) => void;
}

/**
 * Lista de paradas navegable con teclado: flechas para moverse, Enter (o espacio) para
 * seleccionar. Usa "roving tabindex": solo la fila enfocada entra en el orden de tabulación.
 */
export default function StopList({ stops, selectedId, onSelect }: Props) {
  const [focus, setFocus] = useState(0);
  const rows = useRef<(HTMLLIElement | null)[]>([]);

  // Si cambia la selección desde el mapa o la línea de tiempo, la lista la sigue.
  useEffect(() => {
    const i = stops.findIndex((s) => s.shipmentId === selectedId);
    if (i >= 0) {
      setFocus(i);
      rows.current[i]?.scrollIntoView({ block: "nearest" });
    }
  }, [selectedId, stops]);

  useEffect(() => {
    if (focus >= stops.length) setFocus(Math.max(0, stops.length - 1));
  }, [stops.length, focus]);

  function move(to: number) {
    const i = Math.min(stops.length - 1, Math.max(0, to));
    setFocus(i);
    rows.current[i]?.focus();
  }

  if (stops.length === 0) {
    return <p className="px-4 py-6 text-center text-13 text-muted">Ninguna parada coincide con la búsqueda.</p>;
  }

  return (
    <ul
      role="listbox"
      aria-label="Paradas en orden de visita"
      className="space-y-1 px-2 py-2"
      onKeyDown={(e) => {
        if (e.key === "ArrowDown") {
          e.preventDefault();
          move(focus + 1);
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          move(focus - 1);
        } else if (e.key === "Home") {
          e.preventDefault();
          move(0);
        } else if (e.key === "End") {
          e.preventDefault();
          move(stops.length - 1);
        } else if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect(stops[focus].shipmentId);
        }
      }}
    >
      {stops.map((s, i) => (
        <StopRow
          key={s.shipmentId}
          ref={(el) => (rows.current[i] = el)}
          stop={s}
          selected={s.shipmentId === selectedId}
          focused={i === focus}
          onSelect={() => {
            setFocus(i);
            onSelect(s.shipmentId);
          }}
        />
      ))}
    </ul>
  );
}
