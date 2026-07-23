#!/usr/bin/env python3
"""
SMSCenter 短信接收接口
运行：uvicorn main:app --host 0.0.0.0 --port 8000
"""

import hashlib
import json
import os
import time
from datetime import datetime

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

app = FastAPI(title="SMSCenter Receiver")

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
os.makedirs(DATA_DIR, exist_ok=True)


@app.post("/api/sms")
async def receive_sms(request: Request):
    try:
        body = await request.json()
    except Exception:
        return JSONResponse({"status": "error", "message": "invalid JSON"}, status_code=400)

    sender = body.get("sender", "unknown")
    sms_body = body.get("body", "")
    timestamp = body.get("timestamp", int(time.time() * 1000))
    sms_time = body.get("time", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))

    # 生成唯一文件名：sms_时间戳_前8位MD5
    key = f"{sender}|{sms_body}|{timestamp}"
    short_hash = hashlib.md5(key.encode()).hexdigest()[:8]
    filename = f"sms_{timestamp}_{short_hash}.json"

    record = {
        "sender": sender,
        "body": sms_body,
        "timestamp": timestamp,
        "time": sms_time,
        "received_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    }

    filepath = os.path.join(DATA_DIR, filename)
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(record, f, ensure_ascii=False, indent=2)

    return JSONResponse({"status": "ok", "id": filename})


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)