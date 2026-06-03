package org.avidd.lsm;

/**
 * In-memory memtable value.
 * @param value the value if and only if !tombstone
 * @param tombstone true, if and only if deleted (should be singleton)
 */
public record MemtableValue(String value, boolean tombstone) {
  public MemtableValue {
    assert !tombstone || value == null;
  }
}
