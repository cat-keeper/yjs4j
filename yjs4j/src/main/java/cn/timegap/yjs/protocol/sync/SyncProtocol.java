package cn.timegap.yjs.protocol.sync;

import cn.timegap.yjs.utils.Doc;
import cn.timegap.yjs.utils.encoding.EncodingUtil;
import cn.timegap.yjs.utils.lib0.decoding.Decoder;
import cn.timegap.yjs.utils.lib0.encoding.Encoder;

/**
 * SyncProtocol 实现了 Yjs 的文档同步协议（对应 y-protocols/sync）。
 *
 * <p>同步协议定义了三种消息类型：
 * <ul>
 *   <li><b>SyncStep1</b>（类型 0）：发送方的状态向量。接收方据此计算差异。</li>
 *   <li><b>SyncStep2</b>（类型 1）：包含接收方缺失的所有操作和完整的删除集。</li>
 *   <li><b>Update</b>（类型 2）：增量更新，后续的实时修改通过此消息传递。</li>
 * </ul>
 *
 * <p>完整的同步握手流程：
 * <pre>
 * Client A                          Client B
 *    |                                 |
 *    |--- SyncStep1(svA) ------------>|  A 发送自己的状态向量
 *    |                                 |
 *    |<-- SyncStep1(svB) -------------|  B 也发送自己的状态向量
 *    |<-- SyncStep2(diff for A) ------|  B 根据 svA 计算 A 缺失的数据并发送
 *    |                                 |
 *    |--- SyncStep2(diff for B) ----->|  A 根据 svB 计算 B 缺失的数据并发送
 *    |                                 |
 *    |--- Update(incremental) ------->|  后续的实时增量更新
 *    |<-- Update(incremental) --------|
 * </pre>
 *
 * <p>对应 JS 版本的 y-protocols/sync。
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
