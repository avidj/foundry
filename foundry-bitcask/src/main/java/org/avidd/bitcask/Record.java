package org.avidd.bitcask;

import org.avidd.storage.PayloadCodec;

import java.lang.reflect.Array;
import java.util.Arrays;

public record Record(long timestamp, byte[] key, byte[] value, boolean tombstone) {
  public static final int HEADER_BYTES = 16;
  public static final PayloadCodec<Record> CODEC = new RecordCodec();

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Record that)) {
      return false;
    }
    return this.timestamp == that.timestamp &&
      this.tombstone == that.tombstone &&
      Arrays.equals(this.key, that.key) &&
      Arrays.equals(this.value, that.value);
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(this.timestamp);
    result = 31 * result + Arrays.hashCode(this.key);
    result = 31 * result + Arrays.hashCode(this.value);
    result = 31 * result + Boolean.hashCode(this.tombstone);
    return result;
  }
}
