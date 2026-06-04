package org.avidd.lsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemtableTest {

    @Test
    void put_new_key_entry_present(@TempDir Path tempDir) throws Exception {
        // TC-MT-01: EC-MT-01, EC-KEY-01, EC-VAL-01
        Memtable mt = Memtable.memtable(tempDir, 0);
        mt.put("k", "v");
        MemtableValue entry = mt.get("k");
        assertThat(entry, is(notNullValue()));
        assertThat(entry.value(), is("v"));
        assertThat(entry.tombstone(), is(false));
    }

    @Test
    void put_overwrite_updates_entry(@TempDir Path tempDir) throws Exception {
        // TC-MT-02: EC-MT-02
        Memtable mt = Memtable.memtable(tempDir, 0);
        mt.put("k", "v1");
        mt.put("k", "v2");
        assertThat(mt.get("k").value(), is("v2"));
    }

    @Test
    void delete_stores_tombstone(@TempDir Path tempDir) throws Exception {
        // TC-MT-03: EC-MT-03
        Memtable mt = Memtable.memtable(tempDir, 0);
        mt.put("k", "v");
        mt.delete("k");
        MemtableValue entry = mt.get("k");
        assertThat(entry.tombstone(), is(true));
        assertThat(entry.value(), is(nullValue()));
    }

    @Test
    void get_absent_key_returns_null(@TempDir Path tempDir) throws Exception {
        // TC-MT-04: EC-MT-04
        Memtable mt = Memtable.memtable(tempDir, 0);
        assertThat(mt.get("missing"), is(nullValue()));
    }

    @Test
    void size_bytes_tracks_ops(@TempDir Path tempDir) throws Exception {
        // TC-MT-05: EC-MT-05
        Memtable mt = Memtable.memtable(tempDir, 0);
        MemtableOpCodec codec = MemtableOpCodec.getInstance();

        MemtableOp putOp = new MemtableOp(OpType.PUT,
            "k".getBytes(StandardCharsets.UTF_8),
            "v".getBytes(StandardCharsets.UTF_8));
        int sizeAfterPut = mt.put("k", "v");
        assertThat(sizeAfterPut, is(codec.sizeBytes(putOp)));

        MemtableOp delOp = new MemtableOp(OpType.DELETE,
            "k2".getBytes(StandardCharsets.UTF_8),
            Memtable.DEL_BYTES);
        int sizeAfterDelete = mt.delete("k2");
        assertThat(sizeAfterDelete, is(codec.sizeBytes(putOp) + codec.sizeBytes(delOp)));
    }

    @Test
    void flush_creates_sstable_deletes_wal(@TempDir Path tempDir) throws Exception {
        // TC-MT-06: EC-MT-06
        Memtable mt = Memtable.memtable(tempDir, 0);
        mt.put("k", "v");
        Path sstDir = tempDir.resolve("sst");
        mt.flush(sstDir);
        assertThat(sstDir.resolve("0" + SSTable.FILE_EXT).toFile().exists(), is(true));
        assertThat(tempDir.resolve("0" + Memtable.BAL_FILE_EXT).toFile().exists(), is(false));
    }

    @Test
    void put_after_flush_throws(@TempDir Path tempDir) throws Exception {
        // TC-MT-07: EC-MT-07
        Memtable mt = Memtable.memtable(tempDir, 0);
        mt.put("k", "v");
        mt.flush(tempDir.resolve("sst"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> mt.put("k", "v2"));
        assertThat(ex.getMessage(), is("flush already triggered"));
    }

    @Test
    void delete_after_flush_throws(@TempDir Path tempDir) throws Exception {
        // TC-MT-08: EC-MT-08
        Memtable mt = Memtable.memtable(tempDir, 0);
        mt.put("k", "v");
        mt.flush(tempDir.resolve("sst"));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> mt.delete("k"));
        assertThat(ex.getMessage(), is("flush already triggered"));
    }

    @Test
    void wal_recovery_replays_ops(@TempDir Path tempDir) throws Exception {
        // TC-MT-09: EC-MT-09 — reconstruct memtable from same folder+epoch; WAL ops replayed
        Memtable mt1 = Memtable.memtable(tempDir, 0);
        mt1.put("k", "v");
        // mt1 not flushed — WAL has the PUT op

        Memtable mt2 = Memtable.memtable(tempDir, 0);
        assertThat(mt2.get("k").value(), is("v"));
    }
}
