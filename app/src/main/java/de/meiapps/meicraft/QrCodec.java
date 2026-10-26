package de.meiapps.meicraft;

import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.*;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.IOException;
import java.util.*;

/** QR optics shared by the real camera and image-based tests. No network or Android API. */
final class QrCodec {
    private QrCodec(){}
    static BitMatrix encode(String text)throws IOException{
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M);hints.put(EncodeHintType.MARGIN,4);
        try{return new QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,0,0,hints);}
        catch(WriterException e){throw new IOException("QR-Code konnte nicht erzeugt werden",e);}
    }
    static String decode(byte[] luma,int width,int height){
        if(width<1||height<1||width>2048||height>2048||luma.length<(long)width*height)return null;
        LuminanceSource source=new PlanarYUVLuminanceSource(luma,width,height,0,0,width,height,false);
        String result=decodeSource(source);
        if(result!=null||width==height)return result;
        // A centered code can be easier to detect without busy objects around the viewfinder.
        int side=Math.min(width,height);return decodeSource(source.crop((width-side)/2,(height-side)/2,side,side));
    }
    private static String decodeSource(LuminanceSource source){
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
        for(int attempt=0;attempt<2;attempt++){
            QRCodeReader reader=new QRCodeReader();
            try{return reader.decode(new BinaryBitmap(new HybridBinarizer(attempt==0?source:source.invert())),hints).getText();}
            catch(ReaderException ignored){
                // Dense world fragments can contain extra finder-like patterns. Try all candidates.
                BinaryBitmap bitmap=new BinaryBitmap(new HybridBinarizer(attempt==0?source:source.invert()));
                try{Result[] results=new QRCodeMultiReader().decodeMultiple(bitmap,hints);if(results.length>0)return results[0].getText();}
                catch(ReaderException absent){/* No valid geometric candidate. */}
                // Axis-aligned, isolated codes (including screenshots) can be sampled directly.
                hints.put(DecodeHintType.PURE_BARCODE,Boolean.TRUE);
                try{return reader.decode(bitmap,hints).getText();}
                catch(ReaderException absent){/* Wait for the next camera image. */}
                finally{hints.remove(DecodeHintType.PURE_BARCODE);}
            }
            finally{reader.reset();}
        }
        return null;
    }
}
