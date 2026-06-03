package org.avidd.lsm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;

import org.junit.jupiter.api.Test;

public class BloomFilterTest {

  @Test
  public void testNoFalseNegatives() {
    int insertions = 100;
    BloomFilter bf = new BloomFilter(insertions, 0.1);
    for ( int i = 0; i < insertions; i++ ) {
      bf.put(Integer.toString(i));
    }
    for ( int i = 0; i < insertions; i++ ) {
      assertThat(bf.mayHave(Integer.toString(i)), is(true));
    }
    int falsePos = 0;
    for ( int i = insertions; i < 2 * insertions; i++ ) {
      falsePos += bf.mayHave(Integer.toString(i)) ? 1 : 0;
    }
    assertThat(falsePos, is(lessThan(15)));
  }

  @Test
  public void testFalsePositiveRate() {
    int insertions = 100;
    BloomFilter bf = new BloomFilter(insertions, 0.1);
    for ( int i = 0; i < insertions; i++ ) {
      bf.put(Integer.toString(i));
    }
    int falsePos = 0;
    for ( int i = insertions; i < insertions + 1000; i++ ) {
      falsePos += bf.mayHave(Integer.toString(i)) ? 1 : 0;
    }
    assertThat(falsePos, is(lessThan(150)));
  }
}
