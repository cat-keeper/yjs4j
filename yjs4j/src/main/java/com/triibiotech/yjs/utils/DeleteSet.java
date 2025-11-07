package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.*;
import java.util.function.Consumer;

/**
 * DeleteSet tracks deleted items in the document.
 * We no longer maintain a DeleteStore. DeleteSet is a temporary object that is created when needed.
 * - When created in a transaction, it must only be accessed after sorting, and merging
 * - This DeleteSet is send to other clients
 * - We do not create a DeleteSet when we send a sync message. The DeleteSet message is created directly from StructStore
 * - We read a DeleteSet as part of a sync/update message. In this case the DeleteSet is already sorted and merged.
 *
 * @author zbs
 * @date 2025/07/30  17:38:05
 */
@SuppressWarnings("unused")
public class DeleteSet {
    /**
     * Map from client ID to list of DeleteItems
     */
    public SafeLinkedHashMap<Long, List<DeleteItem>> clients;

    public DeleteSet() {
        clients = new SafeLinkedHashMap<>();
    }

    public SafeLinkedHashMap<Long, List<DeleteItem>> getClients() {
        return clients;
    }

    public void setClients(SafeLinkedHashMap<Long, List<DeleteItem>> clients) {
        this.clients = clients;
    }


    /**
     * 迭代删除结构
     * 从 DeleteSet 中取出所有删除范围，遍历对应 client 的结构数组，逐个执行回调函数。
     */
    public static void iterateDeletedStructs(Transaction transaction, DeleteSet ds, Consumer<AbstractStruct> consumer) {
        StructStore store = transaction.getDoc().getStore();
        for (Map.Entry<Long, List<DeleteItem>> entry : ds.getClients().entrySet()) {
            long clientId = entry.getKey();
            List<DeleteItem> deletes = entry.getValue();
            LinkedList<AbstractStruct> structs = store.getClients().get(clientId);

            if (structs == null || structs.isEmpty()) {
                continue;
            }
            AbstractStruct lastStruct = structs.get(structs.size() - 1);
            long clockState = lastStruct.getId().getClock() + lastStruct.getLength();

            for (DeleteItem del : deletes) {
                if (del.getClock() >= clockState) {
                    break;
                }
                StructStore.iterateStructs(transaction, structs, del.getClock(), del.getLen(), consumer);
            }
        }
    }

    /**
     * Find index of DeleteItem that contains the given clock
     *
     * @param dis   List of DeleteItems
     * @param clock Clock to find
     * @return Index or null if not found
     */
    public static Integer findIndexDs(List<DeleteItem> dis, Long clock) {
        int left = 0;
        int right = dis.size() - 1;
        while (left <= right) {
            int midIndex = (left + right) / 2;
            DeleteItem mid = dis.get(midIndex);
            long midClock = mid.clock;
            if (midClock <= clock) {
                if (clock < midClock + mid.len) {
                    return midIndex;
                }
                left = midIndex + 1;
            } else {
                right = midIndex - 1;
            }
        }
        return null;
    }

    /**
     * Check if an ID is deleted
     */
    public boolean isDeleted(ID id) {
        List<DeleteItem> dis = clients.get(id.client);
        return dis != null && findIndexDs(dis, id.clock) != null;
    }

    /**
     * Sort and merge delete set
     */
    public void sortAndMergeDeleteSet() {
        this.clients.forEach((client, dels) -> {
            dels.sort(Comparator.comparingLong(a -> a.clock));
            // merge items without filtering or splicing the array
            // i is the current pointer
            // j refers to the current insert position for the pointed item
            // try to merge dels[i] into dels[j-1] or set dels[j]=dels[i]
            int i, j;
            for (i = 1, j = 1; i < dels.size(); i++) {
                DeleteItem left = dels.get(j - 1);
                DeleteItem right = dels.get(i);
                if (left.clock + left.len >= right.clock) {
                    left.len = Math.max(left.len, right.clock + right.len - left.clock);
                } else {
                    if (j < i) {
                        dels.set(j, right);
                    }
                    j++;
                }
            }
            // Truncate the list to remove merged items
            while (dels.size() > j) {
                dels.remove(dels.size() - 1);
            }
        });
    }

    /**
     * Merge multiple delete sets
     */
    public static DeleteSet mergeDeleteSets(List<DeleteSet> dss) {
        DeleteSet merged = new DeleteSet();
        for (int dssI = 0; dssI < dss.size(); dssI++) {
            DeleteSet ds = dss.get(dssI);
            final int currentIndex = dssI;
            ds.clients.forEach((client, delsLeft) -> {
                if (!merged.clients.containsKey(client)) {
                    // Write all missing keys from current ds and all following.
                    // If merged already contains `client` current ds has already been added.
                    List<DeleteItem> dels = new ArrayList<>(delsLeft);
                    for (int j = currentIndex + 1; j < dss.size(); j++) {
                        List<DeleteItem> clientDels = dss.get(j).clients.get(client);
                        if (clientDels != null) {
                            dels.addAll(clientDels);
                        }
                    }
                    merged.clients.put(client, dels);
                }
            });
        }
        merged.sortAndMergeDeleteSet();
        return merged;
    }

    /**
     * Add a deleted range to the set
     */
    public void addToDeleteSet(long client, long clock, long length) {
        clients.computeIfAbsent(client, k -> new ArrayList<>()).add(new DeleteItem(clock, length));
    }

    public static void addToDeleteSet(DeleteSet ds, long client, long clock, long length) {
        ds.clients.computeIfAbsent(client, k -> new ArrayList<>()).add(new DeleteItem(clock, length));
    }

    /**
     * Create empty delete set
     */
    public static DeleteSet createDeleteSet() {
        return new DeleteSet();
    }

    public static DeleteSet createDeleteSetFromStructStore(StructStore ss) {
        DeleteSet ds = createDeleteSet();
        ss.getClients().forEach((client, structs) -> {
            LinkedList<DeleteItem> deleteItems = new LinkedList<>();
            for (int i = 0; i < structs.size(); i++) {
                AbstractStruct struct = structs.get(i);
                if (struct.isDeleted()) {
                    long clock = struct.id.clock;
                    long len = struct.length;
                    if (i + 1 < structs.size()) {
                        while (i + 1 < structs.size() && structs.get(i + 1).isDeleted()) {
                            AbstractStruct next = structs.get(i + 1);
                            len += next.length;
                            i++;
                        }

                    }
                    deleteItems.addLast(new DeleteItem(clock, len));
                }
            }
            if (!deleteItems.isEmpty()) {
                ds.clients.put(client, deleteItems);
            }
        });
        return ds;
    }

    public static void writeDeleteSet(DSEncoder encoder, DeleteSet ds) {
        Encoder.writeVarUint(encoder.getRestEncoder(), ds.clients.size());
        // 确保按客户端 ID 从大到小排序
        List<Map.Entry<Long, List<DeleteItem>>> sortedEntries = new ArrayList<>(ds.getClients().entrySet());
        // 降序排列客户端 ID
        sortedEntries.sort((a, b) -> Long.compare(b.getKey(), a.getKey()));
        // 遍历排序后的客户端条目
        for (Map.Entry<Long, List<DeleteItem>> entry : sortedEntries) {
            Long client = entry.getKey();
            List<DeleteItem> dsItems = entry.getValue();

            encoder.resetDsCurVal();
            Encoder.writeVarUint(encoder.getRestEncoder(), client);
            int len = entry.getValue().size();
            Encoder.writeVarUint(encoder.getRestEncoder(), len);
            for (DeleteItem item : dsItems) {
                encoder.writeDsClock(item.clock);
                encoder.writeDsLen(item.len);
            }
        }
    }

    public static DeleteSet readDeleteSet(DSDecoder decoder) {
        DeleteSet ds = new DeleteSet();
        long numClients = Decoder.readVarUint(decoder.getRestDecoder());
        for (int i = 0; i < numClients; i++) {
            decoder.resetDsCurVal();
            long client = Decoder.readVarUint(decoder.getRestDecoder());
            long numberOfDeletes = Decoder.readVarUint(decoder.getRestDecoder());
            if (numberOfDeletes > 0) {
                List<DeleteItem> dsField = ds.getClients().computeIfAbsent(client, k -> new ArrayList<>());
                // 为每个删除项创建 DeleteItem 并添加到 dsField
                for (long j = 0; j < numberOfDeletes; j++) {
                    DeleteItem item = new DeleteItem(decoder.readDsClock(), decoder.readDsLen());
                    // 将 DeleteItem 添加到客户端的删除项列表中
                    dsField.add(item);
                }
            }
        }
        return ds;
    }


    /**
     * 读取并应用 DeleteSet，同时返回未能应用的部分（作为补丁二进制）。
     *
     * @param decoder     V1 或 V2 的 DS 解码器
     * @param transaction 当前事务
     * @param store       当前结构存储
     * @return 若存在未应用的删除集，返回其编码后的字节数组；否则返回 null
     */
    public static byte[] readAndApplyDeleteSet(DSDecoder decoder, Transaction transaction, StructStore store) {
        DeleteSet unappliedDs = new DeleteSet();
        long numClients = Decoder.readVarUint(decoder.getRestDecoder());

        for (int i = 0; i < numClients; i++) {
            decoder.resetDsCurVal();
            long client = Decoder.readVarUint(decoder.getRestDecoder());
            long numDeletes = Decoder.readVarUint(decoder.getRestDecoder());

            LinkedList<AbstractStruct> structs = store.getClients().getOrDefault(client, new LinkedList<>());
            long state = StructStore.getState(store, client);

            for (int j = 0; j < numDeletes; j++) {
                long clock = decoder.readDsClock();
                long clockEnd = clock + decoder.readDsLen();

                if (clock < state) {
                    if (state < clockEnd) {
                        DeleteSet.addToDeleteSet(unappliedDs, client, state, clockEnd - state);
                    }

                    int index = StructStore.findIndexSS(structs, clock);
                    AbstractStruct struct = structs.get(index);

                    if (struct instanceof Item item && !item.isDeleted() && item.getId().getClock() < clock) {
                        Item split = Item.splitItem(transaction, item, clock - item.getId().getClock());
                        structs.add(index + 1, split);
                        index++; // increase we now want to use the next struct
                    }

                    while (index < structs.size()) {
                        struct = structs.get(index++);
                        if (struct.getId().getClock() < clockEnd) {
                            if (struct instanceof Item item && !item.isDeleted()) {
                                if (clockEnd < item.getId().getClock() + item.getLength()) {
                                    Item split = Item.splitItem(transaction, item, clockEnd - item.getId().getClock());
                                    structs.add(index, split);
                                }
                                item.delete(transaction);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    DeleteSet.addToDeleteSet(unappliedDs, client, clock, clockEnd - clock);
                }
            }
        }

        if (!unappliedDs.getClients().isEmpty()) {
            UpdateEncoderV2 dsEncoder = new UpdateEncoderV2();
            // encode 0 structs
            Encoder.writeVarUint(dsEncoder.restEncoder, 0);
            DeleteSet.writeDeleteSet(dsEncoder, unappliedDs);
            return dsEncoder.toUint8Array();
        }
        return null;
    }

    /**
     * Check if two delete sets are equal
     */
    public static boolean equalDeleteSets(DeleteSet ds1, DeleteSet ds2) {
        if (ds1.clients.size() != ds2.clients.size()) {
            return false;
        }
        for (Map.Entry<Long, List<DeleteItem>> entry : ds1.clients.entrySet()) {
            long client = entry.getKey();
            List<DeleteItem> deleteItems1 = entry.getValue();
            List<DeleteItem> deleteItems2 = ds2.clients.get(client);
            if (deleteItems2 == null || deleteItems1.size() != deleteItems2.size()) {
                return false;
            }
            for (int i = 0; i < deleteItems1.size(); i++) {
                DeleteItem di1 = deleteItems1.get(i);
                DeleteItem di2 = deleteItems2.get(i);
                if (!Objects.equals(di1.clock, di2.clock) || !Objects.equals(di1.len, di2.len)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get all clients with deletions (for compatibility)
     */
    public Set<Long> getClientsKeySet() {
        return clients.keySet();
    }


    /**
     * Add a deleted range (for compatibility)
     */
    public void add(long client, long clock, long length) {
        addToDeleteSet(client, clock, length);
    }


    public static void sortAndMergeDeleteSet(DeleteSet ds) {
        ds.clients.values().forEach(dels -> {
            dels.sort(Comparator.comparing(a -> a.clock));
            int i, j;
            for (i = 1, j = 1; i < dels.size(); i++) {
                DeleteItem left = dels.get(j - 1);
                DeleteItem right = dels.get(i);
                if (left.clock + left.len >= right.clock) {
                    left.len = Math.max(left.len, right.clock + right.len - left.clock);
                } else {
                    if (j < i) {
                        dels.set(j, right);
                    }
                    j++;
                }
            }
            while (dels.size() > j) {
                dels.removeLast();
            }
        });
    }

}
