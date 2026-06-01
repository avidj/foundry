package org.avidd.storage;

public record Frame<T>(long offset, T payload, byte[] rawPayload) {
    /* Payload layout (inside BinaryAppendLog frame):

[timestamp(8) | key_size(4) | value_size(4) | key bytes | value bytes]

- value_size = -1  ⇒  tombstone (delete record). Key bytes still present.
- All ints big-endian.
- key/value bytes are UTF-8.
- HEADER_BYTES = 16

     */

}
