package org.avidd.lsm;

import org.avidd.kvstore.KVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class LsmKvStore implements KVStore, Closeable {
  private static final Logger logger = LoggerFactory.getLogger(LsmKvStore.class);
  private static final long ROTATION_THRESHOLD_BYTES = 4 * 1024 * 1024;
  private static final int COMPACTION_THRESHOLD = 4;
  private static final String SSTABLES = "sstables";
  private final Path memtableFolder;
  private final List<SSTable> sstables;
  private final Object mutex = new Object();
  private int epoch;
  private volatile Memtable memtable;
  private volatile Memtable flushingMemtable;
  private volatile boolean closing;
  private boolean compacting = false;
  // Injected by tests to block between memtable swap and SSTable IO — null in production
  volatile Runnable postSwapHook = null;

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
      if ( memtable.getSizeBytes() == 0 ) { return; }
      // atomic swap
      flushingMemtable = memtable;
      epoch += 2;
      memtable = Memtable.memtable(memtableFolder, epoch); // even epochs on rotation
    }
    if ( postSwapHook != null ) { postSwapHook.run(); }
    SSTable sstable = flushingMemtable.flush(sstableFolder(this.memtableFolder));

    synchronized ( mutex ) {
      this.sstables.add(sstable);
      flushingMemtable = null;
      // wake up waiters
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
    List<SSTable> toGetFrom = null;
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
      toGetFrom = new ArrayList<>(sstables);
      newestSST = toGetFrom.size() - 1;
    }
    // 3. sstables from newest to oldest
    for ( int i = newestSST; i >= 0; i-- ) {
      SSTable current = toGetFrom.get(i);
      if ( !current.mayHave(key) ) { continue; }
      MemtableValue value = current.get(key);
      if ( value != null ) {
        return value.value();
      }
    }
    return null;
  }

  @Override
  public void compact() throws IOException, InterruptedException {
    synchronized ( mutex ) {
      if ( compacting ) {
        return;
      }
      compacting = true;
    }
    try {
      List<SSTable> toCompact;
      int compactEpoch;
      synchronized (  mutex ) {
        toCompact = new ArrayList<>(sstables);
        compactEpoch = epoch -1;
      }
      // TODO: make async
      // compute compacted map
      Map<String, MemtableValue> compacted = new TreeMap<>();
      // oldest to newest, overwrite in compacted, drop tombstones
      for ( SSTable sst : toCompact ) {
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
      SSTable compactedSst = SSTableIO.write(sstableFolder(memtableFolder), compactEpoch, compacted);

      // atomically swap SSTs
      synchronized ( mutex ) {
        sstables.removeAll(toCompact);
        sstables.add(0, compactedSst); // add as smallest to precede concurrent flushes
      }
      for ( SSTable sst : toCompact ) {
        boolean deleted = sst.path.toFile().delete();
        if ( !deleted ) {
          logger.warn("File '" + sst.path + "' was not deleted after compaction, resource leak.");
        }
      }
    } finally {
      synchronized ( mutex ) {
        compacting = false;
      }
    }
  }
}
