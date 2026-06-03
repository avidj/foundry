package org.avidd.lsm;

import org.avidd.storage.BinaryAppendLog;
import org.avidd.storage.Frame;
import org.avidd.storage.PayloadCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

class Memtable {
  private static final PayloadCodec<MemtableOp> CODEC = MemtableOpCodec.getInstance();
  static final byte[] DEL_BYTES = new byte[0];
  static final String BAL_FILE_EXT = ".bal";
  private final BinaryAppendLog<MemtableOp> bal;
  private final Object mutex = new Object();
  private transient boolean isSealed = false;
  private transient int sizeBytes = 0;
  final Map<String, MemtableValue> map = Collections.synchronizedMap(new TreeMap<>());
  final int epoch;

  private Memtable(int epoch, BinaryAppendLog<MemtableOp> bal) {
    this.epoch = epoch;
    this.bal = bal;
  }

  static Memtable memtable(Path folder, int epoch) throws IOException {
    Path path = folder.resolve(epoch + BAL_FILE_EXT);
    BinaryAppendLog<MemtableOp> bal = new BinaryAppendLog<>(path, CODEC);
    bal.recover();
    Memtable memtable = new Memtable(epoch, bal);
    try ( BinaryAppendLog.CloseableIterator<Frame<MemtableOp>> ops = bal.iterator() ) {
      while ( ops.hasNext() ) {
        MemtableOp op = ops.next().payload();
        op.replay(memtable);
        memtable.sizeBytes += CODEC.sizeBytes(op);
      }
    }
    return memtable;
  }

  SSTable flush(Path sstableFolder) throws IOException {
    if ( this.isSealed ) {
      throw new IllegalStateException("flush already triggered");
    }
    this.isSealed = true;
    sstableFolder.toFile().mkdirs();
    SSTable sst = SSTableIO.write(sstableFolder, this);
    boolean deleted = bal.delete();
    assert deleted;
    return sst;
  }

  MemtableValue get(String key) {
    return this.map.get(key);
  }

  int getSizeBytes() {
    synchronized ( mutex ) {
      return this.sizeBytes;
    }
  }

  /**
   * @param key the key to write
   * @param value the value to write
   * @return the size of this memtable in bytes after write
   * @throws IOException if the write fails
   */
  int put(String key, String value) throws IOException {
    if ( this.isSealed ) {
      throw new IllegalStateException("flush already triggered");
    }
    final byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    final byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);
    MemtableOp op = new MemtableOp(OpType.PUT, keyBytes, valBytes);
    synchronized ( mutex ) {
      bal.append(op);
      sizeBytes += CODEC.sizeBytes(op);
      this.map.put(key, new MemtableValue(value, false));
    }
    return sizeBytes;
  }

  /**
   * @param key the key to delete
   * @return the size of this memtable in bytes after write
   * @throws IOException if the write fails
   */
  int delete(String key) throws IOException {
    if ( this.isSealed ) {
      throw new IllegalStateException("flush already triggered");
    }
    final byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    MemtableOp op = new MemtableOp(OpType.DELETE, keyBytes, DEL_BYTES);
    synchronized ( mutex ) {
      bal.append(op);
      sizeBytes += CODEC.sizeBytes(op);
      this.map.put(key, new MemtableValue(null, true));
    }
    return sizeBytes;
  }
}
