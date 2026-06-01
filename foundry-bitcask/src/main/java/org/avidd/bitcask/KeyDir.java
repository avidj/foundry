package org.avidd.bitcask;

import java.util.HashMap;
import java.util.Map;

public class KeyDir {
  private final Map<String, Entry> map = new HashMap<>();

  public record Entry(long fileId, long valueOffset, int valueSize, long timestamp) {}
}
