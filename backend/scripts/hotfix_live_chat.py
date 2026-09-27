"""Apply the chat startup hotfix to the deployed Worker without changing its bindings.

The deployed Worker contains features absent from this checkout. This script
patches its current compiled module with exact-match guards and uses Cloudflare's
content-only endpoint, preserving the live configuration and Durable Objects.
"""

import argparse
import email
import hashlib
import json
import os
import subprocess
import tempfile
import urllib.request


SCRIPT_NAME = "character-chat-worker"
EXPECTED_SHA256 = "940352134d4b7a3d11895b6a90f9388e319f16bb064877c5510d474791b615ec"
API_BASE = "https://api.cloudflare.com/client/v4"

HELPERS = '''async function requiredChatPreparation(promise, label) {
  let timer;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => {
        timer = setTimeout(() => {
          console.warn("Chat preparation timed out", { step: label });
          reject(new AppError(503, "CHAT_PREPARATION_TIMEOUT", "Chat is temporarily unavailable. Please try again."));
        }, 1e4);
      })
    ]);
  } finally {
    clearTimeout(timer);
  }
}
async function optionalChatPreparation(promise, fallback, label) {
  let timer;
  try {
    return await Promise.race([
      promise,
      new Promise((resolve) => {
        timer = setTimeout(() => {
          console.warn("Optional chat preparation timed out", { step: label });
          resolve(fallback);
        }, 5e3);
      })
    ]);
  } catch (error) {
    console.warn("Optional chat preparation failed", { step: label, error });
    return fallback;
  } finally {
    clearTimeout(timer);
  }
}
'''

OLD_CONTEXT = '''  const [character, transcript, memoryPrompt, personaPrompt2] = await Promise.all([
    getCharacterById(context.env, context.user.userId, conversation.character_id),
    loadTranscript(context, conversationId),
    buildCharacterMemoryPrompt(context, conversationId),
    resolveConversationPersonaPrompt(context.env, conversationId, context.user.userId)
  ]);'''

NEW_CONTEXT = '''  const [character, transcript, memoryPrompt, personaPrompt2] = await Promise.all([
    requiredChatPreparation(getCharacterById(context.env, context.user.userId, conversation.character_id), "character"),
    requiredChatPreparation(loadTranscript(context, conversationId), "transcript"),
    optionalChatPreparation(buildCharacterMemoryPrompt(context, conversationId), "", "memory"),
    optionalChatPreparation(resolveConversationPersonaPrompt(context.env, conversationId, context.user.userId), "", "persona")
  ]);'''

OLD_MODEL = '''  const model = await resolveChatModel(
    context,
    conversationId,
    latestUserContent,
    conversation.version,
    /thoughtful|reflective|analytical|philosoph|deliberate/i.test(character.system_prompt),
    options.appendedUserContent !== void 0
  );'''

NEW_MODEL = '''  const model = await optionalChatPreparation(resolveChatModel(
    context,
    conversationId,
    latestUserContent,
    conversation.version,
    /thoughtful|reflective|analytical|philosoph|deliberate/i.test(character.system_prompt),
    options.appendedUserContent !== void 0
  ), modelResolution(context.env, "auto", "standard"), "model selection");'''


def patch_source(source: str) -> str:
    replacements = [
        ("async function buildAssistantContext(context, conversation, options = {}) {", HELPERS + "async function buildAssistantContext(context, conversation, options = {}) {"),
        (OLD_CONTEXT, NEW_CONTEXT),
        (OLD_MODEL, NEW_MODEL),
    ]
    for old, new in replacements:
        if source.count(old) != 1:
            raise ValueError(f"Expected exactly one copy of chat patch anchor; found {source.count(old)}")
        source = source.replace(old, new, 1)
    return source


def get_live_script(account_id: str, token: str):
    url = f"{API_BASE}/accounts/{account_id}/workers/scripts/{SCRIPT_NAME}"
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=30) as response:
        content_type = response.headers.get("Content-Type", "application/javascript")
        main_module = response.headers.get("CF-WORKER-MAIN-MODULE-PART")
        etag = response.headers.get("ETag")
        body = response.read()
    if content_type.startswith("multipart/"):
        message = email.message_from_bytes(
            f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode() + body
        )
        modules = []
        for part in message.walk():
            if not part.is_multipart():
                name = part.get_param("name", header="content-disposition")
                if name != "metadata":
                    content_type = part.get_content_type()
                    if content_type == "text/plain" and name.endswith(".js"):
                        content_type = "application/javascript+module"
                    modules.append((name, content_type, part.get_payload(decode=True)))
    else:
        modules = [(main_module or "index.js", "application/javascript+module", body)]
    if not modules or any(not name for name, _, _ in modules):
        raise ValueError("Could not identify the deployed Worker modules")
    return modules, main_module, etag


def encode_multipart(modules, main_module: str):
    boundary = "MeekChatHotfixBoundary"
    body = bytearray()

    def add_part(name: str, content_type: str, value: bytes):
        body.extend(f"--{boundary}\r\n".encode())
        disposition = f'Content-Disposition: form-data; name="{name}"'
        if name != "metadata":
            disposition += f'; filename="{name}"'
        body.extend(f"{disposition}\r\n".encode())
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode())
        body.extend(value)
        body.extend(b"\r\n")

    add_part("metadata", "application/json", json.dumps({"main_module": main_module}).encode())
    for name, content_type, payload in modules:
        add_part(name, content_type, payload)
    body.extend(f"--{boundary}--\r\n".encode())
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", help="Test a local excerpt without accessing Cloudflare")
    parser.add_argument("--deploy", action="store_true", help="Apply the content-only update")
    arguments = parser.parse_args()
    if arguments.input:
        with open(arguments.input) as source_file:
            source = source_file.read()
        patched = patch_source(source)
        print(f"Local anchors passed; patched {len(patched) - len(source)} bytes")
        return
    if not arguments.deploy:
        parser.error("Choose --input or --deploy")

    account_id = os.environ["CLOUDFLARE_ACCOUNT_ID"]
    token = os.environ["CLOUDFLARE_API_TOKEN"]
    modules, main_module, etag = get_live_script(account_id, token)
    sources = [payload.decode("utf-8", errors="replace") for _, _, payload in modules]
    digest = hashlib.sha256("\n".join(sources).encode()).hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError(f"Live Worker changed since review ({digest}); refusing to deploy")
    target_indexes = [i for i, source in enumerate(sources) if "// src/services/chat/index.ts" in source]
    if len(target_indexes) != 1:
        raise ValueError("Expected exactly one live chat module")
    index = target_indexes[0]
    patched = patch_source(sources[index])
    with tempfile.TemporaryDirectory() as directory:
        check_path = os.path.join(directory, "worker.mjs")
        with open(check_path, "w") as output:
            output.write(patched)
        subprocess.run(["node", "--check", check_path], check=True)
    modules[index] = (modules[index][0], modules[index][1], patched.encode())
    selected_main = main_module or modules[index][0]
    if selected_main not in [name for name, _, _ in modules]:
        raise ValueError("Main module is missing from Worker download")
    content, content_type = encode_multipart(modules, selected_main)
    url = f"{API_BASE}/accounts/{account_id}/workers/scripts/{SCRIPT_NAME}/content"
    headers = {"Authorization": f"Bearer {token}", "Content-Type": content_type}
    if etag:
        headers["If-Match"] = etag
    request = urllib.request.Request(url, data=content, headers=headers, method="PUT")
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.load(response)
    if not result.get("success"):
        raise ValueError(f"Cloudflare rejected the content update: {result.get('errors')}")
    after, _, _ = get_live_script(account_id, token)
    if hashlib.sha256(b"\n".join(payload for _, _, payload in after)).hexdigest() != hashlib.sha256(
        b"\n".join(payload for _, _, payload in modules)
    ).hexdigest():
        raise ValueError("Cloudflare accepted the update but the live Worker content differs")
    print("Updated production Worker code and verified the exact patched content; configuration preserved")


if __name__ == "__main__":
    main()
