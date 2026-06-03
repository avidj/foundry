package org.avidd.lsm;

import org.avidd.kvstore.KVStore;
import org.avidd.storage.BinaryAppendLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LsmKvStore implements KVStore {
  private final Path folder;
  private final List<SSTable> ssTables;
  private int epoch;
  private Memtable memtable;
  private Memtable flushingMemtable;

  private LsmKvStore(Path folder, List<SSTable> ssTables, int epoch) throws IOException {
    this.folder = folder;
    this.ssTables = ssTables;
    this.epoch = epoch;
    this.memtable = Memtable.memtable(folder, epoch);
  }

  private static LsmKvStore lsmKvStore(Path folder) throws IOException {
    final int epoch = -1;
    final List<SSTable> ssTables = new ArrayList<>();
    return new LsmKvStore(folder, ssTables, epoch);
  }

  private void rotate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void put(String key, String value) throws IOException {

  }

  @Override
  public String get(String key) {
    return "";
  }

  @Override
  public void delete(String key) throws IOException {

  }
}
