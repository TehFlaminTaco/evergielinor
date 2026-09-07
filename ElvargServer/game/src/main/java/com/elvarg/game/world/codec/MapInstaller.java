package com.elvarg.game.world.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * Writes generated map files into both stores that hold them: the server's
 * {@code data/clipping/maps} directory and the client's cache index 4.
 *
 * The two must stay in lockstep. The server derives collision from its copy and
 * validates every object interaction against it, while the client renders and
 * pathfinds from its own; if they diverge, players walk through walls the server
 * still believes in, and click objects the server says are not there.
 *
 * Payloads are gzip-compressed, which is what both readers expect - the server
 * gunzips in RegionManager#loadMapFiles and the client in ResourceProvider.
 *
 * @author EverGielinor world generator
 */
public final class MapInstaller implements AutoCloseable {

    /**
     * The client gunzips map files into a fixed buffer of this size
     * (ResourceProvider#gzipInputBuffer). Anything larger is silently truncated,
     * so the installer refuses to ship it.
     */
    private static final int CLIENT_GUNZIP_LIMIT = 0x71868;

    private final Path serverMapsDirectory;
    private final JagCache clientCache;
    private int filesWritten;

    public MapInstaller(Path serverMapsDirectory, Path clientCacheDirectory) throws IOException {
        this.serverMapsDirectory = serverMapsDirectory;
        Files.createDirectories(serverMapsDirectory);
        this.clientCache = new JagCache(clientCacheDirectory);
    }

    /**
     * Installs one file id into both stores.
     *
     * @param fileId      the map file id, taken from an existing map_index entry
     * @param rawPayload  the uncompressed landscape bytes
     */
    public void install(int fileId, byte[] rawPayload) throws IOException {
        if (rawPayload.length > CLIENT_GUNZIP_LIMIT) {
            throw new IOException("map file " + fileId + " is " + rawPayload.length
                    + " bytes uncompressed, over the client's " + CLIENT_GUNZIP_LIMIT + " byte gunzip buffer");
        }
        byte[] compressed = gzip(rawPayload);

        // Server copy first: it is a plain file, so a failure here leaves the cache
        // untouched and the pair still consistent.
        Files.write(serverMapsDirectory.resolve(fileId + ".dat"), compressed);
        clientCache.write(JagCache.INDEX_MAPS, fileId, compressed);
        filesWritten++;
    }

    /**
     * Reads back what was installed for a file id, from the client cache, and
     * confirms it matches the server's copy byte for byte.
     */
    public boolean verify(int fileId) throws IOException {
        byte[] fromCache = clientCache.read(JagCache.INDEX_MAPS, fileId);
        Path serverFile = serverMapsDirectory.resolve(fileId + ".dat");
        if (fromCache == null || !Files.exists(serverFile)) {
            return false;
        }
        return java.util.Arrays.equals(fromCache, Files.readAllBytes(serverFile));
    }

    public int filesWritten() {
        return filesWritten;
    }

    public long cacheGrowthBytes() throws IOException {
        return clientCache.wastedBytes();
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 3);
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        }
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        clientCache.close();
    }
}
