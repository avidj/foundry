// SPDX-License-Identifier: Apache-2.0

package org.avidd.foundry.storage;

/* Payload layout (inside BinaryAppendLog frame):
   [timestamp(8) | key_size(4) | value_size(4) | key bytes | value bytes]
    - HEADER_BYTES = 16
    - key/value bytes are UTF-8.
    - All ints big-endian.
    - value_size = -1  ⇒  tombstone (delete record). Key bytes still present.
*/
public record Frame<T>(long offset, T payload, byte[] rawPayload) {
}
