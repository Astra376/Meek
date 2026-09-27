"""Patch only reviewed transcript handlers; preserve other live features/bindings.

Production code/schema stay in disposable runner files, never logs/artifacts.
Checks exercise the real downloaded handlers with synthetic local D1 data.
"""

import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request

from hotfix_live_chat import API_BASE, SCRIPT_NAME, get_live_script, encode_multipart

SCRIPT_ROOT = Path(__file__).resolve().parent
EXPECTED = json.loads((SCRIPT_ROOT / "transcript-before.json").read_text())


def fragments(source):
    marker = "// src/db/queries/transcriptMutations.ts"
    if source.count(marker) != 1:
        raise ValueError("Expected one transcript query module")
    start = source.index(marker)
    end = source.index("\n// src/", start + len(marker))
    result = {"queries": source[start:end]}
    for name in ("requireMutableMessage", "editMessage", "rewindConversation"):
        matches = list(re.finditer(r"^(?:async )?function " + name + r"\([^\n]*\) \{.*?^}", source, re.S | re.M))
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one {name} function")
        result[name] = matches[0].group()
    return result


def patch_source(source, updated):
    before = fragments(source)
    after = fragments(updated)
    # Wrangler adds debug-name annotations to an otherwise identical bundle.
    def without_debug_names(parts):
        return {name: re.sub(r'^__name\([\w$]+, "[\w$]+"\);\n?', '', value, flags=re.M) for name, value in parts.items()}
    if without_debug_names(before) == without_debug_names(after):
        return source
    if without_debug_names(before) != without_debug_names(EXPECTED):
        changed = [name for name in before if before[name] != EXPECTED[name]]
        # Diagnose compiler naming/formatting without disclosing live source.
        # Only tokens already present in public baseline source are printable.
        token_pattern = r'[A-Za-z_$][\w$]*|[^\s]'
        for name in changed:
            known = re.findall(token_pattern, EXPECTED[name])
            actual = re.findall(token_pattern, before[name])
            allowed = set(known)
            changes = []
            for op, a, b, c, d in difflib.SequenceMatcher(None, known, actual, autojunk=False).get_opcodes():
                if op == "equal":
                    continue
                public = actual[c:d]
                safe = [value if value in allowed else "<unmatched-identifier>" if re.fullmatch(r'[A-Za-z_$][\w$]*', value) else "<unmatched-token>" for value in public]
                changes.append({"kind": op, "expected": known[a:b][:15], "actual_known_tokens": safe[:15], "count": d-c})
            print("Public-token comparison", name, json.dumps(changes[:16]), flush=True)
        raise ValueError(f"Live transcript code differs from reviewed baseline: {changed}; no deployment performed")
    for name, old in before.items():
        if source.count(old) != 1:
            raise ValueError(f"Ambiguous replacement for {name}")
        source = source.replace(old, after[name], 1)
    return source


def runtime_check(modules, main_module, schema, expect_blocked=False):
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        manifest = []
        for index, (name, mime, payload) in enumerate(modules):
            path = root / f"module-{index}.mjs"
            path.write_bytes(payload)
            manifest.append({"name": name, "path": str(path), "main": name == main_module or index == 0})
        (root / "modules.json").write_text(json.dumps(manifest))
        (root / "schema.json").write_text(json.dumps(schema))
        env = {key: value for key, value in os.environ.items() if not key.startswith("CLOUDFLARE_")}
        env["EXPECT_ACTIVE_LOCK_BLOCK"] = "1" if expect_blocked else "0"
        subprocess.run(["node", str(SCRIPT_ROOT / "check_transcript_runtime.mjs"), directory], env=env, check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", required=True)
    parser.add_argument("--input", help="Check patch against a local baseline only")
    parser.add_argument("--deploy", action="store_true")
    args = parser.parse_args()
    updated = Path(args.bundle).read_text()
    if args.input:
        source = Path(args.input).read_text()
        patched = patch_source(source, updated)
        assert fragments(patched) == fragments(updated)
        assert patch_source(patched, updated) == patched
        print("Exact patch anchors and repeat-deploy checks passed")
        return

    account_id = os.environ["CLOUDFLARE_ACCOUNT_ID"]
    token = os.environ["CLOUDFLARE_API_TOKEN"]
    modules, main_module, etag = get_live_script(account_id, token)
    request = urllib.request.Request(
        f"{API_BASE}/accounts/{account_id}/workers/scripts/{SCRIPT_NAME}/deployments",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        deployment = json.load(response)["result"]["deployments"][0]
    print("Active Worker deployment", deployment["created_on"], deployment["versions"], flush=True)
    targets = [index for index, (_, _, payload) in enumerate(modules) if b"// src/db/queries/transcriptMutations.ts" in payload]
    if len(targets) != 1:
        raise ValueError("Expected one live transcript module")
    index = targets[0]
    original = modules[index][2].decode()
    patched = patch_source(original, updated)
    request = urllib.request.Request(
        f"{API_BASE}/accounts/{account_id}/d1/database/ad611737-fc84-4002-bcfe-0f0ce257f2ae/query",
        data=json.dumps({"sql": "SELECT name, type, sql FROM sqlite_master WHERE type IN ('table', 'index', 'trigger') AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY CASE type WHEN 'table' THEN 0 WHEN 'index' THEN 1 ELSE 2 END, name", "params": []}).encode(),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        schema_result = json.load(response)
    if not schema_result.get("success"):
        raise ValueError("Could not load empty test schema")
    schema = schema_result["result"][0]["results"]
    print("Checking original handlers against local synthetic conversations", flush=True)
    runtime_check(modules, main_module, schema, expect_blocked=original != patched)
    updated_modules = list(modules)
    updated_modules[index] = (modules[index][0], modules[index][1], patched.encode())
    print("Checking patched handlers against local synthetic conversations", flush=True)
    runtime_check(updated_modules, main_module, schema)
    if not args.deploy or original == patched:
        print("Runtime checks passed; " + ("patch already deployed" if original == patched else "no production write requested"))
        return

    current, _, current_etag = get_live_script(account_id, token)
    if current != modules or current_etag != etag:
        raise ValueError("Production changed during checks; no deployment performed")
    content, content_type = encode_multipart(updated_modules, main_module or modules[index][0])
    headers = {"Authorization": f"Bearer {token}", "Content-Type": content_type}
    if etag:
        headers["If-Match"] = etag
    request = urllib.request.Request(
        f"{API_BASE}/accounts/{account_id}/workers/scripts/{SCRIPT_NAME}/content",
        data=content, headers=headers, method="PUT",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.load(response)
    if not result.get("success"):
        raise ValueError("Cloudflare rejected the update")
    verified, _, _ = get_live_script(account_id, token)
    digest = lambda values: hashlib.sha256(b"\n".join(payload for _, _, payload in values)).digest()
    if digest(verified) != digest(updated_modules):
        raise ValueError("Deployment readback differs from tested code")
    print("Production transcript patch deployed and exact Worker content verified; configuration preserved")


if __name__ == "__main__":
    main()
