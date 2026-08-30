// SPDX-License-Identifier: Apache-2.0

package org.avidd.bitcask;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.avidd.kvstore.KVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class BitcaskKVStoreTest {
  private static final Path LOG_DIR = Path.of("src", "test", "resources");

  @BeforeEach
  @AfterEach
  public void afterEach() {
    if ( LOG_DIR.toFile().exists() ) {
      delete(LOG_DIR.toFile());
    }
  }

  private static void delete(File f) {
    if ( f.isDirectory() ) {
      for ( File file : f.listFiles() ) {
        delete(file);
      }
    }
    f.delete();
  }

  @Test
  public void testPutGet() throws IOException, InterruptedException {
    // given
    assertThat(LOG_DIR.toFile().exists(), is(false));
    KVStore kvs = new BitcaskKVStore(LOG_DIR);
    assertThat(LOG_DIR.toFile().exists(), is(true));
    assertThat(LOG_DIR.toFile().isDirectory(), is(true));
    assertThat(LOG_DIR.toFile().list().length, is(1));

    // when
    kvs.put("key-0", "value-0");
    kvs.put("key-1", "value-1");

    assertThat(kvs.get("key-0"), is("value-0"));
    assertThat(kvs.get("key-1"), is("value-1"));

    kvs.delete("key-0");
    kvs.put("key-1", "value-1'");

    assertThat(kvs.get("key-0"), is(nullValue()));
    assertThat(kvs.get("key-1"), is("value-1'"));
  }

  @Test
  public void testRecover() throws IOException, InterruptedException {
    // given
    assertThat(LOG_DIR.toFile().exists(), is(false));
    KVStore kvs = new BitcaskKVStore(LOG_DIR);
    kvs.put("key-0", "value-0");
    kvs.put("key-1", "value-1");
    kvs.put("key-2", "value-2");
    kvs.delete("key-0");
    kvs.put("key-2", "value-2'");
    assertThat(kvs.get("key-0"), is(nullValue()));
    assertThat(kvs.get("key-1"), is("value-1"));
    assertThat(kvs.get("key-2"), is("value-2'"));

    kvs = new BitcaskKVStore(LOG_DIR);
    assertThat(kvs.get("key-0"), is(nullValue()));
    assertThat(kvs.get("key-1"), is("value-1"));
    assertThat(kvs.get("key-2"), is("value-2'"));
  }
}
