package org.avidd.storage;

import java.io.IOException;

public interface PayloadCodec<T> {
    byte[] encode(T value) throws IOException;
    T decode(byte[] bytes) throws IOException;
}