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
  public void replay(Memtable memtable) {
    this.opType.replay(this, memtable);
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
}
