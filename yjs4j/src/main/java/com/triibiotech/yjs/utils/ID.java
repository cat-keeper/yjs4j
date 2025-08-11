package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.Objects;

/**
 * Unique identifier for operations in Yjs.
 * Each ID consists of a client ID and a clock value.
 *
 * @author zbs
 * @date 2025/07/29  13:47:00
 */
@SuppressWarnings("unused")
public class ID {
    /**
     * Client id
     */
    public long client;

    /**
     * Unique per client id, continuous number
     */
    public long clock;

    /**
     * @param client client id
     * @param clock  unique per client id, continuous number
     */
    public ID(long client, long clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ID id = (ID) obj;
        return client == id.client && clock == id.clock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, clock);
    }

    @Override
    public String toString() {
        return "ID{client=" + client + ", clock=" + clock + "}";
    }

    /**
     * Compare two IDs for equality (handles null values)
     */
    public static boolean compareIds(ID a, ID b) {
        return a == b || (a != null && b != null && a.client == b.client && a.clock == b.clock);
    }

    /**
     * Create a new ID
     */
    public static ID createId(long client, long clock) {
        return new ID(client, clock);
    }

    /**
     * Write ID to encoder
     */
    public static void writeId(Encoder encoder, ID id) {
        Encoder.writeVarUint(encoder, id.client);
        Encoder.writeVarUint(encoder, id.clock);
    }

    /**
     * Read ID from decoder
     */
    public static ID readId(Decoder decoder) {
        return createId(Decoder.readVarUint(decoder), Decoder.readVarUint(decoder));
    }

    /**
     * Find the root type key for a given type
     */
    public static String findRootTypeKey(AbstractType<?> type) {
        for (var entry : type.getDocument().getShare().entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        throw new RuntimeException("Unexpected case: type not found in document share");
    }

    public long getClient() {
        return client;
    }

    public void setClient(int client) {
        this.client = client;
    }

    public long getClock() {
        return clock;
    }

    public void setClock(int clock) {
        this.clock = clock;
    }
}
