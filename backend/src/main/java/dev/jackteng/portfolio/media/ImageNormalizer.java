package dev.jackteng.portfolio.media;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import dev.jackteng.portfolio.catalog.CatalogProblem;

@Component
public class ImageNormalizer {
    private final Semaphore decoding=new Semaphore(1);
    public record Normalized(String format,String mime,int width,int height,byte[] bytes,String sha256) {}
    public Normalized normalize(MultipartFile file) {
        if(file==null || file.isEmpty()) invalid("請選擇PNG或JPEG圖片");
        if(file.getSize()>5242880) throw new CatalogProblem(413,"IMAGE_TOO_LARGE","原始圖片不可超過5MiB");
        if(!decoding.tryAcquire()) throw new CatalogProblem(429,"IMAGE_BUSY","圖片處理中，請稍後重試");
        try {
            byte[] original=file.getBytes();
            String mime=file.getContentType();
            boolean png=original.length>=8 && Arrays.equals(Arrays.copyOf(original,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean jpg=original.length>=3 && original[0]==(byte)255 && original[1]==(byte)216 && original[2]==(byte)255;
            if((!png && !jpg) || !(png?"image/png":"image/jpeg").equals(mime)) invalid("只接受內容與類型一致的PNG／JPEG");
            try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
                var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext()) invalid("圖片無法解析");
                var reader=readers.next();
                try {
                    reader.setInput(stream,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1 || height<1 || width>4096 || height>4096 || (long)width*height>8000000) invalid("圖片最大4096px且總像素不可超過800萬");
                    BufferedImage image=reader.read(0);if(image==null) invalid("圖片無法解碼");
                    if(jpg && image.getType()!=BufferedImage.TYPE_INT_RGB) {
                        var rgb=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=rgb.createGraphics();
                        try { graphics.drawImage(image,0,0,null); } finally { graphics.dispose();image.flush(); }
                        image=rgb;
                    }
                    var output=new ByteArrayOutputStream();
                    try { if(!ImageIO.write(image,png?"png":"jpg",output)) invalid("圖片無法重編碼"); }
                    finally { image.flush(); }
                    byte[] bytes=output.toByteArray();if(bytes.length>10485760) invalid("標準化圖片過大");
                    return new Normalized(png?"png":"jpg",png?"image/png":"image/jpeg",width,height,bytes,
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                } finally { reader.dispose(); }
            }
        } catch(IOException e) { invalid("圖片損毀或無法讀取");return null; }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        finally { decoding.release(); }
    }
    private static void invalid(String message) { throw new CatalogProblem(400,"INVALID_IMAGE",message); }
}
