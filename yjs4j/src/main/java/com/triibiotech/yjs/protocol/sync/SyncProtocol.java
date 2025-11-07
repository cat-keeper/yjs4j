package com.triibiotech.yjs.protocol.sync;

import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

/**
 * Sync protocol implementation for Yjs
 * Core Yjs defines three message types:
 * • YjsSyncStep1: Includes the State Set of the sending client
 * • YjsSyncStep2: Includes all missing structs and the complete delete set
 * • YjsUpdate: Incremental updates
 *
 * @author zbs
 * @date 2025/10/27  10:36:50
 */
public class SyncProtocol {

    // Message types
    public static final int MESSAGE_YJS_SYNC_STEP1 = 0;
    public static final int MESSAGE_YJS_SYNC_STEP2 = 1;
    public static final int MESSAGE_YJS_UPDATE = 2;

    public static void writeSyncStep1(Encoder encoder, Doc doc) {
        Encoder.writeVarUint(encoder, MESSAGE_YJS_SYNC_STEP1);
        byte[] sv = EncodingUtil.encodeStateVector(doc);
        Encoder.writeVarUint8Array(encoder, sv);
    }

    public static void writeSyncStep2(Encoder encoder, Doc doc, byte[] encodedStateVector) {
        Encoder.writeVarUint(encoder, MESSAGE_YJS_SYNC_STEP2);
        byte[] sv = EncodingUtil.encodeStateAsUpdate(doc, encodedStateVector);
        Encoder.writeVarUint8Array(encoder, sv);
    }

    public static void writeUpdate(Encoder encoder, byte[] update) {
        Encoder.writeVarUint(encoder, MESSAGE_YJS_UPDATE);
        Encoder.writeVarUint8Array(encoder, update);
    }

    /**
     * Read SyncStep1 message and reply with SyncStep2
     */
    public static void readSyncStep1(Decoder decoder, Encoder encoder, Doc doc) {
        writeSyncStep2(encoder, doc, Decoder.readVarUint8Array(decoder));
    }

    /**
     * Read and apply SyncStep2 message
     */
    public static void readSyncStep2(Decoder decoder, Doc doc, Object transactionOrigin) {
        try {
            EncodingUtil.applyUpdate(doc, Decoder.readVarUint8Array(decoder), transactionOrigin);
        } catch (Exception error) {
            System.err.println("Caught error while handling a Yjs update: " + error.getMessage());
        }
    }

    public static void readUpdate(Decoder decoder, Doc doc, Object transactionOrigin) {
        readSyncStep2(decoder, doc, transactionOrigin);
    }

    /**
     * Read sync message and handle different message types
     */
    public static void readSyncMessage(Decoder decoder, Encoder encoder, Doc doc, Object transactionOrigin) {
        long messageType = Decoder.readVarUint(decoder);
        switch ((int) messageType) {
            case MESSAGE_YJS_SYNC_STEP1:
                readSyncStep1(decoder, encoder, doc);
                break;
            case MESSAGE_YJS_SYNC_STEP2:
                readSyncStep2(decoder, doc, transactionOrigin);
                break;

            case MESSAGE_YJS_UPDATE:
                readUpdate(decoder, doc, transactionOrigin);
                break;
            default:
                throw new IllegalArgumentException("Unknown message type: " + messageType);
        }
    }


}
