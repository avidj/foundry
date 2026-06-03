package org.avidd.lsm;

import org.avidd.storage.PayloadCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MemtableOpCodec implements PayloadCodec<MemtableOp> {
  /* Op(1) + key-length(4) + value-length(4) */
  private static final int HEADER_BYTES = 9;


  @Override
  public byte[] encode(MemtableOp op) {
    int len = HEADER_BYTES + op.key().length + op.value().length;
    ByteBuffer buf = ByteBuffer.allocate(len);
    buf.put((byte)op.opType().ordinal());
    buf.putInt(op.key().length);
    buf.putInt(op.value().length);
    buf.put(op.key());
    buf.put(op.value());
    buf.flip();
    return buf.array();
  }

  @Override
  public MemtableOp decode(byte[] bytes) throws IOException {
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    OpType opType = OpType.values()[(int)buf.get()];
    byte[] keyBytes = new byte[buf.getInt()];
    byte[] valBytes = new byte[buf.getInt()];
    buf.get(keyBytes);
    buf.get(valBytes);
    return new MemtableOp(opType, keyBytes, valBytes);
  }
}
