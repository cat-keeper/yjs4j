package com.triibiotech.yjs.websocket.handler;

import com.triibiotech.yjs.protocol.sync.SyncProtocol;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * web套接字处理程序
 *
 * @author zbs
 * @date 2025/08/01  16:58:29
 */
@Component
public class WebSocketHandler extends BinaryWebSocketHandler {
    static final Logger logger = LoggerFactory.getLogger("Yjs");

    public static final int MESSAGE_SYNC = 0;
    public static final int MESSAGE_AWARENESS = 1;

    private final Map<String, WSSharedDoc> docs = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> pingInterval;

    private final AtomicBoolean pongReceived = new AtomicBoolean(true);

    @Override
    protected void handlePongMessage(@Nonnull WebSocketSession session, @Nonnull PongMessage message) throws Exception {
        super.handlePongMessage(session, message);
        pongReceived.set(true);
    }

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
        String docName = extractDocName(session);
        WSSharedDoc doc = getDoc(docName);
        doc.connections.put(session, new LinkedHashSet<>());

        this.pingInterval = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!pongReceived.get()) {
                    if (doc.connections.containsKey(session)) {
                        closeConnection(doc, session);
                    }
                    pingInterval.cancel(false);
                } else if (doc.connections.containsKey(session)) {
                    pongReceived.set(false);
                    try {
                        session.sendMessage(new PingMessage());
                    } catch (Exception e) {
                        closeConnection(doc, session);
                        pingInterval.cancel(false);
                    }
                }
            } catch (Exception e) {
                // Log error but don't stop the timer
                System.err.println("Error in awareness cleanup: " + e.getMessage());
            }
        }, 30000, 30000, TimeUnit.MILLISECONDS);

        // Send sync step 1
        Encoder encoder = Encoder.createEncoder();
        Encoder.writeVarUint(encoder, MESSAGE_SYNC);
        SyncProtocol.writeSyncStep1(encoder, doc);
        send(doc, session, Encoder.toUint8Array(encoder));
        Map<Long, Object> awarenessStates = doc.awareness.getStates();
        if (!awarenessStates.isEmpty()) {
            Encoder awarenessEncoder = Encoder.createEncoder();
            Encoder.writeVarUint(awarenessEncoder, MESSAGE_AWARENESS);
            Encoder.writeVarUint8Array(awarenessEncoder, doc.awareness.encodeAwarenessUpdate(awarenessStates.keySet().stream().toList(), null));
            send(doc, session, Encoder.toUint8Array(encoder));
        }
        String ip = session.getRemoteAddress().getAddress().getHostAddress();
        logger.info("New connection from {} to {}", ip, docName);
    }

    private String extractDocName(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(1);
    }

    private WSSharedDoc getDoc(String docName) {
        WSSharedDoc doc = docs.get(docName);
        if (doc != null) {
            return doc;
        }
        doc = new WSSharedDoc(docName);
        doc.gc = true;
        this.docs.put(docName, doc);
        return doc;
    }

    @Override
    public void handleBinaryMessage(@Nonnull WebSocketSession session, BinaryMessage message) {
        String docName = extractDocName(session);
        WSSharedDoc doc = getDoc(docName);

        Encoder encoder = Encoder.createEncoder();
        Decoder decoder = Decoder.createDecoder(message.getPayload().array());
        int messageType = Math.toIntExact(Decoder.readVarUint(decoder));
        switch (messageType) {
            case MESSAGE_SYNC: {
                Encoder.writeVarUint(encoder, MESSAGE_SYNC);
                SyncProtocol.readSyncMessage(decoder, encoder, doc, session);
                if (Encoder.length(encoder) > 1) {
                    send(doc, session, Encoder.toUint8Array(encoder));
                }
                break;
            }
            case MESSAGE_AWARENESS: {
                doc.awareness.applyAwarenessUpdate(Decoder.readVarUint8Array(decoder), session.getId());
                break;
            }
        }
    }

    @Override
    public void handleTransportError(@Nonnull WebSocketSession session, Throwable exception) {
        logger.error("", exception);
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus closeStatus) {
        String docName = extractDocName(session);
        WSSharedDoc doc = getDoc(docName);
        logger.info("Connection closed: {} ({})", docName, closeStatus.getReason());
        if (pingInterval != null) {
            pingInterval.cancel(false);
        }
        closeConnection(doc, session);
    }

    static void closeConnection(WSSharedDoc doc, WebSocketSession session) {
        if (doc.connections.containsKey(session)) {
            Set<Long> controlledIds = doc.connections.get(session);
            doc.connections.remove(session);
            doc.awareness.removeAwarenessStates(controlledIds.stream().toList(), null);
        }
        try {
            session.close();
        } catch (IOException ignored) {
        }
    }


    static void send(WSSharedDoc doc, WebSocketSession session, byte[] m) {
        if (session.isOpen()) {
            synchronized (WebSocketSession.class) {
                try {
                    session.sendMessage(new BinaryMessage(m));
                } catch (Exception e) {
                    logger.error("Error sending message to {}:", session.getId(), e);
                    closeConnection(doc, session);
                }
            }
        } else {
            closeConnection(doc, session);
        }
    }
}
