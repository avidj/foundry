// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.storage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.zip.CRC32;

public final class BinaryAppendLog<T> implements Closeable {
  private static final int HEADER_LENGTH = 8; // CRC(4) + payload-length(4)
  private final Path path;
  private final PayloadCodec<T> codec;
  private final FileChannel channel;
  private final Object mutex = new Object();
  private boolean recovered;
  private volatile long size = 0L;

  public BinaryAppendLog(Path path, PayloadCodec<T> codec) throws IOException {
    this.path = path;
    this.codec = codec;

    final File file = path.toFile();
    final boolean isNew = !file.exists();
    if ( isNew ) {
      file.getParentFile().mkdirs();
      file.createNewFile();
    }
    channel = createChannel();

    if (isNew) {
      recovered = true;
    } else {
      recover();
    }
  }

  /**
   * @param value record to append
   * @return absolute offset of payload bytes within the file
   * @throws IOException if the write fails
   */
  public long append(T value) throws IOException {
    checkIsOpen();
    synchronized ( mutex ) {
      final long offset = size;
      byte[] payload = codec.encode(value);
      CRC32 crc = new CRC32();
      crc.update(payload);

      ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH + payload.length);
      buf.putInt((int)crc.getValue());
      buf.putInt(payload.length);
      buf.put(payload);
      buf.flip();
      channel.write(buf);
      size += buf.capacity(); // CRC(4) + PAYLOAD_SIZE(4) + PAYLOAD
      return offset + HEADER_LENGTH;
    }
  }

  private void checkIsOpen() throws IllegalStateException {
    if ( !recovered ) {
      throw new IllegalStateException("binary log is closed, must recover()");
    }
  }

  public boolean delete() {
    return this.path.toFile().delete();
  }

  public void fsync() throws IOException {
    channel.force(true);
  }

  public long size() {
    return size;
  }

  public long recover() throws IOException {
    long validEnd = 0;
    synchronized ( mutex ) {
      try ( FileChannel rc = FileChannel.open(path, StandardOpenOption.READ) ) {
        ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH);
        while ( true ) {
          header.clear();
          if ( rc.read(header) < 8 ) {
            break;
          }
          header.flip();
          final int storedCrc = header.getInt();
          final int payloadLen = header.getInt();
          if ( payloadLen < 0 ) {
            break;
          }
          ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
          if ( rc.read(payloadBuf) < payloadLen ) {
            break;
          };
          payloadBuf.flip();
          byte[] payload = payloadBuf.array();
          CRC32 crc = new CRC32();
          crc.update(payload);

          if ( (int)crc.getValue() != storedCrc ) {
            break; // bad CRC = torn write
          }
          validEnd += 8 + payloadLen;
        }
        channel.truncate(validEnd);
        size = validEnd;
      }
      recovered = true;
    }
    return size;
  }

  public CloseableIterator<Frame<T>> iterator() throws IOException {
    return new BinaryAppendLogIterator();
  }

  private FileChannel createChannel() throws IOException {
    return FileChannel.open(path,
      StandardOpenOption.WRITE,
      StandardOpenOption.APPEND,
      StandardOpenOption.CREATE);
  }

  @Override
  public void close() throws IOException {
    synchronized ( mutex ) {
      this.channel.close();
      this.recovered = false;
    }
  }

  public interface CloseableIterator<T> extends Iterator<T>, Closeable
  {}

  private class BinaryAppendLogIterator implements CloseableIterator<Frame<T>> {
    private final Object mutex = new Object();
    private final FileChannel rc;
    private final ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH);
    private long read = 0L;

    private BinaryAppendLogIterator() throws IOException {
      this.rc = FileChannel.open(BinaryAppendLog.this.path, StandardOpenOption.READ);
    }

    @Override
    public void close() throws IOException {
      this.rc.close();
    }

    private Frame<T> readNext() throws IOException {
      synchronized ( mutex ) {
        final long offset = read + HEADER_LENGTH;
        if ( rc.read(header) < HEADER_LENGTH ) {
          throw new IOException(String.format("corrupt frame at offset %d", offset));
        }
        header.flip();
        final int storedCrc = header.getInt();
        final int payloadLen = header.getInt();
        header.clear();

        ByteBuffer payloadBuf = ByteBuffer.allocate(payloadLen);
        if ( rc.read(payloadBuf) < payloadLen ) {
          throw new IOException(String.format("corrupt frame at offset %d", offset));
        }
        byte[] bytes = payloadBuf.array();
        payloadBuf.clear();

        final CRC32 crc = new CRC32();
        crc.update(bytes);
        if ( (int)crc.getValue() != storedCrc ) {
          throw new IOException(String.format("corrupt frame at offset %d", offset));
        }

        T payload = BinaryAppendLog.this.codec.decode(bytes);
        read += HEADER_LENGTH + bytes.length;
        return new Frame<>(offset, payload, bytes);
      }
    }

    @Override
    public boolean hasNext() {
      return read < BinaryAppendLog.this.size;
    }

    @Override
    public Frame<T> next() throws RuntimeException {
      try {
        return readNext();
      } catch ( IOException e ) {
        throw new RuntimeException(e);
      }
    }
  }
}
