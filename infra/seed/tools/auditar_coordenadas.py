"""Auditoría de coordenadas de la semilla (Sprint 5, issue #84).

Lee los locales de infra/seed/delivery/afterMigrate.sql y compara la
coordenada fija de cada uno con su dirección geocodificada:

  1. Nominatim, búsqueda estructurada con número de calle (place_rank 30);
  2. Nominatim, búsqueda libre ("Av. Santa Rosa 500, Santiago, Chile");
  3. opcional (--esri): el geocodificador mundial de Esri, como segunda
     opinión independiente de OpenStreetMap. Solo se usa para comparar
     (forStorage=false): ninguna coordenada de la semilla sale de Esri.

Lista los locales que quedan a más de UMBRAL metros (100 por defecto).
Respeta 1 request por segundo en Nominatim y usa un User-Agent genérico.

Uso:
  python -I infra/seed/tools/auditar_coordenadas.py infra/seed/delivery/afterMigrate.sql salida.json [--esri] [--umbral 100]
"""
import json, math, re, sys, time, urllib.parse, urllib.request

UA = {"User-Agent": "lastmile-seed-dev/1.0"}
ROW = re.compile(r"\(\s*(\d+),\s*'((?:[^']|'')*)',\s*'\w+',\s*'((?:[^']|'')*)',\s*'([^']*)',\s*(-?\d+\.\d+),\s*(-?\d+\.\d+)")
PREFIJOS = {"Av. ": "Avenida "}


def get(url, tries=4):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception:
            if i == tries - 1:
                raise
            time.sleep(5 * (i + 1))


def metros(lat1, lng1, lat2, lng2):
    r = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def rumbo(lat1, lng1, lat2, lng2):
    """Hacia dónde está el punto 2 visto desde el punto 1 (N, NE, E, ...)."""
    dy = (lat2 - lat1) * 111320
    dx = (lng2 - lng1) * 111320 * math.cos(math.radians(lat1))
    ang = (math.degrees(math.atan2(dx, dy)) + 360) % 360
    return ["N", "NE", "E", "SE", "S", "SO", "O", "NO"][int((ang + 22.5) // 45) % 8]


def separar(direccion):
    m = re.match(r"(.*\D)\s+(\d+)$", direccion)
    calle, numero = m.group(1).strip(), m.group(2)
    for k, v in PREFIJOS.items():
        if calle.startswith(k):
            calle = v + calle[len(k):]
    return calle, numero


def nominatim_estructurada(calle, numero, comuna):
    q = urllib.parse.urlencode({"format": "jsonv2", "addressdetails": 1, "limit": 5,
                                "street": f"{numero} {calle}", "city": comuna, "country": "Chile"})
    time.sleep(1.1)
    for r in get("https://nominatim.openstreetmap.org/search?" + q):
        hn = r.get("address", {}).get("house_number", "").split(";")[0].strip()
        if r.get("place_rank") == 30 and hn == numero:
            return float(r["lat"]), float(r["lon"]), r.get("osm_type", "") + "/" + str(r.get("osm_id", ""))
    return None


def nominatim_libre(direccion, comuna):
    q = urllib.parse.urlencode({"format": "jsonv2", "limit": 1, "q": f"{direccion}, {comuna}, Chile"})
    time.sleep(1.1)
    r = get("https://nominatim.openstreetmap.org/search?" + q)
    return (float(r[0]["lat"]), float(r[0]["lon"]), r[0].get("place_rank")) if r else None


def esri(direccion, comuna):
    q = urllib.parse.urlencode({"SingleLine": f"{direccion}, {comuna}, Región Metropolitana, Chile",
                                "f": "json", "outFields": "Addr_type", "maxLocations": 1, "forStorage": "false"})
    r = get("https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/findAddressCandidates?" + q)
    c = (r.get("candidates") or [None])[0]
    if not c:
        return None
    return c["location"]["y"], c["location"]["x"], c["attributes"].get("Addr_type"), c.get("score")


def main():
    sql, salida = sys.argv[1], sys.argv[2]
    usar_esri = "--esri" in sys.argv
    umbral = float(sys.argv[sys.argv.index("--umbral") + 1]) if "--umbral" in sys.argv else 100.0
    texto = open(sql, encoding="utf-8").read()
    bloque = texto[texto.index("INSERT INTO seed_customer"):]
    bloque = bloque[:bloque.index(";")]
    out = []
    for idx, nombre, direccion, comuna, lat, lng in ROW.findall(bloque):
        nombre, direccion = nombre.replace("''", "'"), direccion.replace("''", "'")
        lat, lng = float(lat), float(lng)
        calle, numero = separar(direccion)
        rec = {"local": int(idx), "nombre": nombre, "direccion": direccion, "comuna": comuna,
               "semilla": [lat, lng]}
        try:
            e = nominatim_estructurada(calle, numero, comuna)
            if e:
                rec["nominatim"] = {"lat": e[0], "lng": e[1], "osm": e[2],
                                    "m": round(metros(lat, lng, e[0], e[1]), 1)}
            l = nominatim_libre(direccion, comuna)
            if l:
                rec["nominatim_libre"] = {"lat": l[0], "lng": l[1], "place_rank": l[2],
                                          "m": round(metros(lat, lng, l[0], l[1]), 1)}
            if usar_esri:
                s = esri(direccion, comuna)
                if s:
                    rec["esri"] = {"lat": s[0], "lng": s[1], "tipo": s[2], "score": s[3],
                                   "m": round(metros(lat, lng, s[0], s[1]), 1),
                                   "semilla_queda_al": rumbo(s[0], s[1], lat, lng)}
        except Exception as ex:  # red o cuota
            rec["error"] = str(ex)
        marcas = []
        if "nominatim" not in rec:
            marcas.append("nominatim sin número exacto")
        elif rec["nominatim"]["m"] > umbral:
            marcas.append(f"nominatim a {rec['nominatim']['m']:.0f} m")
        if rec.get("esri", {}).get("tipo") in ("PointAddress", "StreetAddress", "Subaddress") \
                and rec["esri"]["m"] > umbral:
            marcas.append(f"esri a {rec['esri']['m']:.0f} m ({rec['esri']['tipo']}; la semilla queda al "
                          f"{rec['esri']['semilla_queda_al']})")
        rec["revisar"] = marcas
        print(f"{'!!' if marcas else 'ok'} {idx:>2} {direccion}, {comuna}: "
              + (", ".join(marcas) if marcas else
                 f"nominatim {rec['nominatim']['m']:.0f} m"
                 + (f", esri {rec['esri']['m']:.0f} m ({rec['esri']['tipo']})" if 'esri' in rec else "")),
              flush=True)
        out.append(rec)
    json.dump(out, open(salida, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    malos = [r for r in out if r["revisar"]]
    print(f"\n{len(malos)} de {len(out)} locales a más de {umbral:.0f} m o sin número exacto")


if __name__ == "__main__":
    main()
