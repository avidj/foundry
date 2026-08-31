// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.lsm;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

public class BloomFilter {
  final int m; // size
  final int k; // hash function count
  private final BitSet bits;

  public BloomFilter(byte[] bytes, int m, int k) {
    bits = BitSet.valueOf(bytes);
    this.m = m;
    this.k = k;
  }

  public BloomFilter(int expectedInsertions, double falsePositiveRate) {
    assert expectedInsertions > 0;
    assert falsePositiveRate > 0.0 && falsePositiveRate <= 1.0;
    this.m = optimalBits(expectedInsertions, falsePositiveRate);
    this.k = optimalHashFunctions(m, expectedInsertions);
    this.bits = new BitSet(m);
  }

  private static int optimalBits(int n, double p) {
    return (int)(-n * Math.log(p) / ( Math.log(2) * Math.log(2)));
  }

  private static int optimalHashFunctions(int m, int n) {
    return Math.max(1, (int)Math.round((double)m/n * Math.log(2)));
  }

  public byte[] getBytes() {
    return bits.toByteArray();
  }

  public boolean mayHave(String key) {
    byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
    int h1 = hash1(bytes);
    int h2 = hash2(bytes);
    for ( int i = 0; i < k; i++ ) {
      if ( !bits.get(Math.floorMod(h1 + i * h2, m))) {
        return false;
      }
    }
    return true;
  }

  public void put(String key) {
    byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
    int h1 = hash1(bytes);
    int h2 = hash2(bytes);
    // simulates k independent hash functions from two (Kirsch & Mitzenmacher 2006)
    for ( int i = 0; i < k; i++ ) {
      bits.set(Math.floorMod(h1 + i * h2, m)); // always >= 0, unlike %
    }
  }

  private static int hash1(byte[] bytes) {
    // FNV-1a
    int hash = 0x811c9dc5;
    for ( byte aByte : bytes ) {
      hash ^= aByte & 0xFF;
      hash *= 0x01000193;
    }
    return hash;
  }

  private static int hash2(byte[] bytes) {
    // DJB2
    int hash = 5381;
    for ( byte aByte : bytes ) {
      hash = (hash * 33) ^ (aByte & 0xFF);
    }
    return hash;
  }
}
