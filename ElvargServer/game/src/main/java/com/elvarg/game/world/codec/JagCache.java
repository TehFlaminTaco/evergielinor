package com.elvarg.game.world.codec;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Read/write access to the 317-style client cache ({@code main_file_cache.dat}
 * plus its {@code .idxN} tables).
 *
 * A cache file is a linked chain of 520-byte blocks: an 8-byte header
 * (file id, chunk number, next block, index id) followed by 512 payload bytes.
 * The index entry is 6 bytes - a 3-byte length and a 3-byte first block.
 *
 * Writes always <em>append</em> fresh blocks and then repoint the index entry.
 * Rewriting in place would be denser, but a generated map file is rarely the same
 * length as the one it replaces, and appending means a failure part-way through
 * leaves every existing file intact and readable. The blocks the old file used
 * are simply orphaned; {@link #wastedBytes} reports how much has accumulated.
 *
 * @author EverGielinor world generator
 */
public final class JagCache implements Closeable {

    public static final int BLOCK_SIZE = 520;
    public static final int BLOCK_HEADER = 8;
    public static final int BLOCK_PAYLOAD = BLOCK_SIZE - BLOCK_HEADER;
    private static final int INDEX_ENTRY = 6;

    /** Cache index holding map (landscape and terrain) files. */
    public static final int INDEX_MAPS = 4;

    private final RandomAccessFile data;
    private final Path directory;
    private final RandomAccessFile[] indices = new RandomAccessFile[6];
    private long initialDataLength;

    public JagCache(Path cacheDirectory) throws IOException {
        this.directory = cacheDirectory;
        Path dat = cacheDirectory.resolve("main_file_cache.dat");
        if (!Files.exists(dat)) {
            throw new IOException("main_file_cache.dat not found in " + cacheDirectory);
        }
        this.data = new RandomAccessFile(dat.toFile(), "rw");
        this.initialDataLength = data.length();
    }

    private RandomAccessFile index(int indexNo) throws IOException {
        if (indices[indexNo] == null) {
            Path path = directory.resolve("main_file_cache.idx" + indexNo);
            if (!Files.exists(path)) {
                throw new IOException("missing index " + path);
            }
            indices[indexNo] = new RandomAccessFile(path.toFile(), "rw");
        }
        return indices[indexNo];
    }

    public int fileCount(int indexNo) throws IOException {
        return (int) (index(indexNo).length() / INDEX_ENTRY);
    }

    /**
     * Reads one file, following its block chain. Returns null if the file has no
     * entry or a zero length.
     */
    public byte[] read(int indexNo, int fileId) throws IOException {
        RandomAccessFile idx = index(indexNo);
        long offset = (long) fileId * INDEX_ENTRY;
        if (offset + INDEX_ENTRY > idx.length()) {
            return null;
        }
        idx.seek(offset);
        byte[] entry = new byte[INDEX_ENTRY];
        idx.readFully(entry);
        int length = readTriple(entry, 0);
        int block = readTriple(entry, 3);
        if (length <= 0 || block <= 0) {
            return null;
        }

        byte[] out = new byte[length];
        byte[] buffer = new byte[BLOCK_SIZE];
        int written = 0;
        int chunk = 0;
        while (written < length) {
            if (block <= 0 || (long) block * BLOCK_SIZE >= data.length()) {
                throw new IOException("block chain ran off the end for " + indexNo + "/" + fileId);
            }
            data.seek((long) block * BLOCK_SIZE);
            data.readFully(buffer);
            int gotFile = ((buffer[0] & 0xff) << 8) | (buffer[1] & 0xff);
            int gotChunk = ((buffer[2] & 0xff) << 8) | (buffer[3] & 0xff);
            int next = readTriple(buffer, 4);
            int gotIndex = buffer[7] & 0xff;
            if (gotFile != fileId || gotChunk != chunk || gotIndex != indexNo + 1) {
                throw new IOException("corrupt block chain for " + indexNo + "/" + fileId
                        + ": header says file=" + gotFile + " chunk=" + gotChunk + " index=" + gotIndex);
            }
            int take = Math.min(BLOCK_PAYLOAD, length - written);
            System.arraycopy(buffer, BLOCK_HEADER, out, written, take);
            written += take;
            chunk++;
            block = next;
        }
        return out;
    }

    /**
     * Writes one file by appending a fresh block chain and repointing the index.
     */
    public void write(int indexNo, int fileId, byte[] payload) throws IOException {
        if (payload.length == 0) {
            throw new IOException("refusing to write an empty file " + indexNo + "/" + fileId);
        }
        RandomAccessFile idx = index(indexNo);
        long required = (long) (fileId + 1) * INDEX_ENTRY;
        if (idx.length() < required) {
            idx.setLength(required);
        }

        // Append on a block boundary so the chain stays addressable by block number.
        long end = data.length();
        if (end % BLOCK_SIZE != 0) {
            end += BLOCK_SIZE - (end % BLOCK_SIZE);
            data.setLength(end);
        }
        int firstBlock = (int) (end / BLOCK_SIZE);
        if (firstBlock <= 0) {
            throw new IOException("computed a zero first block, which the format reserves");
        }

        int chunks = (payload.length + BLOCK_PAYLOAD - 1) / BLOCK_PAYLOAD;
        byte[] block = new byte[BLOCK_SIZE];
        for (int chunk = 0; chunk < chunks; chunk++) {
            int blockNo = firstBlock + chunk;
            int next = (chunk == chunks - 1) ? 0 : blockNo + 1;
            java.util.Arrays.fill(block, (byte) 0);
            block[0] = (byte) (fileId >> 8);
            block[1] = (byte) fileId;
            block[2] = (byte) (chunk >> 8);
            block[3] = (byte) chunk;
            writeTriple(block, 4, next);
            block[7] = (byte) (indexNo + 1);
            int offset = chunk * BLOCK_PAYLOAD;
            int take = Math.min(BLOCK_PAYLOAD, payload.length - offset);
            System.arraycopy(payload, offset, block, BLOCK_HEADER, take);
            data.seek((long) blockNo * BLOCK_SIZE);
            data.write(block);
        }

        byte[] entry = new byte[INDEX_ENTRY];
        writeTriple(entry, 0, payload.length);
        writeTriple(entry, 3, firstBlock);
        idx.seek((long) fileId * INDEX_ENTRY);
        idx.write(entry);
    }

    /** Bytes appended since this handle was opened, i.e. orphaned old chains plus new data. */
    public long wastedBytes() throws IOException {
        return data.length() - initialDataLength;
    }

    private static int readTriple(byte[] b, int off) {
        return ((b[off] & 0xff) << 16) | ((b[off + 1] & 0xff) << 8) | (b[off + 2] & 0xff);
    }

    private static void writeTriple(byte[] b, int off, int value) {
        b[off] = (byte) (value >> 16);
        b[off + 1] = (byte) (value >> 8);
        b[off + 2] = (byte) value;
    }

    @Override
    public void close() throws IOException {
        data.close();
        for (RandomAccessFile idx : indices) {
            if (idx != null) {
                idx.close();
            }
        }
    }
}
