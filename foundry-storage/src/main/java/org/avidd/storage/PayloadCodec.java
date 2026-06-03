package org.avidd.storage;

import java.io.IOException;

/**
 * A binary codec for type <T>.
 *
 * @param <T> the type of objects to be binary encoded and decoded
 */
public interface PayloadCodec<T> {
    /**
     * @param value the value to encode
     * @return a binary representation of the input
     * @throws IOException if anything goes wrong during encoding
     */
    byte[] encode(T value) throws IOException;

    /**
     * @param bytes the binary representation to decode to a <T>
     * @return the T resulting from decoding the input bytes
     * @throws IOException if anything goes wrong during decoding
     */
    T decode(byte[] bytes) throws IOException;

    int sizeBytes(T value);
}