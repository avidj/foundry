// SPDX-License-Identifier: Apache-2.0

/**
 * Record MemtableOp: binary WAL entries
 * class MemtableCodec implements PayloadCodec<MemtableOp>
 * Record MemtableEntry (object or tombstone)
 * class Memtable
 *   - int epoch (monotonically increasing)
 *   - TreeMap<String, MemtableEntry> map (sorted map)
 *   - BinaryAppendLog<MemtableOp> bal
 *   public void flush/close() // write to SSTable, fsync, delete bal
 *   public .. get/put(..) // delegate to map
 *   public void delete(..) // tombstone in map
 * class BloomFilter
 *   BloomFilter(int expectedInsertions, double falsePositives)
 * class SStable
 *   bloomFilter
 *   sparseIndex: ArrayList<Record<String, Long>> and Collections.binarySearch
 *   get: if bloomFilter.has(key) and "floorIndex = binarySearch(sparseIndex,key) > 0; if < 0, convert to insertion point -1: random access to offset from sparseIndex and scan until >= key, return if =key
 * LsmKvStore
 *   - Memtable memtable
 *   - Memtable flushingMemtable // usually null
 *   - List<SSTable> ssTables
 *   public String get: 1. memtable, 2. flushing memtable, 3. bloom-filter + sparse-index (floor-offset) + sequential scan from offset
 *   public void put/delete: delegate to memtable
 *   public void rotate(); // atomically: flushingMemtable = memtable; memtable = new Memtable(epoch++); outside lock: oldMemtable.flush()
 *   public Iterator<Map.Entry<String, String>> scan(String fromKey, String toKey)
 */
package org.avidd.lsm;
