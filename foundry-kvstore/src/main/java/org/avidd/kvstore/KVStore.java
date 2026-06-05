package org.avidd.kvstore;

import java.io.IOException;

/**
 *
 * @author david
 */
public interface KVStore {

  void put(String key, String value) throws IOException, InterruptedException ;

  String get(String key) throws IOException;

  void delete(String key) throws IOException, InterruptedException;

  void compact() throws IOException, InterruptedException;
}
