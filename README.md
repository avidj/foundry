# Foundry

Reusable systems-programming components built from scratch — storage engines, stream processing internals, and more.

## Modules

- **foundry-storage** — storage primitives: write-ahead logs, binary append log
- **foundry-kvstore** — `KVStore` interface + simple HashMap-backed single-node store
- **foundry-bitcask** — Bitcask-style KV store: append-only log files, in-memory key index
- **foundry-lsm** — LSM-tree KV store: memtable + SSTable flush, bloom filter, size-tiered compaction
- **foundry-streaming** — stream processing internals (placeholder)

## Build

Requires Java 21+.

```bash
mvn compile   # build
mvn test      # test + JaCoCo coverage
mvn verify    # full build + test
```
