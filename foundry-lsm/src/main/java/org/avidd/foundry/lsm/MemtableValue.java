// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.lsm;

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
