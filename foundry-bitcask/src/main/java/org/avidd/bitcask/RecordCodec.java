package org.avidd.bitcask;

import org.avidd.storage.PayloadCodec;

import java.io.IOException;

public class RecordCodec implements PayloadCodec<Record> {
    @Override
    public byte[] encode(Record record) throws IOException {
        record.timestamp();
        record.key();
        record.tombstone();
        record.value();
        return new byte[0];
    }

    @Override
    public Record decode(byte[] bytes) throws IOException {
        long ts = Long.MIN_VALUE;
        byte[] key = new byte[0];
        byte[] value = new byte[0];
        boolean isTombstone = false;
        return new Record(ts, key, value, isTombstone);
    }
}
