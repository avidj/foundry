// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.storage;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public interface WriteAheadLog extends Closeable {

  public void append(String key, String value) throws IOException ;

  public void delete(String key) throws IOException;

  public Map<String, String> recover() throws IOException;

  public void rotate() throws IOException;

  public String determineLogFileName();
}
