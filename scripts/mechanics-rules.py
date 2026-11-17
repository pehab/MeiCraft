"""Extend existing bounded journal batches with resource-conserving block permutations.
Run after nature-rules.py, or once against the checked-in rules. No broader write grants.
"""
import json
from pathlib import Path
p=Path('database.rules.json');d=json.loads(p.read_text());s=d['rules']['worlds']['$owner']['$world']['storage'];a=s['head']['actors']['$uid']
if 'mechanic' in a:raise SystemExit('Mechanics rules already installed')
a['mechanic']={'.validate':"newData.isBoolean() && newData.val() == true && newData.parent().child('batch').exists()"}
old="root.child('worlds').child($owner).child($world).child('storage')"
actor='newData.parent().parent()';last="newData.child('last')"
# Keep all existing inventory revision, nonce, journal, generation and pose fences.
for i in range(16):
 node=a['batch'][f'b{i}'];expr=node['.validate']
 needle=f"({actor}.child('resourceRegion').exists() || {actor}.child('reactionRegion').exists()"
 assert needle in expr
 expr=expr.replace(needle,f"({actor}.child('mechanic').val() == true || {actor}.child('resourceRegion').exists() || {actor}.child('reactionRegion').exists()",1)
 source=f"{actor}.child('batch').child('b' + {last}.child('from').val()).child('last')"
 old_source=f"{old}.child('chunks').child({source}.child('chunk').val()).child('cells').child({source}.child('key').val())"
 old_cell=f"{old}.child('chunks').child({last}.child('chunk').val()).child('cells').child({last}.child('key').val())"
 ref=f"{last}.child('from')";typ=f"{last}.child('type').val()"
 check=f"{ref}.isNumber() && {ref}.val() % 1 == 0 && {last}.child('water').val() == false && (({typ} == 0 && {ref}.val() == -1) || ({typ} > 0 && {ref}.val() >= 0 && {ref}.val() < {actor}.child('count').val() && {old_source}.child('type').val() == {typ} && {typ} != 6 && {typ} != 10 && {typ} != 15))"
 # Distinct sources forbid copying one original solid into several destinations.
 for j in range(16):
  if i!=j:check+=f" && ({typ} == 0 || {actor}.child('count').val() <= {j} || {ref}.val() != {actor}.child('batch/b{j}/last/from').val())"
 # Every original solid in the mutation must have exactly one destination: no loss or duplication.
 refs=' || '.join(f"({actor}.child('count').val() > {j} && {actor}.child('batch/b{j}/last/from').val() == {i})" for j in range(16))
 check+=f" && (!{old_cell}.exists() || {old_cell}.child('type').val() == 0 || ({refs}))"
 # Permutations cannot consume a natural block which has no durable source cell.
 check+=f" && ({last}.child('nature').val() == '' || {last}.child('nature').val() == 'user' || {last}.child('nature').val().matches(/^mech_[A-Za-z0-9_-]{{20,100}}$/))"
 node['.validate']=expr+f" && (!{actor}.child('mechanic').exists() || ({check}))"
p.write_text(json.dumps(d,indent=2)+'\n')
