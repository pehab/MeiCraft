from PIL import Image,ImageDraw
import random
random.seed(413)
im=Image.new('RGB',(1024,32));d=ImageDraw.Draw(im)
colors=[(255,0,255),(113,171,67),(135,94,58),(132,140,143),(218,201,147),(139,102,57),(58,132,60),(170,83,64),(163,214,225),(181,143,91),(53,148,183),(244,207,104),(223,238,239),(135,94,58),(171,134,80),(255,255,255),(130,137,140),(177,188,193),(245,80,13),(62,78,112),(83,103,145),(69,56,52),(145,80,40),(180,100,45),(255,218,92)]
for t,col in enumerate(colors):
 x=t*32;d.rectangle((x,0,x+31,31),fill=col)
 for i in range(130):
  px=x+random.randrange(32);py=random.randrange(32);a=random.randint(-17,17);c=tuple(max(0,min(255,k+a)) for k in col);d.rectangle((px,py,px+random.randrange(1,4),py+1),fill=c)
 if t==13:
  d.rectangle((x,0,x+31,5),fill=(105,160,61))
  for i in range(0,32,3):d.rectangle((x+i,4,x+i+2,random.randrange(6,11)),fill=(105,160,61))
 if t==5:
  for i in [3,9,16,25,30]:d.line((x+i,0,x+i,31),fill=(91,67,40),width=2)
 if t==14:
  for i in [3,7,11]:d.rectangle((x+i,i,x+31-i,31-i),outline=(115,81,44))
 if t==7:
  for y in [0,8,16,24]:
   d.line((x,y,x+31,y),fill=(215,190,157),width=2)
   for off in [0,16,32]:d.line((x+(off+(8 if y%16 else 0))%32,y,x+(off+(8 if y%16 else 0))%32,y+8),fill=(215,190,157),width=2)
 if t==8:
  d.rectangle((x,0,x+31,31),outline=(220,248,252),width=2);d.line((x+4,23,x+24,3),fill=(210,241,248),width=2);d.line((x+12,27,x+28,11),fill=(194,233,240),width=2)
 if t==9:
  for y in [0,8,16,24]:d.line((x,y,x+31,y),fill=(117,86,51))
 if t==10:
  for y in [5,17,27]:d.line((x+random.randrange(8),y,x+random.randrange(20,32),y),fill=(93,177,203),width=2)
 if t==11:
  d.rectangle((x+3,3,x+28,28),outline=(255,240,178),width=3)
 if t==16:
  for dx,dy in [(4,5),(19,4),(11,18),(24,23)]:d.rectangle((x+dx,dy,x+dx+5,dy+4),fill=(188,129,83))
 if t==17:
  d.rectangle((x+1,1,x+30,30),outline=(218,228,231),width=2);d.line((x+3,27,x+27,27),fill=(121,136,143),width=2)
 if t==18:
  for dx,dy in [(2,5),(16,13),(5,25),(23,3)]:d.rectangle((x+dx,dy,x+min(31,dx+8),dy+3),fill=(255,206,49))
 if t==20:
  d.rectangle((x+5,9,x+11,16),fill=(244,230,148));d.rectangle((x+20,9,x+26,16),fill=(244,230,148))
  d.rectangle((x+8,11,x+10,15),fill=(26,30,52));d.rectangle((x+21,11,x+23,15),fill=(26,30,52))
  d.line((x+11,24,x+20,24),fill=(30,42,70),width=2)
 if t in (21,22,23):
  for dx,dy in [(3,8),(12,19),(22,4)]:
   d.line((x+dx,dy,x+dx+3,dy+6,x+min(31,dx+8),dy+9),fill=(255,127,41) if t==21 else (255,201,79),width=2)
 if t==22:
  for dx,dy in [(4,5),(19,4),(11,18),(24,23)]:d.rectangle((x+dx,dy,x+dx+5,dy+4),fill=(255,174,61))
 if t==23:d.rectangle((x+1,1,x+30,30),outline=(255,215,105),width=2)
# Dedicated avatar atlas: skin, trousers and five stable shirt colors. World tiles stay byte-identical.
skin=(239,188,147)
for t,color in enumerate([skin,(48,65,90),(57,134,232),(69,184,106),(237,157,50),(164,102,220),(223,92,100)],25):
 x=t*32;d.rectangle((x,0,x+31,31),fill=color)
 if t>=27:d.rectangle((x+5,26,x+26,31),fill=tuple(max(0,c-18) for c in color))
 if t==25:
  d.rectangle((x+6,10,x+11,14),fill=(43,47,58));d.rectangle((x+20,10,x+25,14),fill=(43,47,58))
  d.line((x+11,23,x+20,23),fill=(136,76,62),width=2)
im.save('app/src/main/assets/blocks.png')

