package org.avidd.lsm;

import static org.avidd.lsm.Memtable.DEL_BYTES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.avidd.storage.PayloadCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MemtableOpCodecTest {

  @Test
  public void testRoundTripValue() throws IOException {
    PayloadCodec<MemtableOp> codec = new MemtableOpCodec();
    MemtableOp op = new MemtableOp(
      OpType.PUT,
      "key".getBytes(StandardCharsets.UTF_8),
      "val".getBytes(StandardCharsets.UTF_8));

    assertThat(codec.decode(codec.encode(op)), is(equalTo(op)));
  }

  @Test
  public void testRoundTripTombstone() throws IOException {
    PayloadCodec<MemtableOp> codec = new MemtableOpCodec();
    MemtableOp op = new MemtableOp(
      OpType.DELETE,
      "key".getBytes(StandardCharsets.UTF_8),
      DEL_BYTES);

    assertThat(codec.decode(codec.encode(op)), is(equalTo(op)));
  }
}
