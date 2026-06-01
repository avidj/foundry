package org.avidd.bitcask;

import org.avidd.storage.PayloadCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RecordCodec implements PayloadCodec<Record> {
  private static final byte TOMBSTONE = -1;

  @Override
  public byte[] encode(Record record) throws IOException {
    final int len = Record.HEADER_BYTES +
      record.key().length +
      (record.tombstone() ? 0 : record.value().length);
    ByteBuffer buf = ByteBuffer.allocate(len);

    // write header
    buf.putLong(record.timestamp());
    buf.putInt(record.key().length);
    buf.putInt(record.tombstone() ? -1 : record.value().length);

    // write payload
    buf.put(record.key(), 0, record.key().length);
    if (!record.tombstone()) {
      buf.put(record.value(), 0, record.value().length);
    }
    buf.flip();
    return buf.array();
  }

  @Override
  public Record decode(byte[] bytes) throws IOException {
    assert bytes.length >= Record.HEADER_BYTES: "invalid record bytes";

    int offset = 0;
    int len = Long.BYTES;
    ByteBuffer buf = ByteBuffer.allocate(len);
    buf.put(bytes, offset, len);
    buf.rewind();
    offset += len;
    long ts = buf.getLong();

    len = Integer.BYTES;
    buf = ByteBuffer.allocate(len);
    buf.put(bytes, offset, len);
    buf.rewind();
    offset += len;
    int keyLen = buf.getInt();

    len = Integer.BYTES;
    buf = ByteBuffer.allocate(len);
    buf.put(bytes, offset, len);
    buf.rewind();
    offset += len;
    int valLen = buf.getInt();

    boolean isTombstone = valLen == (int)TOMBSTONE;

    len = keyLen;
    buf = ByteBuffer.allocate(len);
    buf.put(bytes, offset, len);
    buf.rewind();
    offset += len;
    byte[] key = buf.array();

    byte[] value = new byte[0];
    if (!isTombstone) {
      len = valLen;
      buf = ByteBuffer.allocate(len);
      buf.put(bytes, offset, len);
      buf.rewind();
      value = buf.array();
    }
    return new Record(ts, key, value, isTombstone);
  }
}
