package de.meiapps.meicraft;

/** Conservative CPU rejection of whole chunk boxes already outside GPU clip planes.
 * Column-major projection * view matrix; touching/near-plane boxes are retained.
 */
final class ViewFrustum {
    private final double[] planes=new double[24],roundingScale=new double[24],tolerance=new double[6];
    void update(float[] matrix){
        for(int i=0;i<6;i++){
            int row=i/2;double sign=(i&1)==0?1:-1;
            int p=i*4;
            for(int c=0;c<4;c++){
                double w=matrix[c*4+3],v=matrix[c*4+row];
                planes[p+c]=w+sign*v;roundingScale[p+c]=Math.abs(w)+Math.abs(v);
            }
            tolerance[i]=.001*Math.sqrt(planes[p]*planes[p]+planes[p+1]*planes[p+1]+planes[p+2]*planes[p+2]);
        }
    }
    boolean intersects(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
        double ax=Math.max(Math.abs(minX),Math.abs(maxX)),ay=Math.max(Math.abs(minY),Math.abs(maxY)),az=Math.max(Math.abs(minZ),Math.abs(maxZ));
        for(int i=0;i<6;i++){
            int p=i*4;double a=planes[p],b=planes[p+1],c=planes[p+2];
            double x=a>=0?maxX:minX,y=b>=0?maxY:minY,z=c>=0?maxZ:minZ;
            // Independent float dot products on the GPU can round differently
            // from combined CPU planes, especially at the far plane. Retain
            // their worst-case float rounding neighborhood, as well as 1 mm.
            double error=1e-6*(roundingScale[p]*ax+roundingScale[p+1]*ay+roundingScale[p+2]*az+roundingScale[p+3]);
            if(a*x+b*y+c*z+planes[p+3]<-tolerance[i]-error)return false;
        }
        return true;
    }
}
