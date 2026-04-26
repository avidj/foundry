package org.avidd.storage;

import java.io.IOException;
import java.util.Map;

/**
 *
 * @author david
 */
public class BinaryWriteAheadLog implements WriteAheadLog {

  @Override
  public void append(String key, String value) {
    // escape nl and : in key and value
    // fsync
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void delete(String key) {
    // append escape(key):/0 as tombstone
    // fsync
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void close() throws Exception {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Map<String, String> recover() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public void rotate() throws IOException {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public String determineLogFileName() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

}
