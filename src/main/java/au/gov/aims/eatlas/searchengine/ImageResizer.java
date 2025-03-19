package au.gov.aims.eatlas.searchengine;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageResizer {
    public static byte[] getImageData(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    public static BufferedImage resizeCropImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
        //BufferedImage originalImage = ImageIO.read(imageFile);
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // Calculate the scaling factor to maintain aspect ratio
        double scale = Math.max(
            (double) targetWidth / originalWidth,
            (double) targetHeight / originalHeight
        );

        int scaledWidth = (int) (scale * originalWidth);
        int scaledHeight = (int) (scale * originalHeight);

        // Resize the image
        Image tmp = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        // Crop the image to the target dimensions (center crop)
        int x = (scaledWidth - targetWidth) / 2;
        int y = (scaledHeight - targetHeight) / 2;

        return resizedImage.getSubimage(x, y, targetWidth, targetHeight);
    }
}
