#!/usr/bin/env python3
import hashlib
import json
import os
import secrets
import asyncio
import time
from datetime import datetime
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, Header, Request, HTTPException
from fastapi.responses import JSONResponse, HTMLResponse, StreamingResponse

app = FastAPI(title="SMSCenter Receiver")

BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data"
HTML_FILE = BASE_DIR / "index.html"
TOKEN_FILE = BASE_DIR / "token.txt"
DATA_DIR.mkdir(exist_ok=True)

# ── Token ──
def generate_or_load_token() -> str:
    if TOKEN_FILE.exists():
        token = TOKEN_FILE.read_text(encoding="utf-8").strip()
        if token: return token
    token = secrets.token_hex(16)
    TOKEN_FILE.write_text(token, encoding="utf-8")
    return token

TOKEN = generate_or_load_token()
print(f"\n{'='*60}\n  Token: {TOKEN}\n  访问: https://game.ahsfnuckgf.cn/sms\n{'='*60}\n")

def verify_token(token: Optional[str]) -> bool:
    return token == TOKEN

def extract_bearer_token(authorization: Optional[str]) -> Optional[str]:
    if not authorization: return None
    parts = authorization.split()
    return parts[1] if len(parts) == 2 and parts[0].lower() == "bearer" else None

# ── SSE 管理器 ──
class SSEManager:
    def __init__(self):
        self.queues: List[asyncio.Queue] = []
    def add_client(self):
        q = asyncio.Queue(maxsize=32)
        self.queues.append(q)
        return q
    def remove_client(self, q):
        if q in self.queues: self.queues.remove(q)
    async def broadcast(self, data: dict):
        payload = json.dumps(data, ensure_ascii=False)
        dead = []
        for q in self.queues:
            try: q.put_nowait(payload)
            except asyncio.QueueFull: dead.append(q)
        for q in dead: self.remove_client(q)

sse_manager = SSEManager()

# ── API ──
@app.post("/api/sms")
async def receive_sms(request: Request):
    try: body = await request.json()
    except Exception: return JSONResponse({"status":"error","message":"invalid JSON"}, 400)

    sender = body.get("sender","unknown")
    sms_body = body.get("body","")
    timestamp = body.get("timestamp", int(time.time()*1000))
    sms_time = body.get("time", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

    key = f"{sender}|{sms_body}|{timestamp}"
    short_hash = hashlib.md5(key.encode()).hexdigest()[:8]
    filename = f"sms_{timestamp}_{short_hash}.json"

    record = {"sender":sender,"body":sms_body,"timestamp":timestamp,"time":sms_time,
              "received_at":datetime.now().strftime("%Y-%m-%d %H:%M:%S")}

    (DATA_DIR / filename).write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    await sse_manager.broadcast(record)
    return JSONResponse({"status":"ok","id":filename})

@app.get("/api/sms/list")
def list_sms(authorization: Optional[str] = Header(None)):
    token = extract_bearer_token(authorization)
    if not verify_token(token): raise HTTPException(403, detail="invalid token")
    files = sorted(DATA_DIR.glob("sms_*.json"), reverse=True)
    return [json.loads(f.read_text(encoding="utf-8")) for f in files]

# ── SSE 推送（Token 从 Authorization Header 读取）──
@app.get("/api/sms/stream")
async def sms_stream(authorization: Optional[str] = Header(None)):
    token = extract_bearer_token(authorization)
    if not verify_token(token): raise HTTPException(403, detail="invalid token")
    q = sse_manager.add_client()
    async def event_generator():
        try:
            yield ":ok\n\n"
            while True:
                try: data = await asyncio.wait_for(q.get(), timeout=30)
                except asyncio.TimeoutError: yield ":keepalive\n\n"; continue
                yield f"data: {data}\n\n"
        except asyncio.CancelledError: pass
        finally: sse_manager.remove_client(q)
    return StreamingResponse(event_generator(), media_type="text/event-stream",
                             headers={"Cache-Control":"no-cache","Connection":"keep-alive","X-Accel-Buffering":"no"})

# ── 前端 ──
@app.get("/sms", response_class=HTMLResponse)
def index():
    return HTML_FILE.read_text(encoding="utf-8") if HTML_FILE.exists() else "<h1>index.html not found</h1>"

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=7336)