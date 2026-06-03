package org.avidd.lsm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;

public class SSTable {
  private final BloomFilter bloom;
  private final ArrayList<IndexEntry> sparseIndex;
  private final Path path;

  static record IndexEntry(String key, long offset) implements Comparable<IndexEntry> {
    @Override
    public int compareTo(IndexEntry o) { return this.key.compareTo(o.key); }
  }

  static String toFileName(int epoch) {
    return new StringBuilder(Integer.toString(epoch)).append(".sstable").toString();
  }

  SSTable(Path path) throws IOException {
    this.path = path;

    // recover
    int size = 100;
    this.bloom = new BloomFilter(size, 0.01);
    this.sparseIndex = new ArrayList<>(size);
  }

  public String get(String key) {
    if ( !bloom.mayHave(key) ) { return null; }
    int idx = Collections.binarySearch(sparseIndex, new IndexEntry(key, -1));
    int floorIdx = ( idx < 0 ) ? -idx -2 : idx;
    if ( floorIdx < 0 ) { return null; } // cannot be found
    long offset = sparseIndex.get(floorIdx).offset;

    // random access the sstable file at offset, scan and if key is found return
    return null;
  }
}
