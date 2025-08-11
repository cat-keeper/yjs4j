package com.triibiotech.yjs.protocol.awareness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.Observable;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * The Awareness class implements a simple shared state protocol that can be used for non-persistent data like awareness information
 * (cursor, username, status, ..). Each client can update its own local state and listen to state changes of
 * remote clients. Every client may set a state of a remote peer to null to mark the client as offline.
 *
 * @author zbs
 * @date 2025/08/01  16:09:03
 */
public class Awareness extends Observable<String> {
    private final Doc doc;
    private final long clientId;
    /**
     * Maps from client id to client state
     */
    public final Map<Long, Object> states = new ConcurrentHashMap<>();
    public final Map<Long, MetaClientState> meta = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> checkInterval;

    public static final long OUTDATED_TIMEOUT = 30000;

    /**
     * Meta client state
     */
    public record MetaClientState(long clock, long lastUpdated) {
    }

    /**
     * Constructor
     */
    public Awareness(Doc doc) {
        this.doc = doc;
        this.clientId = doc.getClientId();
        // Start cleanup interval - runs every 3 seconds (OUTDATED_TIMEOUT / 10)
        this.checkInterval = scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                Object localState = getLocalState();

                // Renew local state if it's getting stale (after 15 seconds)
                if (localState != null) {
                    MetaClientState localMeta = meta.get(clientId);
                    if (localMeta != null && (OUTDATED_TIMEOUT / 2 <= now - localMeta.lastUpdated)) {
                        this.setLocalState(localState);
                    }
                }

                // Remove outdated remote clients (after 30 seconds)
                List<Long> toRemove = new ArrayList<>();
                meta.forEach((clientId, metaState) -> {
                    if (!clientId.equals(this.clientId) &&
                            OUTDATED_TIMEOUT <= now - metaState.lastUpdated &&
                            states.containsKey(clientId)) {
                        toRemove.add(clientId);
                    }
                });

                if (!toRemove.isEmpty()) {
                    removeAwarenessStates(toRemove, "timeout");
                }
            } catch (Exception e) {
                // Log error but don't stop the timer
                System.err.println("Error in awareness cleanup: " + e.getMessage());
            }
        }, OUTDATED_TIMEOUT / 10, OUTDATED_TIMEOUT / 10, TimeUnit.MILLISECONDS);

        doc.on("destroy", this::destroy);
        setLocalState(new HashMap<>());
    }

    /**
     * Destroy the awareness instance
     */
    @Override
    public void destroy() {
        this.emit("destroy", this);
        setLocalState(null);
        super.destroy();
        if (checkInterval != null) {
            checkInterval.cancel(false);
        }
        scheduler.shutdown();
    }

    /**
     * Get local state
     */
    public Object getLocalState() {
        return states.get(clientId);
    }

    /**
     * Set local state
     */
    public void setLocalState(Object state) {
        MetaClientState currLocalMeta = meta.get(clientId);
        long clock = currLocalMeta == null ? 0L : currLocalMeta.clock + 1;
        Object prevState = states.get(clientId);

        if (state == null) {
            states.remove(clientId);
        } else {
            states.put(clientId, state);
        }

        meta.put(clientId, new MetaClientState(clock, System.currentTimeMillis()));

        List<Long> added = new ArrayList<>();
        List<Long> updated = new ArrayList<>();
        List<Long> filteredUpdated = new ArrayList<>();
        List<Long> removed = new ArrayList<>();

        if (state == null) {
            removed.add(clientId);
        } else if (prevState == null) {
            added.add(clientId);
        } else {
            updated.add(clientId);
            if (!Objects.equals(prevState, state)) {
                filteredUpdated.add(clientId);
            }
        }

        if (!added.isEmpty() || !filteredUpdated.isEmpty() || !removed.isEmpty()) {
            emit("change", new AwarenessEventParams(added, filteredUpdated, removed, "local"));
        }
        emit("update", new AwarenessEventParams(added, updated, removed, "local"));
    }

    /**
     * Set local state field
     */
    public void setLocalStateField(String field, Object value) {
        Object state = getLocalState();
        if (state != null) {
            Map<String, Object> stateMap = JSON.parseObject(JSON.toJSONString(state), new TypeReference<Map<String, Object>>() {
            });
            stateMap.put(field, value);
            setLocalState(stateMap);
        }
    }

    /**
     * Get all states
     */
    public Map<Long, Object> getStates() {
        return new HashMap<>(states);
    }

    /**
     * Get client ID
     */
    public long getClientId() {
        return clientId;
    }

    /**
     * Remove awareness states
     */
    public void removeAwarenessStates(List<Long> clients, String origin) {
        List<Long> removed = new ArrayList<>();
        for (Long clientId : clients) {
            if (states.containsKey(clientId)) {
                states.remove(clientId);
                if (clientId == this.clientId) {
                    MetaClientState curMeta = meta.get(clientId);
                    if (curMeta != null) {
                        meta.put(clientId, new MetaClientState(curMeta.clock + 1, System.currentTimeMillis()));
                    }
                }
                removed.add(clientId);
            }
        }

        if (!removed.isEmpty()) {
            this.emit("change", new AwarenessEventParams(new ArrayList<>(), new ArrayList<>(), removed, origin));
            this.emit("update", new AwarenessEventParams(new ArrayList<>(), new ArrayList<>(), removed, origin));
        }
    }

    public byte[] encodeAwarenessUpdate(List<Long> clients, Map<Long, Object> states) {
        if (states == null) {
            states = this.states;
        }
        int len = clients.size();
        Encoder encoder = Encoder.createEncoder();
        Encoder.writeVarUint(encoder, len);
        for (int i = 0; i < len; i++) {
            Long clientId = clients.get(i);
            Object state = states.get(clientId);
            long clock = meta.get(clientId).clock;
            Encoder.writeVarUint(encoder, clientId);
            Encoder.writeVarUint(encoder, clock);
            Encoder.writeVarString(encoder, JSON.toJSONString(state));
        }
        return Encoder.toUint8Array(encoder);
    }

    public static byte[] modifyAwarenessUpdate(byte[] update, Function<Object, Object> modify) {
        Decoder decoder = Decoder.createDecoder(update);
        Encoder encoder = Encoder.createEncoder();
        long len = Decoder.readVarUint(decoder);
        Encoder.writeVarUint(encoder, len);
        for (long i = 0; i < len; i++) {
            long clientId = Decoder.readVarUint(decoder);
            long clock = Decoder.readVarUint(decoder);
            Object state = JSON.parse(Decoder.readVarString(decoder));
            Object modifiedState = modify.apply(state);
            Encoder.writeVarUint(encoder, clientId);
            Encoder.writeVarUint(encoder, clock);
            Encoder.writeVarString(encoder, JSON.toJSONString(modifiedState));
        }
        return Encoder.toUint8Array(encoder);
    }

    public void applyAwarenessUpdate(byte[] update, Object origin) {
        Decoder decoder = Decoder.createDecoder(update);
        long timestamp = System.currentTimeMillis();
        List<Long> added = new ArrayList<>();
        List<Long> updated = new ArrayList<>();
        List<Long> filteredUpdated = new ArrayList<>();
        List<Long> removed = new ArrayList<>();

        long len = Decoder.readVarUint(decoder);
        for (int i = 0; i < len; i++) {
            long clientId = Decoder.readVarUint(decoder);
            long clock = Decoder.readVarUint(decoder);
            String json = Decoder.readVarString(decoder);
            Object state = "null".equals(json) ? null : JSON.parse(json);

            MetaClientState clientMeta = meta.get(clientId) == null ? null : meta.get(clientId);
            Object prevState = states.get(clientId);
            long currClock = clientMeta == null ? 0 : clientMeta.clock;

            if (currClock < clock || (currClock == clock && state == null && states.containsKey(clientId))) {
                if (state == null) {
                    if (clientId == this.clientId && getLocalState() != null) {
                        clock++;
                    } else {
                        states.remove(clientId);
                    }
                } else {
                    states.put(clientId, state);
                }

                meta.put(clientId, new MetaClientState(clock, timestamp));

                if (clientMeta == null && state != null) {
                    added.add(clientId);
                } else if (clientMeta != null && state == null) {
                    removed.add(clientId);
                } else if (state != null) {
                    if (!Objects.equals(state, prevState)) {
                        filteredUpdated.add(clientId);
                    }
                    updated.add(clientId);
                }
            }
        }

        if (!added.isEmpty() || !filteredUpdated.isEmpty() || !removed.isEmpty()) {
            emit("change",
                    new AwarenessEventParams(added, filteredUpdated, removed, null),
                    origin
            );
        }

        if (!added.isEmpty() || !updated.isEmpty() || !removed.isEmpty()) {
            emit("update", new AwarenessEventParams(added, updated, removed, null),
                    origin);
        }
    }

    /**
     * Get meta information for a client
     */
    public MetaClientState getMeta(long clientId) {
        return meta.get(clientId);
    }
}
