package org.avidd.storage;

import java.io.IOException;
import java.util.Map;

/**
 *
 * @author david
 */
public interface WriteAheadLog extends AutoCloseable {

  public void append(String key, String value) throws IOException ;

  public void delete(String key) throws IOException;

  public Map<String, String> recover() throws IOException;

  public void rotate() throws IOException;

  public String determineLogFileName();
}
