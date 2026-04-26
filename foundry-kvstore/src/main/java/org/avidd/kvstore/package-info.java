/*
 * This package implements a real durable, lossless, single-node, Bitcask-style KV-store.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>In-memory {@code HashMap<String, String>} for reads</li>
 *   <li>Append-only WAL ({@code FileChannel} + {@code fsync}) for durability</li>
 *   <li>Periodic snapshots of the full map to disk</li>
 * </ul>
 *
 * <h2>Write path</h2>
 * WAL append -> fsync -> update in-memory map -> ack.
 *
 * <h2>Log format</h2>
 * One entry per line: {@code escape(key):value} or {@code escape(key):\0} for deletes.
 *
 * <h2>Recovery</h2>
 * Load latest {@code .snap} file, replay all subsequent {@code .log} files in order.
 *
 * <h2>Compaction</h2>
 * When current log exceeds threshold: rotate to new log, background thread
 * loads latest snapshot + replays log -> writes new snapshot -> old log/snapshot deletable.
 *
 * <h2>Concurrency</h2>
 * Striped {@code ReadWriteLock}: {@code locks[hash(key) % NUM_STRIPES]}.
 * Concurrent reads within a stripe; writes lock one stripe, not the whole map.
 *
 * Implementation Tasks:
 * - add KVStore interface
 * - create DataRecord class
 * - add KVStore implementation (high-level)
 * - add interfaces along the way, define implementations as they are added
 * - create RecordCodec
 * - add API layer in the end (optional)
 */
package org.avidd.kvstore;
