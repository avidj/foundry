// SPDX-License-Identifier: Apache-2.0

package org.avidd.lsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.zip.CRC32;

import static org.avidd.lsm.LsmKvStore.lsmKvStore;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LsmKvStoreTest {

    // EC-KEY-03: long multi-byte UTF-8 key (each Japanese char = 3 bytes → 15 bytes × 4 = 60 bytes)
    private static final String UTF8_KEY = "キーワード".repeat(4);
    // EC-VAL-03: long value > 1000 chars
    private static final String LONG_VALUE = "x".repeat(1024);

    // ---- PUT (EC-PUT-*) ----

    @Test
    void put_new_key_get_returns_value(@TempDir Path tempDir) throws Exception {
        // TC-PUT-01: EC-PUT-01, EC-KEY-01, EC-VAL-01
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
            assertThat(store.get("k"), is("v"));
        }
    }

    @Test
    void put_overwrite_get_returns_latest(@TempDir Path tempDir) throws Exception {
        // TC-PUT-02: EC-PUT-02, EC-KEY-02, EC-VAL-02
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("hello_world_key", "v1");
            store.put("hello_world_key", "v2");
            assertThat(store.get("hello_world_key"), is("v2"));
        }
    }

    @Test
    void put_after_delete_restores_key(@TempDir Path tempDir) throws Exception {
        // TC-PUT-03: EC-PUT-03, EC-DEL-01
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
            store.delete("k");
            store.put("k", "v2");
            assertThat(store.get("k"), is("v2"));
        }
    }

    @Test
    void put_after_close_throws(@TempDir Path tempDir) throws Exception {
        // TC-PUT-04: EC-PUT-04
        LsmKvStore store = lsmKvStore(tempDir);
        store.close();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> store.put("k", "v"));
        assertThat(ex.getMessage(), is("kv store is closed"));
    }

    // ---- DELETE (EC-DEL-*) ----

    @Test
    void delete_existing_key_get_returns_null(@TempDir Path tempDir) throws Exception {
        // TC-DEL-01: EC-DEL-01, EC-GET-02
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
            store.delete("k");
            assertThat(store.get("k"), is(nullValue()));
        }
    }

    @Test
    void delete_absent_key_get_returns_null(@TempDir Path tempDir) throws Exception {
        // TC-DEL-02: EC-DEL-02, EC-GET-03
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.delete("k");
            assertThat(store.get("k"), is(nullValue()));
        }
    }

    @Test
    void delete_after_close_throws(@TempDir Path tempDir) throws Exception {
        // TC-DEL-03: EC-DEL-03
        LsmKvStore store = lsmKvStore(tempDir);
        store.close();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> store.delete("k"));
        assertThat(ex.getMessage(), is("kv store is closed"));
    }

    // ---- GET (EC-GET-*) ----

    @Test
    void get_absent_key_returns_null(@TempDir Path tempDir) throws Exception {
        // TC-GET-01: EC-GET-03
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            assertThat(store.get("missing"), is(nullValue()));
        }
    }

    @Test
    void get_after_close_throws(@TempDir Path tempDir) throws Exception {
        // TC-GET-08: EC-GET-09
        LsmKvStore store = lsmKvStore(tempDir);
        store.close();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> store.get("k"));
        assertThat(ex.getMessage(), is("kv store is closed"));
    }

    // ---- FLUSH (EC-FLUSH-*) ----

    @Test
    void flush_empty_memtable_is_noop(@TempDir Path tempDir) throws Exception {
        // TC-FLUSH-01: EC-FLUSH-01 — drives the empty-flush no-op guard
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.flush();
            assertThat(countSSTables(tempDir), is(0));
        }
    }

    @Test
    void flush_makes_data_readable_from_sstable(@TempDir Path tempDir) throws Exception {
        // TC-FLUSH-02: EC-FLUSH-02, EC-GET-04, EC-KEY-03, EC-VAL-03
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put(UTF8_KEY, LONG_VALUE);
            store.flush();
            assertThat(store.get(UTF8_KEY), is(LONG_VALUE));
        }
    }

    @Test
    void flush_sparse_index_all_entries_readable(@TempDir Path tempDir) throws Exception {
        // TC-FLUSH-03: EC-FLUSH-03, EC-GET-04 — 129 entries forces two sparse index entries
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            for (int i = 0; i < 129; i++) {
                store.put(String.format("sparse-key-%04d", i), "val-" + i);
            }
            store.flush();
            for (int i = 0; i < 129; i++) {
                assertThat("key " + i, store.get(String.format("sparse-key-%04d", i)), is("val-" + i));
            }
        }
    }

    @Test
    void two_flushes_all_data_readable(@TempDir Path tempDir) throws Exception {
        // TC-FLUSH-04: EC-FLUSH-04, EC-GET-08
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("a1", "v1"); store.put("a2", "v2");
            store.flush();
            store.put("b1", "v3"); store.put("b2", "v4");
            store.flush();
            assertThat(store.get("a1"), is("v1"));
            assertThat(store.get("a2"), is("v2"));
            assertThat(store.get("b1"), is("v3"));
            assertThat(store.get("b2"), is("v4"));
        }
    }

    @Test
    void auto_rotation_at_threshold(@TempDir Path tempDir) throws Exception {
        // TC-FLUSH-05: EC-FLUSH-05 — ~8200 entries × 517 bytes each exceeds 4 MB threshold
        String value = "x".repeat(500);
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            for (int i = 0; i < 8_200; i++) {
                store.put(String.format("key-%04d", i), value);
            }
            assertThat(countSSTables(tempDir), is(greaterThanOrEqualTo(1)));
            assertThat(store.get("key-0000"), is(value));
            assertThat(store.get("key-8199"), is(value));
        }
    }

    // ---- GET read-path (SSTable scenarios) ----

    @Test
    void tombstone_in_sstable_returns_null(@TempDir Path tempDir) throws Exception {
        // TC-GET-03: EC-GET-05
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
            store.delete("k");
            store.flush();
            assertThat(store.get("k"), is(nullValue()));
        }
    }

    @Test
    void memtable_shadows_sstable(@TempDir Path tempDir) throws Exception {
        // TC-GET-04: EC-GET-06
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v1");
            store.flush();
            store.put("k", "v2");
            assertThat(store.get("k"), is("v2"));
        }
    }

    @Test
    void tombstone_in_memtable_shadows_sstable(@TempDir Path tempDir) throws Exception {
        // TC-GET-05: EC-GET-07
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
            store.flush();
            store.delete("k");
            assertThat(store.get("k"), is(nullValue()));
        }
    }

    @Test
    void multi_sstable_newest_wins(@TempDir Path tempDir) throws Exception {
        // TC-GET-06: EC-GET-08
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v1");
            store.flush();
            store.put("k", "v2");
            store.flush();
            assertThat(store.get("k"), is("v2"));
        }
    }

    // ---- RECOVERY (EC-RECOV-*) ----

    @Test
    void open_empty_folder_returns_fresh_store(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-01: EC-RECOV-01
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            assertThat(store.get("k"), is(nullValue()));
        }
    }

    @Test
    void close_and_reopen_sstable_readable(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-02: EC-RECOV-02 — close() flushes, reopen loads SSTable
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
        }
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            assertThat(store.get("k"), is("v"));
        }
    }

    @Test
    void wal_replay_without_close(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-03: EC-RECOV-03 — data in WAL, no flush; reopen replays WAL
        LsmKvStore store1 = lsmKvStore(tempDir);
        store1.put("k", "v");
        // store1 intentionally not closed — simulates crash before flush

        try (LsmKvStore store2 = lsmKvStore(tempDir)) {
            assertThat(store2.get("k"), is("v"));
        }
        // store1 deliberately not closed — closing it would flush to the same epoch as store2,
        // causing an SSTable file collision (assert created in SSTableIO.write)
    }

    @Test
    void wal_tombstone_replayed(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-04: EC-RECOV-04 — DELETE in WAL replayed as tombstone
        LsmKvStore store1 = lsmKvStore(tempDir);
        store1.put("k", "v");
        store1.delete("k");

        try (LsmKvStore store2 = lsmKvStore(tempDir)) {
            assertThat(store2.get("k"), is(nullValue()));
        }
        // store1 deliberately not closed — same reason as wal_replay_without_close
    }

    @Test
    void sstable_and_wal_both_loaded(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-05: EC-RECOV-05 — k1 in SSTable, k2 in WAL; both readable on reopen
        LsmKvStore store1 = lsmKvStore(tempDir);
        store1.put("k1", "v1");
        store1.flush();
        store1.put("k2", "v2");
        // store1 not closed — WAL has k2, SSTable has k1

        try (LsmKvStore store2 = lsmKvStore(tempDir)) {
            assertThat(store2.get("k1"), is("v1"));
            assertThat(store2.get("k2"), is("v2"));
        }
        // store1 deliberately not closed — same reason as wal_replay_without_close
    }

    @Test
    void torn_wal_valid_ops_replayed(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-06: EC-RECOV-06 — valid frame + garbage bytes; only valid op replayed
        MemtableOp validOp = new MemtableOp(OpType.PUT,
            "k1".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        writeTornWal(tempDir, validOp);

        try (LsmKvStore store = lsmKvStore(tempDir)) {
            assertThat(store.get("k1"), is("v1"));
        }
    }

    // ---- COMPACTION (EC-COMPACT-*) ----

    @Test
    void four_flushes_trigger_compaction(@TempDir Path tempDir) throws Exception {
        // TC-COMPACT-01: EC-COMPACT-01, EC-COMPACT-02 — 4 flushes → auto-compaction; all keys readable; 1 SSTable
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("a1", "va1"); store.put("a2", "va2"); store.flush();
            store.put("b1", "vb1"); store.put("b2", "vb2"); store.flush();
            store.put("c1", "vc1"); store.put("c2", "vc2"); store.flush();
            store.put("d1", "vd1"); store.put("d2", "vd2"); store.flush();

            assertThat(store.get("a1"), is("va1")); assertThat(store.get("a2"), is("va2"));
            assertThat(store.get("b1"), is("vb1")); assertThat(store.get("b2"), is("vb2"));
            assertThat(store.get("c1"), is("vc1")); assertThat(store.get("c2"), is("vc2"));
            assertThat(store.get("d1"), is("vd1")); assertThat(store.get("d2"), is("vd2"));
            assertThat(countSSTables(tempDir), is(1));
        }
    }

    @Test
    void compaction_newest_value_wins(@TempDir Path tempDir) throws Exception {
        // TC-COMPACT-02: EC-COMPACT-04 — key written to multiple SSTables; latest value survives compaction
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v1"); store.flush();
            store.put("k", "v2"); store.flush();
            store.put("pad1", "x"); store.flush();
            store.put("pad2", "x"); store.flush();
            assertThat(store.get("k"), is("v2"));
        }
    }

    @Test
    void compaction_tombstone_gc(@TempDir Path tempDir) throws Exception {
        // TC-COMPACT-03: EC-COMPACT-03 — deleted key must be absent after compaction; 1 SSTable on disk
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v"); store.flush();
            store.delete("k"); store.flush();
            store.put("pad1", "x"); store.flush();
            store.put("pad2", "x"); store.flush();
            assertThat(store.get("k"), is(nullValue()));
            assertThat(countSSTables(tempDir), is(1));
        }
    }

    // ---- LIFECYCLE (EC-LIFE-*) ----

    @Test
    void close_with_data_flushes(@TempDir Path tempDir) throws Exception {
        // TC-LIFE-02: EC-LIFE-01 — close() on non-empty store creates SSTable
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("k", "v");
        }
        assertThat(countSSTables(tempDir), is(greaterThanOrEqualTo(1)));
    }

    @Test
    void close_empty_no_flush(@TempDir Path tempDir) throws Exception {
        // TC-LIFE-03: EC-LIFE-02 — close() on empty store creates no SSTable
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            // intentionally empty
        }
        assertThat(countSSTables(tempDir), is(0));
    }

    @Test
    void double_close_is_idempotent(@TempDir Path tempDir) throws Exception {
        // TC-LIFE-04: EC-LIFE-03
        LsmKvStore store = lsmKvStore(tempDir);
        store.close();
        store.close(); // must not throw
    }

    // ---- recovery after compaction ----

    @Test
    void recovery_after_compaction(@TempDir Path tempDir) throws Exception {
        // TC-RECOV-07: close a store whose SSTables were compacted into one, reopen, verify all data
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            store.put("a", "va"); store.flush();
            store.put("b", "vb"); store.flush();
            store.put("c", "vc"); store.flush();
            store.put("d", "vd"); store.flush(); // triggers compaction → 1 SSTable
        }
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            assertThat(store.get("a"), is("va"));
            assertThat(store.get("b"), is("vb"));
            assertThat(store.get("c"), is("vc"));
            assertThat(store.get("d"), is("vd"));
        }
    }

    // ---- delete-triggered rotation ----

    @Test
    void delete_triggers_rotation(@TempDir Path tempDir) throws Exception {
        // TC-FLUSH-06: covers the rotate() call in delete() — genuine coverage gap
        // 8338 puts of 6-byte key + 488-byte value = 8338 × 503 = 4,194,014 bytes (just below threshold)
        // One delete with 282-byte key adds 9+282 = 291 bytes → 4,194,305 > threshold → rotation
        String value = "x".repeat(488);
        try (LsmKvStore store = lsmKvStore(tempDir)) {
            for (int i = 0; i < 8_338; i++) {
                store.put(String.format("a%05d", i), value);
            }
            assertThat(countSSTables(tempDir), is(0));
            store.delete("d".repeat(282));
            assertThat(countSSTables(tempDir), is(greaterThanOrEqualTo(1)));
        }
    }

    // ---- helpers ----

    private static int countSSTables(Path tempDir) {
        File[] files = tempDir.resolve("sstables").toFile()
            .listFiles(f -> f.getName().endsWith(SSTable.FILE_EXT));
        return files == null ? 0 : files.length;
    }

    /** Writes one valid WAL frame followed by 3 garbage bytes to simulate a torn write. */
    private static void writeTornWal(Path dir, MemtableOp validOp) throws IOException {
        byte[] payload = MemtableOpCodec.getInstance().encode(validOp);
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteBuffer frame = ByteBuffer.allocate(8 + payload.length);
        frame.putInt((int) crc.getValue());
        frame.putInt(payload.length);
        frame.put(payload);
        Path walPath = dir.resolve("0" + Memtable.BAL_FILE_EXT);
        try (FileOutputStream fos = new FileOutputStream(walPath.toFile())) {
            fos.write(frame.array());
            fos.write(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE}); // torn write
        }
    }
}
