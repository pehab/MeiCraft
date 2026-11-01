const {test}=require('node:test');
const assert=require('node:assert/strict');
const {readFileSync}=require('node:fs');
const {FirestoreApi}=require('firebase-tools/lib/firestore/api');
const config=JSON.parse(readFileSync('firestore.indexes.json','utf8'));
const override=(group,field)=>config.fieldOverrides.find(v=>v.collectionGroup===group&&v.fieldPath===field);
test('index configuration passes the actual Firebase CLI validator without deployment',()=>{
  new FirestoreApi().validateSpec(config);
  assert.equal(JSON.parse(readFileSync('firebase.json','utf8')).firestore.indexes,'firestore.indexes.json');
});
test('delta query retains its collection-scope updatedAt and implicit document-name ordering',()=>{
  assert.deepEqual(override('blocks','updatedAt').indexes,[{order:'ASCENDING',queryScope:'COLLECTION'}]);
  // The other block fields are returned from the document, not used as filters.
  assert.deepEqual(override('blocks','*').indexes,[]);
  assert.deepEqual(config.indexes,[]);
});
test('whole-document maps, inventories and members have no unused field indexes',()=>{
  for(const [group,field] of [['sync','players'],['sync','regions'],['inventory','*'],['members','*']])assert.deepEqual(override(group,field).indexes,[]);
});
