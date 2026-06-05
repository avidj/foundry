package org.avidd.kvstore;

import java.io.IOException;
import java.util.Map;
import org.avidd.storage.StringWriteAheadLog;
import org.avidd.storage.WriteAheadLog;

/**
 * This simple KVStore uses a hash map to keep the entire data in memory.
 * Every operation is written to a string-based WAL which is compacted once it reaches 1MB.
 * Compaction means:
 * - rotate the WAL file, so operation can continue
 * - replay the old WAL into a map
 * - write the resulting map (a snapshot) into a new WAL (only inserts)
 * Deletions are tombstoned.
 * On recovery / restart: replay the latest snapshot and the latest WAL
 * On write: append, fsync (inside the WAL), mutate memtable
 * @author david
 */
public class SingleNodeKVStore implements KVStore, AutoCloseable {
  public static final long MB = 1024 * 1024;
  private static final long MAX_LOG_SIZE_BYTES = MB;
  private final Map<String, String> memTable;
  private final WriteAheadLog wal;
  private final CompactionWatcher compactor;
  // do the string version first, then do the binary version

  public SingleNodeKVStore() throws IOException {
    final String path = "dummy";
    // create WAL with path
    wal = new StringWriteAheadLog(path);
    // trigger recovery on startup
    memTable = wal.recover();
    // start scheduled CompactionWatcher
    compactor = new CompactionWatcher(wal, MAX_LOG_SIZE_BYTES).start();
  }

  @Override
  public void close() {
    try {
      wal.close();
    } catch ( Exception e) {
      // log execption
    }
    try {
      compactor.close();
    } catch ( Exception e ) {
      // log exception
    }
  }

  @Override
  public void put(String key, String value) throws IOException {
    synchronized ( this ) { // add striping later
      wal.append(key, value);
      memTable.put(key, value);
    }
  }

  @Override
  public String get(String key) {
    synchronized ( this ) {
      return memTable.get(key);
    }
  }

  @Override
  public void delete(String key) throws IOException {
    synchronized ( this ) {
      wal.delete(key);
      memTable.remove(key);
    }
  }

  @Override
  public void compact() throws IOException {
    wal.rotate();
  }
}
