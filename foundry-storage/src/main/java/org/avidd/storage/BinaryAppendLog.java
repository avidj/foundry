package org.avidd.storage;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public final class BinaryAppendLog<T> implements Closeable {
    private final Path file;
    private final PayloadCodec<T> codec;

    public BinaryAppendLog(Path file, PayloadCodec<T> codec) throws IOException {
        this.file = file;
        this.codec = codec;
    }

    /**
     * @param payload record to append
     * @return absolute offset of payload bytes within the file
     * @throws IOException
     */
    public long append(T payload) throws IOException {
        throw new UnsupportedOperationException();
    }

    public Iterator<Frame<T>> iterator() {
        throw new UnsupportedOperationException();
    }

    public long recover() throws IOException {
        throw new UnsupportedOperationException();
    }

    public long size() {
        throw new UnsupportedOperationException();
    }

    public void fsync() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }
}
