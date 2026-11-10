"""Métricas de la ruta del día: km, término, paradas y minutos fuera de ventana.

Uso: python infra/seed/tools/medir_ruta.py [fecha] [--json salida.json]
Arma la ruta con POST /api/routes/build (gateway, LASTMILE_URL o :8080) como el
despachador semilla (infra/seed/README.md), desde la bodega de Av. Italia 1300 a las 08:00.
HU-28 la usa para la tabla antes/después del optimizador.
"""
import json, sys, urllib.request, datetime as dt

import os
BASE = os.environ.get("LASTMILE_URL", "http://localhost:8080")
DEPOT = (-33.446393, -70.624979)


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": "Bearer " + token} if token else {})})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode())


def minutes(t, day_offset=0):
    h, m = map(int, t.split(":")[:2])
    return h * 60 + m + 1440 * (day_offset or 0)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    date = args[0] if args else dt.date.today().isoformat()
    token = call("POST", "/api/auth/login", {"email": "despacho@lastmile.cl", "password": "lastmile123"})["accessToken"]
    stops = call("POST", "/api/routes/build", {"date": date, "depotLat": DEPOT[0], "depotLng": DEPOT[1],
                                               "departureTime": "08:00"}, token)
    km = sum((s.get("legDistanceMeters") or 0) for s in stops) / 1000
    out_stops, out_min = 0, 0
    for s in stops:
        start = minutes(s["serviceStart"], s.get("dayOffset"))
        end = minutes(s["windowEnd"]) if s.get("windowEnd") else None
        if s.get("outsideWindow"):
            out_stops += 1
            if end is not None:
                out_min += max(0, start - end)
    last = stops[-1]
    finish = last["estimatedDeparture"] + (" (+1 día)" if last.get("dayOffset") else "")
    names = [s["customerName"] for s in stops]
    res = {"fecha": date, "paradas": len(stops), "locales_distintos": len(set(names)), "km": round(km, 1),
           "termino": finish, "fuera_de_ventana": out_stops, "minutos_fuera_de_ventana": out_min,
           "fuente_tiempos": sorted({s.get("timingSource") for s in stops})}
    print(json.dumps(res, ensure_ascii=False))
    for s in stops:
        print(f'{s["position"]:>2} {s["serviceStart"]} [{s.get("windowStart","")[:5]}-{s.get("windowEnd","")[:5]}]'
              f'{" FUERA" if s.get("outsideWindow") else ""}  {s["customerName"]}')
    if "--json" in sys.argv:
        # Sin la geometría de OSRM (path, coordenadas): deja el diff revisable y alcanza para la tabla.
        keep = ["position", "customerName", "address", "windowStart", "windowEnd", "orderPreference",
                "shipmentCount", "estimatedArrival", "waitMinutes", "serviceStart", "estimatedDeparture",
                "travelMinutes", "legDistanceMeters", "outsideWindow", "dayOffset", "timingSource",
                "optimizer", "vehiclePlate"]
        rows = [json.dumps({k: s[k] for k in keep if k in s}, ensure_ascii=False) for s in stops]
        text = ['{', '  "resumen": ' + json.dumps(res, ensure_ascii=False) + ',', '  "paradas": [',
                ',\n'.join('    ' + r for r in rows), '  ]', '}', '']
        with open(sys.argv[sys.argv.index("--json") + 1], "w", encoding="utf-8", newline="\n") as out:
            out.write('\n'.join(text))


if __name__ == "__main__":
    main()
