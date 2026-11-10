"""Proxy que cuenta las consultas a OSRM (HU-32).

Uso: python infra/seed/tools/contar_osrm.py 5099, y delivery-service con OSRM_URL=http://host.docker.internal:5099.
Reenvía cada consulta al OSRM público y la cuenta por servicio (route, table).
GET /__count devuelve los conteos; GET /__reset los pone en 0.
"""
import http.server, json, sys, urllib.request, threading
UPSTREAM = "https://router.project-osrm.org"
counts = {}
lock = threading.Lock()
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/__"):
            with lock:
                if self.path == "/__reset": counts.clear()
                body = json.dumps(counts).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json"); self.end_headers(); self.wfile.write(body); return
        service = self.path.split("/")[1]
        with lock: counts[service] = counts.get(service, 0) + 1
        try:
            req = urllib.request.Request(UPSTREAM + self.path, headers={"User-Agent": "lastmile-dev-measure"})
            with urllib.request.urlopen(req, timeout=20) as r:
                data = r.read(); code = r.status
        except urllib.error.HTTPError as e:
            data = e.read(); code = e.code
        self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
    def log_message(self, *a): pass
http.server.ThreadingHTTPServer(("0.0.0.0", int(sys.argv[1])), H).serve_forever()
