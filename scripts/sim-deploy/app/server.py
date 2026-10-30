"""ms de mentira para o sim-deploy: sobe devagar como o Spring e responde /actuator/health."""
import os
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

VERSION = os.environ.get("SIM_VERSION", "dev")
DELAY = float(os.environ.get("SIM_START_DELAY", "8"))
BROKEN = os.environ.get("SIM_HEALTH", "up") == "down"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/actuator/health"):
            if BROKEN:
                self._send(503, '{"status":"DOWN"}')
            else:
                self._send(200, '{"status":"UP","version":"%s"}' % VERSION)
        else:
            self._send(200, VERSION)

    def _send(self, code, body):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    # Como o Spring: a porta só abre depois do tempo de partida.
    time.sleep(DELAY)
    print("fake-ms %s listening on 9090 (broken=%s)" % (VERSION, BROKEN), flush=True)
    HTTPServer(("0.0.0.0", 9090), Handler).serve_forever()
