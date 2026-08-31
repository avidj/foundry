// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.kvstore;

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
