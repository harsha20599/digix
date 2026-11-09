#!/usr/bin/env python3
"""A stand-in for an AI provider, for the emulator tests. It behaves like an OpenAI-style service and
writes what it received to a log, so a test can prove what the app actually sent. A key of "bad" gets
the error a real provider returns for an invalid key."""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = sys.argv[2]
turns = {}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        size = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(size)
        with open(LOG, "a") as log:
            log.write(f"{self.path} bytes={size} riff={b'RIFF' in body[:4096]}\n")
        if self.headers.get("Authorization") == "Bearer bad":
            self.reply(401, {"error": "invalid key"})
        elif self.path.endswith("/audio/transcriptions"):
            self.reply(200, {"text": "what is my battery"})
        elif self.path.endswith("/chat/completions"):
            messages = json.loads(body)["messages"]
            with open(LOG, "a") as log:
                log.write(f"messages={len(messages)} last={messages[-1]['content']}\n")
            # The second exchange in a conversation has history in it, and says goodbye.
            finished = len(messages) > 2
            content = {
                "heard": messages[-1]["content"],
                "say": "Goodbye." if finished else "Here is your battery.",
                "action": None if finished else "BATTERY",
                "end": finished,
            }
            self.reply(200, {"choices": [{"message": {"content": json.dumps(content)}}]})
        else:
            self.reply(404, {"error": "not found"})


ThreadingHTTPServer(("0.0.0.0", int(sys.argv[1])), Handler).serve_forever()
