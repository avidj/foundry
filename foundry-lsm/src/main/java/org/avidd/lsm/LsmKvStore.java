package org.avidd.lsm;

import org.avidd.kvstore.KVStore;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class LsmKvStore implements KVStore, Closeable {
  private static final long ROTATION_THRESHOLD_BYTES = 64 * 1024 * 1024;
  private static final int COMPACTION_THRESHOLD = 4;
  private static final String SSTABLES = "sstables";
  private final Path memtableFolder;
  private final List<SSTable> sstables;
  private final Object mutex = new Object();
  private int epoch;
  private volatile Memtable memtable;
  private volatile Memtable flushingMemtable;
  private volatile boolean closing;

  private LsmKvStore(Path folder, List<SSTable> sstables, int epoch) throws IOException {
    sstables.sort(Comparator.comparingInt(s -> s.epoch));
    this.memtableFolder = folder;
    this.sstables = sstables;
    this.epoch = epoch;
    this.memtable = Memtable.memtable(folder, epoch);
  }

  private static Path sstableFolder(Path folder) {
    return folder.resolve(SSTABLES);
  }

  private static int epoch(File f) {
    assert f.getName().endsWith(SSTable.FILE_EXT);
    return Integer.parseInt(f.getName().substring(0, f.getName().indexOf('.')));
  }

  /**
   * Restores an LSM key-value store from the given folder
   * @param folder the root folder to use for recovery and storage
   * @return the restored key-value store, or a fresh one
   * @throws IOException if an error occurs during recovery
   */
  static LsmKvStore lsmKvStore(Path folder) throws IOException {
    // recover sstables
    final List<SSTable> sstables = Collections.synchronizedList(new ArrayList<>());
    File sstableFolder = sstableFolder(folder).toFile();
    if ( sstableFolder.exists() ) {
      assert sstableFolder.isDirectory();
      File[] files = sstableFolder(folder).toFile().listFiles();
      assert files != null;
      List<File> sstableFiles = Arrays.stream(files)
        .filter(f -> f.getName().endsWith(SSTable.FILE_EXT))
        .sorted(Comparator.comparingInt(LsmKvStore::epoch))
        .toList();
      for ( File sstFile : sstableFiles ) {
        SSTable sst = SSTableIO.sstable(epoch(sstFile), sstFile.toPath());
        sstables.add(sst);
      }
    }

    // determine epoch, recover binary log if present
    File[] walFiles = folder.toFile().listFiles(f -> f.getName().endsWith(Memtable.BAL_FILE_EXT));
    int walEpoch = 0;
    if ( walFiles != null && walFiles.length > 0 ) {
      walEpoch = Arrays.stream(walFiles)
        .mapToInt(f -> Integer.parseInt(f.getName().replace(Memtable.BAL_FILE_EXT, "")))
        .max()
        .getAsInt();
    }
    int epoch = sstables.isEmpty() ? 0 : sstables.getLast().epoch + 1;
    epoch = Math.max(walEpoch, epoch);

    return new LsmKvStore(folder, sstables, epoch);
  }

  @Override
  public void close() throws IOException {
    synchronized ( mutex ) {
      if ( closing ) { return; }
      closing = true;
    }
    try {
      if ( memtable.getSizeBytes() > 0 ) { flush(); }
    } catch ( InterruptedException e ) {
      throw new IOException("interrupted while closing", e);
    }
  }

  void flush() throws IOException, InterruptedException {
    doFlush(false);
  }

  private void rotate() throws IOException, InterruptedException {
    if ( closing ) { throw new IllegalStateException("kv store is closed"); }
    doFlush(true);
  }

  private void doFlush(boolean checkSize) throws IOException, InterruptedException {
    synchronized ( mutex ) {
      // wait for current write to finish
      while ( flushingMemtable != null ) { mutex.wait(); }
      if ( checkSize && memtable.getSizeBytes() <= ROTATION_THRESHOLD_BYTES ) { return; }
      // atomic swap
      flushingMemtable = memtable;
      epoch += 2;
      memtable = Memtable.memtable(memtableFolder, epoch); // even epochs on rotation
    }
    if ( flushingMemtable == null ) { return; }
    SSTable sstable = flushingMemtable.flush(sstableFolder(this.memtableFolder));
    this.sstables.add(sstable);

    // wake up waiters
    synchronized ( mutex ) {
      flushingMemtable = null;
      mutex.notifyAll();
    }
    if ( sstables.size() >= COMPACTION_THRESHOLD ) {
      compact();
    }
  }

  @Override
  public void put(String key, String value) throws IOException, InterruptedException {
    if ( closing ) { throw new IllegalStateException("kv store is closed"); }
    int size = memtable.put(key, value);
    if ( size > ROTATION_THRESHOLD_BYTES ) {
      rotate();
    }
  }

  @Override
  public void delete(String key) throws IOException, InterruptedException {
    if ( closing ) { throw new IllegalStateException("kv store is closed"); }
    int size = memtable.delete(key);
    if ( size > ROTATION_THRESHOLD_BYTES ) {
      rotate();
    }
  }

  @Override
  public String get(String key) throws IOException {
    if ( closing ) { throw new IllegalStateException("kv store is closed"); }
    int newestSST;
    synchronized ( mutex ) {
      // 1. memtable
      MemtableValue value = memtable.get(key);
      if ( value != null ) {
        return value.value();
      }
      // 2. flushingMemtable
      value = flushingMemtable != null ? flushingMemtable.get(key) : null;
      if ( value != null ) {
        return value.value();
      }
      newestSST = sstables.size() - 1;
    }
    // 3. sstables from newest to oldest
    for ( int i = newestSST; i >= 0; i-- ) {
      SSTable current = sstables.get(i);
      if ( !current.mayHave(key) ) { continue; }
      MemtableValue value = current.get(key);
      if ( value != null ) {
        return value.value();
      }
    }
    return null;
  }

  private void compact() throws IOException, InterruptedException {
    // TODO: make async
    // compute compacted map
    Map<String, MemtableValue> compacted = new TreeMap<>();
    // oldest to newest, overwrite in compacted, drop tombstones
    for ( SSTable sst : sstables ) {
      try ( SSTableIO.SSTableIterator iterator = sst.iterator() ) {
        while ( iterator.hasNext() ) {
          Map.Entry<String, MemtableValue> entry = iterator.next();
          if ( !entry.getValue().tombstone() ) {
            compacted.put(entry.getKey(), entry.getValue());
          } else {
            compacted.remove(entry.getKey());
          }
        }
      }
    }

    // write the new map into SST epoch = current - 1, preceding the current memtable
    SSTable compactedSst = SSTableIO.write(sstableFolder(memtableFolder), epoch - 1, compacted);

    // atomically swap SSTs
    synchronized ( mutex ) {
      sstables.clear();
      sstables.add(compactedSst);
    }
  }
}
