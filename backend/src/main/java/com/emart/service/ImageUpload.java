package com.emart.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Validates uploaded product images. Images are served back from the application's own origin, so a file that is
 * really HTML or SVG (both can run script) must never be stored or served as an "image". The client-declared
 * content type and file name are therefore not trusted: the type is detected from the file's first bytes, only
 * PNG, JPEG, GIF and WebP are accepted, and the stored name is stripped of any path.
 */
public final class ImageUpload {

    public record Validated(String name, String contentType, byte[] data) {}

    private static final int MAX_NAME_LENGTH = 200;

    private ImageUpload() {}

    /** @throws IllegalArgumentException (mapped to HTTP 400) if the file is not a supported image */
    public static Validated validate(MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        String type = detect(data);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported image. Please upload a PNG, JPEG, GIF or WebP file.");
        }
        return new Validated(safeName(file.getOriginalFilename()), type, data);
    }

    static String detect(byte[] b) {
        if (b == null) return null;
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    static String safeName(String original) {
        if (original == null || original.isBlank()) return "image";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) return "image";
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
