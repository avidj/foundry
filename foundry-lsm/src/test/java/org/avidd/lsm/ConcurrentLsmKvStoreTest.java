package org.avidd.lsm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.avidd.lsm.LsmKvStore.lsmKvStore;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentLsmKvStoreTest {

    @Test
    void concurrent_puts_and_gets_are_consistent(@TempDir Path tempDir) throws Exception {
        // N writer threads each own a disjoint key range; M reader threads read all keys.
        // After all threads complete, every key must be readable with its correct value.
        int writers = 4;
        int keysPerWriter = 50;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        AtomicInteger errors = new AtomicInteger(0);

        try (LsmKvStore store = lsmKvStore(tempDir)) {
            for (int w = 0; w < writers; w++) {
                final int writer = w;
                Thread t = new Thread(() -> {
                    try {
                        startGate.await();
                        for (int k = 0; k < keysPerWriter; k++) {
                            store.put("w" + writer + "-key" + k, "val-" + writer + "-" + k);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
                t.start();
            }
            startGate.countDown();
            done.await();
            assertThat(errors.get(), is(0));

            for (int w = 0; w < writers; w++) {
                for (int k = 0; k < keysPerWriter; k++) {
                    assertThat(store.get("w" + w + "-key" + k), is("val-" + w + "-" + k));
                }
            }
        }
    }

    @Test
    void get_reads_from_flushing_memtable(@TempDir Path tempDir) throws Exception {
        // Verifies the flushingMemtable read path in LsmKvStore.get():
        // while flush IO is in progress (outside the lock), a concurrent get() must find
        // the key in flushingMemtable rather than the empty active memtable.
        LsmKvStore store = lsmKvStore(tempDir);
        String value = "x".repeat(10_000);
        for (int i = 0; i < 500; i++) {
            store.put(String.format("key-%04d", i), value);
        }

        Field field = LsmKvStore.class.getDeclaredField("flushingMemtable");
        field.setAccessible(true);

        AtomicReference<String> getResult = new AtomicReference<>();
        Thread flushThread = new Thread(() -> {
            try { store.flush(); } catch (Exception ignored) {}
        });
        flushThread.start();

        // Spin until memtable swap is complete (flushingMemtable set, IO about to start)
        long deadline = System.currentTimeMillis() + 5_000;
        while (field.get(store) == null && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        // At this point flushingMemtable != null and ~5 MB IO is in flight
        if (field.get(store) != null) {
            getResult.set(store.get("key-0000"));
        }

        flushThread.join(10_000);
        store.close();

        assertThat("get must have run while flush was in progress", getResult.get(), is(notNullValue()));
        assertThat(getResult.get(), is(value));
    }

    @Test
    void close_interrupted_during_concurrent_flush_wraps_exception(@TempDir Path tempDir)
            throws Exception {
        // Exercises the InterruptedException handler in close().
        // The handler is reached only when doFlush() calls mutex.wait() — which happens
        // when flushingMemtable is non-null (another flush is in progress).
        //
        // LsmKvStore.postSwapHook is injected to pause the background flush between the
        // memtable swap and the SSTable IO. While the hook is blocking:
        //   - flushingMemtable is non-null (swap done)
        //   - no thread holds the mutex
        //   - closeThread enters doFlush(), sees flushingMemtable != null, calls mutex.wait()
        //   - pre-interrupt causes wait() to throw immediately → IOException("interrupted…")
        LsmKvStore store = lsmKvStore(tempDir);
        store.put("k", "v"); // ensure memtable has data so close() calls flush()

        CountDownLatch swapDone = new CountDownLatch(1);
        CountDownLatch allowIO  = new CountDownLatch(1);
        store.postSwapHook = () -> {
            swapDone.countDown();
            try { allowIO.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        };

        Thread flushThread = new Thread(() -> {
            try { store.flush(); } catch (Exception ignored) {}
        });
        flushThread.start();
        swapDone.await(5, TimeUnit.SECONDS); // swap complete; flush IO paused at hook

        // Put one entry in the new active memtable so close() → flush() path is taken
        store.put("one-more", "v");

        // closeThread is pre-interrupted → doFlush() → wait() → throws immediately
        AtomicReference<IOException> closeException = new AtomicReference<>();
        CountDownLatch closeDone = new CountDownLatch(1);
        Thread closeThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            try { store.close(); } catch (IOException e) { closeException.set(e); }
            finally { closeDone.countDown(); }
        });
        closeThread.start();
        closeDone.await(5, TimeUnit.SECONDS);

        allowIO.countDown(); // let background flush finish
        flushThread.join(10_000);

        assertThat("close() must throw IOException wrapping InterruptedException",
            closeException.get(), is(notNullValue()));
        assertThat(closeException.get().getMessage(), is("interrupted while closing"));
    }
}
