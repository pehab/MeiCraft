const {readFileSync}=require('node:fs');
const assert=require('node:assert/strict');
const {createMockUserToken}=require('@firebase/util');
const {before,after,beforeEach,test}=require('node:test');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,collection,getDoc,getDocs,setDoc,updateDoc,deleteDoc,writeBatch,serverTimestamp,query,where,orderBy,limit,increment}=require('firebase/firestore');
let env;
const world='a'.repeat(32),invite='b'.repeat(32),root=`onlineWorlds/${world}`;
const db=uid=>uid?env.authenticatedContext(uid).firestore():env.unauthenticatedContext().firestore();
const memberData=(token=invite)=>({name:'Miko',banned:false,invite:token,sector:0,x:48.5,y:30,z:48.5,yaw:0,updatedAt:serverTimestamp()});
const blockData=(uid='child')=>({x:1,y:3,z:2,type:7,by:uid,updatedAt:serverTimestamp()});
const blockPath=`${root}/sectors/0/blocks/1_3_2`;
async function create(protocol=1){const owner=db('parent'),batch=writeBatch(owner);batch.set(doc(owner,root),{owner:'parent',name:'Familienwelt',seed:123,side:2,protocol,invite,closed:false});if(protocol===4)batch.set(doc(owner,`${root}/sync/state`),{regions:{},players:{}});batch.set(doc(owner,`${root}/members/parent`),memberData());return batch.commit();}
async function join(uid='child',token=invite){const user=db(uid);return setDoc(doc(user,`${root}/members/${uid}`),memberData(token));}
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-meicraft',firestore:{rules:readFileSync('firestore.rules','utf8')}});});
beforeEach(async()=>{await env.clearFirestore();});
after(async()=>{if(env)await env.cleanup();});

test('owner and initial membership are created atomically',async()=>{await assertSucceeds(create());await assertSucceeds(getDoc(doc(db('parent'),root)));});
test('world cannot be created without owner membership',async()=>{const user=db('parent');await assertFails(setDoc(doc(user,root),{owner:'parent',name:'World',seed:1,side:2,protocol:1,invite,closed:false}));});
test('unauthenticated and unrelated users cannot read worlds',async()=>{await create();await assertFails(getDoc(doc(db(),root)));await assertFails(getDoc(doc(db('stranger'),root)));await assertFails(getDocs(collection(db('parent'),'onlineWorlds')));});
test('only correct invitation permits joining',async()=>{await create();await assertFails(join('stranger','c'.repeat(32)));await assertSucceeds(join());await assertSucceeds(getDoc(doc(db('child'),root)));});
test('member can edit blocks and read ordered incremental changes',async()=>{await create();await join();const child=db('child');await assertSucceeds(setDoc(doc(child,blockPath),blockData()));await assertSucceeds(getDocs(query(collection(child,`${root}/sectors/0/blocks`),where('updatedAt','>=',new Date(0)),orderBy('updatedAt'),orderBy('__name__'),limit(200))));});
test('outsider cannot read or write blocks',async()=>{await create();await join();const user=db('stranger');await assertFails(getDoc(doc(user,blockPath)));await assertFails(setDoc(doc(user,blockPath),blockData('stranger')));});
test('member cannot forge author or timestamp',async()=>{await create();await join();const child=db('child');await assertFails(setDoc(doc(child,blockPath),blockData('parent')));await assertFails(setDoc(doc(child,blockPath),{...blockData(),updatedAt:new Date(0)}));});
test('foundation, unknown blocks, mismatched IDs and out-of-bounds sectors are denied',async()=>{await create();await join();const child=db('child');await assertFails(setDoc(doc(child,`${root}/sectors/0/blocks/1_0_2`),{...blockData(),y:0}));await assertFails(setDoc(doc(child,blockPath),{...blockData(),type:17}));await assertFails(setDoc(doc(child,blockPath),{...blockData(),x:2}));await assertFails(setDoc(doc(child,`${root}/sectors/4/blocks/1_3_2`),blockData()));});
test('position and display name can change; membership capabilities cannot',async()=>{await create();await join();const child=db('child'),self=doc(child,`${root}/members/child`);await assertSucceeds(updateDoc(self,{name:'Peter',x:20,updatedAt:serverTimestamp()}));await assertFails(updateDoc(self,{role:'owner',updatedAt:serverTimestamp()}));await assertFails(updateDoc(self,{sector:4,updatedAt:serverTimestamp()}));await assertFails(updateDoc(self,{x:100,updatedAt:serverTimestamp()}));await assertFails(updateDoc(doc(child,`${root}/members/parent`),{name:'Fake',updatedAt:serverTimestamp()}));});
test('only owner can ban another player and the ban survives the old invite',async()=>{await create();await join();await join('sibling');await assertFails(updateDoc(doc(db('sibling'),`${root}/members/child`),{banned:true}));await assertSucceeds(updateDoc(doc(db('parent'),`${root}/members/child`),{banned:true}));const child=db('child');await assertSucceeds(getDoc(doc(child,`${root}/members/child`)));await assertFails(getDoc(doc(child,root)));await assertFails(getDoc(doc(child,blockPath)));await assertFails(setDoc(doc(child,blockPath),blockData()));await assertFails(join());await assertFails(updateDoc(doc(child,`${root}/members/child`),{banned:false}));await assertFails(deleteDoc(doc(db('parent'),`${root}/members/child`)));});
test('owner cannot ban self; members cannot change world owner or invite',async()=>{await create();await join();await assertFails(updateDoc(doc(db('parent'),`${root}/members/parent`),{banned:true}));await assertFails(updateDoc(doc(db('child'),root),{invite:'d'.repeat(32)}));await assertFails(updateDoc(doc(db('parent'),root),{owner:'child'}));});
test('closing world blocks gameplay and joining while owner retains access',async()=>{await create();await join();await assertSucceeds(updateDoc(doc(db('parent'),root),{closed:true}));await assertFails(getDoc(doc(db('child'),root)));await assertFails(setDoc(doc(db('child'),blockPath),blockData()));await assertFails(join('newcomer'));await assertSucceeds(getDoc(doc(db('parent'),root)));});
test('invitation rotation rejects old invite, preserves current members',async()=>{await create();await join();await updateDoc(doc(db('parent'),root),{invite:'d'.repeat(32)});await assertFails(join('newcomer'));await assertSucceeds(join('newcomer','d'.repeat(32)));await assertSucceeds(getDoc(doc(db('child'),root)));});
test('extra data cannot be smuggled into block or membership documents',async()=>{await create();await join();await assertFails(setDoc(doc(db('child'),blockPath),{...blockData(),payload:'unexpected'}));await assertFails(setDoc(doc(db('newcomer'),`${root}/members/newcomer`),{...memberData(),owner:true}));});

// Exercise the same REST shape used by the Android client, including transform timestamps,
// compare-and-set writes and the incremental query cursor. No production requests.
async function rest(uid,path,body){
  const endpoint=`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/demo-meicraft/databases/(default)/documents${path}`;
  const token=createMockUserToken({sub:uid,user_id:uid},'demo-meicraft');
  const response=await fetch(endpoint,{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify(body)});
  return {status:response.status,body:await response.json()};
}
const docName=path=>`projects/demo-meicraft/databases/(default)/documents/${path}`;
function restBlock(type,condition){return {update:{name:docName(blockPath),fields:{x:{integerValue:'1'},y:{integerValue:'3'},z:{integerValue:'2'},type:{integerValue:String(type)},by:{stringValue:'child'}}},currentDocument:condition,updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]};}
test('REST commits preserve concurrent edits through updateTime preconditions',async()=>{
  await create();await join();
  const first=await rest('child',':commit',{writes:[restBlock(7,{exists:false})]});assert.equal(first.status,200,JSON.stringify(first.body));
  const stale=first.body.writeResults[0].updateTime;
  await setDoc(doc(db('child'),blockPath),{...blockData(),type:4});
  const conflict=await rest('child',':commit',{writes:[restBlock(8,{updateTime:stale})]});assert.ok(conflict.status>=400);assert.equal((await getDoc(doc(db('child'),blockPath))).data().type,4);
  const duplicate=await rest('child',':commit',{writes:[restBlock(8,{exists:false})]});assert.ok(duplicate.status>=400);
  const result=await rest('child',`/${root}/sectors/0:runQuery`,{structuredQuery:{from:[{collectionId:'blocks'}],where:{fieldFilter:{field:{fieldPath:'updatedAt'},op:'GREATER_THAN_OR_EQUAL',value:{timestampValue:'1970-01-01T00:00:00Z'}}},orderBy:[{field:{fieldPath:'updatedAt'},direction:'ASCENDING'},{field:{fieldPath:'__name__'},direction:'ASCENDING'}],limit:200}});
  assert.equal(result.status,200,JSON.stringify(result.body));assert.equal(result.body.filter(row=>row.document).length,1);
  const d=result.body.find(row=>row.document).document;
  const end=await rest('child',`/${root}/sectors/0:runQuery`,{structuredQuery:{from:[{collectionId:'blocks'}],orderBy:[{field:{fieldPath:'updatedAt'},direction:'ASCENDING'},{field:{fieldPath:'__name__'},direction:'ASCENDING'}],startAt:{values:[d.fields.updatedAt,{referenceValue:d.name}],before:false},limit:200}});
  assert.equal(end.status,200);assert.equal(end.body.filter(row=>row.document).length,0);
});

const invPath=uid=>`${root}/members/${uid}/inventory/main`;
const emptyInventory=()=>({items:Array(17).fill(0),chapter:0,iron:0,placed:0,glassPlaced:0,buckets:0,sword:false,lastBlock:'',updatedAt:serverTimestamp()});
async function initInventory(uid){await setDoc(doc(db(uid),invPath(uid)),emptyInventory());}
async function readRest(uid,path){const response=await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/${docName(path)}`,{headers:{Authorization:`Bearer ${createMockUserToken({sub:uid,user_id:uid},'demo-meicraft')}`}});assert.equal(response.status,200);return response.json();}
function inventoryWrite(uid,state,lastBlock,version){
  const fields={items:{arrayValue:{values:state.items.map(n=>({integerValue:String(n)}))}}};
  for(const k of ['chapter','iron','placed','glassPlaced','buckets'])fields[k]={integerValue:String(state[k])};
  fields.sword={booleanValue:state.sword};fields.lastBlock={stringValue:lastBlock};
  return {update:{name:docName(invPath(uid)),fields},currentDocument:{updateTime:version},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]};
}
function resourceBlock(uid,type,condition){const write=restBlock(type,condition);write.update.fields.by={stringValue:uid};write.update.fields.renewable={booleanValue:false};return write;}

test('resource inventory starts empty and is private to its player',async()=>{
 await create(2);await join();await join('sibling');await assertSucceeds(initInventory('child'));
 await assertSucceeds(getDoc(doc(db('child'),invPath('child'))));await assertFails(getDoc(doc(db('sibling'),invPath('child'))));await assertFails(updateDoc(doc(db('parent'),invPath('child')),{iron:99,updatedAt:serverTimestamp()}));
 const free=emptyInventory();free.items[2]=50;await assertFails(setDoc(doc(db('sibling'),invPath('sibling')),free));
});
test('invalid inventory sizes, negative counts and unknown fields are rejected',async()=>{
 await create(2);await join();await initInventory('child');const self=doc(db('child'),invPath('child'));
 await assertFails(updateDoc(self,{items:Array(16).fill(0),updatedAt:serverTimestamp()}));
 const negative=Array(17).fill(0);negative[2]=-1;await assertFails(updateDoc(self,{items:negative,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(self,{chapter:8,updatedAt:serverTimestamp()}));await assertFails(updateDoc(self,{admin:true,updatedAt:serverTimestamp()}));
});
test('resource world refuses a block mutation without its atomic inventory update',async()=>{
 await create(2);await join();await initInventory('child');
 const solo=await rest('child',':commit',{writes:[resourceBlock('child',0,{exists:false})]});assert.ok(solo.status>=400);
 const own=await readRest('child',invPath('child')),state=emptyInventory();state.items[2]=1;
 const wrong=await rest('child',':commit',{writes:[resourceBlock('child',0,{exists:false}),inventoryWrite('child',state,'0/9_3_2',own.updateTime)]});assert.ok(wrong.status>=400);assert.equal((await getDoc(doc(db('child'),invPath('child')))).data().items[2],0);
});
test('two simultaneous harvests yield exactly one block across both inventories',async()=>{
 await create(2);await join();await join('sibling');await initInventory('child');await initInventory('sibling');
 const [a,b]=await Promise.all([readRest('child',invPath('child')),readRest('sibling',invPath('sibling'))]);
 const state=emptyInventory();state.items[2]=1;
 const results=await Promise.all([rest('child',':commit',{writes:[resourceBlock('child',0,{exists:false}),inventoryWrite('child',state,'0/1_3_2',a.updateTime)]}),rest('sibling',':commit',{writes:[resourceBlock('sibling',0,{exists:false}),inventoryWrite('sibling',state,'0/1_3_2',b.updateTime)]})]);
 assert.equal(results.filter(r=>r.status===200).length,1,JSON.stringify(results));
 const invA=(await getDoc(doc(db('child'),invPath('child')))).data(),invB=(await getDoc(doc(db('sibling'),invPath('sibling')))).data();assert.equal(invA.items[2]+invB.items[2],1);
 const winner=invA.items[2]?'child':'sibling',current=await readRest(winner,invPath(winner)),block=await readRest(winner,blockPath),placed=emptyInventory();placed.placed=1;
 const writes=[resourceBlock(winner,2,{updateTime:block.updateTime}),inventoryWrite(winner,placed,'0/1_3_2',current.updateTime)];
 const first=await rest(winner,':commit',{writes});assert.equal(first.status,200,JSON.stringify(first.body));const duplicate=await rest(winner,':commit',{writes});assert.ok(duplicate.status>=400);assert.equal((await getDoc(doc(db(winner),invPath(winner)))).data().items[2],0);
});
test('only owner can upgrade an existing world, with stored blocks retained',async()=>{
 await create();await join();await setDoc(doc(db('child'),blockPath),blockData());await assertFails(updateDoc(doc(db('child'),root),{protocol:2}));
 await assertSucceeds(updateDoc(doc(db('parent'),root),{protocol:2}));assert.equal((await getDoc(doc(db('child'),blockPath))).data().type,7);
 await assertFails(updateDoc(doc(db('parent'),root),{protocol:1}));await assertSucceeds(initInventory('child'));
});
test('banned players also lose access to their world inventory',async()=>{
 await create(2);await join();await initInventory('child');await updateDoc(doc(db('parent'),`${root}/members/child`),{banned:true});await assertFails(getDoc(doc(db('child'),invPath('child'))));await assertFails(updateDoc(doc(db('child'),invPath('child')),{iron:10,updatedAt:serverTimestamp()}));
});

// The continuous terrain uses its own protocol so 0.8.0 cannot generate conflicting blocks.
test('protocol 3 creates a new global-terrain world with private atomic inventory',async()=>{
  await create(3);await join();await initInventory('child');
  const user=db('child');const inventory=await readRest('child',invPath('child'));
  const state=emptyInventory();state.items[2]=1;
  const result=await rest('child',':commit',{writes:[resourceBlock('child',0,{exists:false}),inventoryWrite('child',state,'0/1_3_2',inventory.updateTime)]});
  assert.equal(result.status,200,JSON.stringify(result.body));
  assert.equal((await getDoc(doc(user,invPath('child')))).data().items[2],1);
  await assertFails(getDoc(doc(db('parent'),invPath('child'))));
  await assertFails(updateDoc(doc(db('parent'),root),{protocol:2}));
});
test('existing protocol 2 worlds cannot silently change to a different landscape',async()=>{
  await create(2);await assertFails(updateDoc(doc(db('parent'),root),{protocol:3}));
});

test('only the owner can delete and only after closing the world',async()=>{
  await create(3);await join();await initInventory('child');
  await assertFails(deleteDoc(doc(db('parent'),root)));
  await assertFails(deleteDoc(doc(db('parent'),`${root}/members/child`)));
  await assertFails(deleteDoc(doc(db('parent'),invPath('child'))));
  await assertSucceeds(updateDoc(doc(db('parent'),root),{closed:true}));
  for(const uid of ['child','stranger']){
    await assertFails(deleteDoc(doc(db(uid),root)));
    await assertFails(deleteDoc(doc(db(uid),`${root}/members/parent`)));
    await assertFails(deleteDoc(doc(db(uid),invPath('child'))));
    await assertFails(getDocs(collection(db(uid),`${root}/sectors/0/blocks`)));
  }
  await assertFails(getDoc(doc(db('parent'),invPath('child'))));
  await assertSucceeds(deleteDoc(doc(db('parent'),invPath('child'))));
  await assertSucceeds(deleteDoc(doc(db('parent'),`${root}/members/child`)));
});
async function listRest(uid,path){
  const response=await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/${docName(path)}?pageSize=100`,{headers:{Authorization:`Bearer ${createMockUserToken({sub:uid,user_id:uid},'demo-meicraft')}`}});
  assert.equal(response.status,200,await response.clone().text());return response.json();
}
async function eraseCollection(path,members){
  for(;;){const page=await listRest('parent',path),docs=page.documents||[];if(!docs.length)return;
    const writes=[];for(const d of docs){if(members)writes.push({delete:d.name+'/inventory/main'});writes.push({delete:d.name});}
    const result=await rest('parent',':commit',{writes});assert.equal(result.status,200,JSON.stringify(result.body));
  }
}
test('closed world deletion removes every page, member and private inventory and can resume',async()=>{
  await create(3);await join();await initInventory('parent');await initInventory('child');
  await env.withSecurityRulesDisabled(async ctx=>{
    const batch=writeBatch(ctx.firestore());
    for(let i=0;i<111;i++)batch.set(doc(ctx.firestore(),`${root}/sectors/0/blocks/${i%96}_3_${Math.floor(i/96)}`),{...blockData(),x:i%96,z:Math.floor(i/96),renewable:false});
    batch.set(doc(ctx.firestore(),`${root}/sectors/3/blocks/1_3_2`),{...blockData(),renewable:false});await batch.commit();
  });
  await updateDoc(doc(db('parent'),root),{closed:true});
  // Partial attempt: remove one inventory first, like a connection interruption mid-delete.
  await deleteDoc(doc(db('parent'),invPath('child')));
  await eraseCollection(`${root}/sectors/0/blocks`,false);await eraseCollection(`${root}/sectors/3/blocks`,false);
  await eraseCollection(`${root}/members`,true);
  const result=await rest('parent',':commit',{writes:[{delete:docName(root)}]});assert.equal(result.status,200,JSON.stringify(result.body));
  await env.withSecurityRulesDisabled(async ctx=>{
    const store=ctx.firestore();assert.equal((await getDoc(doc(store,root))).exists(),false);
    assert.equal((await getDocs(collection(store,`${root}/members`))).size,0);
    for(const uid of ['parent','child'])assert.equal((await getDoc(doc(store,invPath(uid)))).exists(),false);
    for(const sector of [0,3])assert.equal((await getDocs(collection(store,`${root}/sectors/${sector}/blocks`))).size,0);
  });
});

const syncPath=`${root}/sync/state`;
function revisionWrite(sector='0'){
  return {update:{name:docName(syncPath),fields:{}},updateMask:{fieldPaths:[]},currentDocument:{exists:true},updateTransforms:[
    {fieldPath:`regions.s${sector}.revision`,increment:{integerValue:'1'}},
    {fieldPath:`regions.s${sector}.updatedAt`,setToServerValue:'REQUEST_TIME'}]};
}
function presenceWrite(uid,x=48.5){
  const data={name:{stringValue:'Miko'},sector:{integerValue:'0'},x:{doubleValue:x},y:{doubleValue:30},z:{doubleValue:48.5},yaw:{doubleValue:0}};
  return {update:{name:docName(syncPath),fields:{players:{mapValue:{fields:{[uid]:{mapValue:{fields:data}}}}}}},updateMask:{fieldPaths:[`players.\`${uid}\``]},currentDocument:{exists:true},updateTransforms:[{fieldPath:`players.\`${uid}\`.updatedAt`,setToServerValue:'REQUEST_TIME'}]};
}
test('protocol 4 initializes state atomically and only the owner upgrades protocol 3',async()=>{
  await assertSucceeds(create(4));assert.deepEqual((await getDoc(doc(db('parent'),syncPath))).data(),{regions:{},players:{}});
  await env.clearFirestore();await create(3);await join();
  await assertFails(updateDoc(doc(db('child'),root),{protocol:4}));
  await assertFails(updateDoc(doc(db('parent'),root),{protocol:4}));
  const owner=db('parent'),batch=writeBatch(owner);batch.update(doc(owner,root),{protocol:4});batch.set(doc(owner,syncPath),{regions:{},players:{}});await assertSucceeds(batch.commit());
});
test('presence writes update only the caller and never change region revisions',async()=>{
  await create(4);await join();await join('sibling');
  for(const uid of ['child','sibling']){const result=await rest(uid,':commit',{writes:[presenceWrite(uid)]});assert.equal(result.status,200,JSON.stringify(result.body));}
  let state=(await getDoc(doc(db('child'),syncPath))).data();assert.deepEqual(state.regions,{});assert.equal(Object.keys(state.players).length,2);
  const attack=await rest('child',':commit',{writes:[presenceWrite('sibling',60)]});assert.ok(attack.status>=400);
  await assertFails(updateDoc(doc(db('child'),syncPath),{'regions.s0':{revision:1,updatedAt:serverTimestamp()}}));
  await assertFails(getDoc(doc(db('stranger'),syncPath)));
  state=(await getDoc(doc(db('child'),syncPath))).data();assert.equal(state.players.sibling.x,48.5);
});
test('protocol 4 refuses edits without revision signals and commits block, inventory and signal together',async()=>{
  await create(4);await join();await initInventory('child');const inventory=await readRest('child',invPath('child')),state=emptyInventory();state.items[2]=1;
  const writes=[resourceBlock('child',0,{exists:false}),inventoryWrite('child',state,'0/1_3_2',inventory.updateTime)];
  const missing=await rest('child',':commit',{writes});assert.ok(missing.status>=400);
  const signalOnly=await rest('child',':commit',{writes:[revisionWrite()]});assert.ok(signalOnly.status>=400);
  const result=await rest('child',':commit',{writes:[...writes,revisionWrite()]});assert.equal(result.status,200,JSON.stringify(result.body));
  const snapshot=(await getDoc(doc(db('child'),syncPath))).data();assert.equal(snapshot.regions.s0.revision,1);
  const before=(await getDoc(doc(db('child'),syncPath))).data().regions.s0.revision;
  await rest('child',':commit',{writes:[presenceWrite('child',55)]});assert.equal((await getDoc(doc(db('child'),syncPath))).data().regions.s0.revision,before);
});
test('concurrent edits preserve both player positions and revision increments',async()=>{
  await create(4);await join();await join('sibling');for(const uid of ['child','sibling'])await initInventory(uid);
  const versions=await Promise.all(['child','sibling'].map(uid=>readRest(uid,invPath(uid))));
  const responses=await Promise.all(['child','sibling'].map((uid,i)=>{
    const state=emptyInventory();state.items[2]=1;const block=resourceBlock(uid,0,{exists:false});block.update.name=docName(`${root}/sectors/0/blocks/${i+1}_3_2`);block.update.fields.x={integerValue:String(i+1)};
    return rest(uid,':commit',{writes:[block,inventoryWrite(uid,state,`0/${i+1}_3_2`,versions[i].updateTime),revisionWrite()]});
  }));for(const result of responses)assert.equal(result.status,200,JSON.stringify(result.body));
  await Promise.all(['child','sibling'].map(uid=>rest(uid,':commit',{writes:[presenceWrite(uid)]})));
  const state=(await getDoc(doc(db('child'),syncPath))).data();assert.equal(state.regions.s0.revision,2);assert.equal(Object.keys(state.players).length,2);
});
test('banned members lose access to shared sync state and closed worlds can delete it',async()=>{
  await create(4);await join();await updateDoc(doc(db('parent'),`${root}/members/child`),{banned:true});
  await assertFails(getDoc(doc(db('child'),syncPath)));const result=await rest('child',':commit',{writes:[presenceWrite('child')]});assert.ok(result.status>=400);
  await assertFails(deleteDoc(doc(db('parent'),syncPath)));await updateDoc(doc(db('parent'),root),{closed:true});await assertSucceeds(deleteDoc(doc(db('parent'),syncPath)));
});

test('only the owner can remove stale or banned player positions without changing terrain',async()=>{
  await create(4);await join();for(const uid of ['parent','child']){const result=await rest(uid,':commit',{writes:[presenceWrite(uid)]});assert.equal(result.status,200,JSON.stringify(result.body));}
  const remove={update:{name:docName(syncPath),fields:{}},updateMask:{fieldPaths:['players.child']},currentDocument:{exists:true}};
  const rejected=await rest('child',':commit',{writes:[{...remove,updateMask:{fieldPaths:['players.parent']}}]});assert.ok(rejected.status>=400);
  const owner=db('parent');await updateDoc(doc(owner,`${root}/members/child`),{banned:true});const result=await rest('parent',':commit',{writes:[remove]});assert.equal(result.status,200,JSON.stringify(result.body));
  const state=(await getDoc(doc(owner,syncPath))).data();assert.equal(state.players.child,undefined);assert.ok(state.players.parent);assert.deepEqual(state.regions,{});
});

test('paginated REST block queries use one snapshot while another player edits',async()=>{
  await create();await join();
  const store=db('child'),batch=writeBatch(store);
  for(let i=0;i<205;i++)batch.set(doc(store,`${root}/sectors/0/blocks/${i%90+1}_3_${Math.floor(i/90)+1}`),{...blockData(),x:i%90+1,z:Math.floor(i/90)+1});
  await batch.commit();
  const parent=`/${root}/sectors/0:runQuery`,order=fieldPath=>({field:{fieldPath},direction:'ASCENDING'});
  const structuredQuery={from:[{collectionId:'blocks'}],orderBy:[order('updatedAt'),order('__name__')],limit:200};
  const first=await rest('child',parent,{structuredQuery});assert.equal(first.status,200,JSON.stringify(first.body));
  const docs=first.body.filter(row=>row.document);assert.equal(docs.length,200);
  const snapshot=first.body.find(row=>row.readTime).readTime,last=docs.at(-1).document;
  const target=docs[0].document.name.split('/documents/')[1];
  await updateDoc(doc(store,target),{type:8,updatedAt:serverTimestamp()});
  const next=await rest('child',parent,{structuredQuery:{...structuredQuery,startAt:{values:[last.fields.updatedAt,{referenceValue:last.name}],before:false}},readTime:snapshot});
  assert.equal(next.status,200,JSON.stringify(next.body));
  assert.equal(next.body.filter(row=>row.document).length,5,'updated first-page block must not move onto the next snapshot page');
});

function combinedRevisionPresence(uid,sector='0',x=48.5){
  const position=presenceWrite(uid,x),signal=revisionWrite(sector);
  return {...position,updateTransforms:[...position.updateTransforms,...signal.updateTransforms]};
}
async function harvestWithSignal(uid,x,signal){
  const inventory=await readRest(uid,invPath(uid)),state=emptyInventory();state.items[2]=1;
  const block=resourceBlock(uid,0,{exists:false});block.update.name=docName(`${root}/sectors/0/blocks/${x}_3_2`);block.update.fields.x={integerValue:String(x)};
  return rest(uid,':commit',{writes:[block,inventoryWrite(uid,state,`0/${x}_3_2`,inventory.updateTime),signal]});
}
test('one shared write combines an edit signal and own presence; peer positions survive',async()=>{
  await create(4);await join();await join('sibling');for(const uid of ['child','sibling'])await initInventory(uid);
  const before=await rest('sibling',':commit',{writes:[presenceWrite('sibling',60)]});assert.equal(before.status,200);
  const result=await harvestWithSignal('child',1,combinedRevisionPresence('child','0',55));assert.equal(result.status,200,JSON.stringify(result.body));
  assert.equal(result.body.writeResults.length,3,'block, inventory and combined signal/position remain exactly three document writes');
  const state=(await getDoc(doc(db('child'),syncPath))).data();assert.equal(state.regions.s0.revision,1);assert.equal(state.players.child.x,55);assert.equal(state.players.sibling.x,60);
});
test('combined edit signal refuses foreign, invalid and missing-timestamp positions atomically',async()=>{
  await create(4);await join();for(const uid of ['child'])await initInventory(uid);
  for(const signal of [combinedRevisionPresence('parent'),combinedRevisionPresence('child','0',150),(()=>{const w=combinedRevisionPresence('child');w.updateTransforms=w.updateTransforms.filter(t=>!t.fieldPath.startsWith('players.'));return w;})()]){
    const result=await harvestWithSignal('child',1,signal);assert.ok(result.status>=400,JSON.stringify(result.body));
    assert.equal((await getDoc(doc(db('child'),blockPath))).exists(),false);
    assert.equal((await getDoc(doc(db('child'),invPath('child')))).data().items[2],0);
  }
});
test('concurrent combined position and block writes keep both players and all revisions',async()=>{
  await create(4);await join();await join('sibling');for(const uid of ['child','sibling'])await initInventory(uid);
  const results=await Promise.all(['child','sibling'].map((uid,i)=>harvestWithSignal(uid,i+1,combinedRevisionPresence(uid,'0',40+i))));
  for(const result of results)assert.equal(result.status,200,JSON.stringify(result.body));
  const state=(await getDoc(doc(db('child'),syncPath))).data();assert.equal(state.regions.s0.revision,2);assert.equal(state.players.child.x,40);assert.equal(state.players.sibling.x,41);
});

async function syncSnapshot(uid){
  const result=await rest(uid,':batchGet',{documents:[docName(syncPath)]});
  assert.equal(result.status,200,JSON.stringify(result.body));
  const row=result.body.find(row=>row.found);
  assert.ok(row?.readTime,'overview must include its server read snapshot');return row;
}

test('snapshot overview remains one private document read and banned users cannot batchGet it',async()=>{
  await create(4);await join();
  const snapshot=await syncSnapshot('child');assert.equal(snapshot.found.name,docName(syncPath));
  await updateDoc(doc(db('parent'),`${root}/members/child`),{banned:true});
  const banned=await rest('child',':batchGet',{documents:[docName(syncPath)]});assert.ok(banned.status>=400);
  const outsider=await rest('outsider',':batchGet',{documents:[docName(syncPath)]});assert.ok(outsider.status>=400);
});

test('completed overview snapshot eliminates timestamp-boundary rereads without losing later edits',async()=>{
  // Protocol 1 allows a controlled 205-document same-timestamp fixture. Query and
  // batchGet shapes are identical to protocol 4; protocol-4 rights are tested above.
  await create();await join();const store=db('child'),batch=writeBatch(store);
  for(let i=0;i<205;i++)batch.set(doc(store,`${root}/sectors/0/blocks/${i%90+1}_3_${Math.floor(i/90)+1}`),{...blockData(),x:i%90+1,z:Math.floor(i/90)+1});
  await batch.commit();
  // Protocol 1 does not have a sync overview. Use an authorized batchGet of the
  // world document to obtain exactly the same server readTime contract.
  const overview=await rest('child',':batchGet',{documents:[docName(root)]});assert.equal(overview.status,200,JSON.stringify(overview.body));
  const snapshot=overview.body.find(row=>row.found).readTime;
  const parent=`/${root}/sectors/0:runQuery`,order=fieldPath=>({field:{fieldPath},direction:'ASCENDING'});
  const structuredQuery={from:[{collectionId:'blocks'}],select:{fields:['x','y','z','type','renewable','updatedAt'].map(fieldPath=>({fieldPath}))},orderBy:[order('updatedAt'),order('__name__')],limit:200};
  const first=await rest('child',parent,{structuredQuery,readTime:snapshot});assert.equal(first.status,200,JSON.stringify(first.body));
  const docs=first.body.filter(row=>row.document);assert.equal(docs.length,200);
  assert.equal(docs[0].document.fields.by,undefined,'unused author is not transferred');
  assert.ok(docs[0].document.updateTime,'projection preserves CAS metadata');
  const target=docs[0].document.name.split('/documents/')[1];
  await updateDoc(doc(store,target),{type:8,updatedAt:serverTimestamp()});
  const last=docs.at(-1).document;
  const next=await rest('child',parent,{structuredQuery:{...structuredQuery,startAt:{values:[last.fields.updatedAt,{referenceValue:last.name}],before:false}},readTime:snapshot});
  assert.equal(next.status,200,JSON.stringify(next.body));assert.equal(next.body.filter(row=>row.document).length,5);
  const filtered=op=>({...structuredQuery,where:{fieldFilter:{field:{fieldPath:'updatedAt'},op,value:{timestampValue:op==='GREATER_THAN'?snapshot:last.fields.updatedAt.timestampValue}}}});
  const boundary=await rest('child',parent,{structuredQuery:filtered('GREATER_THAN_OR_EQUAL')});assert.equal(boundary.status,200,JSON.stringify(boundary.body));
  assert.equal(boundary.body.filter(row=>row.document).length,200,'old watermark downloads its timestamp boundary again (first page)');
  const delta=await rest('child',parent,{structuredQuery:filtered('GREATER_THAN')});assert.equal(delta.status,200,JSON.stringify(delta.body));
  const changed=delta.body.filter(row=>row.document);assert.equal(changed.length,1,'only the later edit crosses the completed snapshot');
  assert.equal(changed[0].document.name,docName(target));assert.equal(changed[0].document.fields.type.integerValue,'8');
});

test('masked inventory commits preserve tools and counters, retain CAS and atomic block revisions',async()=>{
  await create(4);await join();await initInventory('child');
  const store=db('child');
  await updateDoc(doc(store,invPath('child')),{chapter:7,iron:19,buckets:3,sword:true,placed:42,glassPlaced:8,updatedAt:serverTimestamp()});
  const before=await readRest('child',invPath('child')),state=emptyInventory();state.items[2]=1;
  const partial={update:{name:docName(invPath('child')),fields:{items:{arrayValue:{values:state.items.map(n=>({integerValue:String(n)}))}},lastBlock:{stringValue:'0/1_3_2'}}},updateMask:{fieldPaths:['items','lastBlock']},currentDocument:{updateTime:before.updateTime},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]};
  const result=await rest('child',':commit',{writes:[resourceBlock('child',0,{exists:false}),partial,revisionWrite()]});assert.equal(result.status,200,JSON.stringify(result.body));
  const inventory=(await getDoc(doc(store,invPath('child')))).data();
  for(const [key,value] of Object.entries({chapter:7,iron:19,buckets:3,sword:true,placed:42,glassPlaced:8}))assert.equal(inventory[key],value,`unmodified ${key} survives masked write`);
  assert.equal(inventory.items[2],1);assert.equal((await getDoc(doc(store,syncPath))).data().regions.s0.revision,1);
  const stale=await rest('child',':commit',{writes:[partial]});assert.ok(stale.status>=400);assert.equal((await getDoc(doc(store,invPath('child')))).data().items[2],1);
  const current=await readRest('child',invPath('child'));
  const invalid={...partial,update:{name:docName(invPath('child')),fields:{iron:{integerValue:'-1'}}},updateMask:{fieldPaths:['iron']},currentDocument:{updateTime:current.updateTime}};
  const rejected=await rest('child',':commit',{writes:[invalid]});assert.ok(rejected.status>=400);assert.equal((await getDoc(doc(store,invPath('child')))).data().iron,19);
});

test('own-only snapshot cuts construction history from the next peer delta without losing that peer edit',async()=>{
  await create();await join();await join('sibling');const store=db('child');
  const initial=await rest('child',':batchGet',{documents:[docName(root)]});assert.equal(initial.status,200);
  const initialTime=initial.body.find(row=>row.found).readTime;
  const batch=writeBatch(store);for(let i=0;i<180;i++)batch.set(doc(store,`${root}/sectors/0/blocks/${i%90+1}_3_${Math.floor(i/90)+1}`),{...blockData(),x:i%90+1,z:Math.floor(i/90)+1});await batch.commit();
  // The client has the exact revisions and contents of all 180 confirmed own edits.
  const ownOnly=await rest('child',':batchGet',{documents:[docName(root)]});assert.equal(ownOnly.status,200);const completed=ownOnly.body.find(row=>row.found).readTime;
  await setDoc(doc(db('sibling'),`${root}/sectors/0/blocks/91_3_3`),{...blockData('sibling'),x:91,z:3});
  const delta=async timestamp=>rest('child',`/${root}/sectors/0:runQuery`,{structuredQuery:{from:[{collectionId:'blocks'}],where:{fieldFilter:{field:{fieldPath:'updatedAt'},op:'GREATER_THAN',value:{timestampValue:timestamp}}},orderBy:[{field:{fieldPath:'updatedAt'},direction:'ASCENDING'},{field:{fieldPath:'__name__'},direction:'ASCENDING'}],limit:200}});
  const [old,next]=await Promise.all([delta(initialTime),delta(completed)]);assert.equal(old.status,200);assert.equal(next.status,200);
  assert.equal(old.body.filter(row=>row.document).length,181);assert.equal(next.body.filter(row=>row.document).length,1);
  assert.equal(next.body.find(row=>row.document).document.fields.by.stringValue,'sibling');
});

// Protocol 5: resource-local read authorization, per-player inventory and causal tail,
// per-cell nonces in shared spatial chunks. No global document-version precondition.
const v5Path=`${root}/sync/v5`;
const inv5=()=> '1.'+'A'.repeat(16)+'.'+'B'.repeat(146);
const participant5=()=>({inventory:inv5(),rev:0,seq:0,log:{},last:{},position:{}});
function state5(ready=true){return {schema:5,owner:'parent',seed:123,side:2,name:'Familienwelt',invite,open:ready,phase:ready?'ready':'importing',access:{parent:true},players:{parent:participant5()},regions:{},migration:{sector:0,cursor:'',count:0,hash:'0'.repeat(64)}};}
async function create5(){const user=db('parent'),batch=writeBatch(user);batch.set(doc(user,root),{owner:'parent',name:'Familienwelt',seed:123,side:2,protocol:5,invite,closed:false});batch.set(doc(user,`${root}/members/parent`),memberData());batch.set(doc(user,v5Path),state5());await batch.commit();}
async function join5(uid='child'){await join(uid);const user=db(uid);await getDoc(doc(user,v5Path));return updateDoc(doc(user,v5Path),{[`access.${uid}`]:true,[`players.${uid}`]:participant5()});}
function change5(uid,{seq=1,rev=1,x=1,y=3,z=2,type=7,renewable=false,before='',nonce='c'.repeat(32),sector=0,inventory=inv5()}={}){
 const user=db(uid),batch=writeBatch(user),key=`${x}_${y}_${z}`,chunk=`${Math.floor(x/16)}_${Math.floor(y/16)}_${Math.floor(z/16)}`;
 const last={sector,x,y,z,type,renewable,token:nonce,before};
 batch.set(doc(user,`${root}/sectors/${sector}/chunks/${chunk}`),{cells:{[key]:`${type}:${renewable?1:0}:${nonce}`},updatedAt:serverTimestamp()},{mergeFields:[`cells.${key}`,'updatedAt']});
 batch.update(doc(user,v5Path),{[`players.${uid}.inventory`]:inventory,[`players.${uid}.rev`]:rev,[`players.${uid}.seq`]:seq,[`players.${uid}.last`]:last,
  [`players.${uid}.log.e${seq%16}`]:`${seq}/${sector}/${key}/${type}/${renewable?1:0}/${nonce}/${before}`,
  [`regions.s${sector}.revision`]:increment(1),[`regions.s${sector}.updatedAt`]:serverTimestamp()});return batch;
}
test('v5 creates and joins without resetting existing players',async()=>{await assertSucceeds(create5());await assertSucceeds(join5());const result=(await getDoc(doc(db('parent'),v5Path))).data();assert.deepEqual(result.access,{parent:true,child:true});assert.deepEqual(result.players.parent,participant5());});
test('v5 normal overview and presence need no root or membership dependency',async()=>{await create5();await join5();await env.withSecurityRulesDisabled(async context=>{const admin=context.firestore();await deleteDoc(doc(admin,root));await deleteDoc(doc(admin,`${root}/members/parent`));await deleteDoc(doc(admin,`${root}/members/child`));});await assertSucceeds(getDoc(doc(db('child'),v5Path)));await assertSucceeds(updateDoc(doc(db('child'),v5Path),{'players.child.position':{name:'Child',sector:0,x:12,y:30,z:12,yaw:0,updatedAt:serverTimestamp()}}));await assertFails(getDoc(doc(db('stranger'),v5Path)));});
test('v5 block, inventory, region revision and tail are one two-document commit',async()=>{await create5();await join5();await assertSucceeds(change5('child').commit());const data=(await getDoc(doc(db('parent'),v5Path))).data();assert.equal(data.regions.s0.revision,1);assert.equal(data.players.child.seq,1);assert.equal(data.players.child.rev,1);assert.equal(data.players.child.log.e1,`1/0/1_3_2/7/0/${'c'.repeat(32)}/`);await assertSucceeds(getDocs(query(collection(db('parent'),`${root}/sectors/0/chunks`),where('updatedAt','>',new Date(0)),orderBy('updatedAt'),orderBy('__name__'),limit(4))));});
test('v5 two and three simultaneous builders in one chunk preserve all cells and inventories',async()=>{await create5();await join5();await join5('sibling');await Promise.all(['parent','child','sibling'].map((uid,i)=>assertSucceeds(change5(uid,{x:i+1,nonce:String(i+1).repeat(32)}).commit())));const chunk=(await getDoc(doc(db('parent'),`${root}/sectors/0/chunks/0_0_0`))).data();assert.equal(Object.keys(chunk.cells).length,3);const state=(await getDoc(doc(db('parent'),v5Path))).data();assert.equal(state.regions.s0.revision,3);for(const uid of ['parent','child','sibling'])assert.equal(state.players[uid].rev,1);});
test('v5 same-cell race has one winner; loser inventory and tail remain unchanged',async()=>{await create5();await join5();const result=await Promise.allSettled([change5('parent',{nonce:'a'.repeat(32)}).commit(),change5('child',{nonce:'b'.repeat(32)}).commit()]);assert.equal(result.filter(v=>v.status==='fulfilled').length,1);const state=(await getDoc(doc(db('parent'),v5Path))).data();assert.equal(state.regions.s0.revision,1);assert.equal(state.players.parent.rev+state.players.child.rev,1);assert.equal(state.players.parent.seq+state.players.child.seq,1);});
test('v5 rejects block-only and overview-only fake commits',async()=>{await create5();await join5();const child=db('child');await assertFails(setDoc(doc(child,`${root}/sectors/0/chunks/0_0_0`),{cells:{'1_3_2':`7:0:${'c'.repeat(32)}`},updatedAt:serverTimestamp()}));const batch=change5('child');batch._mutations.shift();await assertFails(batch.commit());});
test('v5 cannot alter another inventory, capabilities, world metadata or nonce preconditions',async()=>{await create5();await join5();const child=db('child');for(const patch of [{'players.parent.inventory':{...inv5(),chapter:1}},{'access.parent':false},{owner:'child'},{seed:9},{open:false},{'players.child.rev':1,'players.child.seq':1}])await assertFails(updateDoc(doc(child,v5Path),patch));await assertSucceeds(change5('child').commit());await assertFails(change5('child',{seq:2,rev:2,before:'',nonce:'d'.repeat(32)}).commit());await assertSucceeds(change5('child',{seq:2,rev:2,before:'c'.repeat(32),nonce:'d'.repeat(32),type:0}).commit());});
test('v5 preserves per-cell concurrency after many edits; bounded tail never grows',async()=>{await create5();await join5();for(let seq=1;seq<=20;seq++)await assertSucceeds(change5('child',{seq,rev:seq,x:seq,nonce:seq.toString(16).padStart(32,'0')}).commit());const state=(await getDoc(doc(db('parent'),v5Path))).data();assert.equal(Object.keys(state.players.child.log).length,16);assert.equal(state.players.child.seq,20);assert.equal(state.regions.s0.revision,20);});
test('v5 rejects invalid coordinates, chunk escapes, types and unbounded log/position payloads',async()=>{await create5();await join5();for(const patch of [{y:0},{x:96},{sector:4},{type:17}])await assertFails(change5('child',patch).commit());await assertFails(updateDoc(doc(db('child'),v5Path),{'players.child.log.extra':'fake'}));await assertFails(updateDoc(doc(db('child'),v5Path),{'players.child.position':{name:'X',sector:0,x:0,y:30,z:0,yaw:0,updatedAt:serverTimestamp(),payload:'bad'}}));});
test('v5 ordinary inventory updates use only own revision without global CAS',async()=>{await create5();await join5();const inventory=inv5().replace(/B/g,'C');await assertSucceeds(updateDoc(doc(db('child'),v5Path),{'players.child.inventory':inventory,'players.child.rev':1}));await assertFails(updateDoc(doc(db('child'),v5Path),{'players.child.inventory':inv5(),'players.child.rev':1}));await assertSucceeds(change5('parent').commit());await assertSucceeds(updateDoc(doc(db('child'),v5Path),{'players.child.inventory':inv5(),'players.child.rev':2}));});
test('v5 ban immediately revokes overview, chunk reads and writes and cannot be self-rejoined',async()=>{await create5();await join5();await change5('child').commit();const parent=db('parent'),batch=writeBatch(parent);batch.update(doc(parent,v5Path),{'access.child':false});batch.update(doc(parent,`${root}/members/child`),{banned:true});await assertSucceeds(batch.commit());await assertFails(getDoc(doc(db('child'),v5Path)));await assertFails(getDoc(doc(db('child'),`${root}/sectors/0/chunks/0_0_0`)));await assertFails(change5('child',{seq:2,rev:2,x:2}).commit());await assertFails(join5());await assertFails(updateDoc(doc(parent,v5Path),{'access.parent':false}));});
test('v5 closing root and overview is atomic; orphan reopening is rejected',async()=>{await create5();await join5();await assertFails(updateDoc(doc(db('parent'),root),{closed:true}));await assertFails(updateDoc(doc(db('parent'),v5Path),{open:false}));const user=db('parent'),batch=writeBatch(user);batch.update(doc(user,root),{closed:true});batch.update(doc(user,v5Path),{open:false});await assertSucceeds(batch.commit());await assertFails(getDoc(doc(db('child'),v5Path)));await assertFails(updateDoc(doc(user,v5Path),{open:true}));});
test('v4 migration locks legacy mutations, imports as owner, and activates atomically',async()=>{
 await create(4);await join();await initInventory('child');const parent=db('parent');
 await assertFails(updateDoc(doc(db('child'),root),{closed:true,migration:'v5'}));await assertSucceeds(updateDoc(doc(parent,root),{closed:true,migration:'v5'}));
 await assertFails(change5('child').commit());await assertFails(getDoc(doc(parent,`${root}/members/child/inventory/main`)));
 const state=state5(false);state.access.child=true;state.players.child=participant5();const begin=writeBatch(parent);begin.set(doc(parent,v5Path),state);begin.update(doc(parent,root),{protocol:5});await assertSucceeds(begin.commit());
 const chunk=doc(parent,`${root}/sectors/0/chunks/0_0_0`),page=writeBatch(parent);page.set(chunk,{cells:{'1_3_2':`7:0:${'d'.repeat(32)}`},updatedAt:serverTimestamp()},{mergeFields:['cells.1_3_2','updatedAt']});page.update(doc(parent,v5Path),{migration:{sector:1,cursor:'',count:1,hash:'a'.repeat(64)}});await assertSucceeds(page.commit());
 await assertFails(updateDoc(doc(parent,root),{closed:false}));await assertFails(updateDoc(doc(parent,v5Path),{open:true,phase:'ready'}));
 const activate=writeBatch(parent);activate.update(doc(parent,root),{closed:false,migration:require('firebase/firestore').deleteField()});activate.update(doc(parent,v5Path),{open:true,phase:'ready'});await assertSucceeds(activate.commit());
 await assertSucceeds(getDoc(doc(db('child'),chunk.path)));await assertFails(setDoc(doc(db('child'),blockPath),blockData()));await assertFails(setDoc(chunk,{cells:{},updatedAt:serverTimestamp()}));
});
function typed5(value){if(typeof value==='boolean')return {booleanValue:value};if(typeof value==='number')return {integerValue:String(value)};if(typeof value==='string')return {stringValue:value};if(Array.isArray(value))return {arrayValue:{values:value.map(typed5)}};return {mapValue:{fields:Object.fromEntries(Object.entries(value).map(([k,v])=>[k,typed5(v)]))}};}
test('v5 Android REST field masks and transforms commit two documents and batchGet projects private inventories out',async()=>{
 await create5();await join5();const nonce='e'.repeat(32),last={sector:0,x:1,y:3,z:2,type:7,renewable:false,token:nonce,before:''};
 const entry={inventory:inv5(),rev:1,seq:1,last,log:{e1:`1/0/1_3_2/7/0/${nonce}/`}};
 const result=await rest('child',':commit',{writes:[
  {update:{name:docName(`${root}/sectors/0/chunks/0_0_0`),fields:typed5({cells:{'1_3_2':`7:0:${nonce}`}}).mapValue.fields},updateMask:{fieldPaths:['cells.`1_3_2`']},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]},
  {update:{name:docName(v5Path),fields:typed5({players:{child:entry}}).mapValue.fields},updateMask:{fieldPaths:['players.`child`.inventory','players.`child`.rev','players.`child`.seq','players.`child`.last','players.`child`.log.e1']},currentDocument:{exists:true},updateTransforms:[{fieldPath:'regions.s0.revision',increment:{integerValue:'1'}},{fieldPath:'regions.s0.updatedAt',setToServerValue:'REQUEST_TIME'}]}
 ]});assert.equal(result.status,200,JSON.stringify(result.body));assert.equal(result.body.writeResults.length,2);
 const projection=await rest('parent',':batchGet',{documents:[docName(v5Path)],mask:{fieldPaths:['phase','open','access','regions','players.`child`.seq','players.`child`.log','players.`parent`.inventory','players.`parent`.rev']}});
 assert.equal(projection.status,200,JSON.stringify(projection.body));const fields=projection.body.find(row=>row.found).found.fields;assert.ok(fields.players.mapValue.fields.child.mapValue.fields.log);assert.equal(fields.players.mapValue.fields.child.mapValue.fields.inventory,undefined);
});
test('v5 migration page can patch eighteen chunks with one checkpoint inside rule access limits',async()=>{
 await create(4);const parent=db('parent');await updateDoc(doc(parent,root),{closed:true,migration:'v5'});let batch=writeBatch(parent);batch.set(doc(parent,v5Path),state5(false));batch.update(doc(parent,root),{protocol:5});await batch.commit();
 batch=writeBatch(parent);for(let x=0;x<6;x++)for(let z=0;z<3;z++)batch.set(doc(parent,`${root}/sectors/0/chunks/${x}_1_${z}`),{cells:{[`${x*16}_30_${z*16}`]:`7:0:${'a'.repeat(32)}`},updatedAt:serverTimestamp()},{merge:true});batch.update(doc(parent,v5Path),{migration:{sector:0,cursor:'fixture',count:18,hash:'a'.repeat(64)}});await assertSucceeds(batch.commit());
});

test('v5 central ban protects retained legacy data and legacy ban cannot bypass the central ACL',async()=>{
 await create5();await join5();await assertFails(updateDoc(doc(db('parent'),`${root}/members/child`),{banned:true}));
 await assertSucceeds(updateDoc(doc(db('parent'),v5Path),{'access.child':false}));
 await assertFails(getDoc(doc(db('child'),root)));await assertFails(getDocs(collection(db('child'),`${root}/sectors/0/blocks`)));await assertFails(getDoc(doc(db('child'),invPath('child'))));
 await assertFails(updateDoc(doc(db('child'),v5Path),{'access.child':true}));
});
test('closed legacy world can list an absent v5 chunk collection for owner deletion recovery',async()=>{await create(4);await updateDoc(doc(db('parent'),root),{closed:true});await deleteDoc(doc(db('parent'),`${root}/members/parent`));await assertSucceeds(getDoc(doc(db('parent'),root)));await assertSucceeds(getDocs(collection(db('parent'),`${root}/sectors/0/chunks`)));});
test('v5 bounded overview fits 256 identities and sixteen compact entries without exposing plain inventories',async()=>{
 await create5();const full=state5();full.players={};full.access={};
 for(let i=0;i<256;i++){const uid=i===0?'parent':`u${i}`.padEnd(128,'x'),p=participant5();p.rev=900000000000;p.seq=900000000000;p.position={name:'中'.repeat(24),sector:8,x:95,y:47,z:95,yaw:99999,updatedAt:new Date()};p.last={sector:8,x:95,y:47,z:95,type:16,renewable:false,token:'a'.repeat(32),before:'b'.repeat(32)};for(let slot=0;slot<16;slot++)p.log[`e${slot}`]=`${900000000000+slot}/8/95_47_95/16/0/${'a'.repeat(32)}/${'b'.repeat(32)}`;full.players[uid]=p;full.access[uid]=true;}
 await env.withSecurityRulesDisabled(async context=>{await setDoc(doc(context.firestore(),v5Path),full);});
 const response=await rest('parent',':batchGet',{documents:[docName(v5Path)]});assert.equal(response.status,200,JSON.stringify(response.body));assert.ok(Buffer.byteLength(JSON.stringify(response.body))<4*1024*1024);
 assert.equal(typeof response.body.find(row=>row.found).found.fields.players.mapValue.fields.parent.mapValue.fields.inventory.stringValue,'string');
});
test('v5 migration can read its not-yet-created target and the frozen legacy overview only as owner',async()=>{
 await create(4);await join();
 await updateDoc(doc(db('parent'),root),{closed:true,migration:'v5'});
 await assertSucceeds(getDoc(doc(db('parent'),`${root}/sync/state`)));assert.equal((await assertSucceeds(getDoc(doc(db('parent'),v5Path)))).exists(),false);
 await assertFails(getDoc(doc(db('child'),`${root}/sync/state`)));await assertFails(getDoc(doc(db('child'),v5Path)));
});
test('v5 exact REST migration sequence preserves private inventories and can resume after target deletion',async()=>{
 await create(4);await join();await initInventory('parent');await initInventory('child');
 const mined=await harvestWithSignal('child',1,revisionWrite('0'));assert.equal(mined.status,200);
 const childPrivate=await readRest('child',invPath('child'));
 await updateDoc(doc(db('parent'),root),{closed:true,migration:'v5'});
 const missing=await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/${docName(v5Path)}`,{headers:{Authorization:`Bearer ${createMockUserToken({sub:'parent',user_id:'parent'},'demo-meicraft')}`}});assert.equal(missing.status,404);
 const legacy=await readRest('parent',`${root}/sync/state`);await listRest('parent',`${root}/members`);
 const state=state5(false);state.players.parent.inventory='';state.players.child={...participant5(),inventory:''};state.access.child=true;state.regions={};
 const stateFields=typed5(state).mapValue.fields;stateFields.regions=legacy.fields.regions;
 const begin=await rest('parent',':commit',{writes:[{update:{name:docName(v5Path),fields:stateFields},currentDocument:{exists:false}},{update:{name:docName(root),fields:{protocol:{integerValue:'5'}}},updateMask:{fieldPaths:['protocol']},currentDocument:{exists:true}}]});assert.equal(begin.status,200,JSON.stringify(begin.body));
 const source=await rest('parent',`/${root}/sectors/0:runQuery`,{structuredQuery:{from:[{collectionId:'blocks'}],orderBy:[{field:{fieldPath:'__name__'},direction:'ASCENDING'}],limit:200}});assert.equal(source.status,200);assert.ok(legacy.fields.regions);
 const original=source.body.find(row=>row.document).document;const crypto=require('node:crypto');const nonce=crypto.createHash('sha256').update(original.name+'|'+original.updateTime).digest('hex').slice(0,32);const digest=crypto.createHash('sha256').update(`0/1_3_2/0:0:${nonce}`).digest('hex');
 const page=await rest('parent',':commit',{writes:[{update:{name:docName(`${root}/sectors/0/chunks/0_0_0`),fields:typed5({cells:{'1_3_2':`0:0:${nonce}`}}).mapValue.fields},updateMask:{fieldPaths:['cells.`1_3_2`']},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]},{update:{name:docName(v5Path),fields:typed5({migration:{sector:4,cursor:'',count:1,hash:digest}}).mapValue.fields},updateMask:{fieldPaths:['migration']},currentDocument:{exists:true}}]});assert.equal(page.status,200,JSON.stringify(page.body));
 await readRest('parent',v5Path);const chunks=await rest('parent',`/${root}/sectors/0:runQuery`,{structuredQuery:{from:[{collectionId:'chunks'}],orderBy:[{field:{fieldPath:'__name__'},direction:'ASCENDING'}],limit:4}});assert.equal(chunks.status,200);assert.equal(chunks.body.filter(row=>row.document).length,1);assert.equal(chunks.body.find(row=>row.document).document.fields.cells.mapValue.fields['1_3_2'].stringValue,`0:0:${nonce}`);
 const activate=await rest('parent',':commit',{writes:[{update:{name:docName(v5Path),fields:typed5({phase:'ready',open:true}).mapValue.fields},updateMask:{fieldPaths:['phase','open']},currentDocument:{exists:true}},{update:{name:docName(root),fields:typed5({closed:false}).mapValue.fields},updateMask:{fieldPaths:['closed','migration']},currentDocument:{exists:true}}]});assert.equal(activate.status,200,JSON.stringify(activate.body));
 assert.deepEqual((await readRest('child',invPath('child'))).fields,childPrivate.fields);
 for(const uid of ['parent','child']){const adopted=await rest(uid,':commit',{writes:[{update:{name:docName(v5Path),fields:typed5({players:{[uid]:{inventory:inv5(),rev:1}}}).mapValue.fields},updateMask:{fieldPaths:[`players.\`${uid}\`.inventory`,`players.\`${uid}\`.rev`]},currentDocument:{exists:true}}]});assert.equal(adopted.status,200,JSON.stringify(adopted.body));}
 const owner=db('parent'),close=writeBatch(owner);close.update(doc(owner,root),{closed:true});close.update(doc(owner,v5Path),{open:false});await close.commit();await deleteDoc(doc(owner,`${root}/members/parent`));await deleteDoc(doc(owner,v5Path));
 assert.equal((await assertSucceeds(getDoc(doc(owner,v5Path)))).exists(),false);await assertSucceeds(deleteDoc(doc(owner,root)));
});
test('v5 retains legacy historical-snapshot semantics while denying current reads and writes after a ban',async()=>{
 for(const protocol of [4,5]){
  await env.clearFirestore();if(protocol===4){await create(4);await join();}else{await create5();await join5();}
  const path=protocol===4?`${root}/sync/state`:v5Path;const before=await rest('child',':batchGet',{documents:[docName(path)]});assert.equal(before.status,200);const row=before.body.find(row=>row.found),time=row.readTime;
  const parent=db('parent'),ban=writeBatch(parent);if(protocol===5)ban.update(doc(parent,v5Path),{'access.child':false});ban.update(doc(parent,`${root}/members/child`),{banned:true});await ban.commit();
  if(protocol===4){const moved=await rest('parent',':commit',{writes:[presenceWrite('parent',88)]});assert.equal(moved.status,200);}else await updateDoc(doc(parent,v5Path),{'players.parent.position':{name:'Parent',sector:0,x:88,y:30,z:0,yaw:0,updatedAt:serverTimestamp()}});
  const current=await rest('child',':batchGet',{documents:[docName(path)]});assert.equal(current.status,403);
  const historical=await rest('child',':batchGet',{documents:[docName(path)],readTime:time});assert.equal(historical.status,200,JSON.stringify(historical.body));assert.deepEqual(historical.body.find(row=>row.found).found.fields,row.found.fields,'historical response contains only the earlier authorized data, never post-ban updates');
  if(protocol===4){const denied=await rest('child',':commit',{writes:[presenceWrite('child',20)]});assert.equal(denied.status,403);}else await assertFails(updateDoc(doc(db('child'),v5Path),{'players.child.position':{name:'Child',sector:0,x:20,y:30,z:0,yaw:0,updatedAt:serverTimestamp()}}));
 }
});
