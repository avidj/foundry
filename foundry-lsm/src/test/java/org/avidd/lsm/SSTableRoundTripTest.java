// SPDX-License-Identifier: Apache-2.0

package org.avidd.lsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SSTableRoundTripTest {

    @Test
    void write_read_single_entry(@TempDir Path tempDir) throws Exception {
        // TC-SST-01: EC-SST-01
        Map<String, MemtableValue> data = new TreeMap<>();
        data.put("k", new MemtableValue("v", false));

        SSTableIO.write(tempDir, 0, data);
        SSTable sst = SSTableIO.sstable(0, tempDir.resolve(SSTable.toFileName(0)));

        assertThat(sst.mayHave("k"), is(true));
        MemtableValue result = sst.get("k");
        assertThat(result, is(notNullValue()));
        assertThat(result.value(), is("v"));
        assertThat(result.tombstone(), is(false));
    }

    @Test
    void write_read_tombstone(@TempDir Path tempDir) throws Exception {
        // TC-SST-02: EC-SST-02 — value_size=-1 wire format for tombstone
        Map<String, MemtableValue> data = new TreeMap<>();
        data.put("k", new MemtableValue(null, true));

        SSTableIO.write(tempDir, 0, data);
        SSTable sst = SSTableIO.sstable(0, tempDir.resolve(SSTable.toFileName(0)));

        assertThat(sst.mayHave("k"), is(true));
        MemtableValue result = sst.get("k");
        assertThat(result, is(notNullValue()));
        assertThat(result.tombstone(), is(true));
        assertThat(result.value(), is(nullValue()));
    }

    @Test
    void write_129_entries_all_readable(@TempDir Path tempDir) throws Exception {
        // TC-SST-03: EC-SST-03 — 129 entries forces 2 sparse index entries (at records 0 and 128)
        Map<String, MemtableValue> data = new TreeMap<>();
        for (int i = 0; i < 129; i++) {
            data.put(String.format("key-%04d", i), new MemtableValue("val-" + i, false));
        }

        SSTableIO.write(tempDir, 0, data);
        SSTable sst = SSTableIO.sstable(0, tempDir.resolve(SSTable.toFileName(0)));

        for (int i = 0; i < 129; i++) {
            String key = String.format("key-%04d", i);
            assertThat("key " + i, sst.mayHave(key), is(true));
            MemtableValue result = sst.get(key);
            assertThat("value for " + key, result.value(), is("val-" + i));
        }
    }

    @Test
    void bloom_filter_round_trip(@TempDir Path tempDir) throws Exception {
        // TC-SST-04: EC-SST-04 — bloom filter serialized to disk and restored correctly
        Map<String, MemtableValue> data = new TreeMap<>();
        data.put("k", new MemtableValue("v", false));

        SSTableIO.write(tempDir, 0, data);
        SSTable sst = SSTableIO.sstable(0, tempDir.resolve(SSTable.toFileName(0)));

        assertThat(sst.mayHave("k"), is(true));
        // find an absent key the bloom filter correctly identifies as absent
        String absentKey = null;
        for (int i = 0; i < 100; i++) {
            String candidate = "absent-" + i;
            if (!sst.mayHave(candidate)) {
                absentKey = candidate;
                break;
            }
        }
        assertThat("bloom filter must reject at least one absent key in 100 tries", absentKey, is(notNullValue()));
        assertThat(sst.mayHave(absentKey), is(false));
    }

    @Test
    void get_absent_key_false_positive_returns_null(@TempDir Path tempDir) throws Exception {
        // TC-SST-06: covers SSTable.get() comp>0 branch and trailing return null
        // Requires a bloom filter false positive for an absent key that falls between existing keys
        Map<String, MemtableValue> data = new TreeMap<>();
        for (int i = 0; i < 100; i++) {
            data.put(String.format("key-%04d", i), new MemtableValue("val-" + i, false));
        }
        SSTableIO.write(tempDir, 0, data);
        SSTable sst = SSTableIO.sstable(0, tempDir.resolve(SSTable.toFileName(0)));

        // "key-NNNN-MMMM" lexicographically falls between "key-NNNN" and "key-NNNN+1"
        // With 1% FPR and ~19800 candidates, finding a false positive is virtually certain.
        String falsePositive = null;
        outer:
        for (int i = 0; i < 99 && falsePositive == null; i++) {
            for (int j = 0; j < 200; j++) {
                String candidate = String.format("key-%04d-%04d", i, j);
                if (sst.mayHave(candidate)) {
                    falsePositive = candidate;
                    break outer;
                }
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(falsePositive != null,
            "no bloom filter false positive found in 19800 candidates — extremely unlikely");
        assertThat(sst.get(falsePositive), is(nullValue()));
    }

    @Test
    void get_without_mayHave_throws(@TempDir Path tempDir) throws Exception {
        // TC-SST-05: EC-SST-05 — SSTable.get() contract: must call mayHave() first
        Map<String, MemtableValue> data = new TreeMap<>();
        data.put("k", new MemtableValue("v", false));

        SSTableIO.write(tempDir, 0, data);
        SSTable sst = SSTableIO.sstable(0, tempDir.resolve(SSTable.toFileName(0)));

        // find an absent key the bloom filter correctly identifies as absent
        String absentKey = null;
        for (int i = 0; i < 100; i++) {
            String candidate = "absent-" + i;
            if (!sst.mayHave(candidate)) {
                absentKey = candidate;
                break;
            }
        }
        assertThat("need an absent key for this test", absentKey, is(notNullValue()));
        String finalAbsentKey = absentKey;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> sst.get(finalAbsentKey));
        assertThat(ex.getMessage(), is("test with mayHave first"));
    }
}
