package org.avidd.kvstore;

import java.io.IOException;

/**
 *
 * @author david
 */
public interface KVStore {

  public void put(String key, String value) throws IOException ;

  public String get(String key);

  public void delete(String key) throws IOException;

}
