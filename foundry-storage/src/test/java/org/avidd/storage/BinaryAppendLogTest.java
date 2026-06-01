package org.avidd.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class BinaryAppendLogTest {
  private static final PayloadCodec<String> codec = new StringCodec();
  private static final Path path = Path.of("test", "resources", "binary-log", "log.bal");

  @BeforeEach
  public void beforeEach() throws IOException {
    path.getParent().toFile().mkdirs();
  }

  @AfterEach
  public void afterEach() throws IOException {
    final File file = path.toFile();
    if ( file.exists() ) {
      file.delete();
    }
  }

  @Test
  public void testAppend() throws Exception {
    try ( BinaryAppendLog<String> bal = new BinaryAppendLog<>(path, codec) ) {
      long size = bal.size();
      assertThat(size, is(0L));

      bal.append("Hello");
      assertThat(bal.size(), is(greaterThan(size)));
      size = bal.size();
      bal.fsync();

      bal.append("World");
      assertThat(bal.size(), is(greaterThan(size)));
      size = bal.size();
      bal.fsync();

      bal.append("Goodbye");
      assertThat(bal.size(), is(greaterThan(size)));
      size = bal.size();

      bal.append("Universe");
      assertThat(bal.size(), is(greaterThan(size)));
      bal.fsync();

      try ( BinaryAppendLog.CloseableIterator<Frame<String>> iter = bal.iterator() ) {
        StringBuilder s = new StringBuilder();
        while ( iter.hasNext() ) {
          s.append(iter.next().payload());
        }
        assertThat(s.toString(), is(equalTo("HelloWorldGoodbyeUniverse")));
      }
    }
  }

  @Test
  public void testRecover() throws IOException {
    try ( BinaryAppendLog<String> bal = new BinaryAppendLog<>(path, codec) ) {
      bal.append("Hello");
      bal.append("World");
      bal.append("Goodbye");
      bal.append("Universe");
      bal.fsync();
    }
    corruptFile(path, "HelloWorld".getBytes().length + 2 * 8 + 3);

    try ( BinaryAppendLog<String> bal = new BinaryAppendLog<>(path, codec) ) {
      try ( BinaryAppendLog.CloseableIterator<Frame<String>> iter = bal.iterator() ) {
        StringBuilder s = new StringBuilder();
        while ( iter.hasNext() ) {
          s.append(iter.next().payload());
        }
        assertThat(s.toString(), is(equalTo("HelloWorld")));
      }
    }
  }

  private void corruptFile(Path path, int at) throws IOException {
    try ( RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw") ) {
      raf.seek(at);
      raf.write(new byte[]{0x00, 0x00, 0x00, 0x00});
    }
  }

  private static class StringCodec implements PayloadCodec<String> {
    private static final Charset CHARSET = Charset.forName("UTF-8");

    @Override
    public byte[] encode(String value) throws IOException {
      return value.getBytes(CHARSET);
    }

    @Override
    public String decode(byte[] bytes) throws IOException {
      return new String(bytes, 0, bytes.length, CHARSET);
    }
  }
}