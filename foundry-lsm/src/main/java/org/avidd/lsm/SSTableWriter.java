package org.avidd.lsm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.avidd.lsm.Memtable.DEL_BYTES;

class SSTableWriter {
  static void write(Path folder, Memtable memtable) throws IOException {
    Path path = folder.resolve(SSTable.toFileName(memtable.epoch));
    File file = path.toFile();
    boolean created = file.createNewFile();
    assert created : "file exists already";

    try ( FileOutputStream out = new FileOutputStream(file) ) {
      FileChannel channel = out.getChannel();
      int numRecords = 0;
      long offset = 0;
      // every 128th key -> data offset
      List<SSTable.IndexEntry> sparseIndex = new ArrayList<>();
      int expectedInsertions = Math.max(1, memtable.map.size());
      BloomFilter bloomFilter = new BloomFilter(expectedInsertions, 0.01);
      // data [key_size(4), value_size[4], key bytes, value bytes]
      // value_size = -1 -> tombstone
      for ( Map.Entry<String, MemtableValue> entry : memtable.map.entrySet() ) {
        ByteBuffer buf = encode(entry);
        int written = channel.write(buf);
        assert written == buf.capacity();
        if ( numRecords++ % 128 == 0 ) { // check condition before increment to anchor at first entry
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
    ByteBuffer buf = ByteBuffer.allocate(24);
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
}
