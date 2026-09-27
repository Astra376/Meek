"""Run production handlers in a disposable local database with synthetic data.

Production access is read-only. No code, schema, records, or secrets are printed
or uploaded. Temporary production inputs are deleted when the checks finish.
"""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.error
import urllib.request

from hotfix_live_chat import API_BASE, get_live_script

account_id = os.environ["CLOUDFLARE_ACCOUNT_ID"]
token = os.environ["CLOUDFLARE_API_TOKEN"]
# Exercise the real ingress with invalid auth. These cannot reach data writes.
for method, path in [("PATCH", "/v1/messages/transcript-test"), ("POST", "/v1/messages/transcript-test/rewind")]:
    probe = urllib.request.Request(
        "https://character-chat-worker.robloxproxy.workers.dev" + path,
        data=b'{"content":"test"}', method=method,
        headers={"Authorization": "Bearer intentionally-invalid-token", "Content-Type": "application/json", "User-Agent": "okhttp/4.12.0"},
    )
    try:
        with urllib.request.urlopen(probe, timeout=15) as response:
            status, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, payload = error.code, error.read()
    try:
        code = json.loads(payload).get("code", "non-application-response")
    except (ValueError, AttributeError):
        code = "non-application-response"
    print("Production ingress", method, "status", status, "code", code)
    assert status == 401, "Transcript endpoint is blocked before authentication"
modules, main_module, _ = get_live_script(account_id, token)
body = json.dumps({"sql": "SELECT name, type, sql FROM sqlite_master WHERE type IN ('table', 'index', 'trigger') AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 ELSE 2 END, name", "params": []}).encode()
request = urllib.request.Request(
    f"{API_BASE}/accounts/{account_id}/d1/database/ad611737-fc84-4002-bcfe-0f0ce257f2ae/query",
    data=body, headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
)
with urllib.request.urlopen(request, timeout=30) as response:
    result = json.load(response)
if not result.get("success"):
    raise RuntimeError("Read-only schema query failed")

with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    manifest = []
    for index, (name, mime, payload) in enumerate(modules):
        path = root / f"module-{index}.mjs"
        path.write_bytes(payload)
        manifest.append({"name": name, "path": str(path), "main": name == main_module or index == 0})
    (root / "modules.json").write_text(json.dumps(manifest))
    (root / "schema.json").write_text(json.dumps(result["result"][0]["results"]))
    # Never forward Cloudflare credentials to the local runtime test process.
    env = {key: value for key, value in os.environ.items() if not key.startswith("CLOUDFLARE_")}
    subprocess.run(["node", str(Path(__file__).with_name("check_transcript_runtime.mjs")), directory], env=env, check=True)
