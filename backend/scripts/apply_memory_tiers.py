"""Patch the active Worker without replacing its newer services or bindings."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import deploy_task_routing as api

MIMO = '{ ...context.env, OPENROUTER_MODEL: "xiaomi/mimo-v2.6-pro", OPENROUTER_FALLBACK_MODELS: "", OPENROUTER_PROVIDERS: "" }'
FLASH = MIMO.replace("xiaomi/mimo-v2.6-pro", api.MODEL)


def once(s, old, new):
    if s.count(old) != 1:
        raise ValueError("Unexpected production anchor: " + old[:70])
    return s.replace(old, new, 1)


def handler(s, name):
    return re.search(r"^(?:async )?function " + name + r"\(.*?^}", s, re.M | re.S)[0]


def patch(source):
    print("Checking live handler anchors in", hashlib.sha256(source.encode()).hexdigest(), flush=True)
    s = source
    for name in ("autoCreateCharacter", "generateCharacterGreeting", "consolidateCharacterMemory", "processOfflineMessages", "processGroupFollowups"):
        body = handler(s, name)
        print(name, [line.strip()[:340] for line in body.splitlines() if ("completeChatText(" in line or "const midSources" in line or "const durableSources" in line or "processCandidate(" in line)], flush=True)
    completion = handler(s, "completeChatText")
    if api.MODEL in completion:
        s = once(s, completion, re.sub(r'^  env = \{[^\n]*deepseek-v4\.1-flash-1017[^\n]*\};\n', '', completion, flags=re.M))
    assert api.MODEL not in handler(s, "completeChatText")
    if "BACKGROUND_GENERATION_DISABLED" not in s:
        guard = 'throw new AppError(410, "BACKGROUND_GENERATION_DISABLED", "Background generation is disabled.");\n'
        for name in ("conversationBackground", "generateChatBackground"):
            s = api.insert_at_entry(s, name, "  " + guard)
        s = api.insert_at_entry(s, "generateAppearance", '  if (input.kind === "background") ' + guard)
    assert s.count("BACKGROUND_GENERATION_DISABLED") == 3
    s = once(s, 'const response = await completeChatText({ ...context.env, OPENROUTER_MODEL: context.env.OPENROUTER_ULTRA_MODEL || "deepseek/deepseek-v4-pro-0813", OPENROUTER_FALLBACK_MODELS: "", OPENROUTER_PROVIDERS: context.env.OPENROUTER_ULTRA_PROVIDERS || "" },', 'const response = await completeChatText(' + MIMO + ',')
    s = once(s, "const greeting = await completeChatText(context.env,", "const greeting = await completeChatText(" + MIMO + ",")
    offline = handler(s, "processOfflineMessages")
    left = offline.index("  const result")
    right = offline.index("  await processRecommendations", left)
    assert "processCandidate(" in offline[left:right] and "offline_deliveries" in offline[left:right]
    assert not any(word in offline[left:right] for word in ("reap", "cleanup", "processRecommendations", "deliverNotificationEmails"))
    s = once(s, offline, offline[:left] + offline[right:])
    group = handler(s, "processGroupFollowups")
    assert '"away"' in group
    s = once(s, group, "async function processGroupFollowups(env) {}")
    helper = Path("/tmp/durable-source.mjs").read_text().split("\nexport {")[0]
    assert "function resolveMemorySource(" in helper and "function resolveMemorySource(" not in s
    merge = handler(s, "mergeLongTermMemory")
    left = merge.index("    if (sources &&")
    right = merge.index("    const normalized", left)
    s = once(s, merge, helper + "\n" + merge[:left] + """    const sourcePosition = sources ? resolveMemorySource(candidate, sources) : Math.min(maximumSourcePosition, Math.max(minimumSourcePosition, requestedPosition));
    if (sourcePosition === undefined) continue;
""" + merge[right:])
    m = handler(s, "consolidateCharacterMemory")
    new = m
    for old, replacement in [
        ("conversationId, changedFromPosition) {", "conversationId, changedFromPosition, retryConflict = true) {"),
        ("  const shortSources =", "  const durableSources = memory.long_term.trim() ? newSources : transcript;\n  const shortSources ="),
        (" - IMMEDIATE_CONTEXT_MESSAGES - SHORT_TERM_HORIZON_MESSAGES", " - IMMEDIATE_CONTEXT_MESSAGES"),
        ("await modelEnvironmentForUser(context.env, context.user.userId)", FLASH),
        ("Preserve existing useful mid-term information.", "Use earlierArcHorizon and the new transcript. Record ongoing goals, relationship changes, and unresolved threads as soon as established. Preserve existing useful mid-term information."),
        ("quote the exact supporting phrase from the new transcript.", "use durableEvidence when supplied or newOrCorrectedTranscript for citations, including established facts previously missed. Copy the exact quote and its integer position."),
        ("      newOrCorrectedTranscript:", "      durableEvidence: durableSources === newSources ? undefined : format(durableSources, 26e3),\n      newOrCorrectedTranscript:"),
        ('typeof update.midTerm === "string" ?', 'typeof update.midTerm === "string" && update.midTerm.trim() ?'),
        ("latestPosition, limits.longTerm, newSources)", "latestPosition, limits.longTerm, durableSources)"),
        ("  await saveAutomaticConversationMemory(", "  const saved = await saveAutomaticConversationMemory("),
    ]:
        new = once(new, old, replacement)
    new = new[:-1] + "  if (!saved && retryConflict) await consolidateCharacterMemory(context, conversationId, undefined, false);\n}"
    return once(s, m, new)


def check(s):
    subprocess.run(["node", "--input-type=module", "--check"], input=s.encode(), check=True)
    for name in ("autoCreateCharacter", "generateCharacterGreeting"):
        assert MIMO in handler(s, name)
    assert api.MODEL not in handler(s, "completeChatText")
    assert FLASH in handler(s, "consolidateCharacterMemory")
    assert "processCandidate(" not in handler(s, "processOfflineMessages")
    assert not re.search(r'claimGroupContinuation\([^\n;]*["\']away["\']', s)
    assert not re.search(r'runGroup\([^\n;]*["\']away["\']', s)
    assert s.count("BACKGROUND_GENERATION_DISABLED") == 3
    names = ["consolidateCharacterMemory", "mergeLongTermMemory", "normalizeMemoryQuote", "resolveMemorySource", "selectShortTermHorizon", "parseMemoryUpdate", "parseAutomaticEntries", "saveAutomaticConversationMemory"]
    code = "\n".join(handler(s, name) for name in names)
    subprocess.run(["node","--input-type=module"],input=code+"\n"+Path("scripts/check_memory_tiers.mjs").read_text(),text=True,check=True)


def main():
    old = api.active()
    modules, etag = api.download()
    assert len(modules) == 1
    name, mime, data = modules[0]
    source = data.decode()
    if "function resolveMemorySource(" in source and FLASH in handler(source, "consolidateCharacterMemory"):
        check(source)
        assert api.download()[0][0][2] == data and api.active()["id"] == old["id"]
        assert len(old["versions"]) == 1 and old["versions"][0]["percentage"] == 100
        print("Verified existing active production deployment", old["versions"])
        return
    updated = patch(source)
    check(updated)
    assert api.download() == (modules, etag) and api.active()["id"] == old["id"]
    body, mime = api.encode_multipart([(name, mime, updated.encode())], name)
    headers = {**api.HEADERS, "Content-Type": mime}
    if etag:
        headers["If-Match"] = etag
    req = urllib.request.Request(api.ROOT + "/content", data=body, headers=headers, method="PUT")
    with urllib.request.urlopen(req, timeout=60) as res:
        assert json.load(res)["success"]
    time.sleep(10)
    assert api.download()[0][0][2] == updated.encode()
    current = api.active()
    assert current["id"] != old["id"] and len(current["versions"]) == 1 and current["versions"][0]["percentage"] == 100
    print("Verified active production deployment", current["versions"])


if __name__ == "__main__":
    main()
