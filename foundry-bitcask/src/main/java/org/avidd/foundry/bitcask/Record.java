// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.bitcask;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public record Record(long timestamp, byte[] key, byte[] value, boolean tombstone) {
  public static final int HEADER_BYTES = 16;

  public Record(long timestamp, byte[] key, byte[] value, boolean tombstone) {
    assert !tombstone || value.length == 0;
    this.timestamp = timestamp;
    this.key = key;
    this.value = value;
    this.tombstone = tombstone;
  }

  @Override
  public String toString() {
    return "Record[" +
      "timestamp=" + timestamp() +
      ", key=" + new String(key(), StandardCharsets.UTF_8) +
      ", value=" + new String(value(), StandardCharsets.UTF_8) +
      ", tombstone=" + tombstone();
  }

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
