// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.kvstore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.avidd.foundry.storage.WriteAheadLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompactionWatcher implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(CompactionWatcher.class);
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final WriteAheadLog wal;
  private final long maxLogSizeBytes;

  public CompactionWatcher(WriteAheadLog wal, long maxLogSizeBytes) {
    this.wal = wal;
    this.maxLogSizeBytes = maxLogSizeBytes;
  }

  @Override
  public void close() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public CompactionWatcher start() {
    executor.scheduleAtFixedRate(new Watcher(), 5000, 5000, TimeUnit.MILLISECONDS);
    return this;
  }

  private class Watcher implements Runnable {
    @Override
    public void run() {
      // TODO: better just let the wal rotate on file size exceeding!
      String logFileName = wal.determineLogFileName();
      File logFile = new File(logFileName);
      if ( logFile.exists() &&
           logFile.length() >= CompactionWatcher.this.maxLogSizeBytes ) {
        try {
          wal.rotate();
        } catch ( IOException e ) {
          logger.error("log rotation failed", e);
        }
      }
    }
  }
}
