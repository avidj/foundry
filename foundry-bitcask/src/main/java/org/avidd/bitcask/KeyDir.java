package org.avidd.bitcask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyDir {
  private final Map<String, Entry> map = new ConcurrentHashMap<>();
  public record Entry(long fileId, long valueOffset, int valueSize, long timestamp) {}

  public void put(String key, Entry entry) {
    this.map.put(key, entry);
  }

  public Entry get(String key) {
    return this.map.get(key);
  }

  public boolean remove(String key) {
    return this.map.remove(key) != null;
  }
}
