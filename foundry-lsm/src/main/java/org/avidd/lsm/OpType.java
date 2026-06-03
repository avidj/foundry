package org.avidd.lsm;

import java.nio.charset.StandardCharsets;

public enum OpType {
  PUT(0) {
    @Override void replay(MemtableOp op, Memtable memtable) {
      memtable.map.put(toString(op.key()), new MemtableValue(toString(op.value()), false));
    }
  },
  DELETE(1) {
    @Override void replay(MemtableOp op, Memtable memtable) {
      memtable.map.put(toString(op.key()), new MemtableValue(null, true));
    }
  };


  OpType(int ordinal) { assert this.ordinal() == ordinal; }

  static String toString(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  abstract void replay(MemtableOp op, Memtable memtable);
}
