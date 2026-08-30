// SPDX-License-Identifier: Apache-2.0

package org.avidd.lsm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SSTable {
  public static final double FALSE_POSITIVE_RATE = 0.01;
  public static final int SIZE = 10000;
  public static final String FILE_EXT = ".sstable";
  public final int epoch;
  final Path path;
  private final BloomFilter bloom;
  private final List<IndexEntry> sparseIndex;
  final long indexOffset;

  record IndexEntry(String key, long offset) implements Comparable<IndexEntry> {
    @Override
    public int compareTo(IndexEntry o) { return this.key.compareTo(o.key); }
  }

  static String toFileName(int epoch) {
    return epoch + FILE_EXT;
  }

  SSTable(int epoch, Path path, BloomFilter bloom, List<IndexEntry> sparseIndex, long indexOffset) {
    this.epoch = epoch;
    this.path = path;
    this.bloom = bloom;
    this.sparseIndex = sparseIndex;
    this.indexOffset = indexOffset;
  }

  SSTableIO.SSTableIterator iterator() throws IOException {
    return SSTableIO.iterator(this.path, 0L, this.indexOffset);
  }

  boolean mayHave(String key) {
    return bloom.mayHave(key);
  }

  MemtableValue get(String key) throws IOException {
    if ( !mayHave(key) ) {
      throw new IllegalArgumentException("test with mayHave first");
    }
    int idx = Collections.binarySearch(sparseIndex, new IndexEntry(key, -1));
    int floorIdx = (idx < 0) ? -idx - 2 : idx;
    if ( floorIdx < 0 ) {
      return null;
    } // cannot be found
    long offset = sparseIndex.get(floorIdx).offset;
    long endOffset = (floorIdx + 1 < sparseIndex.size())
      ? sparseIndex.get(floorIdx + 1).offset
      : indexOffset;
    try ( SSTableIO.SSTableIterator iterator = SSTableIO.iterator(path, offset, endOffset) ) {
      while ( iterator.hasNext() ) {
        Map.Entry<String, MemtableValue> entry = iterator.next();
        int comp = entry.getKey().compareTo(key);
        if ( comp == 0 ) {
          return entry.getValue();
        } else if ( comp > 0 ) { // passed the potential position
          return null;
        }
      }
    }
    return null;
  }
}
