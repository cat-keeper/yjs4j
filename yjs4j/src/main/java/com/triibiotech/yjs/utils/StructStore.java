package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.GC;
import com.triibiotech.yjs.structs.Item;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * StructStore manages all structs in a document.
 * Matches the functionality of StructStore.js from the original Yjs implementation.
 *
 * @author zbs
 * @date 2025/07/29  11:06:19
 */
public class StructStore {

    /**
     * Map from client ID to list of structs (GC|Item)
     */
    private final Map<Long, LinkedList<AbstractStruct>> clients = new LinkedHashMap<>();

    /**
     * Pending structs that couldn't be integrated yet
     */
    public PendingStructs pendingStructs = null;

    /**
     * Pending delete set
     */
    public byte[] pendingDs = null;

    public PendingStructs getPendingStructs() {
        return pendingStructs;
    }

    public void setPendingStructs(PendingStructs pendingStructs) {
        this.pendingStructs = pendingStructs;
    }

    public byte[] getPendingDs() {
        return pendingDs;
    }

    public void setPendingDs(byte[] pendingDs) {
        this.pendingDs = pendingDs;
    }

    /**
     * Represents pending structs with missing dependencies
     */
    public static class PendingStructs {
        public Map<Long, Long> missing = new HashMap<>();
        public byte[] update;

        public PendingStructs(byte[] update) {
            this.update = update;
        }

        public PendingStructs(Map<Long, Long> missing, byte[] update) {
            this.missing = missing;
            this.update = update;
        }
    }

    public StructStore() {
        // Initialize empty store
    }

    /**
     * Get structs for a client
     */
    public LinkedList<AbstractStruct> getClientStructs(Long client) {
        return clients.getOrDefault(client, new LinkedList<>());
    }

    /**
     * Get the state vector as a Map<client,clock>
     * Note that clock refers to the next expected clock id.
     */
    public Map<Long, Long> getStateVector() {
        Map<Long, Long> sm = new HashMap<>();
        clients.forEach((client, structs) -> {
            if (!structs.isEmpty()) {
                AbstractStruct struct = structs.get(structs.size() - 1);
                sm.put(client, struct.id.clock + struct.length);
            }
        });
        return sm;
    }

    /**
     * Get the current state (clock) for a client
     */
    public long getState(long client) {
        List<AbstractStruct> structs = clients.get(client);
        if (structs == null || structs.isEmpty()) {
            return 0;
        }
        AbstractStruct lastStruct = structs.get(structs.size() - 1);
        return lastStruct.id.clock + lastStruct.length;
    }

    /**
     * Add a struct to the store
     */
    public void addStruct(AbstractStruct struct) {
        LinkedList<AbstractStruct> structs = clients.get(struct.id.client);
        if (structs == null) {
            structs = new LinkedList<>();
            clients.put(struct.id.client, structs);
        } else {
            AbstractStruct lastStruct = structs.get(structs.size() - 1);
            if (lastStruct.id.clock + lastStruct.length != struct.id.clock) {
                throw new IllegalStateException("StructStore failed integrity check: expected clock " +
                        (lastStruct.id.clock + lastStruct.length) + ", got " + struct.id.clock);
            }
        }
        structs.addLast(struct);
    }

    /**
     * Find a struct by ID using binary search (matching original findIndexSS)
     */
    public AbstractStruct find(ID id) {
        List<AbstractStruct> structs = clients.get(id.client);
        if (structs == null || structs.isEmpty()) {
            throw new IllegalStateException("Struct not found: " + id);
        }
        return structs.get(findIndexSS(structs, id.clock));
    }

    /**
     * Get an Item by ID (expects that id is actually in store)
     */
    public AbstractStruct getItem(ID id) {
        AbstractStruct struct = find(id);
        if (!(struct instanceof Item)) {
            throw new IllegalStateException("Expected Item, got " + struct.getClass().getSimpleName());
        }
        return struct;
    }


    public static AbstractStruct getItemCleanStart(Transaction transaction, ID id) {
        LinkedList<AbstractStruct> structs = transaction.doc.store.clients.get(id.client);
        int index = findIndexCleanStart(transaction, structs, id.clock);
        return structs.get(index);
    }

    /**
     * Find index for clean start (may split items)
     */
    public static int findIndexCleanStart(Transaction transaction, LinkedList<AbstractStruct> structs, long clock) {
        int index = findIndexSS(structs, clock);
        AbstractStruct struct = structs.get(index);
        if (struct.id.clock < clock && struct instanceof Item item) {
            Item rightItem = Item.splitItem(transaction, item, clock - struct.id.clock);
            structs.add(index + 1, rightItem);
            return index + 1;
        }
        return index;
    }

    /**
     * Expects that id is actually in store. This function throws or is an infinite loop otherwise.
     */
    public AbstractStruct getItemCleanEnd(Transaction transaction, ID id) {
        List<AbstractStruct> structs = this.clients.get(id.client);
        int index = findIndexSS(structs, id.clock);
        AbstractStruct struct = structs.get(index);
        if (id.clock != struct.id.clock + struct.length - 1 && !(struct instanceof GC)) {
            Item item = Item.splitItem(transaction, struct, id.clock - struct.id.clock + 1);
            structs.add(index + 1, item);
        }
        return struct;
    }

    /**
     * Optimized binary search with caching
     */
    public static int findIndexSS(List<AbstractStruct> structs, Long clock) {
        if (structs.isEmpty()) {
            throw new IllegalStateException("Empty structs list");
        }

        int left = 0;
        int right = structs.size() - 1;
        AbstractStruct mid = structs.get(right);
        long midclock = mid.id.clock;
        if (midclock == clock) {
            return right;
        }
        int midindex = (int) Math.floor((clock * 1.0 / (midclock + mid.length - 1.0)) * right);
        while (left <= right) {
            mid = structs.get(midindex);
            midclock = mid.id.clock;
            if (midclock <= clock) {
                if (clock < midclock + mid.length) {
                    return midindex;
                }
                left = midindex + 1;
            } else {
                right = midindex - 1;
            }
            midindex = (int) Math.floor((left + right) / 2.0);
        }

        throw new IllegalStateException("Struct not found: clock=" + clock);
    }

    /**
     * Replace a struct with a new struct
     */
    public void replaceStruct(AbstractStruct struct, AbstractStruct newStruct) {
        List<AbstractStruct> structs = clients.get(struct.id.client);
        if (structs != null) {
            int index = findIndexSS(structs, struct.id.clock);
            structs.set(index, newStruct);
        }
    }

    /**
     * Iterate over a range of structs
     */
    public void iterateStructs(Transaction transaction, Long client, Long clockStart, Long len,
                               java.util.function.Consumer<AbstractStruct> f) {
        if (len == 0) {
            return;
        }

        List<AbstractStruct> structs = clients.get(client);
        if (structs == null) {
            return;
        }

        long clockEnd = clockStart + len;
        int index = findIndexSS(structs, clockStart);

        while (index < structs.size()) {
            AbstractStruct struct = structs.get(index);
            if (struct.id.clock >= clockEnd) {
                break;
            }

            if (struct.id.clock + struct.length > clockStart) {
                f.accept(struct);
            }
            index++;
        }
    }

    public static void iterateStructs(Transaction transaction, LinkedList<AbstractStruct> structs, long clockStart, long len, Consumer<AbstractStruct> f) {
        if (len == 0) {
            return;
        }
        long clockEnd = clockStart + len;
        int index = findIndexCleanStart(transaction, structs, clockStart);
        AbstractStruct struct;
        do {
            struct = structs.get(index++);
            if (clockEnd < struct.id.clock + struct.length) {
                findIndexCleanStart(transaction, structs, clockEnd);
            }
            f.accept(struct);
        } while (index < structs.size() && structs.get(index).id.clock < clockEnd);
    }

    /**
     * Perform integrity check on the store
     */
    public void integrityCheck() {
        clients.forEach((client, structs) -> {
            for (int i = 1; i < structs.size(); i++) {
                AbstractStruct l = structs.get(i - 1);
                AbstractStruct r = structs.get(i);
                if (l.id.clock + l.length != r.id.clock) {
                    throw new IllegalStateException("StructStore failed integrity check for client " + client);
                }
            }
        });
    }

    // Getters
    public Map<Long, LinkedList<AbstractStruct>> getClients() {
        return clients;
    }

    /**
     * Get state vector matching original implementation
     */
    public Map<Long, Long> getStateVectorMap() {
        return getStateVector();
    }

    /**
     * Clear all structs
     */
    public void clear() {
        clients.clear();
        pendingStructs = null;
        pendingDs = null;
    }

    public static long getState(StructStore store, long client) {
        List<AbstractStruct> clientStructs = store.getClients().get(client);
        if (clientStructs == null || clientStructs.isEmpty()) {
            return 0;
        }
        AbstractStruct lastStruct = clientStructs.get(clientStructs.size() - 1);
        return lastStruct.getId().getClock() + lastStruct.getLength();
    }

    public static Map<Long, Long> getStateVector(StructStore store) {
        Map<Long, Long> stateVector = new HashMap<>();
        store.getClients().forEach((client, structs) -> {
            AbstractStruct struct = structs.get(structs.size() - 1);
            stateVector.put(client, struct.id.clock + struct.length);
        });
        return stateVector;
    }
}
