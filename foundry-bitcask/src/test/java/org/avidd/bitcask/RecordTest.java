package org.avidd.bitcask;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;

import org.junit.jupiter.api.Test;

public class RecordTest {

  @Test
  public void testEquals() {
    assertThat(new Record(1L, "key0".getBytes(), "value0".getBytes(), false),
      is(equalTo(new Record(1L, "key0".getBytes(), "value0".getBytes(), false))));

    assertThat(new Record(1L, "key0".getBytes(), "value0".getBytes(), false),
      is(not(equalTo(new Record(2L, "key0".getBytes(), "value0".getBytes(), false)))));

    assertThat(new Record(1L, "key0".getBytes(), "value0".getBytes(), false),
      is(not(equalTo(new Record(1L, "key1".getBytes(), "value0".getBytes(), false)))));

    assertThat(new Record(1L, "key0".getBytes(), "value0".getBytes(), false),
      is(not(equalTo(new Record(1L, "key0".getBytes(), "value1".getBytes(), false)))));

    assertThat(new Record(1L, "key0".getBytes(), "value0".getBytes(), false),
      is(not(equalTo(new Record(1L, "key0".getBytes(), "value0".getBytes(), true)))));
  }

  @Test
  public void testHashCode() {
    final Record record1 = new Record(1L, "key0".getBytes(), "value0".getBytes(), false);
    final Record record2 = new Record(1L, "key0".getBytes(), "value0".getBytes(), false);
    assertThat(record1.hashCode(), is(equalTo(record2.hashCode())));
  }
}
