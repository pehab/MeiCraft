package de.meiapps.meicraft;

/** Offline progression and recipes, independent of Android and the renderer. */
public final class Story {
 public final int[] inventory=new int[World.NAMES.length];
 public int chapter,iron,placed,glassPlaced,buckets;
 public boolean sword;
 public static final String[] TOOLS={"Hand","Holzwerkzeug","Holzspitzhacke","Steinspitzhacke","Eisenspitzhacke"};
 public int tool(){return chapter>=5?4:chapter>=3?3:chapter>=2?2:chapter>=1?1:0;}
 public boolean furnace(){return chapter>=4;}
 public static int needed(int block){switch(block){case 1:case 2:return 0;case 4:case 5:case 6:case 9:case 12:return 1;case 3:case 7:return 2;case 8:case 13:return 3;default:return 4;}}
 public boolean canMine(int block){return block>0&&block<World.NAMES.length&&tool()>=needed(block);}
 public float seconds(int block){float base=block==3?1.25f:block==13?1.7f:block==11?2f:block==5?.9f:.5f;return base/(1+.18f*tool());}
 public boolean add(int block,int amount){if(block<=0||block>=inventory.length||amount<0||inventory[block]>1000000-amount)return false;inventory[block]+=amount;return true;}
 public boolean ready(){switch(chapter){case 0:return inventory[2]>=8;case 1:return inventory[2]>=8&&inventory[5]>=6;case 2:return inventory[3]>=12&&inventory[5]>=4;case 3:return inventory[3]>=8&&inventory[5]>=4;case 4:return iron>=4&&inventory[5]>=4;case 5:return placed>=30&&glassPlaced>=2;case 6:return inventory[11]>=4;default:return false;}}
 public String title(){switch(chapter){case 0:return "1 · Der Bauplatz";case 1:return "2 · Die Werkbank";case 2:return "3 · Härterer Stein";case 3:return "4 · Ein heißer Ofen";case 4:return "5 · Das Eisenwerkzeug";case 5:return "6 · Dein Zuhause";case 6:return "7 · Tief im Berg";default:return "Baumeister · Frei weiterbauen";}}
 public String goal(){switch(chapter){case 0:return "Erde sammeln: "+inventory[2]+" / 8";case 1:return "8 Erde + 6 Holz → Holzspitzhacke";case 2:return "12 Stein + 4 Holz → Steinspitzhacke";case 3:return "8 Stein + 4 Holz → Ofen";case 4:return "4 Eisenbarren + 4 Holz → Eisenspitzhacke";case 5:return "Bauen: "+Math.min(placed,30)+"/30 · Fenster: "+Math.min(glassPlaced,2)+"/2";case 6:return "Leuchtstein finden: "+inventory[11]+" / 4";default:return "Alle Werkzeuge frei · Deine Welt wächst weiter";}}
 public String detail(){return "Baumeister Miko hilft dir, hier ein neues Zuhause aufzubauen.\n\n"+goal()+"\n\n"+(chapter==0?"Halte − gedrückt und sammle Gras oder Erde. Gras gibt Erde. Miko tauscht 8 Erde gegen dein erstes Holzwerkzeug. Damit kannst du Holz und Sand abbauen.":chapter==1?"Bringe Miko 8 Erde und 6 Holz. Gemeinsam baut ihr eine Werkbank und eine Holzspitzhacke. Damit lässt sich Stein abbauen.":chapter==2?"Mit 12 Stein und 4 Holz baust du eine Steinspitzhacke. Damit erreichst du Eisenerz im Gestein.":chapter==3?"Baue einen Ofen aus 8 Stein und 4 Holz. Darin lassen sich Erz und Sand verarbeiten. Holz dient als Brennstoff.":chapter==4?"Schmilz Erz im Ofen: 1 Eisenerz + 1 Holz ergeben einen Eisenbarren. Mit 4 Barren und 4 Holz entsteht eine Eisenspitzhacke.":chapter==5?"Platziere insgesamt 30 Blöcke und davon mindestens 2 Glasblöcke. Glas stellst du im Ofen aus Sand und Holz her. Deine gesammelten Materialien werden beim Bauen verbraucht.":chapter==6?"Suche tief im Gestein nach goldgelbem Leuchtstein. Zum Abbauen brauchst du die Eisenspitzhacke. Die vier gesammelten Steine darfst du behalten.":"Du hast alle Werkzeuge und Rezepte freigeschaltet. Sammle weiter, baue größere Gebäude und erkunde die Landschaft.")+"\n\nWerkzeug: "+TOOLS[tool()]+"\nVorräte: "+inventory[2]+" Erde · "+inventory[5]+" Holz · "+inventory[3]+" Stein · "+inventory[13]+" Erz · "+iron+" Eisenbarren";}
 public boolean claim(){if(!ready())return false;switch(chapter){case 0:inventory[2]-=8;break;case 1:inventory[2]-=8;inventory[5]-=6;break;case 2:inventory[3]-=12;inventory[5]-=4;break;case 3:inventory[3]-=8;inventory[5]-=4;break;case 4:iron-=4;inventory[5]-=4;break;default:break;}chapter++;return true;}
 public String craft(int recipe){
  if(chapter<2)return "Baue zuerst die Werkbank im zweiten Auftrag.";
  int output=0,amount=0,costA=0,costB=0,typeA=0,typeB=0;
  switch(recipe){
  case 6:if(iron<3)return "Du brauchst 3 Eisenbarren für einen Eiseneimer.";if(buckets>=1000000)return "Dein Vorrat ist voll.";iron-=3;buckets++;return "Eiseneimer hergestellt · − auf Lava zum Füllen";
  case 7:if(iron<2||inventory[5]<1)return "Du brauchst 2 Eisenbarren und 1 Holz.";if(sword)return "Du hast schon ein Eisenschwert.";iron-=2;inventory[5]--;sword=true;return "Eisenschwert hergestellt · − zum Angreifen";
  case 8:if(!furnace())return "Baue zuerst den Ofen.";if(buckets<1||inventory[11]<1||inventory[3]<4||inventory[5]<2)return "Du brauchst 1 leeren Eimer, 1 Leuchtstein, 4 Stein und 2 Holz.";if(!add(15,1))return "Dein Vorrat ist voll.";buckets--;inventory[11]--;inventory[3]-=4;inventory[5]-=2;return "Lavaeimer im Ofen geschmolzen";
  case 0:output=9;amount=4;typeA=5;costA=1;break;case 1:output=7;amount=4;typeA=2;costA=4;typeB=3;costB=1;break;case 2:if(!furnace())return "Baue zuerst den Ofen.";output=8;amount=2;typeA=4;costA=2;typeB=5;costB=1;break;case 3:if(!furnace())return "Baue zuerst den Ofen.";if(inventory[13]<1||inventory[5]<1)return "Du brauchst 1 Eisenerz und 1 Holz.";if(iron>=1000000)return "Dein Vorrat ist voll.";inventory[13]--;inventory[5]--;iron++;return "1 Eisenbarren hergestellt";case 4:if(tool()<4)return "Stelle zuerst die Eisenspitzhacke her.";if(iron<9)return "Du brauchst 9 Eisenbarren.";if(!add(14,1))return "Dein Vorrat ist voll.";iron-=9;return "1 Eisenblock hergestellt";case 5:output=1;amount=4;typeA=2;costA=4;typeB=6;costB=1;break;default:return "Unbekanntes Rezept";}
  if(inventory[typeA]<costA||inventory[typeB]<costB)return "Dafür fehlen noch Rohstoffe.";
  if(!add(output,amount))return "Dein Vorrat ist voll.";inventory[typeA]-=costA;inventory[typeB]-=costB;return amount+" "+World.NAMES[output]+" hergestellt";
 }
}
