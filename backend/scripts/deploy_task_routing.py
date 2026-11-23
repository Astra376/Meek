"""Apply the reviewed task-routing changes to the production Worker bundle."""
import email
import hashlib
import json
import os
import re
import time
import subprocess
import urllib.request
from email.policy import default
from hotfix_live_chat import encode_multipart

ROOT = "https://api.cloudflare.com/client/v4/accounts/6418d8b7a0996630c6eb574d93c85b54/workers/scripts/character-chat-worker"
HEADERS = {"Authorization": "Bearer " + os.environ["CLOUDFLARE_API_TOKEN"]}
MODEL = "deepseek/deepseek-v4.1-flash-1017"
DIGEST = "e0700689c01c6a27257eccffa6282cc920410550a11b864fe7bc581d951aa246"


def fetch(path):
    return urllib.request.urlopen(urllib.request.Request(ROOT + path + "?check=" + str(time.time_ns()), headers={**HEADERS, "Cache-Control": "no-cache"}), timeout=30)


def download():
    with fetch("/content/v2") as response:
        message = email.message_from_bytes(
            ("Content-Type: " + response.headers["Content-Type"] + "\r\n\r\n").encode() + response.read(),
            policy=default,
        )
        parts = [(p.get_filename(), p.get_content_type(), p.get_payload(decode=True)) for p in message.iter_parts()]
        return parts, response.headers.get("ETag")


def active():
    with fetch("/deployments") as response:
        return json.load(response)["result"]["deployments"][0]


def insert_at_entry(source, name, lines):
    pattern = r"^async function " + name + r"\([^\n]*\) \{\n"
    matches = list(re.finditer(pattern, source, re.M))
    if len(matches) != 1:
        raise ValueError("Unexpected handler signature for " + name)
    pos = matches[0].end()
    return source[:pos] + lines + source[pos:]


def main():
    original_deployment = active()
    modules, etag = download()
    assert len(modules) == 1 and modules[0][0] and "javascript" in modules[0][1]
    name, mime, code = modules[0]
    assert hashlib.sha256(code).hexdigest() == DIGEST, "Production source changed since routing inspection"
    source = code.decode()
    # The reviewed non-streaming callers are memory, offline generation, character
    # creation, greetings and scene descriptions. Interactive chat uses streamChatText.
    assert MODEL not in source and "BACKGROUND_GENERATION_DISABLED" not in source
    patched = insert_at_entry(source, "completeChatText", (
        '  env = { ...env, OPENROUTER_MODEL: "' + MODEL +
        '", OPENROUTER_FALLBACK_MODELS: "", OPENROUTER_PROVIDERS: "" };\n'
    ))
    disabled = 'throw new AppError(410, "BACKGROUND_GENERATION_DISABLED", "Background generation is disabled.");'
    for fn in ("conversationBackground", "generateChatBackground"):
        patched = insert_at_entry(patched, fn, "  " + disabled + "\n")
    patched = insert_at_entry(patched, "generateAppearance", '  if (input.kind === "background") ' + disabled + "\n")
    subprocess.run(["node", "--input-type=module", "--check"], input=patched.encode(), check=True)
    assert download() == (modules, etag) and active()["id"] == original_deployment["id"]
    updated = [(name, mime, patched.encode())]
    data, content_type = encode_multipart(updated, name)
    headers = {**HEADERS, "Content-Type": content_type}
    if etag:
        headers["If-Match"] = etag
    request = urllib.request.Request(ROOT + "/content", data=data, headers=headers, method="PUT")
    with urllib.request.urlopen(request, timeout=60) as response:
        assert json.load(response)["success"]
    saved, _ = download()
    assert saved[0][2] == patched.encode()
    current = active()
    assert current["id"] != original_deployment["id"]
    assert len(current["versions"]) == 1 and current["versions"][0]["percentage"] == 100
    time.sleep(12)
    assert download()[0][0][2] == patched.encode()
    assert active()["id"] == current["id"]
    print("Verified exact Worker code and 100% production activation", current["versions"][0]["version_id"])


if __name__ == "__main__":
    main()
