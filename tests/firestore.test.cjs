const {readFileSync}=require('node:fs');
const assert=require('node:assert/strict');
const {createMockUserToken}=require('@firebase/util');
const {before,after,beforeEach,test}=require('node:test');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,collection,getDoc,getDocs,setDoc,updateDoc,deleteDoc,writeBatch,serverTimestamp,query,where,orderBy,limit}=require('firebase/firestore');
let env;
const world='a'.repeat(32),invite='b'.repeat(32),root=`onlineWorlds/${world}`;
const db=uid=>uid?env.authenticatedContext(uid).firestore():env.unauthenticatedContext().firestore();
const memberData=(token=invite)=>({name:'Miko',banned:false,invite:token,sector:0,x:48.5,y:30,z:48.5,yaw:0,updatedAt:serverTimestamp()});
const blockData=(uid='child')=>({x:1,y:3,z:2,type:7,by:uid,updatedAt:serverTimestamp()});
const blockPath=`${root}/sectors/0/blocks/1_3_2`;
async function create(){const owner=db('parent'),batch=writeBatch(owner);batch.set(doc(owner,root),{owner:'parent',name:'Familienwelt',seed:123,side:2,protocol:1,invite,closed:false});batch.set(doc(owner,`${root}/members/parent`),memberData());return batch.commit();}
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
