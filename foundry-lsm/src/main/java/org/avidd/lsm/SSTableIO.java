package org.avidd.lsm;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.avidd.lsm.Memtable.DEL_BYTES;

class SSTableIO {
  private static final MemtableValue TOMBSTONE = new MemtableValue(null, true);
  static final int FOOTER_BYTES = 24;
  static final int SPARSE_INDEX_INTERVAL = 128;

  static SSTableIterator iterator(Path path, long offset, long endOffset) throws IOException {
    return new SSTableIterator(path, offset, endOffset);
  }

  static SSTable write(Path folder, Memtable memtable) throws IOException {
    return write(folder, memtable.epoch, memtable.map);
  }

  static SSTable write(Path folder, int epoch, Map<String, MemtableValue> memtable) throws IOException {
    Path path = folder.resolve(SSTable.toFileName(epoch));
    File file = path.toFile();
    boolean created = file.createNewFile();
    assert created : "file exists already";

    try ( FileOutputStream out = new FileOutputStream(file) ) {
      FileChannel channel = out.getChannel();
      int numRecords = 0;
      long offset = 0;
      // every 128th key -> data offset
      List<SSTable.IndexEntry> sparseIndex = new ArrayList<>();
      int expectedInsertions = Math.max(1, memtable.size());
      BloomFilter bloomFilter = new BloomFilter(expectedInsertions, SSTable.FALSE_POSITIVE_RATE);
      // data [key_size(4), value_size[4], key bytes, value bytes]
      // value_size = -1 -> tombstone
      for ( Map.Entry<String, MemtableValue> entry : memtable.entrySet() ) {
        ByteBuffer buf = encode(entry);
        int written = channel.write(buf);
        assert written == buf.capacity();
        if ( numRecords++ % SPARSE_INDEX_INTERVAL == 0 ) { // check condition before increment to anchor at first entry
          sparseIndex.add(new SSTable.IndexEntry(entry.getKey(), offset));
        }
        offset += buf.capacity();
        bloomFilter.put(entry.getKey());
      }
      final long indexOffset = offset;
      final long bloomOffset = indexOffset + writeSparseIndex(channel, sparseIndex);
      writeBloomFilter(channel, bloomFilter);
      writeFooter(channel, indexOffset, bloomOffset, numRecords);
      channel.force(true);

      return new SSTable(epoch, path, bloomFilter, sparseIndex, indexOffset);
    }
  }

  private static long writeSparseIndex(
    FileChannel channel, List<SSTable.IndexEntry> index) throws IOException {
    ByteBuffer buf = ByteBuffer.allocate(4);
    buf.putInt(index.size());
    buf.flip();
    channel.write(buf);
    long offset = 4;
    for ( SSTable.IndexEntry indexEntry : index ) {
      byte[] key = indexEntry.key().getBytes(StandardCharsets.UTF_8);
      buf = ByteBuffer.allocate( 4 + key.length + 8);
      buf.putInt(key.length);
      buf.put(key);
      buf.putLong(indexEntry.offset());
      buf.flip();
      int written = channel.write(buf);
      assert written == buf.capacity();
      offset += written;
    }
    return offset;
  }

  // bit array
  private static void writeBloomFilter(FileChannel channel, BloomFilter bloom) throws IOException {
    byte[] bloomBytes = bloom.getBytes();
    ByteBuffer buf = ByteBuffer.allocate(8 + bloomBytes.length);
    buf.putInt(bloom.m);
    buf.putInt(bloom.k);
    buf.put(bloomBytes);
    buf.flip();
    int written = channel.write(buf);
    assert written == buf.capacity();
  }

  // footer(24) [index_offset(8), bloom_offset(8), num_records(8)]
  private static void writeFooter(
    FileChannel channel, long indexOffset, long bloomOffset, long numRecords) throws IOException {
    ByteBuffer buf = ByteBuffer.allocate(FOOTER_BYTES);
    buf.putLong(indexOffset);
    buf.putLong(bloomOffset);
    buf.putLong(numRecords);
    buf.flip();
    int written = channel.write(buf);
    assert written == buf.capacity();
  }

  // data [key_size(4), value_size[4], key bytes, value bytes]
  // value_size = -1 -> tombstone
  private static ByteBuffer encode(Map.Entry<String, MemtableValue> entry) {
    byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
    byte[] valBytes = entry.getValue().tombstone() ? DEL_BYTES : entry.getValue().value().getBytes(StandardCharsets.UTF_8);
    int len = 8 + keyBytes.length + valBytes.length;
    ByteBuffer buf = ByteBuffer.allocate(len);
    buf.putInt(keyBytes.length);
    buf.putInt(entry.getValue().tombstone() ? -1 : valBytes.length);
    buf.put(keyBytes);
    buf.put(valBytes);
    buf.flip();
    return buf;
  }

  static Map.Entry<String, MemtableValue> decode(RandomAccessFile in, long end) throws IOException {
    if ( in.getFilePointer() >= end ) {
      return null;
    }
    byte[] intBuf = new byte[4];
    in.readFully(intBuf);
    int keyLen = ByteBuffer.wrap(intBuf).getInt();
    in.readFully(intBuf);
    int valLen = ByteBuffer.wrap(intBuf).getInt();
    byte[] keyBytes = new byte[keyLen];
    in.readFully(keyBytes);
    String key = new String(keyBytes, StandardCharsets.UTF_8);
    if ( valLen < 0 ) {
      return new Map.Entry<>() {
        @Override public String getKey() { return key; }
        @Override public MemtableValue getValue() { return TOMBSTONE; }
        @Override public MemtableValue setValue(MemtableValue value) { throw new UnsupportedOperationException(); }
      };
    } else {
      byte[] valBytes = new byte[valLen];
      in.readFully(valBytes);
      String val = new String(valBytes, StandardCharsets.UTF_8);
      MemtableValue mtv = new MemtableValue(val, false);
      return new Map.Entry<>() {
        @Override public String getKey() { return key; }
        @Override public MemtableValue getValue() { return mtv; }
        @Override public MemtableValue setValue(MemtableValue value) { throw new UnsupportedOperationException(); }
      };
    }
  }

  /**
   * Reads an sstable at the given path with the given epoch
   * @param epoch the epoch of the sst
   * @param path the path to the file
   * @return the parsed SSTable
   * @throws IOException if reading failed
   */
  static SSTable sstable(int epoch, Path path) throws IOException {
    try ( RandomAccessFile file = new RandomAccessFile(path.toFile(), "r") ) {
      FileChannel c = file.getChannel();
      long footerOffset = c.size() - FOOTER_BYTES;
      file.seek(footerOffset);
      byte[] footerBytes = new byte[FOOTER_BYTES];
      file.readFully(footerBytes);
      ByteBuffer buf = ByteBuffer.wrap(footerBytes);
      long indexOffset = buf.getLong();
      long bloomOffset = buf.getLong();
      long numRecords = buf.getLong();

      List<SSTable.IndexEntry> sparseIndex = readIndex(file, indexOffset, numRecords);
      BloomFilter bloom = readBloomFilter(file, bloomOffset, c.size() - bloomOffset - FOOTER_BYTES);

      return new SSTable(epoch, path, bloom, sparseIndex, indexOffset);
    }
  }

  /**
   * Read bloom filter of given length starting at offset in file.
   * @param file the file to read from
   * @param offset the offset where the bloom filter starts
   * @param length the length of the bloom filter in bytes
   * @return the parsed bloom filter
   * @throws IOException if reading fails
   */
  private static BloomFilter readBloomFilter(RandomAccessFile file, long offset, long length) throws IOException {
    file.seek(offset);
    byte[] intBuf = new byte[4];
    file.readFully(intBuf);
    int m = ByteBuffer.wrap(intBuf).getInt();
    file.readFully(intBuf);
    int k = ByteBuffer.wrap(intBuf).getInt();
    int len = (int)length - 8;
    byte[] bytes = new byte[len];
    file.readFully(bytes);
    return new BloomFilter(bytes, m, k);
  }

  private static List<SSTable.IndexEntry> readIndex(RandomAccessFile file, long offset, long numRecords) throws IOException {
    file.seek(offset);
    byte[] intBuf = new byte[4];
    byte[] longBuf = new byte[8];
    file.readFully(intBuf);
    int size = ByteBuffer.wrap(intBuf).getInt();
    List<SSTable.IndexEntry> sparseIndex = new ArrayList<>((int)(numRecords / SPARSE_INDEX_INTERVAL));
    for ( int i = 0; i < size; i++ ) {
      file.readFully(intBuf);
      int keyLen = ByteBuffer.wrap(intBuf).getInt();
      byte[] keyBytes = new byte[keyLen];
      file.readFully(keyBytes);
      String key = new String(keyBytes, StandardCharsets.UTF_8);
      file.readFully(longBuf);
      long keyOffset = ByteBuffer.wrap(longBuf).getLong();
      sparseIndex.add(new SSTable.IndexEntry(key, keyOffset));
    }
    return sparseIndex;
  }

  static class SSTableIterator implements Closeable {
    private final RandomAccessFile raf;
    private final long endOffset;
    private Map.Entry<String, MemtableValue> next;

    private SSTableIterator(Path path, long offset, long endOffset) throws IOException {
      assert offset >= 0;
      assert ( endOffset > offset );
      raf = new RandomAccessFile(path.toFile(), "r");
      this.endOffset = endOffset;
      raf.seek(offset);
      next = SSTableIO.decode(raf, this.endOffset);
    }

    @Override
    public void close() throws IOException {
      raf.close();
    }

    public boolean hasNext() {
      return next != null;
    }

    public Map.Entry<String, MemtableValue> next() throws IOException {
      if (!hasNext()) { throw new IllegalStateException(); }
      Map.Entry<String, MemtableValue> entry = next;
      next = SSTableIO.decode(raf, endOffset);
      return entry;
    }
  }
}
