package com.zaremate.tsa_anticheat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;

/** SHA-256 of raw files, or a deterministic Python-compatible ZIP of a folder. */
public final class ResourcePackHasher {
    private static final HexFormat HEX = HexFormat.of();
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final long MAX_ZIP32 = 0xffffffffL;
    private static final int MAX_ENTRIES = 0xffff;

    private ResourcePackHasher() {}

    public static String hash(Path entry) throws IOException {
        if (Files.isDirectory(entry)) return hashDirectory(entry);
        if (Files.isRegularFile(entry)) return hashRawFile(entry);
        throw new IOException("Unsupported resource pack: " + entry);
    }

    private static String hashRawFile(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            updateDigest(digest, in);
        }
        return HEX.formatHex(digest.digest());
    }

    /**
     * Matches tsa_hash_raw.py: ZIP_STORED, sorted paths, 1980-01-01 time,
     * Unix 0644 regular-file attributes, and forced ZIP64 local size fields.
     * No archive is written to disk: its exact bytes flow into SHA-256.
     */
    private static String hashDirectory(Path directory) throws IOException {
        List<Path> files;
        try (var paths = Files.walk(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> zipName(directory, path), ResourcePackHasher::compareCodePoints))
                    .toList();
        }
        if (files.size() > MAX_ENTRIES) throw new IOException("ZIP has too many entries: " + files.size());

        MessageDigest digest = sha256();
        try (var out = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            List<ZipInfo> entries = new ArrayList<>(files.size());
            long offset = 0;

            for (Path file : files) {
                String name = zipName(directory, file);
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                if (nameBytes.length > 0xffff) throw new IOException("ZIP path too long: " + name);
                long size = Files.size(file);
                if (size > MAX_ZIP32) throw new IOException("ZIP entry exceeds 4 GiB: " + name);
                long crc = crc32(file);
                long localOffset = offset;
                boolean utf8 = name.codePoints().anyMatch(cp -> cp > 127);
                int flags = utf8 ? 0x800 : 0;

                // Python zipfile.open(info, 'w', force_zip64=True) local header.
                u32(out, 0x04034b50);
                u16(out, 45);
                u16(out, flags);
                u16(out, 0);
                u16(out, 0);
                u16(out, 33);
                u32(out, crc);
                u32(out, MAX_ZIP32);
                u32(out, MAX_ZIP32);
                u16(out, nameBytes.length);
                u16(out, 20);
                out.write(nameBytes);
                u16(out, 1);
                u16(out, 16);
                u64(out, size);
                u64(out, size);
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(out);
                }
                offset += 30L + nameBytes.length + 20 + size;
                entries.add(new ZipInfo(nameBytes, size, crc, localOffset, flags));
            }

            long centralOffset = offset;
            for (ZipInfo info : entries) {
                if (info.offset > MAX_ZIP32) throw new IOException("ZIP exceeds 4 GiB offset limit");
                u32(out, 0x02014b50);
                u16(out, 0x032d);
                u16(out, 45);
                u16(out, info.flags);
                u16(out, 0);
                u16(out, 0);
                u16(out, 33);
                u32(out, info.crc);
                u32(out, info.size);
                u32(out, info.size);
                u16(out, info.name.length);
                u16(out, 0);
                u16(out, 0);
                u16(out, 0);
                u16(out, 0);
                u32(out, 0100644L << 16);
                u32(out, info.offset);
                out.write(info.name);
                offset += 46L + info.name.length;
            }
            long centralSize = offset - centralOffset;
            if (centralSize > MAX_ZIP32 || centralOffset > MAX_ZIP32) {
                throw new IOException("ZIP exceeds 4 GiB directory limit");
            }
            u32(out, 0x06054b50);
            u16(out, 0);
            u16(out, 0);
            u16(out, entries.size());
            u16(out, entries.size());
            u32(out, centralSize);
            u32(out, centralOffset);
            u16(out, 0);
        }
        return HEX.formatHex(digest.digest());
    }

    private static String zipName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static int compareCodePoints(String a, String b) {
        int[] left = a.codePoints().toArray();
        int[] right = b.codePoints().toArray();
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            if (left[i] != right[i]) return Integer.compare(left[i], right[i]);
        }
        return Integer.compare(left.length, right.length);
    }

    private static long crc32(Path file) throws IOException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream in = Files.newInputStream(file)) {
            int count;
            while ((count = in.read(buffer)) != -1) crc.update(buffer, 0, count);
        }
        return crc.getValue();
    }

    private static void updateDigest(MessageDigest digest, InputStream in) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void u16(OutputStream out, long n) throws IOException {
        out.write((int) n & 255);
        out.write((int) (n >>> 8) & 255);
    }

    private static void u32(OutputStream out, long n) throws IOException {
        u16(out, n);
        u16(out, n >>> 16);
    }

    private static void u64(OutputStream out, long n) throws IOException {
        u32(out, n);
        u32(out, n >>> 32);
    }

    private record ZipInfo(byte[] name, long size, long crc, long offset, int flags) {}
}
