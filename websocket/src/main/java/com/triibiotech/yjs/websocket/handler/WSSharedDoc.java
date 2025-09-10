package com.triibiotech.yjs.websocket.handler;

import com.triibiotech.yjs.protocol.awareness.Awareness;
import com.triibiotech.yjs.protocol.awareness.AwarenessEventParams;
import com.triibiotech.yjs.protocol.sync.SyncProtocol;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.DocOptions;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * wsshared doc
 *
 * @author zbs
 * @date 2025/08/01  15:47:02
 */
public class WSSharedDoc extends Doc {
    protected String name;
    protected Map<WebSocketSession, Set<Long>> connections;
    protected Awareness awareness;

    public WSSharedDoc(String name) {
        super(new DocOptions().withGc(true));
        this.name = name;
        this.connections = new ConcurrentHashMap<>();
        this.awareness = new Awareness(this);
        this.awareness.setLocalState(null);
        this.awareness.on("update", this::awarenessChangeHandler);
        this.on("update", (Handler) args -> {
            byte[] update = (byte[]) args[0];
            WSSharedDoc doc = (WSSharedDoc) args[2];
            updateHandler(update, doc);
        });
    }

    private void awarenessChangeHandler(Object[] objects) {
        AwarenessEventParams params = objects[0] instanceof AwarenessEventParams ? (AwarenessEventParams) objects[0] : null;
        if (params == null) {
            return;
        }
        WebSocketSession conn = (objects.length > 1 && objects[1] instanceof WebSocketSession) ? (WebSocketSession) objects[1] : null;
        awarenessChangeHandler(params, conn);
    }

    private void awarenessChangeHandler(AwarenessEventParams params, WebSocketSession conn) {
        List<Long> changedClients = new ArrayList<>();
        changedClients.addAll(params.updated);
        changedClients.addAll(params.removed);
        if (conn != null) {
            Set<Long> connControlledIds = this.connections.get(conn);
            if (connControlledIds != null) {
                connControlledIds.addAll(params.added);
                connControlledIds.addAll(params.removed);
            }
        }
        Encoder encoder = Encoder.createEncoder();
        Encoder.writeVarUint(encoder, WebSocketHandler.MESSAGE_AWARENESS);
        Encoder.writeVarUint8Array(encoder, this.awareness.encodeAwarenessUpdate(changedClients, null));
        byte[] buff = Encoder.toUint8Array(encoder);
        this.connections.forEach((session, clients) -> {
            WebSocketHandler.send(this, session, buff);
        });

    }

    private void updateHandler(byte[] update, WSSharedDoc doc) {
        Encoder encoder = Encoder.createEncoder();
        Encoder.writeVarUint(encoder, WebSocketHandler.MESSAGE_SYNC);
        SyncProtocol.writeUpdate(encoder, update);
        byte[] message = Encoder.toUint8Array(encoder);
        doc.connections.forEach((session, clients) -> {
            WebSocketHandler.send(doc, session, message);
        });
    }
}
