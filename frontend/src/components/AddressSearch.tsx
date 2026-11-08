import { useEffect, useRef, useState } from "react";
import { geocodingApi } from "@/api/endpoints";
import type { GeocodeResult } from "@/types";

interface AddressSearchProps {
  onSelect: (address: string, result: GeocodeResult | null) => void;
  placeholder?: string;
}

export default function AddressSearch({ onSelect, placeholder }: AddressSearchProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GeocodeResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);

    if (query.trim().length < 4) {
      setResults([]);
      return;
    }

    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const found = await geocodingApi.search(query);
        setResults(found);
        setOpen(true);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 450); // debounce: evita saturar Nominatim en cada tecla

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  function handlePick(result: GeocodeResult) {
    onSelect(result.address, result);
    setQuery(result.address);
    setOpen(false);
  }

  function handleManualConfirm() {
    // Permite añadir una dirección sin coincidencia geocodificada (se completará
    // lat/lng más tarde en el backend si se logra geocodificar igualmente).
    if (query.trim().length > 0) {
      onSelect(query.trim(), null);
      setOpen(false);
    }
  }

  return (
    <div className="relative">
      <div className="flex items-center gap-2 bg-slate-800 rounded-xl px-4 py-3 border border-slate-700 focus-within:border-brand-500">
        <svg className="w-5 h-5 text-slate-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M11 19a8 8 0 100-16 8 8 0 000 16z" />
        </svg>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onFocus={() => results.length > 0 && setOpen(true)}
          onKeyDown={(e) => e.key === "Enter" && handleManualConfirm()}
          placeholder={placeholder || "Pulsa para añadir más"}
          className="bg-transparent outline-none text-slate-100 placeholder:text-slate-500 flex-1 text-sm"
        />
        {loading && <span className="text-xs text-slate-500">buscando…</span>}
      </div>

      {open && results.length > 0 && (
        <ul className="absolute z-20 mt-1 w-full bg-slate-800 border border-slate-700 rounded-xl overflow-hidden shadow-xl max-h-64 overflow-y-auto">
          {results.map((r, i) => (
            <li key={i}>
              <button
                type="button"
                onClick={() => handlePick(r)}
                className="w-full text-left px-4 py-3 hover:bg-slate-700 text-sm text-slate-100 border-b border-slate-700 last:border-0"
              >
                <div>{r.address}</div>
                <div className="text-xs text-slate-500">
                  {r.latitude.toFixed(6)}, {r.longitude.toFixed(6)} · confianza {Math.round(r.confidence * 100)}%
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
