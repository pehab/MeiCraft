package de.meiapps.meicraft;

/** Stable drag position independent of material selection; tap commits only after touch-up. */
final class MaterialBar {
    static final int WIDTH=448,STEP=56;
    float offset,startX,startOffset;boolean dragging;int selected=-1;
    float max(){return Math.max(0,(World.NAMES.length-1)*STEP-WIDTH);}
    void reveal(int type){float left=(type-1)*STEP;if(left<offset)offset=left;else if(left+STEP>offset+WIDTH)offset=left+STEP-WIDTH;offset=Math.max(0,Math.min(max(),offset));selected=type;}
    void down(float x){startX=x;startOffset=offset;dragging=false;}
    void move(float x){if(Math.abs(x-startX)>8)dragging=true;if(dragging)offset=Math.max(0,Math.min(max(),startOffset+startX-x));}
    int up(float x){move(x);if(dragging||x<0||x>=WIDTH)return -1;int type=1+(int)((x+offset)/STEP);return type<World.NAMES.length?type:-1;}
}
