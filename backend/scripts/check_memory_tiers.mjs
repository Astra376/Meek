import assert from "node:assert/strict";
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync(":memory:");
db.exec(`CREATE TABLE conversations(id TEXT PRIMARY KEY, version INTEGER);
CREATE TABLE messages(id TEXT, position INTEGER, role TEXT, content TEXT, conversation_id TEXT, selected_regeneration_id TEXT);
CREATE TABLE assistant_regenerations(id TEXT, message_id TEXT, content TEXT);
CREATE TABLE conversation_memories(conversation_id TEXT PRIMARY KEY, short_term TEXT, long_term TEXT, auto_long_term_entries TEXT, last_consolidated_position INTEGER, revision INTEGER, updated_at INTEGER, mid_term TEXT, scene_state TEXT, emotion_state TEXT, personality_state TEXT, psychology_state TEXT, invalidated_from_position INTEGER);
INSERT INTO conversations VALUES ('test',1);
INSERT INTO conversation_memories VALUES ('test','old short','','[]',8,1,0,'',NULL,NULL,NULL,NULL,NULL);`);
for(let i=0;i<12;i++) db.prepare("INSERT INTO messages VALUES (?,?,?,?,?,NULL)").run("id"+i,i,i%2?"assistant":"user",i===2?"I'll meet you at the old station.":"Synthetic turn "+i,"test");
const IMMEDIATE_CONTEXT_MESSAGES=8, SHORT_TERM_HORIZON_MESSAGES=48, CONSOLIDATION_INTERVAL_MESSAGES=4, LONG_TERM_MEMORY_LIMIT=32000;
const env={DB:{prepare(sql){return {bind(...args){const st=db.prepare(sql);return {all:async()=>({results:st.all(...args)}),run:async()=>({meta:st.run(...args)})}}}}};
const ctx={env,user:{userId:"test"}};
const run=statement=>statement.run();
const requireOwnedConversation=async()=>({version:1,character_id:"character"});
const getCharacterById=async()=>({name:"Character",system_prompt:"fictional roleplay"});
const row=()=>db.prepare("SELECT * FROM conversation_memories").get();
const memorySnapshot=async()=>({raw:row(),memory:row(),limits:{shortTerm:4000,midTerm:8000,longTerm:32000}});
const formatAdvancedCharacterDefinition=()=>"", selectRelevantMemory=s=>s;
const normalizeEmotionState=()=>null,evolveEmotionState=()=>null,normalizePersonalityState=()=>null,evolvePersonalityState=()=>null,normalizePsychologyState=()=>null,normalizeSceneState=()=>null,hasGroundedEvidence=()=>false;
let pass=0;
async function completeChatText(modelEnv,messages){
  assert.equal(modelEnv.OPENROUTER_MODEL,"deepseek/deepseek-v4.1-flash-1017");
  const payload=JSON.parse(messages[1].content);
  assert.match(payload.earlierArcHorizon,/old station/);
  if(pass++===0) {
    assert.match(payload.durableEvidence,/old station/);
    return JSON.stringify({shortTerm:"They set a meeting place.",midTerm:"Their meeting at the station is pending.",longTermAdditions:[{text:"They agreed to meet at the old station.",sourcePosition:"9",sourceQuote:"I’ll meet you at the  old station."},{text:"Invented.",sourcePosition:3,sourceQuote:"Unfounded quote"}]});
  }
  return JSON.stringify({shortTerm:"The story continues.",midTerm:" ",longTermAdditions:[]});
}
await consolidateCharacterMemory(ctx,"test");
assert.equal(row().short_term,"They set a meeting place.");
assert.equal(row().mid_term,"Their meeting at the station is pending.");
assert.equal(row().long_term,"- They agreed to meet at the old station.");
assert.equal(JSON.parse(row().auto_long_term_entries)[0].sourcePosition,2);
for(let i=12;i<16;i++) db.prepare("INSERT INTO messages VALUES (?,?,?,?,?,NULL)").run("id"+i,i,"user","More story "+i,"test");
await consolidateCharacterMemory(ctx,"test");
assert.equal(row().mid_term,"Their meeting at the station is pending.");
assert.equal(row().long_term,"- They agreed to meet at the old station.");
assert.equal(row().last_consolidated_position,15);
console.log("Verified short, mid and durable writes and readback across two consolidations, historical evidence, and rejected ungrounded facts.");
