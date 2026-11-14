"""Extend the reviewed presence rules with atomic protocol-6 storage constraints."""
import json
p='database.rules.json';d=json.load(open(p));w=d['rules']['worlds']['$owner']['$world']
base="root.child('worlds').child($owner).child($world)"
st=f"{base}.child('storage')";old=f"{st}.child('head/actors').child(auth.uid)"
active=f"auth != null && {base}.child('members').child(auth.uid).child('active').val() == true && {base}.child('meta/closed').val() == false"
ready=f"{st}.child('control/phase').val() == 'ready'"
importing=f"auth != null && auth.uid == $owner && {st}.child('control/phase').val() != 'ready'"
parent=lambda n:'newData'+'.parent()'*n
newactor=lambda n:f"{parent(n)}.child('head/actors').child(auth.uid)"
headactor="newData.parent().child('actors').child(auth.uid)"
lease=f"{st}.child('lease')"
host=f"{active} && {lease}.child('uid').val() == auth.uid && {lease}.child('until').val() > now"
# Direct own-meta read also explains closure after the broader world permission is revoked.
w['meta']['closed']['.read']=f"auth != null && (auth.uid == $owner || {base}.child('members').child(auth.uid).child('active').val() == true)"
num=lambda expr:f"{expr}.isNumber() && {expr}.val() % 1 == 0 && {expr}.val() >= 0 && {expr}.val() < 9000000000000"
A='newData';S=parent(3);L="newData.child('last')"
lastchecks=f"{L}.hasChildren(['region','chunk','key','x','y','z','type','water','token','before']) && {L}.child('region').isNumber() && {L}.child('region').val() % 1 == 0 && {L}.child('region').val() >= 0 && {L}.child('region').val() < {base}.child('meta/side').val() * {base}.child('meta/side').val()"
for c,lo,hi in [('x',0,95),('y',1,47),('z',0,95),('type',0,16)]:lastchecks+=f" && {L}.child('{c}').isNumber() && {L}.child('{c}').val() % 1 == 0 && {L}.child('{c}').val() >= {lo} && {L}.child('{c}').val() <= {hi}"
lastchecks+=f" && {L}.child('water').isBoolean() && ({L}.child('water').val() != true || {L}.child('type').val() == 10) && {L}.child('token').isString() && {L}.child('token').val().matches(/^[a-f0-9]{{32}}$/) && {L}.child('before').isString() && {L}.child('before').val().matches(/^([a-f0-9]{{32}})?$/) && {L}.child('token').val() != {L}.child('before').val()"
lastchecks+=f" && {L}.child('key').val() == {L}.child('x').val() + '_' + {L}.child('y').val() + '_' + {L}.child('z').val() && {L}.child('chunk').val() == {L}.child('region').val() + '_' + (({L}.child('x').val() - {L}.child('x').val() % 16) / 16) + '_' + (({L}.child('y').val() - {L}.child('y').val() % 16) / 16) + '_' + (({L}.child('z').val() - {L}.child('z').val() % 16) / 16)"
text=lambda a:f"{a}.child('seq').val() + '/' + {a}.child('last/region').val() + '/' + {a}.child('last/key').val() + '/' + {a}.child('last/type').val() + '/' + ({a}.child('last/water').val() == true ? '1' : '0') + '/' + {a}.child('last/token').val() + '/' + {a}.child('last/before').val()"
newcell=f"{S}.child('chunks').child({L}.child('chunk').val()).child('cells').child({L}.child('key').val())"
oldcell=f"{st}.child('chunks').child({L}.child('chunk').val()).child('cells').child({L}.child('key').val())"
block=f"newData.child('seq').val() == data.child('seq').val() + 1 && {lastchecks} && {newcell}.child('token').val() == {L}.child('token').val() && {newcell}.child('type').val() == {L}.child('type').val() && {newcell}.child('water').val() == {L}.child('water').val() && ({oldcell}.exists() ? {oldcell}.child('token').val() : '') == {L}.child('before').val() && newData.child('tail').child('e' + (newData.child('seq').val() % 16)).val() == {text('newData')} && {S}.child('head/regions').child('s' + {L}.child('region').val()).val() == ({st}.child('head/regions').child('s' + {L}.child('region').val()).exists() ? {st}.child('head/regions').child('s' + {L}.child('region').val()).val() : 0) + 1 && {S}.child('head/chunks').child({L}.child('chunk').val()).val() == ({st}.child('head/chunks').child({L}.child('chunk').val()).exists() ? {st}.child('head/chunks').child({L}.child('chunk').val()).val() : 0) + 1"
entry=f"{S}.child('journal').child($uid).child(newData.child('eventKey').val())"
actor_valid=f"newData.hasChildren(['rev','seq','inventory','op','eventKey']) && {num('newData.child(\"rev\")')} && {num('newData.child(\"seq\")')} && newData.child('inventory').isString() && newData.child('inventory').val().matches(/^1[.][A-Za-z0-9_-]{{16}}[.][A-Za-z0-9_-]{{146}}$/) && newData.child('op').val().matches(/^[a-f0-9]{{32}}$/) && newData.child('eventKey').val().matches(/^[0-9]{{1,13}}$/) && newData.child('eventKey').val() == newData.child('rev').val() + '' && newData.child('rev').val() == (data.exists() ? data.child('rev').val() : 0) + 1 && {S}.child('head/g').val() == {st}.child('head/g').val() + 1 && {entry}.child('rev').val() == newData.child('rev').val() && {entry}.child('op').val() == newData.child('op').val() && ((!data.exists() && newData.child('seq').val() == 0) || data.exists() && (newData.child('seq').val() == data.child('seq').val() || ({block})))"
actor={'.write':f"{active} && {ready} && auth.uid == $uid && newData.exists()",'.validate':f"({importing}) || ({actor_valid})"}
for k in ['rev','seq','inventory','op','eventKey','last']:actor[k]={'.validate':True}
tailactor=parent(2)
actor['tail']={'$slot':{'.validate':f"({importing}) || ($slot.matches(/^e([0-9]|1[0-5])$/) && newData.isString() && (newData.val() == data.val() || $slot == 'e' + ({tailactor}.child('seq').val() % 16) && newData.val() == {text(tailactor)}))"}}
actor['$other']={'.validate':False}
head={'g':{'.write':f"{active} && {ready}",'.validate':f"({importing}) || ({num('newData')} && newData.val() == data.val() + 1 && {headactor}.child('rev').val() == ({old}.exists() ? {old}.child('rev').val() : 0) + 1)"},'actors':{'$uid':actor}}
for name in ['regions','chunks']:
 a=newactor(3);key=f"'s' + {a}.child('last/region').val()" if name=='regions' else f"{a}.child('last/chunk').val()"
 head[name]={'$key':{'.write':f"{active} && {ready} && newData.exists()",'.validate':f"({importing}) || ({num('newData')} && newData.val() == (data.exists() ? data.val() : 0) + 1 && $key == {key} && {a}.child('rev').val() == {old}.child('rev').val() + 1 && {a}.child('seq').val() == {old}.child('seq').val() + 1)"}}
head['$other']={'.validate':False}
a=newactor(4)
cell={'.write':f"{active} && {ready} && newData.exists()",'.validate':f"({importing}) || (newData.hasChildren(['type','water','token']) && $chunk == {a}.child('last/chunk').val() && $cell == {a}.child('last/key').val() && {a}.child('rev').val() == {old}.child('rev').val() + 1 && {a}.child('seq').val() == {old}.child('seq').val() + 1 && newData.child('type').val() == {a}.child('last/type').val() && newData.child('water').val() == {a}.child('last/water').val() && newData.child('token').val() == {a}.child('last/token').val() && (data.exists() ? data.child('token').val() : '') == {a}.child('last/before').val())",'type':{'.validate':True},'water':{'.validate':True},'token':{'.validate':True},'$other':{'.validate':False}}
a=newactor(3)
journal={'.write':f"({active} && {ready} && auth.uid == $uid && !data.exists() && newData.exists()) || ({host} && data.exists() && !newData.exists() && data.child('rev').val() <= {st}.child('checkpoint/actors').child($uid).val() - 16)",'.validate':f"({importing}) || (newData.hasChildren(['rev','seq','inventory','op','event']) && $key == {a}.child('eventKey').val() && newData.child('rev').val() == {a}.child('rev').val() && newData.child('seq').val() == {a}.child('seq').val() && newData.child('inventory').val() == {a}.child('inventory').val() && newData.child('op').val() == {a}.child('op').val() && newData.child('event').val() == ({a}.child('seq').val() > ({old}.exists() ? {old}.child('seq').val() : 0) ? {text(a)} : ''))"}
for k in ['rev','seq','inventory','op','event']:journal[k]={'.validate':True}
journal['$other']={'.validate':False}
storage={'.write':f"({importing}) || (auth != null && auth.uid == $owner && {base}.child('meta/closed').val() == true && !newData.exists())",
 'control':{'.validate':"newData.hasChildren(['phase','epoch','source']) && (newData.child('phase').val() == 'importing' || newData.child('phase').val() == 'ready') && newData.child('epoch').val().matches(/^[a-f0-9]{32}$/)"},
 'head':head,'chunks':{'$chunk':{'cells':{'$cell':cell},'$other':{'.validate':False}}},'journal':{'$uid':{'.indexOn':['rev'],'$key':journal}},
 'lease':{'.write':f"{active} && {ready} && newData.exists() && newData.child('uid').val() == auth.uid && (!data.exists() || data.child('until').val() <= now || data.child('uid').val() == auth.uid)",'.validate':"newData.hasChildren(['uid','token','until']) && newData.child('token').val().matches(/^[a-f0-9]{32}$/) && (newData.child('until').val() == 0 || newData.child('until').val() >= now + 3000 && newData.child('until').val() <= now + 20000)"},
 'checkpoint':{'.write':host,'.validate':f"newData.hasChildren(['g','actors','token']) && newData.child('g').val() <= {st}.child('head/g').val() && (!data.exists() || newData.child('g').val() >= data.child('g').val())",'g':{'.validate':num('newData')},'token':{'.validate':"newData.val().matches(/^[a-f0-9]{32}$/)"},'actors':{'$uid':{'.validate':f"{num('newData')} && newData.val() <= {st}.child('head/actors').child($uid).child('rev').val()"}},'$other':{'.validate':False}},'$other':{'.validate':False}}
for k in ['uid','token','until']:storage['lease'][k]={'.validate':True}
storage['lease']['$other']={'.validate':False}
w['live']['.write']=f"auth != null && auth.uid == $owner && {base}.child('meta/closed').val() == true && !newData.exists()"
w['storage']=storage
json.dump(d,open(p,'w'),indent=2);open(p,'a').write('\n')
