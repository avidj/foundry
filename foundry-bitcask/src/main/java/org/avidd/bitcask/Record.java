package org.avidd.bitcask;

import org.avidd.storage.PayloadCodec;

public record Record(long timestamp, byte[] key, byte[] value, boolean tombstone) {
    public static final int HEADER_BYTES = 16;
    public static final PayloadCodec<Record> CODEC = new RecordCodec();
}
