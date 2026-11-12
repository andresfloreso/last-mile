"""Sprint 5 (#85): revisa en qué calle engancha OSRM cada local de la semilla.

OSRM arma la ruta desde el punto de calle más cercano a la coordenada. Si un
local de esquina queda más cerca de la calle transversal que de la suya, el
camión "llega" por la transversal y la línea da vueltas que no corresponden.
Este script lista los locales cuya calle más cercana (OSRM /nearest) no es la
calle de su dirección, y propone el punto más cercano sobre su propia calle.

Uso: python -I infra/seed/tools/revisar_enganche.py infra/seed/delivery/afterMigrate.sql
"""
import json, re, sys, time, unicodedata, urllib.request

UA = {"User-Agent": "lastmile-seed-dev/1.0"}
ROW = re.compile(r"\(\s*(\d+),\s*'((?:[^']|'')*)',\s*'\w+',\s*'((?:[^']|'')*)',\s*'([^']*)',\s*(-?\d+\.\d+),\s*(-?\d+\.\d+)")


def norm(s):
    s = s.lower().replace("avenida ", "").replace("av. ", "").replace("'", "")
    return unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()


def nearest(lat, lng, n):
    u = f"https://router.project-osrm.org/nearest/v1/driving/{lng},{lat}?number={n}"
    time.sleep(1)
    with urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60) as r:
        return json.loads(r.read().decode())["waypoints"]


def main():
    texto = open(sys.argv[1], encoding="utf-8").read()
    malos = 0
    for idx, _, direccion, _, lat, lng in ROW.findall(texto[texto.index("INSERT INTO seed_customer"):]):
        direccion = direccion.replace("''", "'")
        calle = norm(re.sub(r"\s+\d+$", "", direccion))
        w = nearest(lat, lng, 100)
        if norm(w[0]["name"]) == calle:
            continue
        malos += 1
        propia = next((x for x in w if norm(x["name"]) == calle), None)
        print(f"{idx:>2} {direccion}: engancha en '{w[0]['name'] or '(sin nombre)'}' a {w[0]['distance']:.0f} m; "
              + (f"su calle está a {propia['distance']:.0f} m, en {propia['location'][1]:.6f}, {propia['location'][0]:.6f}"
                 if propia else "su calle no aparece entre los 100 candidatos"))
    print(f"{malos} locales enganchan en otra calle")


if __name__ == "__main__":
    main()
