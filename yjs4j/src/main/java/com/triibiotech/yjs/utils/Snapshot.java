package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.*;

/**
 * Snapshot represents a state of the document at a specific point in time
 *
 * @author zbs
 * @date 2025/07/30  16:19:14
 */
@SuppressWarnings("unused")
public class Snapshot {
    /**
     * Delete set at snapshot time
     */
    public DeleteSet ds;
    /**
     * State vector at snapshot time
     */
    public Map<Long, Long> sv;

    public Snapshot(DeleteSet ds, Map<Long, Long> sv) {
        this.ds = ds;
        this.sv = new HashMap<>(sv);
    }

    public DeleteSet getDs() {
        return ds;
    }

    public void setDs(DeleteSet ds) {
        this.ds = ds;
    }

    public Map<Long, Long> getSv() {
        return sv;
    }

    public void setSv(Map<Long, Long> sv) {
        this.sv = sv;
    }

    public static boolean equalSnapshots(Snapshot snap1, Snapshot snap2) {
        SafeLinkedHashMap<Long, List<DeleteItem>> ds1 = snap1.ds.clients;
        SafeLinkedHashMap<Long, List<DeleteItem>> ds2 = snap2.ds.clients;
        Map<Long, Long> sv1 = snap1.sv;
        Map<Long, Long> sv2 = snap2.sv;

        if (sv1.size() != sv2.size() || ds1.size() != ds2.size()) {
            return false;
        }

        for (Map.Entry<Long, Long> entry : sv1.entrySet()) {
            if (!Objects.equals(sv2.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }

        for (Map.Entry<Long, List<DeleteItem>> entry : ds1.entrySet()) {
            Long client = entry.getKey();
            List<DeleteItem> dsitems1 = entry.getValue();
            List<DeleteItem> dsitems2 = ds2.getOrDefault(client, new ArrayList<>());

            if (dsitems1.size() != dsitems2.size()) {
                return false;
            }

            for (int i = 0; i < dsitems1.size(); i++) {
                DeleteItem dsitem1 = dsitems1.get(i);
                DeleteItem dsitem2 = dsitems2.get(i);
                if (!Objects.equals(dsitem1.clock, dsitem2.clock) || !Objects.equals(dsitem1.len, dsitem2.len)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static byte[] encodeSnapshotV2(Snapshot snapshot, DSEncoder encoder) {
        if (encoder == null) {
            encoder = new UpdateEncoderV2();
        }
        DeleteSet.writeDeleteSet(encoder, snapshot.ds);
        EncodingUtil.writeStateVector(encoder, snapshot.sv);
        return encoder.toUint8Array();
    }

    public static byte[] encodeSnapshot(Snapshot snapshot) {
        return encodeSnapshotV2(snapshot, new UpdateEncoderV1());
    }

    public static Snapshot decodeSnapshotV2(byte[] buf, DSDecoder decoder) {
        if (decoder == null) {
            decoder = new UpdateDecoderV2(Decoder.createDecoder(buf));
        }
        return new Snapshot(DeleteSet.readDeleteSet(decoder), EncodingUtil.readStateVector(decoder));
    }

    public static Snapshot decodeSnapshot(byte[] buf) {
        return decodeSnapshotV2(buf, new UpdateDecoderV1(Decoder.createDecoder(buf)));
    }

    public static Snapshot createSnapshot(DeleteSet ds, Map<Long, Long> sm) {
        return new Snapshot(ds, sm);
    }

    public static final Snapshot emptySnapshot = createSnapshot(DeleteSet.createDeleteSet(), new HashMap<>());

    public static Snapshot snapshot(Doc doc) {
        return createSnapshot(DeleteSet.createDeleteSetFromStructStore(doc.store), doc.store.getStateVector());
    }


    public static boolean isVisible(Item item, Snapshot snapshot) {
        if (snapshot == null) {
            return !item.isDeleted();
        }
        return snapshot.sv.containsKey(item.id.client) &&
                (snapshot.sv.getOrDefault(item.id.client, 0L) > item.id.clock) &&
                !snapshot.ds.isDeleted(item.id);
    }


    @SuppressWarnings("unchecked")
    public static void splitSnapshotAffectedStructs(Transaction transaction, Snapshot snapshot) {
        Set<Snapshot> meta;
        if (transaction.meta.containsKey("splitSnapshotAffectedStructs")) {
            meta = (Set<Snapshot>) transaction.meta.get("splitSnapshotAffectedStructs");
        } else {
            meta = new LinkedHashSet<>();
            transaction.meta.put("splitSnapshotAffectedStructs", meta);
        }
        StructStore store = transaction.doc.getStore();
        if (!meta.contains(snapshot)) {
            snapshot.sv.forEach((client, clock) -> {
                if (clock < store.getState(client)) {
                    StructStore.getItemCleanStart(transaction, ID.createId(client, clock));
                }
            });
            DeleteSet.iterateDeletedStructs(transaction, snapshot.ds, item -> {
            });
            meta.add(snapshot);
        }
    }

    public static Doc createDocFromSnapshot(Doc originDoc, Snapshot snapshot, Doc newDoc) {
        if (newDoc == null) {
            newDoc = new Doc();
        }

        if (originDoc.gc) {
            throw new Error("Garbage-collection must be disabled in `originDoc`!");
        }

        DeleteSet ds = snapshot.ds;
        Map<Long, Long> sv = snapshot.sv;

        UpdateEncoderV2 encoder = new UpdateEncoderV2();

        originDoc.transact(transaction -> {
            long size = 0L;
            for (Long clock : sv.values()) {
                if (clock > 0) {
                    size++;
                }
            }
            Encoder.writeVarUint(encoder.getRestEncoder(), size);

            for (Map.Entry<Long, Long> entry : sv.entrySet()) {
                Long client = entry.getKey();
                Long clock = entry.getValue();

                if (clock == 0) {
                    continue;
                }

                if (clock < originDoc.store.getState(client)) {
                    StructStore.getItemCleanStart(transaction, ID.createId(client, clock));
                }

                LinkedList<AbstractStruct> structs = originDoc.store.getClients().getOrDefault(client, new LinkedList<>());
                int lastStructIndex = StructStore.findIndexSS(structs, clock - 1);

                // write # encoded structs
                Encoder.writeVarUint(encoder.restEncoder, lastStructIndex + 1);
                encoder.writeClient(client);
                // first clock written is 0
                Encoder.writeVarUint(encoder.restEncoder, 0);

                for (int i = 0; i <= lastStructIndex; i++) {
                    structs.get(i).write(encoder, 0);
                }
            }
            DeleteSet.writeDeleteSet(encoder, ds);
            return null;
        });

        EncodingUtil.applyUpdateV2(newDoc, encoder.toUint8Array(), "snapshot", null);
        return newDoc;
    }

    public static boolean snapshotContainsUpdateV2(Snapshot snapshot, byte[] update, Class<? extends DSDecoder> decoder) {
        if (decoder == null) {
            decoder = UpdateDecoderV2.class;
        }
        DSDecoder updateDecoder;
        try {
            updateDecoder = decoder.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder, false);
        for (AbstractStruct curr = lazyDecoder.current(); curr != null; curr = lazyDecoder.next()) {
            if ((snapshot.sv.get(curr.id.client) == null ? 0L : snapshot.sv.get(curr.id.client)) < curr.id.clock + curr.length) {
                return false;
            }
        }
        DeleteSet updateDs = DeleteSet.readDeleteSet(updateDecoder);
        DeleteSet mergedDs = DeleteSet.mergeDeleteSets(Arrays.asList(snapshot.ds, updateDs));
        return DeleteSet.equalDeleteSets(snapshot.ds, mergedDs);
    }


    public static boolean snapshotContainsUpdate(Snapshot snapshot, byte[] update) {
        return snapshotContainsUpdateV2(snapshot, update, UpdateDecoderV1.class);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Snapshot snapshot = (Snapshot) obj;
        return equalSnapshots(this, snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ds.clients, sv);
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                "ds=" + ds +
                ", sv=" + sv +
                '}';
    }
}
