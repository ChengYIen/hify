# -*- coding: utf-8 -*-
"""Mock MCP Streamable HTTP server for Hify acceptance (port 9001, path /mcp)."""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

LOG_FILE = r"D:\dev\HIFY\acceptance_tmp\mcp\mock_mcp.log"

TOOLS = [{
    "name": "query_order",
    "description": "查询订单状态",
    "inputSchema": {
        "type": "object",
        "properties": {
            "userId": {"type": "string"},
            "orderId": {"type": "string"}
        },
        "required": ["userId", "orderId"]
    }
}]


def log(msg):
    line = "[mock-mcp] " + str(msg)
    print(line, flush=True)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _send_json(self, status, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Allow", "GET, POST, OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        if urlparse(self.path).path != "/mcp":
            self._send_json(404, {"error": "not found"})
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        log("GET SSE opened")
        try:
            while True:
                self.wfile.write(b": keep-alive\n\n")
                self.wfile.flush()
                time.sleep(15)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        log("GET SSE closed")

    def do_POST(self):
        if urlparse(self.path).path != "/mcp":
            self._send_json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length).decode("utf-8")
        session = self.headers.get("mcp-session-id")
        log("POST /mcp session=%s body=%s" % (session, raw[:500]))
        try:
            req = json.loads(raw)
        except Exception as e:
            self._send_json(400, {
                "jsonrpc": "2.0",
                "error": {"code": -32700, "message": "parse error: %s" % e},
                "id": None,
            })
            return

        method = req.get("method")
        req_id = req.get("id")
        if method == "initialize":
            protocol = (req.get("params") or {}).get("protocolVersion", "2025-03-26")
            self._send_json(200, {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {
                    "protocolVersion": protocol,
                    "capabilities": {"tools": {"listChanged": False}},
                    "serverInfo": {"name": "mock-order-mcp", "version": "1.0.0"},
                },
            })
            return
        if method in ("notifications/initialized", "notifications/cancelled"):
            self.send_response(202)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if method == "ping":
            self._send_json(200, {"jsonrpc": "2.0", "id": req_id, "result": {}})
            return
        if method == "tools/list":
            self._send_json(200, {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"tools": TOOLS},
            })
            return
        if method == "tools/call":
            params = req.get("params") or {}
            tool_name = params.get("name")
            args = params.get("arguments") or {}
            log("tools/call name=%s arguments=%s" % (
                tool_name, json.dumps(args, ensure_ascii=False)))
            if tool_name == "query_order":
                result = {
                    "content": [{"type": "text", "text": "运输中，SF1234567，预计明天到"}],
                    "isError": False,
                }
            else:
                result = {
                    "content": [{"type": "text", "text": "unknown tool: %s" % tool_name}],
                    "isError": True,
                }
            self._send_json(200, {"jsonrpc": "2.0", "id": req_id, "result": result})
            return
        self._send_json(200, {
            "jsonrpc": "2.0",
            "id": req_id,
            "error": {"code": -32601, "message": "method not found: %s" % method},
        })


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 9001), Handler)
    log("listening on http://127.0.0.1:9001/mcp")
    server.serve_forever()
