package org.avidd.storage;

/**
 *
 * @author david
 */
public class StringWriteAheadLogTest extends WriteAheadLogTest {
  private static final long MB = 1024 * 1024;

  @Override
  protected WriteAheadLog createWal() {
    return new StringWriteAheadLog(WAL_DIR.getAbsolutePath(), MB);
  }
}
