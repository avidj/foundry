package org.avidd.lsm;

import org.avidd.storage.BinaryAppendLog;
import org.avidd.storage.PayloadCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

class Memtable {
  private static final PayloadCodec<MemtableOp> CODEC = new MemtableOpCodec();
  static final byte[] DEL_BYTES = new byte[0];
  private final Path folder;
  private final BinaryAppendLog<MemtableOp> bal;
  private final Object mutex = new Object();
  private transient boolean isSealed = false;
  final Map<String, MemtableValue> map = new TreeMap<>();
  final int epoch;

  private Memtable(Path folder, int epoch, BinaryAppendLog<MemtableOp> bal) throws IOException {
    this.folder = folder;
    this.epoch = epoch;
    this.bal = bal;
  }

  static Memtable memtable(Path folder, int epoch) throws IOException {
    Path path = folder.resolve(Integer.toString(epoch) + ".bal");
    BinaryAppendLog<MemtableOp> bal = new BinaryAppendLog<>(path, CODEC);
    return new Memtable(folder, epoch, bal);
  }

  void flush() throws IOException {
    if ( this.isSealed ) {
      throw new IllegalStateException("flush already triggered");
    }
    this.isSealed = true;
    SSTableWriter.write(folder, this);
  }

  String get(String key) {
    MemtableValue val = this.map.get(key);
    return val != null && !val.tombstone() ? val.value() : null;
  }

  void put(String key, String value) throws IOException {
    if ( this.isSealed ) {
      throw new IllegalStateException("flush already triggered");
    }
    final byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    final byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
    MemtableOp op = new MemtableOp(OpType.PUT, keyBytes, valBytes);
    synchronized ( mutex ) {
      bal.append(op);
      this.map.put(key, new MemtableValue(value, false));
    }
  }

  void delete(String key) throws IOException {
    if ( this.isSealed ) {
      throw new IllegalStateException("flush already triggered");
    }
    final byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    MemtableOp op = new MemtableOp(OpType.DELETE, keyBytes, DEL_BYTES);
    synchronized ( mutex ) {
      bal.append(op);
      this.map.put(key, new MemtableValue(null, true));
    }
  }
}
