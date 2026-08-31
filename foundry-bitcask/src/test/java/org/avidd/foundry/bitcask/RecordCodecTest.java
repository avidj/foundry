// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.bitcask;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class RecordCodecTest {
  private final RecordCodec codec = new RecordCodec();

  @Test
  public void testRoundTrip() throws IOException {
    // given
    Record original = new Record(42L, "key".getBytes(), "value".getBytes(), false);

    // with
    byte[] encoded = codec.encode(original);
    Record decoded = codec.decode(encoded);

    // expect
    assertThat(original, is(equalTo(decoded)));
  }

  @Test
  public void testRoundTripTombstone() throws IOException {
    // given
    Record original = new Record(42L, "key".getBytes(), new byte[0], true);

    // with
    byte[] encoded = codec.encode(original);
    Record decoded = codec.decode(encoded);

    // expect
    assertThat(original, is(equalTo(decoded)));
  }
}
