package com.kadaibook.service;

import com.kadaibook.config.UploadProperties;
import com.kadaibook.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageValidator {

    private final UploadProperties properties;

    public ImageValidator(UploadProperties properties) {
        this.properties = properties;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("empty_image", "Choose a page image to upload.");
        }
        long declared = file.getSize();
        if (declared <= 0 || declared > properties.maxBytes()) {
            throw ApiException.badRequest("image_too_large", "The image exceeds the upload limit.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("unreadable_image", "The image could not be read.");
        }
        if (bytes.length == 0 || bytes.length > properties.maxBytes()) {
            throw ApiException.badRequest("image_too_large", "The image exceeds the upload limit.");
        }
        ImageType type = sniff(bytes);
        if (type == null) {
            throw ApiException.badRequest(
                    "unsupported_image",
                    "Upload a JPEG or PNG page image.");
        }
        int width;
        int height;
        try {
            int[] dimensions = dimensions(bytes);
            width = dimensions[0];
            height = dimensions[1];
        } catch (IOException | RuntimeException exception) {
            throw ApiException.badRequest("unreadable_image", "The image file is damaged or unreadable.");
        }
        long pixels = (long) width * (long) height;
        if (width < 1 || height < 1 || pixels > properties.maxPixels()) {
            throw ApiException.badRequest(
                    "invalid_image_dimensions",
                    "The image dimensions are not supported. Use a normal photo under the pixel limit.");
        }
        return new ValidatedImage(bytes, type.contentType, type.extension, width, height);
    }

    private static ImageType sniff(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return ImageType.PNG;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return ImageType.JPEG;
        }
        return null;
    }

    private static int[] dimensions(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IOException("no image input");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("no image reader");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    public record ValidatedImage(
            byte[] bytes,
            String contentType,
            String extension,
            int width,
            int height
    ) {
    }

    private enum ImageType {
        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }
}
