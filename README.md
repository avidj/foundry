# Foundry

Reusable systems-programming components built from scratch — storage engines, stream processing internals, and more.

## Scope

Foundry implements storage engine internals from primary sources rather than wrapping
existing libraries: a write-ahead log, a Bitcask-style store, and an LSM-tree store,
each with its own on-disk format, recovery path, and compaction strategy. Each is built
to the point where the design tradeoffs become real and testable, and stops there.

**In scope:** on-disk formats and codecs, crash recovery, compaction, bloom filters and
sparse indexes, flush/compaction concurrency.

**Not in scope:** a query layer, transactions, replication, distribution, or performance
competitive with a tuned production engine. Foundry is not a database and is not trying
to become one.

72 tests drive recovery and compaction explicitly: partial-frame replay, tombstone
survival through compaction, concurrent flush interleavings. 

## Modules

- **foundry-storage** — storage primitives: write-ahead logs, binary append log
- **foundry-kvstore** — `KVStore` interface + simple HashMap-backed single-node store
- **foundry-bitcask** — Bitcask-style KV store: append-only log files, in-memory key index
- **foundry-lsm** — LSM-tree KV store: memtable + SSTable flush, bloom filter, size-tiered compaction
- **foundry-server** — minimal HTTP front end over a KV store, with bearer-token auth
- **foundry-streaming** — stream processing internals (placeholder)

## Build

Requires Java 21+. Built against release 21; verified on JDK 24.

```bash
mvn compile   # build
mvn test      # test + JaCoCo coverage
mvn verify    # full build + test
```

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
