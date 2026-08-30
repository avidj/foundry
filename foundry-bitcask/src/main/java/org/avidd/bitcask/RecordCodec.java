// SPDX-License-Identifier: Apache-2.0

package org.avidd.bitcask;

import org.avidd.storage.PayloadCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RecordCodec implements PayloadCodec<Record> {
  private static final int TOMBSTONE = -1;

  public int sizeBytes(Record record) {
    return Record.HEADER_BYTES +
      record.key().length +
      (record.tombstone() ? 0 : record.value().length);
  }

  @Override
  public byte[] encode(Record record) throws IOException {
    final int len = sizeBytes(record);
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
    ByteBuffer buf = ByteBuffer.wrap(bytes);

    // read header
    long ts = buf.getLong();
    int keyLen = buf.getInt();
    int valLen = buf.getInt();

    // read payload
    byte[] key = new byte[keyLen];
    buf.get(key);
    boolean isTombstone = valLen == TOMBSTONE;
    byte[] value = new byte[isTombstone ? 0 : valLen];
    buf.get(value);

    return new Record(ts, key, value, isTombstone);
  }
}
