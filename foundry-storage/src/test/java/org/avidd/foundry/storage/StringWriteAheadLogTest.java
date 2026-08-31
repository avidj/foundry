// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.storage;

public class StringWriteAheadLogTest extends WriteAheadLogTest {
  @Override
  protected WriteAheadLog createWal() {
    return new StringWriteAheadLog(WAL_DIR.getAbsolutePath());
  }
}
