package com.zaremate.tsa_anticheat.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

public final class ResourcePackHasher {
    private static final HexFormat HEX = HexFormat.of();

    private ResourcePackHasher() {
    }

    public static String hash(Path resourcePack) throws IOException {
        if (Files.isDirectory(resourcePack)) {
            return hashDirectory(resourcePack);
        }

        if (Files.isRegularFile(resourcePack)) {
            return hashZipOrFile(resourcePack);
        }

        throw new IOException("Unsupported resource pack: " + resourcePack);
    }

    private static String hashZipOrFile(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return hashZip(zip);
        } catch (java.util.zip.ZipException notZip) {
            // Keep support for any regular resource-pack file by hashing its bytes.
            return hashRawFile(file);
        }
    }

    private static String hashZip(ZipFile zip) throws IOException {
        MessageDigest digest = sha256();

        List<? extends ZipEntry> entries = zip.stream()
                .filter(entry -> !entry.isDirectory())
                .sorted(Comparator.comparing(ZipEntry::getName))
                .toList();

        for (ZipEntry entry : entries) {
            digest.update(entry.getName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);

            try (InputStream input = zip.getInputStream(entry)) {
                updateDigest(digest, input);
            }

            digest.update((byte) 0);
        }

        return HEX.formatHex(digest.digest());
    }

    private static String hashRawFile(Path file) throws IOException {
        MessageDigest digest = sha256();

        try (InputStream input = Files.newInputStream(file)) {
            updateDigest(digest, input);
        }

        return HEX.formatHex(digest.digest());
    }

    private static String hashDirectory(Path directory) throws IOException {
        MessageDigest digest = sha256();
        List<Path> files;

        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path ->
                            directory.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }

        for (Path file : files) {
            String relativePath = directory.relativize(file)
                    .toString()
                    .replace('\\', '/');

            digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);

            try (InputStream input = Files.newInputStream(file)) {
                updateDigest(digest, input);
            }

            digest.update((byte) 0);
        }

        return HEX.formatHex(digest.digest());
    }

    private static void updateDigest(MessageDigest digest, InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        int read;

        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
