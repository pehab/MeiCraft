"""Append bounded atomic journal batches and server-day nature constraints to v6 rules."""
import json
p='database.rules.json';d=json.load(open(p));w=d['rules']['worlds']['$owner']['$world'];st=w['storage']
base="root.child('worlds').child($owner).child($world)";oldst=f"{base}.child('storage')";old=f"{oldst}.child('head/actors').child(auth.uid)"
active=f"auth != null && {base}.child('members').child(auth.uid).child('active').val() == true && {base}.child('meta/closed').val() == false && {oldst}.child('control/phase').val() == 'ready'"
parent=lambda n:'newData'+'.parent()'*n
actor=lambda n:f"{parent(n)}.child('head/actors').child(auth.uid)"
day="((now - now % 86400000) / 86400000)"
run=f"{oldst}.child('growth/run')"
# Existing single edits carry optional durable provenance. Legacy empty strings remain valid.
A=st['head']['actors']['$uid'];cell=st['chunks']['$chunk']['cells']['$cell'];journal=st['journal']['$uid']['$key']
# The wire tail retains backward decoding; nonempty provenance is appended as an eighth field.
def append_text(expr,a):
 old=f"{a}.child('last/before').val()"
 return expr.replace(old,old+f" + ({a}.child('last/nature').exists() && {a}.child('last/nature').val() != '' ? '/' + {a}.child('last/nature').val() : '')")
# Only encoded event equality expressions end in before; do not alter nonce predicates.
def text(a):return f"{a}.child('seq').val() + '/' + {a}.child('last/region').val() + '/' + {a}.child('last/key').val() + '/' + {a}.child('last/type').val() + '/' + ({a}.child('last/water').val() == true ? '1' : '0') + '/' + {a}.child('last/token').val() + '/' + {a}.child('last/before').val()"
for node,a in [(A,'newData'),(A['tail']['$slot'],parent(2)),(journal,actor(3))]:node['.validate']=node['.validate'].replace(text(a),'('+append_text(text(a),a)+')')
# Nature field on a normal cell must agree with its journal last; optional for old clients.
ca=actor(4);cell['nature']={'.validate':f"newData.isString() && newData.val() == {actor(5)}.child('last/nature').val()"}
cell['.validate']=cell['.validate'].replace("newData.hasChildren(['type','water','token'])", "newData.hasChildren(['type','water','token']) && (!newData.child('nature').exists() || newData.child('nature').val() == "+ca+".child('last/nature').val())")
# A batch uses the same inventory revisions, per-cell nonce chains, tail and durable journal.
# A global generation precondition protects even read-only soil/wood dependencies.
B="newData.child('batch')";n="newData.child('count').val()";S=parent(3)
valid=f"newData.hasChildren(['rev','seq','inventory','op','eventKey','batch','count','base']) && newData.child('count').isNumber() && {n} % 1 == 0 && {n} >= 1 && {n} <= 16 && newData.child('base').val() == {oldst}.child('head/g').val() && newData.child('rev').val() == data.child('rev').val() + {n} && newData.child('seq').val() == data.child('seq').val() + {n} && newData.child('eventKey').val() == newData.child('rev').val() + '' && newData.child('op').val().matches(/^[a-f0-9]{{32}}$/) && {S}.child('head/g').val() == {oldst}.child('head/g').val() + 1"
for i in range(16):valid+=f" && ({n} <= {i} || {B}.child('b{i}').exists())"
# The final journal entry is the actor's published inventory / last change.
for field in ['rev','seq','inventory']:valid+=f" && newData.child('{field}').val() == {B}.child('b' + ({n} - 1)).child('{field}').val()"
valid+=f" && newData.child('last/token').val() == {B}.child('b' + ({n} - 1)).child('last/token').val()"
A['.validate']='('+A['.validate']+" && !newData.child('batch').exists()) || ("+valid+')'
for field in ['count','base','growthToken','growthDay']:A[field]={'.validate':True}
A['batch']={}
# All altered cells must have a matching batch slot; each slot requires its cell AND journal.
for i in range(16):
 a=parent(2);L="newData.child('last')";storage=parent(5);oldcell=f"{oldst}.child('chunks').child({L}.child('chunk').val()).child('cells').child({L}.child('key').val())";newcell=f"{storage}.child('chunks').child({L}.child('chunk').val()).child('cells').child({L}.child('key').val())";entry=f"{storage}.child('journal').child(auth.uid).child(newData.child('rev').val() + '')"
 # Reuse reviewed coordinate/nonce checks from the single block actor predicate.
 original=A['.validate'];start=original.index("newData.child('last').hasChildren");end=original.index(" && newData.parent().parent().parent().child('chunks')",start)
 coords=original[start:end]
 v=f"{a}.child('count').val() > {i} && newData.child('rev').val() == {old}.child('rev').val() + {i+1} && newData.child('seq').val() == {old}.child('seq').val() + {i+1} && newData.child('inventory').val().matches(/^1[.][A-Za-z0-9_-]{{16}}[.][A-Za-z0-9_-]{{146}}$/) && {coords} && {newcell}.child('token').val() == {L}.child('token').val() && {newcell}.child('type').val() == {L}.child('type').val() && {newcell}.child('nature').val() == {L}.child('nature').val() && {newcell}.child('slot').val() == 'b{i}' && ({oldcell}.exists() ? {oldcell}.child('token').val() : '') == {L}.child('before').val() && {entry}.child('seq').val() == newData.child('seq').val() && {entry}.child('inventory').val() == newData.child('inventory').val() && {entry}.child('op').val() == {a}.child('op').val() && {entry}.child('event').val() == {append_text(text('newData'),'newData')} && {a}.child('tail').child('e' + (newData.child('seq').val() % 16)).val() == {entry}.child('event').val()"
 # Only natural operations use batch transport. Never charge inventory or overwrite structures.
 v+=f" && ({L}.child('type').val() == 0 || {L}.child('type').val() == 5 || {L}.child('type').val() == 6)"
 A['batch']['b'+str(i)]={'.validate':v}
A['batch']['$other']={'.validate':False}
# Counter deltas are exactly the number of cells for that key (including chunk crossings).
def count(a,name,key):
 field='region' if name=='regions' else 'chunk';prefix="'s' + " if name=='regions' else ''
 return ' + '.join(f"({a}.child('count').val() > {i} && {prefix}{a}.child('batch/b{i}/last/{field}').val() == {key} ? 1 : 0)" for i in range(16))
for name in ['regions','chunks']:
 node=st['head'][name]['$key'];a=actor(3);total=count(a,name,'$key')
 node['.validate']='('+node['.validate']+f") || ({a}.child('batch').exists() && newData.val() == (data.exists() ? data.val() : 0) + ({total}) && ({total}) > 0 && {a}.child('base').val() == {oldst}.child('head/g').val())"
g=st['head']['g'];a=actor(2)
g['.validate']='('+g['.validate']+f") || ({a}.child('batch').exists() && newData.val() == data.val() + 1 && {a}.child('rev').val() == {old}.child('rev').val() + {a}.child('count').val())"
# Each slot also requires counter increments, so omission cannot silently break cache/backup coherence.
for i in range(16):
 node=A['batch']['b'+str(i)];a=parent(2);storage=parent(5)
 for name,key in [('regions',"'s' + newData.child('last/region').val()"),('chunks',"newData.child('last/chunk').val()")]:
  node['.validate']+=f" && {storage}.child('head/{name}').child({key}).val() > ({oldst}.child('head/{name}').child({key}).exists() ? {oldst}.child('head/{name}').child({key}).val() : 0)"
# Allow all 16 tail slots from the corresponding batch entries.
a=parent(2)
for i in range(16):
 b=a+f".child('batch/b{i}')"
 A['tail']['$slot']['.validate']+=f" || ({b}.exists() && $slot == 'e' + ({b}.child('seq').val() % 16) && newData.val() == {append_text(text(b),b)})"

a=actor(4);b=f"{a}.child('batch').child(newData.child('slot').val())"
cell['.validate']='('+cell['.validate']+f") || ({a}.child('batch').exists() && $chunk == {b}.child('last/chunk').val() && $cell == {b}.child('last/key').val() && newData.child('token').val() == {b}.child('last/token').val() && newData.child('type').val() == {b}.child('last/type').val() && newData.child('water').val() == false && (data.exists() ? data.child('token').val() : '') == {b}.child('last/before').val())"
cell['slot']={'.validate':"newData.isString() && newData.val().matches(/^b([0-9]|1[0-5])$/)"}
a=actor(5);cell['nature']['.validate']=f"newData.isString() && (newData.val() == {a}.child('last/nature').val() || newData.val() == {a}.child('batch').child(newData.parent().child('slot').val()).child('last/nature').val())"
a=actor(3);b=f"{a}.child('batch').child(newData.child('slot').val())"
journal['.validate']='('+journal['.validate']+f") || ({b}.exists() && $key == {b}.child('rev').val() + '' && newData.child('rev').val() == {b}.child('rev').val() && newData.child('seq').val() == {b}.child('seq').val() && newData.child('inventory').val() == {b}.child('inventory').val() && newData.child('op').val() == {a}.child('op').val() && newData.child('event').val() == {append_text(text(b),b)})"
journal['slot']={'.validate':"newData.val().matches(/^b([0-9]|1[0-5])$/)"}
# Every protocol-6 pose mutation increments one shared scalar in the SAME request.
# Batch preconditions therefore include joining/moving/leaving players, not just blocks.
live=f"{base}.child('live')"
player=w['live']['players']['$uid']
player['.write']=f"auth != null && {base}.child('members').child(auth.uid).child('active').val() == true && {base}.child('meta/closed').val() == false && auth.uid == $uid"
stationary="data.exists() && newData.exists()" + ''.join(f" && newData.child('{key}').val() == data.child('{key}').val()" for key in ['sector','x','y','z'])
player['.write']+=f" && ({oldst}.child('control/phase').val() != 'ready' || ({stationary}) || {parent(2)}.child('poseVersion').val() == ({live}.child('poseVersion').exists() ? {live}.child('poseVersion').val() : 0) + 1)"
w['live']['poseVersion']={'.write':active,'.validate':"newData.isNumber() && newData.val() == (data.exists() ? data.val() : 0) + 1"}
A['presenceBase']={'.validate':True}
A['.validate']='('+A['.validate']+f") && (!newData.child('batch').exists() || newData.child('decayRegion').exists() || newData.child('presenceBase').val() == ({live}.child('poseVersion').exists() ? {live}.child('poseVersion').val() : 0))"
# An actor with growth provenance must own today's live lease; day rollover invalidates stale clients.
A['.validate']='('+A['.validate']+f") && (!newData.child('growthToken').exists() || ({run}.child('uid').val() == auth.uid && {run}.child('token').val() == newData.child('growthToken').val() && {run}.child('day').val() == {day} && {run}.child('until').val() > now && {run}.child('done').val() == false && newData.child('growthDay').val() == {day}))"
# One shared server cadence per region prevents four clients from removing a crown in one instant.
A['decayRegion']={'.validate':True}
A['.validate']='('+A['.validate']+f") && (!newData.child('decayRegion').exists() || (newData.child('batch').exists() && newData.child('count').val() <= 4 && {parent(3)}.child('growth/decay').child(newData.child('decayRegion').val() + '').child('at').val() == now))"
for i in range(16):
 a=parent(2);A['batch']['b'+str(i)]['.validate']+=f" && (!{a}.child('decayRegion').exists() || (newData.child('last/type').val() == 0 && newData.child('last/region').val() == {a}.child('decayRegion').val()))"
a=actor(3)
decay={'.write':f"{active} && newData.exists() && (!data.exists() || data.child('at').val() <= now - 2800) && {a}.child('decayRegion').val() + '' == $region && {a}.child('batch').exists() && {a}.child('rev').val() == {old}.child('rev').val() + {a}.child('count').val()",'.validate':"newData.hasChildren(['at']) && newData.child('at').val() == now"}
# Atomic index maintenance. Root provenance remains in checkpointed cells even if the index is repaired.
a=actor(4);idx=parent(4)
sap={'.write':active,'.validate':f"newData.hasChildren(['stage','plantedAt','lastGrowthDay','token']) && newData.child('stage').isNumber() && newData.child('stage').val() >= 0 && newData.child('stage').val() <= 3 && newData.child('lastGrowthDay').val() == {day} && newData.child('plantedAt').isNumber() && newData.child('plantedAt').val() <= now && ((newData.child('stage').val() == 0 && {a}.child('last/region').val() + '' == $region && {a}.child('last/key').val() == $key && {a}.child('last/type').val() == 6 && newData.child('token').val() == {a}.child('last/token').val()) || (data.exists() && newData.child('stage').val() == data.child('stage').val() + 1 && data.child('lastGrowthDay').val() < {day} && {a}.child('growthToken').val() == {run}.child('token').val() && {a}.child('batch/b0/last/region').val() + '' == $region && {a}.child('batch/b0/last/key').val() == $key && newData.child('token').val() == {a}.child('batch/b0/last/token').val()))"}
# Deletion is only permitted alongside editing that same root (validate is skipped on null).
sap['.write']=f"{active} && (newData.exists() || ({a}.child('last/region').val() + '' == $region && {a}.child('last/key').val() == $key && {a}.child('rev').val() == {old}.child('rev').val() + 1))"
lease_write=f"{active} && newData.exists() && newData.child('uid').val() == auth.uid && newData.child('day').val() == {day} && ((!data.exists() || data.child('day').val() < {day} || data.child('done').val() == false && data.child('until').val() <= now) || (data.child('day').val() == {day} && data.child('uid').val() == auth.uid && data.child('token').val() == newData.child('token').val() && data.child('done').val() == false))"
# A lost secondary index is reconstructible from checkpointed root provenance, without advancing it.
repair=f"{oldst}.child('chunks').child(newData.child('chunk').val()).child('cells').child($key)"
sap['.validate']='('+sap['.validate']+f") || (newData.child('chunk').val().beginsWith($region + '_') && {repair}.child('token').val() == newData.child('token').val() && {repair}.child('nature').val() == 'tree_' + newData.child('stage').val() + '_' + newData.child('lastGrowthDay').val() + '_' + newData.child('plantedAt').val())"
st['growth']={'decay':{'$region':decay},'indexReady':{'.write':active,'.validate':'newData.val() == true'},'run':{'.write':lease_write,'.validate':"newData.hasChildren(['uid','token','day','until','done']) && newData.child('token').val().matches(/^[a-f0-9]{32}$/) && newData.child('done').isBoolean() && (newData.child('done').val() == true && newData.child('until').val() == 0 || newData.child('done').val() == false && newData.child('until').val() >= now + 3000 && newData.child('until').val() <= now + 20000)"},'saplings':{'$region':{'$key':sap}},'$other':{'.validate':False}}
json.dump(d,open(p,'w'),indent=2);open(p,'a').write('\n')
