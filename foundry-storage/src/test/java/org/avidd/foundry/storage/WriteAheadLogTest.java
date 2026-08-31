// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public abstract class WriteAheadLogTest {
  static final Path TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));
  static final File WAL_DIR = new File(TMP_DIR.toFile(), "wal");
  private WriteAheadLog wal;

  protected abstract WriteAheadLog createWal();

  @BeforeAll
  public static void beforeAll() throws IOException {
  }

  @BeforeEach
  public void beforeEach() throws IOException {
    Path walDir = Paths.get(WAL_DIR.toURI());
    Files.createDirectories(walDir);
    try (Stream<Path> paths = Files.walk(walDir)) {
      paths.sorted(Comparator.reverseOrder())
              .map(Path::toFile)
              .forEach(File::delete);
    }
    WAL_DIR.mkdir();
    wal = createWal();
  }

  @AfterEach
  public void afterEach() throws Exception {
    wal.close();
  }

  private Map<String, String> createTestWrites(int numEntries) {
    Map<String, String> testEntries = new HashMap<>();
    for ( int i = 0; i < numEntries; i++ ) {
      testEntries.put("key-" + i, "value-" + i);
    }
    return testEntries;
  }

  @Test
  public void testAppendSingleEntry() throws IOException {
    Map<String, String> testValues = createTestWrites(1);
    wal.recover();
    assertThat(WAL_DIR.list().length, is(1));

    for ( Map.Entry<String, String> testEntry : testValues.entrySet() ) {
      wal.append(testEntry.getKey(), testEntry.getValue());
      assertThat(WAL_DIR.list().length, is(1));
      File currentLog = new File(WAL_DIR, WAL_DIR.list()[0]);
      assertThat(currentLog.exists(), is(true));
      assertThat(currentLog.length(), is(greaterThan(0L)));
    }

    // Use WAL recovery to read the current state for assertions
    WriteAheadLog anotherWal = createWal();
    Map<String, String> recovered = anotherWal.recover();

    assertThat(recovered, is(equalTo(testValues)));
  }

  @Test
  public void testAppendTwoEntries() throws IOException {
    Map<String, String> testValues = createTestWrites(2);
    wal.recover();
    assertThat(WAL_DIR.list().length, is(1));

    for ( Map.Entry<String, String> testEntry : testValues.entrySet() ) {
      wal.append(testEntry.getKey(), testEntry.getValue());
      assertThat(WAL_DIR.list().length, is(1));
      File currentLog = new File(WAL_DIR, WAL_DIR.list()[0]);
      assertThat(currentLog.exists(), is(true));
      assertThat(currentLog.length(), is(greaterThan(0L)));
    }

    // Use WAL recovery to read the current state for assertions
    WriteAheadLog anotherWal = createWal();
    Map<String, String> recovered = anotherWal.recover();

    assertThat(recovered, is(equalTo(testValues)));
  }

  @Test
  public void testDeleteEntry() throws IOException, Exception {
    Map<String, String> testValues = createTestWrites(2);
    wal.recover();
    assertThat(WAL_DIR.list().length, is(1));

    for ( Map.Entry<String, String> testEntry : testValues.entrySet() ) {
      wal.append(testEntry.getKey(), testEntry.getValue());
      assertThat(WAL_DIR.list().length, is(1));
      File currentLog = new File(WAL_DIR, WAL_DIR.list()[0]);
      assertThat(currentLog.exists(), is(true));
      assertThat(currentLog.length(), is(greaterThan(0L)));
    }

    // Use WAL recovery to read the current state for assertions
    wal.close();
    Map<String, String> recovered = wal.recover();
    assertThat(recovered, is(equalTo(testValues)));

    wal.delete("key-1");
    testValues.remove("key-1");
    wal.close();
    recovered = wal.recover();
    assertThat(recovered, is(equalTo(testValues)));
  }

  @Test
  public void testRotatePreservesData() throws Exception {
    Map<String, String> testValues = createTestWrites(5);
    wal.recover();
    for ( Map.Entry<String, String> entry : testValues.entrySet() ) {
      wal.append(entry.getKey(), entry.getValue());
    }

    wal.rotate();

    // snapshot + new log
    String[] snaps = WAL_DIR.list((dir, name) -> name.endsWith(".wal.snap"));
    assertThat(snaps.length, is(1));

    // old log should be deleted
    String[] logs = WAL_DIR.list((dir, name) -> name.endsWith(".wal.log"));
    assertThat(logs.length, is(1));

    // recovery from snapshot should contain all entries
    WriteAheadLog freshWal = createWal();
    Map<String, String> recovered = freshWal.recover();
    assertThat(recovered, is(equalTo(testValues)));
  }

  @Test
  public void testWriteAfterRotate() throws Exception {
    Map<String, String> beforeRotate = createTestWrites(3);
    wal.recover();
    for ( Map.Entry<String, String> entry : beforeRotate.entrySet() ) {
      wal.append(entry.getKey(), entry.getValue());
    }

    wal.rotate();

    // write more entries after rotation
    wal.append("after-key", "after-value");

    Map<String, String> expected = new HashMap<>(beforeRotate);
    expected.put("after-key", "after-value");

    WriteAheadLog freshWal = createWal();
    Map<String, String> recovered = freshWal.recover();
    assertThat(recovered, is(equalTo(expected)));
  }

  @Test
  public void testDoubleRotate() throws Exception {
    wal.recover();

    // first batch
    wal.append("k1", "v1");
    wal.append("k2", "v2");
    wal.rotate();

    // second batch
    wal.append("k3", "v3");
    wal.rotate();

    // only the latest snapshot and log should remain
    String[] snaps = WAL_DIR.list((dir, name) -> name.endsWith(".wal.snap"));
    assertThat(snaps.length, is(1));

    WriteAheadLog freshWal = createWal();
    Map<String, String> recovered = freshWal.recover();
    assertThat(recovered, is(equalTo(Map.of("k1", "v1", "k2", "v2", "k3", "v3"))));
  }

  @Test
  public void testRotateWithDeletes() throws Exception {
    wal.recover();
    wal.append("k1", "v1");
    wal.append("k2", "v2");
    wal.delete("k1");

    wal.rotate();

    WriteAheadLog freshWal = createWal();
    Map<String, String> recovered = freshWal.recover();
    assertThat(recovered, is(equalTo(Map.of("k2", "v2"))));
  }

  @Test
  public void testEscaping() throws Exception {
    Map<String, String> testValues = Map.of(":-key-1", ":\\\n");
    wal.recover();
    assertThat(WAL_DIR.list().length, is(1));

    for ( Map.Entry<String, String> testEntry : testValues.entrySet() ) {
      wal.append(testEntry.getKey(), testEntry.getValue());
      assertThat(WAL_DIR.list().length, is(1));
      File currentLog = new File(WAL_DIR, WAL_DIR.list()[0]);
      assertThat(currentLog.exists(), is(true));
      assertThat(currentLog.length(), is(greaterThan(0L)));
    }

    // Use WAL recovery to read the current state for assertions
    wal.close();
    Map<String, String> recovered = wal.recover();
    assertThat(recovered, is(equalTo(testValues)));
  }
}
