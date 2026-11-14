const {readFileSync}=require('node:fs');
const assert=require('node:assert/strict');
const {before,after,beforeEach,test}=require('node:test');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {ref,get,set,update,remove,serverTimestamp}=require('firebase/database');
let env;
const world='a'.repeat(32),invite='b'.repeat(32),base=`worlds/parent/${world}`;
const db=uid=>uid?env.authenticatedContext(uid).database():env.unauthenticatedContext().database();
const node=(uid,path='')=>ref(db(uid),base+(path?'/'+path:''));
const member=(active=true)=>({active,proof:invite});
const root=()=>({meta:{invite,side:2,closed:false},members:{parent:member()}});
const pose=()=>({name:'Kind',sector:0,x:48,y:30,z:48,yaw:0,t:serverTimestamp()});
const signal=rev=>({rev,t:serverTimestamp()});
async function create(){await set(node('parent'),root());}
async function join(uid='child'){await set(node(uid,`members/${uid}`),member());}
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-meicraft',database:{rules:readFileSync('database.rules.json','utf8')}});});
beforeEach(async()=>{await env.clearDatabase();});
after(async()=>{if(env)await env.cleanup();});
test('owner can probe missing namespace and initialize it; others cannot squat',async()=>{
 await assertSucceeds(get(node('parent')));await assertFails(set(node('child'),root()));await assertFails(set(node(),root()));await assertSucceeds(create());
 await assertFails(set(ref(db('child'),`worlds/child/${world}`),{...root(),members:{parent:member()}}));
});
test('unknown world IDs, metadata, missing owner and injected initial poses are denied',async()=>{
 await assertFails(set(ref(db('parent'),'worlds/parent/bad'),root()));
 await assertFails(set(node('parent'),{...root(),payload:1}));await assertFails(set(node('parent'),{meta:root().meta,members:{child:member()}}));
 await assertFails(set(node('parent'),{...root(),live:{players:{child:pose()}}}));
});
test('existing root cannot be replaced, invitation and side remain immutable',async()=>{
 await create();await assertFails(set(node('parent'),root()));await assertFails(set(node('parent','meta/invite'),'c'.repeat(32)));await assertFails(set(node('parent','meta/side'),3));
});
test('invitation permits only own initial membership, own probe does not leak other nodes',async()=>{
 await create();await assertSucceeds(get(node('child','members/child')));await assertFails(get(node('child')));await assertFails(get(node('child','members/parent')));
 await assertFails(set(node('child','members/child'),{...member(),proof:'c'.repeat(32)}));await assertFails(set(node('child','members/sibling'),member()));await assertSucceeds(join());
 await assertSucceeds(get(node('child','live')));await assertFails(get(ref(db('child'),'worlds')));
});
test('only own pose and signal are writable; another world remains private',async()=>{
 await create();await join();await assertSucceeds(set(node('child','live/players/child'),pose()));await assertSucceeds(set(node('child','live/signals/child'),signal(1)));
 await assertFails(set(node('child','live/players/parent'),pose()));await assertFails(set(node('child','live/signals/parent'),signal(1)));await assertFails(get(node()));
 await assertFails(set(ref(db('child'),`worlds/stranger/${world}/live/players/child`),pose()));
});
test('poses require server time, valid sector, bounds and a bounded schema',async()=>{
 await create();await join();for(const patch of [{t:0},{sector:4},{sector:.5},{x:-1},{x:96},{y:1},{z:96},{yaw:1e8},{name:''},{name:'x'.repeat(25)},{inventory:'secret'}])await assertFails(set(node('child','live/players/child'),{...pose(),...patch}));
 const incomplete=pose();delete incomplete.y;await assertFails(set(node('child','live/players/child'),incomplete));
 await assertFails(set(node('child','live/surprise'),1));await assertSucceeds(remove(node('child','live/players/child')));
});
test('signal revisions are monotonic, timestamped and cannot be deleted or carry blocks',async()=>{
 await create();await join();await set(node('child','live/signals/child'),signal(5));await assertSucceeds(set(node('child','live/signals/child'),signal(5)));
 for(const value of [signal(4),signal(0),signal(.5),signal(9e12),{...signal(6),t:0},{...signal(6),blocks:{x:1}}])await assertFails(set(node('child','live/signals/child'),value));await assertFails(remove(node('child','live/signals/child')));
});
test('owner bans survive invitation reuse, self restore and owner accidental restoration',async()=>{
 await create();await join();await join('sibling');await assertFails(update(node('sibling','members/child'),{active:false}));await assertSucceeds(update(node('parent','members/child'),{active:false}));
 await assertSucceeds(get(node('child','members/child')));await assertFails(get(node('child','live')));await assertFails(set(node('child','live/players/child'),pose()));await assertFails(join());
 await assertFails(update(node('child','members/child'),{active:true}));await assertFails(update(node('parent','members/child'),{active:true}));await assertFails(remove(node('parent','members/child')));await assertFails(update(node('parent','members/parent'),{active:false}));
});
test('closed namespace is a durable tombstone blocking join, gameplay and reopen',async()=>{
 await create();await join();await assertSucceeds(set(node('parent','meta/closed'),true));await assertFails(join('sibling'));await assertFails(set(node('child','live/players/child'),pose()));await assertFails(get(node('child','live')));
 await assertFails(set(node('parent','meta/closed'),false));await assertSucceeds(get(node('parent')));await assertFails(remove(node('child')));await assertSucceeds(remove(node('parent')));
});
test('multi-location mutation cannot smuggle foreign writes, metadata or membership',async()=>{
 await create();await join();await assertFails(update(node('child'),{'live/players/child':pose(),'members/sibling':member()}));
 await assertFails(update(node('child'),{'live/players/child':pose(),'live/players/parent':pose()}));
 assert.equal((await get(node('child','live/players/child'))).exists(),false);
});
test('revocation is idempotent so a cross-database retry can finish after interruption',async()=>{
 await create();await join();await update(node('parent','members/child'),{active:false});await assertSucceeds(update(node('parent','members/child'),{active:false}));
 await assertFails(update(node('parent','members/child'),{active:true}));
});
test('production REST auth query and conditional initialization match the SDK rules',async()=>{
 const {createMockUserToken}=require('@firebase/util');
 const endpoint=`http://${process.env.FIREBASE_DATABASE_EMULATOR_HOST}/${base}.json?ns=demo-meicraft-default-rtdb`;
 const token=uid=>createMockUserToken({sub:uid,user_id:uid},'demo-meicraft');
 const request=(uid,method,path='',body,headers={})=>fetch(endpoint.replace('.json',path+'.json')+'&auth='+encodeURIComponent(token(uid)),{method,headers:{'Content-Type':'application/json',...headers},body:body===undefined?undefined:JSON.stringify(body)});
 let response=await request('parent','PUT','',root(),{'if-match':'null_etag'});assert.equal(response.status,200,await response.text());
 response=await request('parent','PUT','',root(),{'if-match':'null_etag'});assert.equal(response.status,412);
 response=await request('child','PUT','/members/child',member());assert.equal(response.status,200,await response.text());
 response=await request('child','PUT','/live/players/child',{...pose(),t:{'.sv':'timestamp'}});assert.equal(response.status,200,await response.text());
 response=await request('child','PUT','/live/players/parent',{...pose(),t:{'.sv':'timestamp'}});assert.equal(response.status,401);
});
