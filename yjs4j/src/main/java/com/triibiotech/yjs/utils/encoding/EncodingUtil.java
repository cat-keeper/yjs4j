package com.triibiotech.yjs.utils.encoding;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.GC;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.structs.Skip;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.utils.*;
import com.triibiotech.yjs.utils.lib0.Binary;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author zbs
 * @date 2025/7/29 15:15
 **/
@SuppressWarnings("unused")
public class EncodingUtil {

    public static Logger log = LoggerFactory.getLogger("Yjs");

    /**
     * Write structs starting with ID(client,clock)
     *
     * @param encoder UpdateEncoder instance
     * @param structs All structs by client
     * @param client  Client ID
     * @param clock   Write structs starting with ID(client,clock)
     */
    public static void writeStructs(DSEncoder encoder, List<AbstractStruct> structs, long client, long clock) {
        // write first id
        // make sure the first id exists
        clock = Math.max(clock, structs.get(0).getId().getClock());
        int startNewStructs = StructStore.findIndexSS(structs, clock);
        // write # encoded structs
        Encoder.writeVarUint(encoder.getRestEncoder(), structs.size() - startNewStructs);
        encoder.writeClient(client);
        Encoder.writeVarUint(encoder.getRestEncoder(), clock);
        AbstractStruct firstStruct = structs.get(startNewStructs);
        // write first struct with an offset
        firstStruct.write(encoder, clock - firstStruct.getId().getClock());
        for (int i = startNewStructs + 1; i < structs.size(); i++) {
            structs.get(i).write(encoder, 0);
        }
    }

    /**
     * Write clients structs to encoder
     *
     * @param encoder  UpdateEncoder instance
     * @param store    StructStore instance
     * @param stateMap State map
     */
    public static void writeClientsStructs(DSEncoder encoder, StructStore store, Map<Long, Long> stateMap) {
        // we filter all valid stateMap entries into sm
        Map<Long, Long> sm = new HashMap<>();
        stateMap.forEach((client, clock) -> {
            // only write if new structs are available
            if (StructStore.getState(store, client) > clock) {
                sm.put(client, clock);
            }
        });
        StructStore.getStateVector(store).forEach((client, clock) -> {
            if (!stateMap.containsKey(client)) {
                sm.put(client, 0L);
            }
        });
        // write # states that were updated
        Encoder.writeVarUint(encoder.getRestEncoder(), sm.size());
        // Write items with higher client ids first
        // This heavily improves the conflict algorithm.
        sm.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getKey(), a.getKey()))
                .forEach(entry -> {
                    long client = entry.getKey();
                    long clock = entry.getValue();
                    List<AbstractStruct> clientStructs = store.getClients().get(client);
                    if (clientStructs != null) {
                        writeStructs(encoder, clientStructs, client, clock);
                    }
                });
    }

    public static class ClientStructRefs {
        public int i;
        List<AbstractStruct> refs;

        public ClientStructRefs(int i, List<AbstractStruct> refs) {
            this.i = i;
            this.refs = refs;
        }
    }

    /**
     * Read clients struct refs from decoder
     *
     * @param decoder DSDecoder instance
     * @param doc     Document instance
     * @return Map of client refs
     */
    public static Map<Long, ClientStructRefs> readClientsStructRefs(DSDecoder decoder, Doc doc) {
        Map<Long, ClientStructRefs> clientRefs = new LinkedHashMap<>();
        int numOfStateUpdates = (int) Decoder.readVarUint(decoder.getRestDecoder());
        for (int i = 0; i < numOfStateUpdates; i++) {
            int numberOfStructs = (int) Decoder.readVarUint(decoder.getRestDecoder());
            List<AbstractStruct> refs = new ArrayList<>(numberOfStructs);
            long client = decoder.readClient();
            long clock = Decoder.readVarUint(decoder.getRestDecoder());
            clientRefs.put(client, new ClientStructRefs(0, refs));
            for (int j = 0; j < numberOfStructs; j++) {
                int info = decoder.readInfo();
                switch (Binary.BITS5 & info) {
                    case 0: { // GC
                        long len = decoder.readLen();
                        refs.add(new GC(ID.createId(client, clock), len));
                        clock += len;
                        break;
                    }
                    case 10: { // Skip Struct
                        int len = (int) Decoder.readVarUint(decoder.getRestDecoder());
                        refs.add(new Skip(ID.createId(client, clock), len));
                        clock += len;
                        break;
                    }
                    default: { // Item with content
                        boolean cantCopyParentInfo = (info & (Binary.BIT7 | Binary.BIT8)) == 0;
                        Item struct = new Item(
                                ID.createId(client, clock),
                                null, // left
                                (info & Binary.BIT8) == Binary.BIT8 ? decoder.readLeftId() : null, // origin
                                null, // right
                                (info & Binary.BIT7) == Binary.BIT7 ? decoder.readRightId() : null, // right origin
                                cantCopyParentInfo ? (decoder.readParentInfo() ? doc.get(decoder.readString(), AbstractType.class) : decoder.readLeftId()) : null, // parent
                                cantCopyParentInfo && (info & Binary.BIT6) == Binary.BIT6 ? decoder.readString() : null, // parentSub
                                Item.readItemContent(decoder, info) // item content
                        );
                        refs.add(struct);
                        clock += struct.getLength();
                    }
                }
            }
        }
        return clientRefs;
    }

    /**
     * Integrate structs into the document
     *
     * @param transaction       Transaction instance
     * @param store             StructStore instance
     * @param clientsStructRefs Client struct refs
     * @return Integration result
     */
    public static StructStore.PendingStructs integrateStructs(Transaction transaction, StructStore store, Map<Long, ClientStructRefs> clientsStructRefs) {
        LinkedList<AbstractStruct> stack = new LinkedList<>();
        List<Long> clientsStructRefsIds = new ArrayList<>(clientsStructRefs.keySet());
        clientsStructRefsIds.sort(Long::compareTo);

        if (clientsStructRefsIds.isEmpty()) {
            return null;
        }

        ClientStructRefs curStructsTarget = getNextStructTarget(clientsStructRefs, clientsStructRefsIds);
        if (curStructsTarget == null) {
            return null;
        }

        StructStore restStructs = new StructStore();
        Map<Long, Long> missingSV = new HashMap<>();

        AbstractStruct stackHead = curStructsTarget.refs.get(curStructsTarget.i++);
        Map<Long, Long> state = new LinkedHashMap<>();

        while (true) {
            if (!(stackHead instanceof Skip)) {
                AbstractStruct finalStackHead = stackHead;
                long localClock = state.computeIfAbsent(stackHead.getId().getClient(),
                        k -> StructStore.getState(store, finalStackHead.getId().getClient()));
                long offset = localClock - stackHead.getId().getClock();

                if (offset < 0) {
                    // update from the same client is missing
                    stack.addLast(stackHead);
                    updateMissingSv(missingSV, stackHead.getId().getClient(), stackHead.getId().getClock() - 1);
                    addStackToRestSS(stack, restStructs, clientsStructRefs, clientsStructRefsIds);
                } else {
                    Long missing = stackHead.getMissing(transaction, store);
                    if (missing != null) {
                        stack.addLast(stackHead);
                        ClientStructRefs structRefs = clientsStructRefs.getOrDefault(missing,
                                new ClientStructRefs(0, new ArrayList<>()));
                        if (structRefs.refs.size() == structRefs.i) {
                            updateMissingSv(missingSV, missing, StructStore.getState(store, missing));
                            addStackToRestSS(stack, restStructs, clientsStructRefs, clientsStructRefsIds);
                        } else {
                            stackHead = structRefs.refs.get(structRefs.i++);
                            continue;
                        }
                    } else if (offset == 0 || offset < stackHead.getLength()) {
                        // all fine, apply the stackhead
                        stackHead.integrate(transaction, (int) offset);
                        state.put(stackHead.getId().getClient(), stackHead.getId().getClock() + stackHead.getLength());
                    }
                }
            }

            // iterate to next stackHead
            if (!stack.isEmpty()) {
                stackHead = stack.removeLast();
            } else if (curStructsTarget != null && curStructsTarget.i < curStructsTarget.refs.size()) {
                stackHead = curStructsTarget.refs.get(curStructsTarget.i++);
            } else {
                curStructsTarget = getNextStructTarget(clientsStructRefs, clientsStructRefsIds);
                if (curStructsTarget == null) {
                    break;
                } else {
                    stackHead = curStructsTarget.refs.get(curStructsTarget.i++);
                }
            }
        }

        if (!restStructs.getClients().isEmpty()) {
            UpdateEncoderV2 encoder = new UpdateEncoderV2();
            writeClientsStructs(encoder, restStructs, new HashMap<>());
            Encoder.writeVarUint(encoder.getRestEncoder(), 0);
            return new StructStore.PendingStructs(missingSV, encoder.toUint8Array());
        }
        return null;
    }

    private static void addStackToRestSS(LinkedList<AbstractStruct> stack, StructStore restStructs,
                                         Map<Long, ClientStructRefs> clientsStructRefs, List<Long> clientsStructRefsIds) {
        for (AbstractStruct item : stack) {
            long client = item.getId().getClient();
            ClientStructRefs inapplicableItems = clientsStructRefs.get(client);
            if (inapplicableItems != null) {
                // decrement because we weren't able to apply previous operation
                inapplicableItems.i--;
                restStructs.getClients().put(client, new LinkedList<>(inapplicableItems.refs.subList(inapplicableItems.i, inapplicableItems.refs.size())));
                clientsStructRefs.remove(client);
                inapplicableItems.i = 0;
                inapplicableItems.refs.clear();
            } else {
                // item was the last item on clientsStructRefs and the field was already cleared
                restStructs.getClients().put(client, new LinkedList<>(List.of(item)));
            }
            // remove client from clientsStructRefsIds
            clientsStructRefsIds.removeIf(c -> c.equals(client));
        }
        stack.clear();
    }

    private static ClientStructRefs getNextStructTarget(Map<Long, ClientStructRefs> clientsStructRefs, List<Long> clientsStructRefsIds) {
        if (clientsStructRefsIds.isEmpty()) {
            return null;
        }
        ClientStructRefs nextStructsTarget = clientsStructRefs.get(clientsStructRefsIds.get(clientsStructRefsIds.size() - 1));
        while (nextStructsTarget.refs.size() == nextStructsTarget.i) {
            clientsStructRefsIds.remove(clientsStructRefsIds.size() - 1);
            if (!clientsStructRefsIds.isEmpty()) {
                nextStructsTarget = clientsStructRefs.get(clientsStructRefsIds.get(clientsStructRefsIds.size() - 1));
            } else {
                return null;
            }
        }
        return nextStructsTarget;
    }

    private static void updateMissingSv(Map<Long, Long> missingSV, long client, long clock) {
        Long mclock = missingSV.get(client);
        if (mclock == null || mclock > clock) {
            missingSV.put(client, clock);
        }
    }

    /**
     * Write structs from transaction
     *
     * @param encoder     UpdateEncoder instance
     * @param transaction Transaction instance
     */
    public static void writeStructsFromTransaction(DSEncoder encoder, Transaction transaction) {
        writeClientsStructs(encoder, transaction.getDoc().getStore(), transaction.getBeforeState());
    }

    /**
     * Read and apply document update V2
     *
     * @param decoder           Decoder instance
     * @param ydoc              Document instance
     * @param transactionOrigin Transaction origin
     * @param structDecoder     DSDecoder instance
     */
    public static void readUpdateV2(Decoder decoder, Doc ydoc, Object transactionOrigin, DSDecoder structDecoder) {
        if (structDecoder == null) {
            structDecoder = new UpdateDecoderV2(decoder);
        }

        final DSDecoder finalStructDecoder = structDecoder;
        Transaction.transact(ydoc, transaction -> {
            transaction.local = false;
            boolean retry = false;
            Doc doc = transaction.getDoc();
            StructStore store = doc.getStore();

            Map<Long, ClientStructRefs> ss = readClientsStructRefs(finalStructDecoder, doc);
            StructStore.PendingStructs restStructs = integrateStructs(transaction, store, ss);

            StructStore.PendingStructs pending = store.getPendingStructs();
            if (pending != null) {
                // check if we can apply something
                for (Map.Entry<Long, Long> entry : pending.missing.entrySet()) {
                    if (entry.getValue() < StructStore.getState(store, entry.getKey())) {
                        retry = true;
                        break;
                    }
                }
                if (restStructs != null) {
                    // merge restStructs into store.pending
                    for (Map.Entry<Long, Long> entry : restStructs.missing.entrySet()) {
                        Long mclock = pending.missing.get(entry.getKey());
                        if (mclock == null || mclock > entry.getValue()) {
                            pending.missing.put(entry.getKey(), entry.getValue());
                        }
                    }
                    pending.update = UpdateProcessor.mergeUpdatesV2(Arrays.asList(pending.update, restStructs.update), null, null);
                }
            } else {
                store.setPendingStructs(restStructs != null ?
                        new StructStore.PendingStructs(restStructs.missing, restStructs.update) : null);
            }

            byte[] dsRest = DeleteSet.readAndApplyDeleteSet(finalStructDecoder, transaction, store);
            if (store.getPendingDs() != null) {
                UpdateDecoderV2 pendingDsUpdate = new UpdateDecoderV2(Decoder.createDecoder(store.getPendingDs()));
                Decoder.readVarUint(pendingDsUpdate.getRestDecoder()); // read 0 structs
                byte[] dsRest2 = DeleteSet.readAndApplyDeleteSet(pendingDsUpdate, transaction, store);
                if (dsRest != null && dsRest2 != null) {
                    store.setPendingDs(UpdateProcessor.mergeUpdatesV2(Arrays.asList(dsRest, dsRest2), null, null));
                } else {
                    store.setPendingDs(dsRest != null ? dsRest : dsRest2);
                }
            } else {
                store.setPendingDs(dsRest);
            }

            if (retry) {
                byte[] update = store.getPendingStructs().update;
                store.setPendingStructs(null);
                applyUpdateV2(transaction.getDoc(), update);
            }
            return true;
        }, transactionOrigin, false);
    }

    /**
     * Read and apply document update V1
     *
     * @param decoder           Decoder instance
     * @param ydoc              Document instance
     * @param transactionOrigin Transaction origin
     */
    public static void readUpdate(Decoder decoder, Doc ydoc, Object transactionOrigin) {
        readUpdateV2(decoder, ydoc, transactionOrigin, new UpdateDecoderV1(decoder));
    }

    /**
     * Apply document update V2
     *
     * @param ydoc              Document instance
     * @param update            Update bytes
     * @param transactionOrigin Transaction origin
     * @param decoderClass      Decoder class
     */
    public static void applyUpdateV2(Doc ydoc, byte[] update, Object transactionOrigin, Class<? extends DSDecoder> decoderClass) {
        if (decoderClass == null) {
            decoderClass = UpdateDecoderV2.class;
        }
        Decoder decoder = Decoder.createDecoder(update);
        try {
            DSDecoder structDecoder = decoderClass.getConstructor(Decoder.class).newInstance(decoder);
            readUpdateV2(decoder, ydoc, transactionOrigin, structDecoder);
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    public static void applyUpdateV2(Doc ydoc, byte[] update) {
        applyUpdateV2(ydoc, update, null, UpdateDecoderV2.class);
    }

    /**
     * Apply document update V1
     *
     * @param ydoc              Document instance
     * @param update            Update bytes
     * @param transactionOrigin Transaction origin
     */
    public static void applyUpdate(Doc ydoc, byte[] update, Object transactionOrigin) {
        applyUpdateV2(ydoc, update, transactionOrigin, UpdateDecoderV1.class);
    }

    public static void applyUpdate(Doc ydoc, byte[] update) {
        applyUpdate(ydoc, update, null);
    }

    /**
     * Write state as update
     *
     * @param encoder           UpdateEncoder instance
     * @param doc               Document instance
     * @param targetStateVector Target state vector
     */
    public static void writeStateAsUpdate(DSEncoder encoder, Doc doc, Map<Long, Long> targetStateVector) {
        if (targetStateVector == null) {
            targetStateVector = new HashMap<>();
        }
        writeClientsStructs(encoder, doc.getStore(), targetStateVector);
        DeleteSet.writeDeleteSet(encoder, DeleteSet.createDeleteSetFromStructStore(doc.getStore()));
    }

    /**
     * Write all the document as a single update message that can be applied on the remote document. If you specify the state of the remote client (`targetState`) it will
     * only write the operations that are missing.
     * Use `writeStateAsUpdate` instead if you are working with lib0/encoding.js#Encoder
     */
    public static byte[] encodeStateAsUpdateV2(Doc doc, byte[] encodedTargetStateVector, DSEncoder encoder) {
        if (encodedTargetStateVector == null) {
            encodedTargetStateVector = new byte[]{0};
        }
        if (encoder == null) {
            encoder = new UpdateEncoderV2();
        }

        Map<Long, Long> targetStateVector = decodeStateVector(encodedTargetStateVector);
        writeStateAsUpdate(encoder, doc, targetStateVector);
        List<byte[]> updates = new ArrayList<>();
        updates.add(encoder.toUint8Array());
        if (doc.store.pendingDs != null) {
            updates.add(doc.store.pendingDs);
        }
        if (doc.store.pendingStructs != null) {
            updates.add(UpdateProcessor.diffUpdateV2(doc.store.pendingStructs.update, encodedTargetStateVector, null, null));
        }

        if (updates.size() > 1) {
            if (encoder instanceof UpdateEncoderV1) {
                List<byte[]> convertedUpdates = new ArrayList<>();
                for (int i = 0; i < updates.size(); i++) {
                    convertedUpdates.add(i == 0 ? updates.get(i) : UpdateProcessor.convertUpdateFormatV2ToV1(updates.get(i)));
                }
                return UpdateProcessor.mergeUpdates(convertedUpdates);
            } else if (encoder instanceof UpdateEncoderV2) {
                return UpdateProcessor.mergeUpdatesV2(updates, null, null);
            }
        }
        return updates.get(0);
    }

    /**
     * Encode state as update V1
     *
     * @param doc                      Document instance
     * @param encodedTargetStateVector Encoded target state vector
     * @return Encoded update bytes
     */
    public static byte[] encodeStateAsUpdate(Doc doc, byte[] encodedTargetStateVector) {
        return encodeStateAsUpdateV2(doc, encodedTargetStateVector, new UpdateEncoderV1());
    }

    /**
     * Read state vector from decoder
     *
     * @param decoder DSDecoder instance
     * @return State vector map
     */
    public static Map<Long, Long> readStateVector(DSDecoder decoder) {
        Map<Long, Long> ss = new HashMap<>();
        long ssLength = Decoder.readVarUint(decoder.getRestDecoder());
        for (long i = 0; i < ssLength; i++) {
            long client = Decoder.readVarUint(decoder.getRestDecoder());
            long clock = Decoder.readVarUint(decoder.getRestDecoder());
            ss.put(client, clock);
        }
        return ss;
    }

    /**
     * Read decodedState and return State as Map.
     *
     * @param decodedState Decoded state bytes
     * @return State vector map
     */
    public static Map<Long, Long> decodeStateVector(byte[] decodedState) {
        return readStateVector(new UpdateDecoderV1(Decoder.createDecoder(decodedState)));
    }

    /**
     * Write state vector to encoder
     *
     * @param encoder DSEncoder instance
     * @param sv      State vector map
     */
    public static void writeStateVector(DSEncoder encoder, Map<Long, Long> sv) {
        Encoder.writeVarUint(encoder.getRestEncoder(), sv.size());
        sv.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getKey(), a.getKey()))
                .forEach(entry -> {
                    Encoder.writeVarUint(encoder.getRestEncoder(), entry.getKey());
                    Encoder.writeVarUint(encoder.getRestEncoder(), entry.getValue());
                });
    }

    /**
     * Write document state vector
     *
     * @param encoder DSEncoder instance
     * @param doc     Document instance
     */
    public static void writeDocumentStateVector(DSEncoder encoder, Doc doc) {
        writeStateVector(encoder, StructStore.getStateVector(doc.getStore()));
    }

    /**
     * Encode state vector V2
     *
     * @param doc     Document or state vector map
     * @param encoder DSEncoder instance
     * @return Encoded state vector bytes
     */
    public static byte[] encodeStateVectorV2(Object doc, DSEncoder encoder) {
        if (encoder == null) {
            encoder = new UpdateEncoderV2();
        }
        if (doc instanceof Map) {
            writeStateVector(encoder, (Map<Long, Long>) doc);
        } else {
            writeDocumentStateVector(encoder, (Doc) doc);
        }
        return encoder.toUint8Array();
    }

    /**
     * Encode state vector V1
     *
     * @param doc Document or state vector map
     * @return Encoded state vector bytes
     */
    public static byte[] encodeStateVector(Object doc) {
        return encodeStateVectorV2(doc, new UpdateEncoderV1());
    }


}
