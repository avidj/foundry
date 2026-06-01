package org.avidd.bitcask;

import org.avidd.kvstore.KVStore;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class BitcaskKVStore implements KVStore {
  private static final int ROTATION_THRESHOLD = 1024 * 1024 * 64; //64MB
  private static final Charset CHARSET = Charset.forName("UTF-8");
  private final Path folder;
  private final KeyDir keyDir = new KeyDir();

  public BitcaskKVStore(Path folder) {
    this.folder = folder;
  }

  @Override
  public void put(String key, String value) throws IOException {
      /*
    long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    byte[] keyBytes = bytesFrom(key);
    byte[] valBytes = bytesFrom(value);
    Record record = new Record(now, keyBytes, valBytes, false);
    long payloadOffset = activeFile.append(record);
    activeFile.fsync();
    long valueOffset = payloadOffset + Record.HEADER_BYTES + keyBytes.length;
    keyDir.put(key, new Entry(activeFileId, valueOffset, valBytes.length, now));
    if ( activeFile.size() > ROTATION_THRESHOLD ) {
      rotate();
    }
       */
  }

  private byte[] bytesFrom(String s) {
    /*
    int len = s != null ? s.length() * 2;
    ByteBuffer buf = ByteBuffer.allocate(len);
    for ( char c : s.toCharArray() ) {
      buf.put((byte) c);
    }
    buf.flip();
    return buf.array();
    */
    throw new UnsupportedOperationException();
  }

  private String fromBytes(byte[] bytes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String get(String key) {
    // read location from keyDir
    // obtain record from correct file
    // return value bytes as string
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(String key) throws IOException {
    /*
    Record tombstone = new Record(
      TimeUnit.NANOSECONDS.toMillis(System.nanoTime()),
      key,
      new byte[0],
      true
    );
    long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    byte[] keyBytes = bytesFrom(key);
    byte[] valBytes = new byte[0];
    Record record = new Record(now, keyBytes, valBytes, true);
    long payloadOffset = activeFile.append(record);
    activeFile.fsync();
    keyDir.remove(key);
    if ( activeFile.size() > ROTATION_THRESHOLD ) {
      rotate();
    }
     */
  }

  private void rotate() throws IOException {
    throw new UnsupportedOperationException();
  }
}
