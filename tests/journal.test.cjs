const {readFileSync}=require('node:fs');const assert=require('node:assert/strict');const {before,after,beforeEach,test}=require('node:test');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {ref,get,set,update,increment}=require('firebase/database');
const {doc,getDoc,setDoc,updateDoc,deleteDoc,writeBatch,serverTimestamp}=require('firebase/firestore');
let env;const world='d'.repeat(32),invite='b'.repeat(32),epoch='e'.repeat(32),root=`onlineWorlds/${world}`,base=`worlds/parent/${world}`,storage=base+'/storage';
const cipher='1.'+'a'.repeat(16)+'.'+'b'.repeat(146),member={active:true,proof:invite};
const db=uid=>env.authenticatedContext(uid).database(),fs=uid=>env.authenticatedContext(uid).firestore(),r=(uid,path)=>ref(db(uid),path);
const actor=()=>({rev:1,seq:0,inventory:cipher,op:'a'.repeat(32)});
async function seed(){await set(r('parent',base),{meta:{invite,side:2,closed:false},members:{parent:member,child:member,sibling:member}});await set(r('parent',storage),{control:{phase:'ready',epoch,source:'fixture'},head:{g:0,actors:{parent:actor(),child:actor(),sibling:actor()}}});}
function action(uid,seq=1,rev=2,before='',x=1,token='c'.repeat(32)){
 const key=`${x}_44_1`,chunk=`0_${Math.floor(x/16)}_2_0`,event=`${seq}/0/${key}/2/0/${token}/${before}`,last={region:0,chunk,key,x,y:44,z:1,type:2,water:false,token,before},a=`head/actors/${uid}/`;
 return {'head/g':increment(1),[a+'rev']:rev,[a+'seq']:seq,[a+'inventory']:cipher,[a+'op']:token,[a+'eventKey']:String(rev),[a+'last']:last,[a+`tail/e${seq%16}`]:event,'head/regions/s0':increment(1),['head/chunks/'+chunk]:increment(1),['chunks/'+chunk+'/cells/'+key]:{type:2,water:false,token},[`journal/${uid}/${rev}`]:{rev,seq,inventory:cipher,op:token,event}};
}
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-meicraft-journal',database:{rules:readFileSync('database.rules.json','utf8')},firestore:{rules:readFileSync('firestore.rules','utf8')}});});
beforeEach(async()=>{await env.clearDatabase();await env.clearFirestore();});after(async()=>{if(env)await env.cleanup();});
test('cell, resource revision, durable journal and counters commit together',async()=>{await seed();await assertSucceeds(update(r('child',storage),action('child')));const data=(await get(r('child',storage))).val();assert.equal(data.head.g,1);assert.equal(data.head.actors.child.rev,2);assert.equal(data.journal.child[2].seq,1);assert.equal(data.chunks['0_0_2_0'].cells['1_44_1'].token,'c'.repeat(32));});
test('partial cell, inventory-only, missing journal and forged counters are rejected',async()=>{
 await seed();const a=action('child');await assertFails(set(r('child',storage+'/chunks/0_0_2_0/cells/1_44_1'),a['chunks/0_0_2_0/cells/1_44_1']));
 for(const missing of ['journal/child/2','chunks/0_0_2_0/cells/1_44_1','head/g','head/regions/s0','head/chunks/0_0_2_0']){const p={...a};delete p[missing];await assertFails(update(r('child',storage),p));}
 const p={...a,'head/regions/s0':5};await assertFails(update(r('child',storage),p));assert.equal((await get(r('child',storage+'/head/actors/child/rev'))).val(),1);
});
test('same-cell concurrency accepts one winner and charges one inventory',async()=>{
 await seed();const results=await Promise.allSettled([update(r('child',storage),action('child')),update(r('sibling',storage),action('sibling',1,2,'',1,'f'.repeat(32)))]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);const h=(await get(r('child',storage+'/head'))).val();assert.equal(h.g,1);assert.equal(h.actors.child.rev+h.actors.sibling.rev,3);
});
test('different cells in one chunk commit concurrently without global CAS conflicts',async()=>{
 await seed();await Promise.all([update(r('child',storage),action('child')),update(r('sibling',storage),action('sibling',1,2,'',2,'f'.repeat(32)))]);const h=(await get(r('child',storage+'/head'))).val();assert.equal(h.g,2);assert.equal(h.chunks['0_0_2_0'],2);
});
test('stale resources, forged author, duplicate action and wrong previous nonce cannot change state',async()=>{
 await seed();await update(r('child',storage),action('child'));await assertFails(update(r('child',storage),action('child')));await assertFails(update(r('sibling',storage),action('child',2,3,'c'.repeat(32),1,'f'.repeat(32))));await assertFails(update(r('child',storage),action('child',2,3,'',1,'f'.repeat(32))));
 const h=(await get(r('child',storage+'/head'))).val();assert.equal(h.actors.child.rev,2);assert.equal(h.g,1);
});
test('craft-only resource changes have a journal entry and no block mutation',async()=>{
 await seed();const uid='child',a=`head/actors/${uid}/`,op='f'.repeat(32);await assertSucceeds(update(r(uid,storage),{'head/g':increment(1),[a+'rev']:2,[a+'inventory']:cipher,[a+'seq']:0,[a+'op']:op,[a+'eventKey']:'2','journal/child/2':{rev:2,seq:0,inventory:cipher,op,event:''}}));assert.equal((await get(r(uid,storage+'/head/g'))).val(),1);
});
test('members cannot overwrite durable state, change epoch or delete history without acknowledged backup',async()=>{
 await seed();await update(r('child',storage),action('child'));await assertFails(set(r('child',storage),{}));await assertFails(set(r('parent',storage+'/control/epoch'),'f'.repeat(32)));await assertFails(set(r('child',storage+'/journal/child/2'),null));await assertFails(set(r('child',storage+'/head/actors/parent/rev'),2));
});
test('host lease rejects parallel claim, permits release and another member takeover',async()=>{
 await seed();const a={uid:'child',token:'a'.repeat(32),until:Date.now()+15000};await assertSucceeds(set(r('child',storage+'/lease'),a));await assertFails(set(r('sibling',storage+'/lease'),{...a,uid:'sibling'}));await assertFails(set(r('child',storage+'/lease'),{...a,until:Date.now()+100000}));await assertSucceeds(set(r('child',storage+'/lease'),{...a,until:0}));await assertSucceeds(set(r('sibling',storage+'/lease'),{...a,uid:'sibling',until:Date.now()+15000}));
});
async function seedFs(){await env.withSecurityRulesDisabled(async c=>{const f=c.firestore();await setDoc(doc(f,root),{owner:'parent',name:'World',seed:123,side:2,protocol:6,invite,closed:false});for(const uid of ['parent','child'])await setDoc(doc(f,root+'/members/'+uid),{banned:false});await setDoc(doc(f,root+'/sync/v5'),{owner:'parent',phase:'ready',open:false,access:{parent:true,child:true}});});}
const upload='a'.repeat(32);const stage=(g=1)=>({writer:'child',epoch,g,state:'staging',ids:[upload+'_c_0_0_2_0'],at:serverTimestamp()});
const saved=(g=1)=>({upload,epoch,g,kind:'chunk',chunk:'0_0_2_0',cells:{'1_44_1':'2:0:'+'c'.repeat(32)}});
const manifest=(g=1)=>({epoch,g,upload,chunks:{'0_0_2_0':upload+'_c_0_0_2_0'},inventories:{},counters:{'0_0_2_0':1},regions:{s0:1},actors:{child:2},garbage:[],oldUploads:[],at:serverTimestamp()});
test('immutable backup pages require a staging upload and cannot be overwritten',async()=>{
 await seedFs();const f=fs('child'),path=root+'/saved/'+upload+'_c_0_0_2_0';await assertFails(setDoc(doc(f,path),saved()));await setDoc(doc(f,root+'/uploads/'+upload),stage());await assertSucceeds(setDoc(doc(f,path),saved()));await assertFails(setDoc(doc(f,path),saved()));
});
test('manifest and committed upload publish atomically; partial upload cannot replace current backup',async()=>{
 await seedFs();const f=fs('child');await setDoc(doc(f,root+'/uploads/'+upload),stage());await setDoc(doc(f,root+'/saved/'+upload+'_c_0_0_2_0'),saved());await assertFails(setDoc(doc(f,root+'/sync/checkpoint'),manifest()));await assertFails(updateDoc(doc(f,root+'/uploads/'+upload),{state:'committed'}));
 const b=writeBatch(f);b.set(doc(f,root+'/sync/checkpoint'),manifest());b.update(doc(f,root+'/uploads/'+upload),{state:'committed'});await assertSucceeds(b.commit());await assertFails(deleteDoc(doc(f,root+'/saved/'+upload+'_c_0_0_2_0')));
});
test('banned and unrelated players cannot read or publish checkpoints',async()=>{
 await seedFs();await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),root+'/members/child'),{banned:true}));await assertFails(setDoc(doc(fs('child'),root+'/uploads/'+upload),stage()));await assertFails(getDoc(doc(fs('stranger'),root+'/sync/checkpoint')));
});
test('protocol-6 freeze is atomic, resumable and prevents reopening old writers',async()=>{
 await seedFs();await env.withSecurityRulesDisabled(async c=>{await updateDoc(doc(c.firestore(),root),{protocol:5});await updateDoc(doc(c.firestore(),root+'/sync/v5'),{open:true});});
 const f=fs('parent'),b=writeBatch(f);b.update(doc(f,root),{protocol:6,migration:'rtdb6'});b.update(doc(f,root+'/sync/v5'),{open:false});await assertSucceeds(b.commit());
 await assertFails(updateDoc(doc(f,root+'/sync/v5'),{open:true}));const {deleteField}=require('firebase/firestore');await assertSucceeds(updateDoc(doc(f,root),{migration:deleteField()}));
});
test('abandoning an expired upload fences late writes without affecting the current manifest',async()=>{
 await seedFs();const f=fs('child');await setDoc(doc(f,root+'/uploads/'+upload),stage());await assertFails(updateDoc(doc(f,root+'/uploads/'+upload),{state:'abandoned'}));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),root+'/uploads/'+upload),{at:new Date(Date.now()-180000)}));await assertSucceeds(updateDoc(doc(f,root+'/uploads/'+upload),{state:'abandoned'}));await assertFails(setDoc(doc(f,root+'/saved/'+upload+'_c_0_0_2_0'),saved()));
 const b=writeBatch(f);b.set(doc(f,root+'/sync/checkpoint'),manifest());b.update(doc(f,root+'/uploads/'+upload),{state:'committed'});await assertFails(b.commit());assert.equal((await getDoc(doc(f,root+'/sync/checkpoint'))).exists(),false);
});
test('older and duplicate manifest generations cannot replace a newer coherent checkpoint',async()=>{
 await seedFs();const f=fs('child');await setDoc(doc(f,root+'/uploads/'+upload),stage(3));await setDoc(doc(f,root+'/saved/'+upload+'_c_0_0_2_0'),saved(3));const b=writeBatch(f);b.set(doc(f,root+'/sync/checkpoint'),manifest(3));b.update(doc(f,root+'/uploads/'+upload),{state:'committed'});await b.commit();
 await assertFails(setDoc(doc(f,root+'/sync/checkpoint'),manifest(2)));await assertFails(setDoc(doc(f,root+'/sync/checkpoint'),manifest(3)));assert.equal((await getDoc(doc(f,root+'/sync/checkpoint'))).data().g,3);
});
test('REST stream receives an atomic head frame with resource and block revisions together',async()=>{
 await seed();const {createMockUserToken}=require('@firebase/util');const token=createMockUserToken({sub:'child',user_id:'child'},'demo-meicraft-journal');
 const endpoint=`http://${process.env.FIREBASE_DATABASE_EMULATOR_HOST}/${storage}/head.json?ns=demo-meicraft-journal&auth=${encodeURIComponent(token)}`;
 const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),10000);try{
  const response=await fetch(endpoint,{headers:{Accept:'text/event-stream'},signal:abort.signal});assert.equal(response.status,200);const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
  async function frame(){for(;;){const cut=buffer.indexOf('\n\n');if(cut>=0){const f=buffer.slice(0,cut);buffer=buffer.slice(cut+2);if(f.includes('event: put')||f.includes('event: patch'))return JSON.parse(f.split('\n').find(l=>l.startsWith('data:')).slice(5));}const {value,done}=await reader.read();if(done)throw Error('stream closed');buffer+=decoder.decode(value,{stream:true});}}
  const initial=await frame();await update(r('child',storage),action('child'));const next=await frame();assert.equal(next.path,'/');const state=initial.data;for(const [key,value] of Object.entries(next.data)){const parts=key.split('/');let node=state;for(const part of parts.slice(0,-1))node=node[part]??={};node[parts.at(-1)]=value;}assert.equal(state.g,1);assert.equal(state.actors.child.rev,2);assert.equal(state.regions.s0,1);
 }finally{clearTimeout(timer);abort.abort();}
});
test('world deletion clears durable data only after owner closure and retains the tombstone',async()=>{
 await seed();await update(r('child',storage),action('child'));await assertFails(set(r('parent',storage),null));await set(r('parent',base+'/meta/closed'),true);await assertFails(set(r('child',storage),null));await assertSucceeds(set(r('parent',storage),null));assert.equal((await get(r('parent',storage))).exists(),false);assert.equal((await get(r('parent',base+'/meta/closed'))).val(),true);
});
test('Android POST method override commits the identical atomic journal mutation',async()=>{
 await seed();const {createMockUserToken}=require('@firebase/util');const token=createMockUserToken({sub:'child',user_id:'child'},'demo-meicraft-journal');
 const endpoint=`http://${process.env.FIREBASE_DATABASE_EMULATOR_HOST}/${storage}.json?ns=demo-meicraft-journal&auth=${encodeURIComponent(token)}`;
 const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json','X-HTTP-Method-Override':'PATCH'},body:JSON.stringify(action('child'))});assert.equal(response.status,200,await response.text());const h=(await get(r('child',storage+'/head'))).val();assert.equal(h.g,1);assert.equal(h.actors.child.rev,2);assert.equal((await get(r('child',storage+'/journal/child/2'))).val().seq,1);
});

// Same compact batch contract as JournalBatch.patch: ordinary per-cell revisions,
// one atomic transport, one head generation, exact per-region/chunk counters.
function batch(uid,blocks,{base=0,seq=0,rev=1,presenceBase=0,growthToken,growthDay}={}){
 const a=`head/actors/${uid}/`,patch={'head/g':increment(1),[a+'count']:blocks.length,[a+'base']:base,[a+'presenceBase']:presenceBase,[a+'op']:'f'.repeat(32)},items={};
 blocks.forEach((b,i)=>{
  const slot='b'+i,key=`${b.x}_${b.y}_${b.z}`,chunk=`${b.region??0}_${Math.floor(b.x/16)}_${Math.floor(b.y/16)}_${Math.floor(b.z/16)}`,token=b.token??(i+1).toString(16).padStart(32,'0'),nature=b.nature??'',last={region:b.region??0,chunk,key,x:b.x,y:b.y,z:b.z,type:b.type,water:false,token,before:b.before??'',nature},r=rev+i+1,q=seq+i+1,event=`${q}/${last.region}/${key}/${b.type}/0/${token}/${last.before}`+(nature?'/'+nature:'');
  items[slot]={last,rev:r,seq:q,inventory:cipher};patch[`journal/${uid}/${r}`]={rev:r,seq:q,inventory:cipher,op:'f'.repeat(32),event,slot};patch[a+`tail/e${q%16}`]=event;patch[`chunks/${chunk}/cells/${key}`]={type:b.type,water:false,token,nature,slot};
  if(i===blocks.length-1)Object.assign(patch,{[a+'last']:last,[a+'rev']:r,[a+'seq']:q,[a+'inventory']:cipher,[a+'eventKey']:String(r)});
 });
 patch[a+'batch']=items;
 for(const [name,field] of [['regions','region'],['chunks','chunk']]){
  const counts={};for(const item of Object.values(items)){const key=name==='regions'?'s'+item.last.region:item.last.chunk;counts[key]=(counts[key]??0)+1;}for(const [key,n] of Object.entries(counts))patch[`head/${name}/${key}`]=increment(n);
 }
 if(growthToken)Object.assign(patch,{[a+'growthToken']:growthToken,[a+'growthDay']:growthDay});return patch;
}
const growthBlocks=()=>[{x:20,y:30,z:20,type:5,nature:'tree_1_20000_1234'},{x:20,y:31,z:20,type:6,nature:'leaf'}];
test('nature multi-cell journal batch is atomic and retains every durable revision',async()=>{
 await seed();await assertSucceeds(update(r('child',storage),batch('child',growthBlocks())));const s=(await get(r('child',storage))).val();assert.equal(s.head.g,1);assert.equal(s.head.actors.child.rev,3);assert.equal(s.head.regions.s0,2);assert.equal(s.head.chunks['0_1_1_1'],2);assert.equal(Object.keys(s.journal.child).length,2);
});
test('a 13-cell cross-chunk crown preserves counters, journal and final inventory',async()=>{
 await seed();const blocks=Array.from({length:13},(_,i)=>({x:10+i,y:30,z:20,type:6,nature:'leaf'}));await assertSucceeds(update(r('child',storage),batch('child',blocks)));const s=(await get(r('child',storage))).val();assert.equal(s.head.regions.s0,13);assert.equal(s.head.chunks['0_0_1_1'],6);assert.equal(s.head.chunks['0_1_1_1'],7);assert.equal(s.head.actors.child.rev,14);
});
test('partial batch or wrong region/chunk counter cannot publish any tree block',async()=>{
 await seed();const original=batch('child',growthBlocks());for(const missing of ['journal/child/2','chunks/0_1_1_1/cells/20_30_20','head/regions/s0','head/chunks/0_1_1_1']){const p={...original};delete p[missing];await assertFails(update(r('child',storage),p));}await assertFails(update(r('child',storage),{...original,'head/chunks/0_1_1_1':increment(1)}));assert.equal((await get(r('child',storage+'/head/g'))).val(),0);
});
test('simultaneous builder and nature batch have one winner; stale plans cannot overwrite buildings',async()=>{
 await seed();const results=await Promise.allSettled([update(r('child',storage),batch('child',growthBlocks())),update(r('sibling',storage),action('sibling',1,2,'',20))]);assert.ok(results.some(x=>x.status==='fulfilled'));await assertFails(update(r('child',storage),batch('child',growthBlocks())));
});
test('presence changes invalidate a previously planned batch with no new block traffic',async()=>{
 await seed();await assertSucceeds(update(r('sibling',base+'/live'),{'players/sibling':{name:'Sibling',sector:0,x:20,y:31,z:20,yaw:0,t:{'.sv':'timestamp'}},poseVersion:increment(1)}));await assertFails(update(r('child',storage),batch('child',growthBlocks())));
 await assertFails(set(r('sibling',base+'/live/players/sibling'),{name:'Sibling',sector:0,x:20,y:31,z:20,yaw:0,t:{'.sv':'timestamp'}}));
});
const utcDay=()=>Math.floor(Date.now()/86400000),run=(uid,token='a'.repeat(32))=>({uid,token,day:utcDay(),until:Date.now()+15000,done:false});
test('first daily login wins and completed day cannot be claimed again or forged',async()=>{
 await seed();const results=await Promise.allSettled([set(r('child',storage+'/growth/run'),run('child')),set(r('sibling',storage+'/growth/run'),run('sibling'))]);assert.equal(results.filter(x=>x.status==='fulfilled').length,1);const winner=(await get(r('child',storage+'/growth/run'))).val();await set(r(winner.uid,storage+'/growth/run'),{...winner,until:0,done:true});await assertFails(set(r('child',storage+'/growth/run'),run('child')));await assertFails(set(r('child',storage+'/growth/run'),{...run('child'),day:utcDay()+1}));
});
test('expired daily worker is replaceable but old token is fenced',async()=>{
 await seed();await env.withSecurityRulesDisabled(c=>set(ref(c.database(),storage+'/growth/run'),{...run('child'),until:Date.now()-1000}));await assertSucceeds(set(r('sibling',storage+'/growth/run'),run('sibling','b'.repeat(32))));await assertFails(update(r('child',storage),batch('child',growthBlocks(),{growthToken:'a'.repeat(32),growthDay:utcDay()})));
});
test('one sapling growth per UTC day is enforced by the database index',async()=>{
 await seed();const key='20_30_20',token='c'.repeat(32),plantedAt=Date.now()-86400000;
 await env.withSecurityRulesDisabled(c=>set(ref(c.database(),storage+'/growth/saplings/0/'+key),{stage:0,plantedAt,lastGrowthDay:utcDay()-1,token}));
 await set(r('child',storage+'/growth/run'),run('child'));
 const p=batch('child',growthBlocks(),{growthToken:'a'.repeat(32),growthDay:utcDay()});p['growth/saplings/0/'+key]={stage:1,plantedAt,lastGrowthDay:utcDay(),token:'1'.padStart(32,'0')};await assertSucceeds(update(r('child',storage),p));
 const next=batch('child',[{x:20,y:30,z:20,type:5,token:'3'.padStart(32,'0'),before:'1'.padStart(32,'0'),nature:'tree_2_'+utcDay()+'_'+plantedAt},{x:20,y:31,z:20,type:5,token:'4'.padStart(32,'0'),before:'2'.padStart(32,'0')}],{base:1,seq:2,rev:3,growthToken:'a'.repeat(32),growthDay:utcDay()});next['growth/saplings/0/'+key]={stage:2,plantedAt,lastGrowthDay:utcDay(),token:'3'.padStart(32,'0')};await assertFails(update(r('child',storage),next));
});
test('player planting persists an eighth-field journal event and index atomically',async()=>{
 await seed();const day=utcDay(),at=Date.now()-1000,token='c'.repeat(32),p=action('child'),a='head/actors/child/',last=p[a+'last'];last.type=6;last.nature=`tree_0_${day}_${at}`;const event=`1/0/1_44_1/6/0/${token}//${last.nature}`;p[a+'tail/e1']=event;p['journal/child/2'].event=event;p['chunks/0_0_2_0/cells/1_44_1']={type:6,water:false,token,nature:last.nature};p['growth/saplings/0/1_44_1']={stage:0,plantedAt:at,lastGrowthDay:day,token};await assertSucceeds(update(r('child',storage),p));assert.equal((await get(r('child',storage+'/growth/saplings/0/1_44_1/stage'))).val(),0);
 const incomplete={...p};delete incomplete['chunks/0_0_2_0/cells/1_44_1'];await assertFails(update(r('sibling',storage),incomplete));
});
test('root provenance reconstructs a missing index without changing the saved day or stage',async()=>{
 await seed();const day=utcDay()-1,at=Date.now()-86400000,token='a'.repeat(32),key='20_30_20',chunk='0_1_1_1';await env.withSecurityRulesDisabled(c=>set(ref(c.database(),storage+'/chunks/'+chunk+'/cells/'+key),{type:5,water:false,token,nature:`tree_2_${day}_${at}`}));
 await assertSucceeds(set(r('child',storage+'/growth/saplings/0/'+key),{chunk,stage:2,plantedAt:at,lastGrowthDay:day,token}));await assertFails(set(r('child',storage+'/growth/saplings/0/'+key),{chunk,stage:1,plantedAt:at,lastGrowthDay:day,token}));assert.equal((await get(r('child',storage+'/growth/saplings/0/'+key+'/stage'))).val(),2);
});
test('shared decay cadence prevents several clients from deleting a whole crown at once',async()=>{
 await seed();const wave=uid=>batch(uid,Array.from({length:4},(_,i)=>({x:i+10,y:30,z:20,type:0})));const first=wave('child');first['head/actors/child/decayRegion']=0;first['growth/decay/0']={at:{'.sv':'timestamp'}};await assertSucceeds(update(r('child',storage),first));
 const second=batch('sibling',Array.from({length:4},(_,i)=>({x:i+20,y:30,z:20,type:0})),{base:1});second['head/actors/sibling/decayRegion']=0;second['growth/decay/0']={at:{'.sv':'timestamp'}};await assertFails(update(r('sibling',storage),second));assert.equal((await get(r('child',storage+'/head/regions/s0'))).val(),4);
});
