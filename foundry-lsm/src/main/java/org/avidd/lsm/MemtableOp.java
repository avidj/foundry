package org.avidd.lsm;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * WAL entry.
 * @param opType PUT or DELETE
 * @param key key bytes
 * @param value value bytes
 */
public record MemtableOp(OpType opType, byte[] key, byte[] value) {

  @Override
  public int hashCode() {
    int hash = opType.ordinal() + 1;
    hash = hash * 31 + Arrays.hashCode(key());
    hash = hash * 31 + Arrays.hashCode(value());
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if ( !(other instanceof MemtableOp(OpType type, byte[] key1, byte[] value1))) {
      return false;
    }
    return this.opType == type
      && Arrays.equals(this.key, key1)
      && Arrays.equals(this.value, value1);
  }

  @Override
  public String toString() {
    return new StringBuilder("Record[")
      .append("opType=").append(opType().name())
      .append(", key=").append(new String(key(), StandardCharsets.UTF_8))
      .append(", value=").append(new String(value(), StandardCharsets.UTF_8))
      .toString();
  }
}
