package de.meiapps.meicraft;

import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.*;
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
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
        for(int attempt=0;attempt<2;attempt++){
            QRCodeReader reader=new QRCodeReader();
            try{return reader.decode(new BinaryBitmap(new HybridBinarizer(attempt==0?source:source.invert())),hints).getText();}
            catch(ReaderException ignored){/* Missed or transitional frame: wait for the next image. */}
            finally{reader.reset();}
        }
        return null;
    }
}
