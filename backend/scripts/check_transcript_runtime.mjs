import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { Miniflare, createFetchMock, Log, LogLevel } from 'miniflare';

const root = process.argv[2];
const manifest = JSON.parse(readFileSync(join(root, 'modules.json'), 'utf8'));
const schema = JSON.parse(readFileSync(join(root, 'schema.json'), 'utf8'));
const source = readFileSync(manifest.find(m => m.main).path, 'utf8');
// Only known code-shape booleans leave this process, never deployed source.
for (const name of ['editMessageAtomically', 'rewindToMessageAtomically', 'changes()', 'requiredChatPreparation']) {
  console.log('Known implementation present:', name, source.includes(name));
}
const fetchMock = createFetchMock();
fetchMock.disableNetConnect();
const secret = 'isolated-transcript-runtime-test-not-a-production-key';
const mf = new Miniflare({
  modulesRoot: root,
  modules: manifest.map(m => ({ type: 'ESModule', path: m.path, contents: readFileSync(m.path, 'utf8') })),
  compatibilityDate: '2026-04-01',
  compatibilityFlags: ['enable_request_signal', 'nodejs_compat'],
  d1Databases: ['DB'], r2Buckets: ['ASSETS'],
  bindings: { SESSION_HMAC_SECRET: secret },
  fetchMock, log: new Log(LogLevel.ERROR),
});

function token(userId) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ userId, tokenType: 'access', exp: Math.floor(Date.now()/1000)+600 })).toString('base64url');
  return `${header}.${payload}.${createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url')}`;
}

try {
  const db = await mf.getD1Database('DB');
  // Rebuild only empty schema locally. Production user records are never read.
  for (const row of schema) await db.prepare(row.sql).run();
  async function insert(table, values) {
    const columns = (await db.prepare(`PRAGMA table_info(${table})`).all()).results;
    const record = {};
    for (const col of columns) {
      if (col.name in values) record[col.name] = values[col.name];
      else if (col.notnull && col.dflt_value === null) record[col.name] = /INT|REAL|NUM/i.test(col.type) ? 0 : 'test';
    }
    await db.prepare(`INSERT INTO ${table} (${Object.keys(record).join(', ')}) VALUES (${Object.keys(record).map(() => '?').join(', ')})`).bind(...Object.values(record)).run();
  }
  const now = Date.now();
  await insert('users', { id: 'test-owner', google_subject: 'test-subject', email: 'transcript-test@example.invalid', created_at: now, last_login_at: now });
  await insert('characters', { id: 'test-character', owner_user_id: 'test-owner', name: 'Test', visibility: 'private', greeting: 'Test', system_prompt: 'Test', created_at: now, updated_at: now, last_active_at: now });
  await insert('conversations', { id: 'test-conversation', owner_user_id: 'test-owner', character_id: 'test-character', created_at: now, updated_at: now, started_at: now });
  for (const [id, position, role, content] of [['test-user', 0, 'user', 'Before'], ['test-reply', 1, 'assistant', 'Original'], ['test-later-user', 2, 'user', 'Later'], ['test-later-reply', 3, 'assistant', 'Later reply']]) {
    await insert('messages', { id, conversation_id: 'test-conversation', position, role, content, edited: 0, created_at: now, updated_at: now });
  }
  await insert('assistant_regenerations', { id: 'test-variant', message_id: 'test-reply', content: 'Alternate', created_at: now });
  await db.prepare("UPDATE messages SET selected_regeneration_id = 'test-variant' WHERE id = 'test-reply'").run();

  async function request(method, path, body, user = 'test-owner') {
    const response = await mf.dispatchFetch(`https://local.test${path}`, { method, headers: { Authorization: `Bearer ${token(user)}`, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await response.text();
    let code = ''; try { code = JSON.parse(text).code ?? ''; } catch {}
    console.log('Synthetic route result:', method, path.replace(/test-[\w-]+/g, 'fixture'), response.status, code);
    return response.status;
  }
  const failures = [];
  async function check(label, operation) {
    try { await operation(); console.log('PASS', label); }
    catch (error) { failures.push(label); console.log('FAIL', label, String(error.message).slice(0, 350)); }
  }
  await check('edit persists on server', async () => {
    assert.equal(await request('PATCH', '/v1/messages/test-user', { content: 'Edited' }), 204);
    assert.equal((await db.prepare("SELECT content FROM messages WHERE id='test-user'").first()).content, 'Edited');
  });
  await check('selected response edit persists', async () => {
    assert.equal(await request('PATCH', '/v1/messages/test-reply', { content: 'Edited alternate' }), 204);
    assert.equal((await db.prepare("SELECT content FROM assistant_regenerations WHERE id='test-variant'").first()).content, 'Edited alternate');
  });
  await check('rewind persists on server', async () => {
    assert.equal(await request('POST', '/v1/messages/test-reply/rewind'), 204);
    assert.equal((await db.prepare('SELECT count(*) AS total FROM messages').first()).total, 2);
  });
  await check('repeat rewind remains at selected message', async () => {
    assert.equal(await request('POST', '/v1/messages/test-reply/rewind'), 204);
    assert.equal((await db.prepare('SELECT count(*) AS total FROM messages').first()).total, 2);
  });
  await check('expired generation does not block edits', async () => {
    await db.prepare("UPDATE conversations SET active_run_id='test-expired', active_run_expires_at=?").bind(now-1000).run();
    assert.equal(await request('PATCH', '/v1/messages/test-user', { content: 'After expired run' }), 204);
  });
  await check('other users cannot edit private conversation', async () => {
    assert.equal(await request('PATCH', '/v1/messages/test-user', { content: 'Wrong owner' }, 'test-other'), 403);
  });
  assert.equal(failures.length, 0, `Failed: ${failures.join(', ')}`);
} finally {
  await mf.dispose();
}
