"""HU-31: verifica direcciones candidatas para la semilla.

Para cada (calle, número, comuna):
  1. Nominatim (búsqueda estructurada): exige place_rank 30, el mismo número y la comuna.
  2. OSRM /nearest: distancia del punto a la calle más cercana (máx. 40 m).
  3. Overpass is_in: el punto no cae dentro de un parque, estadio, cancha o área recreativa.
Uso: python verify.py candidates.json results.json
Respeta 1 req/s en Nominatim y usa un User-Agent genérico.
"""
import json, sys, time, urllib.parse, urllib.request

UA = {"User-Agent": "lastmile-seed-dev/1.0"}
BAD = {("leisure", "park"), ("leisure", "stadium"), ("leisure", "pitch"), ("leisure", "sports_centre"),
       ("leisure", "garden"), ("leisure", "playground"), ("landuse", "recreation_ground"),
       ("landuse", "cemetery"), ("amenity", "university"), ("amenity", "hospital")}


def get(url, data=None, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, data=data, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception:
            if i == tries - 1:
                raise
            time.sleep(5 * (i + 1))


def nominatim(street, number, commune):
    q = urllib.parse.urlencode({"format": "jsonv2", "addressdetails": 1, "limit": 3,
                                "street": f"{number} {street}", "city": commune, "country": "Chile"})
    time.sleep(1.1)
    for r in get("https://nominatim.openstreetmap.org/search?" + q):
        a = r.get("address", {})
        if r.get("place_rank") == 30 and a.get("house_number", "").split(";")[0].strip() == str(number):
            return r
    return None


def osrm_nearest(lat, lng):
    r = get(f"https://router.project-osrm.org/nearest/v1/driving/{lng},{lat}?number=1")
    w = r["waypoints"][0]
    return w["distance"], w["name"]


def overpass_bad(lat, lng):
    q = f"[out:json][timeout:25];is_in({lat},{lng})->.a;area.a[~\"^(leisure|landuse|amenity)$\"~\".\"];out tags;"
    time.sleep(1.0)
    r = get("https://overpass-api.de/api/interpreter", urllib.parse.urlencode({"data": q}).encode())
    hits = []
    for el in r.get("elements", []):
        t = el.get("tags", {})
        for k in ("leisure", "landuse", "amenity"):
            if (k, t.get(k)) in BAD:
                hits.append(f"{k}={t.get(k)} {t.get('name', '')}".strip())
    return hits


def main():
    cands = json.load(open(sys.argv[1], encoding="utf-8"))
    out = []
    for c in cands:
        street, number, commune = c["street"], c["number"], c["commune"]
        rec = {**c, "ok": False}
        try:
            n = nominatim(street, number, commune)
            if not n:
                rec["why"] = "nominatim sin número exacto"
            else:
                lat, lng = round(float(n["lat"]), 6), round(float(n["lon"]), 6)
                a = n["address"]
                place = " ".join(str(a.get(k, "")) for k in ("city", "town", "suburb", "city_district", "municipality"))
                rec.update(lat=lat, lng=lng, road=a.get("road"), display=n["display_name"])
                dist, snapped = osrm_nearest(lat, lng)
                rec.update(street_dist_m=round(dist, 1), snapped_road=snapped)
                bad = overpass_bad(lat, lng)
                rec["inside"] = bad
                if commune.lower() not in place.lower():
                    rec["why"] = f"comuna distinta: {place}"
                elif dist > 40:
                    rec["why"] = f"lejos de la calle ({dist:.0f} m)"
                elif bad:
                    rec["why"] = "dentro de " + ", ".join(bad)
                else:
                    rec["ok"] = True
        except Exception as e:  # red, cuota
            rec["why"] = f"error: {e}"
        print(("OK " if rec["ok"] else "-- ") + f"{street} {number}, {commune}: "
              + (f"{rec.get('lat')},{rec.get('lng')} {rec.get('street_dist_m')} m" if rec["ok"] else rec.get("why", "")),
              flush=True)
        out.append(rec)
    json.dump(out, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
