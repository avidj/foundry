package org.avidd.bitcask;

import org.avidd.kvstore.KVStore;
import org.avidd.storage.BinaryAppendLog;
import org.avidd.storage.Frame;
import org.avidd.storage.PayloadCodec;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * This is another single node KV store.
 * It works similar to the {@code org.avidd.kvstore.SingleNodeKVStore} but
 * - uses a BinaryAppendLog
 * - an index from key to BAL file
 */
public class BitcaskKVStore implements KVStore {
  private static final String FILE_EXT = ".bitcask";
  private static final int ROTATION_THRESHOLD = 1024 * 1024 * 64; //64MB
  private static final Charset CHARSET = StandardCharsets.UTF_8;
  private final Path folder;
  private final KeyDir keyDir = new KeyDir();
  private final PayloadCodec<Record> codec = new RecordCodec();
  private final Object mutex = new Object();
  private long activeFileId;
  private BinaryAppendLog<Record> activeFile;

  public BitcaskKVStore(Path folder) throws IOException {
    folder.toFile().mkdirs();
    if ( !folder.toFile().isDirectory() ) {
      throw new IllegalArgumentException("folder must be a directory");
    }
    this.folder = folder;
    recover();
  }

  private Path fromFileId(long fileId) {
    return Path.of(folder.toString(), fileId + FILE_EXT);
  }

  private static long toFileId(File file) {
    String name = file.getName();
    String active = name.substring(0, name.length() - FILE_EXT.length());
    return Long.parseLong(active);
  }

  private File toFile(long fileId) {
    return Paths.get(folder.toString(), fileId + FILE_EXT).toFile();
  }

  private static int logFileComparator(File f1, File f2) {
    return Long.compare(toFileId(f1), toFileId(f2));
  }

  private void recover() throws IOException {
    folder.toFile().mkdirs();
    File[] files = folder.toFile().listFiles((dir, name) -> name.endsWith(FILE_EXT));
    if ( files == null ) {
      throw new IOException("cannot list folder");
    }
    Arrays.sort(files, BitcaskKVStore::logFileComparator);
    for ( File file : files ) {
      BinaryAppendLog<Record> bal = new BinaryAppendLog<>(file.toPath(), codec);
      try ( BinaryAppendLog.CloseableIterator<Frame<Record>> iter = bal.iterator() ) {
        while ( iter.hasNext() ) {
          Frame<Record> frame = iter.next();
          Record r = frame.payload();
          if ( r.tombstone() ) {
            keyDir.remove(new String(r.key(), CHARSET));
          } else {
            long valueOffset = frame.offset() + Record.HEADER_BYTES + r.key().length;
            keyDir.put(new String(r.key(), CHARSET),
              new KeyDir.Entry(toFileId(file), valueOffset, r.value().length, r.timestamp()));
          }
        }
      } finally {
        bal.close();
      }
    }
    if ( files.length > 0 ) {
      File active = files[files.length - 1];
      activeFileId = toFileId(active);
      activeFile = new BinaryAppendLog<>(active.toPath(), codec);
    } else {
      activeFileId = 0;
      rotate(); // rotate to create first file
    }
  }

  @Override
  public void put(String key, String value) throws IOException {
    long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    byte[] keyBytes = key.getBytes(CHARSET);
    byte[] valBytes = value.getBytes(CHARSET);
    Record r = new Record(now, keyBytes, valBytes, false);
    synchronized ( mutex ) {
      long payloadOffset = activeFile.append(r);
      activeFile.fsync();
      long valueOffset = payloadOffset + Record.HEADER_BYTES + keyBytes.length;
      keyDir.put(key, new KeyDir.Entry(activeFileId, valueOffset, valBytes.length, now));
      if ( activeFile.size() > ROTATION_THRESHOLD ) {
        rotate();
      }
    }
  }

  @Override
  public String get(String key) {
    KeyDir.Entry entry = keyDir.get(key);
    if ( entry == null ) {
      return null;
    }
    File file = toFile(entry.fileId());
    byte[] buf = new byte[entry.valueSize()];
    try ( RandomAccessFile raf = new RandomAccessFile(file, "r") ) {
      raf.seek(entry.valueOffset());
      raf.readFully(buf);
    } catch ( IOException e ) {
      throw new RuntimeException(e);
    }
    return new String(buf, CHARSET);
  }

  @Override
  public void delete(String key) throws IOException {
    long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    byte[] keyBytes = key.getBytes(CHARSET);
    byte[] valBytes = new byte[0];
    Record tombstone = new Record(now, keyBytes, valBytes, true);
    synchronized ( mutex ) {
      activeFile.append(tombstone);
      activeFile.fsync();
      keyDir.remove(key);
      if ( activeFile.size() > ROTATION_THRESHOLD ) {
        rotate();
      }
    }
  }

  @Override
  public void compact() throws IOException {
    rotate();
  }

  private void rotate() throws IOException {
    synchronized ( mutex ) {
      if ( activeFile != null ) {
        activeFile.fsync();
        activeFile.close();
      }
      activeFileId += 1;
      activeFile = new BinaryAppendLog<>(fromFileId(activeFileId), codec);
    }
  }
}
