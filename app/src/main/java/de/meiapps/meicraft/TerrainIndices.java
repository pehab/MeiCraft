package de.meiapps.meicraft;

/** Shared 16-bit quad topology. Avoid 65535, the fixed restart index on ES 3. */
final class TerrainIndices {
    static final int MAX_FACES=16383,MAX_VERTICES=MAX_FACES*4;
    private TerrainIndices(){}
    static short[] pattern(){
        short[] indices=new short[MAX_FACES*6];
        for(int face=0;face<MAX_FACES;face++){
            int vertex=face*4,i=face*6;
            indices[i]=(short)vertex;indices[i+1]=(short)(vertex+1);indices[i+2]=(short)(vertex+2);
            indices[i+3]=(short)vertex;indices[i+4]=(short)(vertex+2);indices[i+5]=(short)(vertex+3);
        }
        return indices;
    }
}
