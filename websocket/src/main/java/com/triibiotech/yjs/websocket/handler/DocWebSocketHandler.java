package com.triibiotech.yjs.websocket.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.triibiotech.yjs.protocol.awareness.Awareness;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * web套接字处理程序
 *
 * @author zbs
 * @date 2025/08/01  16:58:29
 */
@Component
public class DocWebSocketHandler extends BinaryWebSocketHandler {
    static final Logger log = LoggerFactory.getLogger("DocWebSocketHandler");
    /**
     * 数据同步消息
     */
    public static final int MESSAGE_SYNC = 0;
    /**
     * 意识消息
     */
    public static final int MESSAGE_AWARENESS = 1;
    /**
     * docId -> doc
     */
    private static final Map<Integer, WSSharedDoc> DOCUMENTS = new ConcurrentHashMap<>();
    /**
     * userId -> docId
     */
    private static final Map<Integer, Integer> USER_DOCUMENTS = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 心跳处理器
     */
    private ScheduledFuture<?> pingInterval;
    /**
     * 心跳响应状态
     */
    private final AtomicBoolean pongReceived = new AtomicBoolean(true);


    @Override
    protected void handlePongMessage(@Nonnull WebSocketSession session, @Nonnull PongMessage message) throws Exception {
        super.handlePongMessage(session, message);
        pongReceived.set(true);
    }

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Integer documentId = (Integer) attributes.get("documentId");
        WSSharedDoc doc = getDoc(documentId);
        USER_DOCUMENTS.put((Integer) attributes.get("userId"), documentId);
        doc.connections.put(session, new LinkedHashSet<>());
        // 心跳
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
        Map<Long, JSONObject> awarenessStates = doc.awareness.getStates();
        if (!awarenessStates.isEmpty()) {
            Encoder awarenessEncoder = Encoder.createEncoder();
            Encoder.writeVarUint(awarenessEncoder, MESSAGE_AWARENESS);
            Encoder.writeVarUint8Array(awarenessEncoder, doc.awareness.encodeAwarenessUpdate(awarenessStates.keySet().stream().toList(), null));
            send(doc, session, Encoder.toUint8Array(encoder));
        }
        String ip = Objects.requireNonNull(session.getRemoteAddress()).getAddress().getHostAddress();
        log.info("New connection from {} to {}", ip, documentId);
    }

    private static WSSharedDoc getDoc(Integer documentId) {
        WSSharedDoc doc = DOCUMENTS.get(documentId);
        if (doc != null) {
            return doc;
        }
        doc = new WSSharedDoc(documentId);
        doc.gc = true;
        DOCUMENTS.put(documentId, doc);
        return doc;
    }

    @Override
    public void handleBinaryMessage(@Nonnull WebSocketSession session, BinaryMessage message) {
        Map<String, Object> attributes = session.getAttributes();
        Integer documentId = (Integer) attributes.get("documentId");
        WSSharedDoc doc = getDoc(documentId);

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
                doc.awareness.applyAwarenessUpdate(Decoder.readVarUint8Array(decoder), session);
                break;
            }
        }
    }

    @Override
    public void handleTransportError(@Nonnull WebSocketSession session, @Nonnull Throwable exception) {
        log.error("", exception);
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus closeStatus) {
        Map<String, Object> attributes = session.getAttributes();
        Integer documentId = (Integer) attributes.get("documentId");
        USER_DOCUMENTS.remove((Integer) attributes.get("userId"), documentId);
        WSSharedDoc doc = getDoc(documentId);
        log.info("Connection closed: {} (reason: {})", documentId, closeStatus.getReason());
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
        if (doc.connections.isEmpty()) {
            DOCUMENTS.remove(doc.documentId);
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
                    log.error("Error sending message to {}:", session.getId(), e);
                    closeConnection(doc, session);
                }
            }
        } else {
            closeConnection(doc, session);
        }
    }

    public static void collaboratorUpdated(Integer documentId, List<CollaboratorChangeEvent> events) {
        if(documentId == null || !DOCUMENTS.containsKey(documentId)) {
            return;
        }
        for (CollaboratorChangeEvent event : events) {
            updateUserStateForUser(documentId, event.userId(), user -> user.put("collaboration", event));
        }
    }

    public static void editDisabledNotice(Integer documentId) {
        updateUserState(documentId, user ->
                user.put("collaboration", new CollaboratorChangeEvent(user.getInteger("userId"), false, true, false, true))
        );
    }

    public static void refreshNotice(Integer documentId) {
        updateUserState(documentId, user -> user.put("refresh", true));
    }

    private static void updateUserState(Integer documentId, java.util.function.Consumer<JSONObject> userUpdater) {
        updateUserStateForUser(documentId, null, userUpdater);
    }

    private static void updateUserStateForUser(Integer documentId, Integer targetUserId, java.util.function.Consumer<JSONObject> userUpdater) {
        WSSharedDoc doc = DOCUMENTS.get(documentId);
        if (doc == null) {
            return;
        }

        doc.awareness.states.forEach((clientId, state) -> {
            Awareness.MetaClientState metaState = doc.awareness.meta.get(clientId);
            if (state == null || metaState == null || !state.containsKey("user")) {
                return;
            }

            JSONObject user = state.getJSONObject("user");
            if (!user.containsKey("userId")) {
                return;
            }

            if (targetUserId != null && !user.getInteger("userId").equals(targetUserId)) {
                return;
            }

            userUpdater.accept(user);

            Encoder encoder = Encoder.createEncoder();
            Encoder.writeVarUint(encoder, MESSAGE_AWARENESS);
            Encoder dataEncoder = Encoder.createEncoder();
            Encoder.writeVarUint(dataEncoder, 1);
            Encoder.writeVarUint(dataEncoder, clientId);
            Encoder.writeVarUint(dataEncoder, metaState.clock() + 1);
            Encoder.writeVarString(dataEncoder, JSON.toJSONString(state));
            Encoder.writeVarUint8Array(encoder, Encoder.toUint8Array(dataEncoder));

            byte[] buff = Encoder.toUint8Array(encoder);
            doc.connections.forEach((session, clients) -> {
                if (clients.contains(clientId)) {
                    send(doc, session, buff);
                }
            });
        });
    }
}
