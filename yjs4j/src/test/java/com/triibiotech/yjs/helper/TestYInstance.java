package com.triibiotech.yjs.helper;

import com.triibiotech.yjs.protocol.sync.SyncProtocol;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.*;

import static com.triibiotech.yjs.helper.TestHelper.broadcastMessage;

/**
 * @author zbs
 * @date 2025/9/11 14:27
 **/
public class TestYInstance extends Doc {
    public long userID;
    public TestConnector tc;
    public Map<TestYInstance, List<byte[]>> receiving;
    public List<byte[]> updates;

    public TestYInstance(TestConnector testConnector, long clientId) {
        this.userID = clientId;
        this.tc = testConnector;
        this.receiving = new HashMap<>();
        this.updates = new ArrayList<>();
        testConnector.allConns.add(this);

        this.on("update", this::updateLoader);
        this.connect();
    }

    public void updateLoader(byte[] update, Object origin) {
        if (!Objects.equals(this.tc, origin)) {
            Encoder encoder = Encoder.createEncoder();
            SyncProtocol.writeUpdate(encoder, update);
            broadcastMessage(this, Encoder.toUint8Array(encoder));
        }
        this.updates.add(update);
    }

    public void disconnect() {
        this.receiving = new HashMap<>();
        this.tc.onlineConns.remove(this);
    }

    public void connect() {
        if (!this.tc.onlineConns.contains(this)) {
            this.tc.onlineConns.add(this);
            Encoder encoder = Encoder.createEncoder();
            SyncProtocol.writeSyncStep1(encoder, this);
            // publish SyncStep1
            broadcastMessage(this, Encoder.toUint8Array(encoder));
            this.tc.onlineConns.forEach(remoteYInstance -> {
                if (remoteYInstance != this) {
                    // remote instance sends instance to this instance
                    Encoder otherEncoder = Encoder.createEncoder();
                    SyncProtocol.writeSyncStep1(otherEncoder, remoteYInstance);
                    this._receive(Encoder.toUint8Array(otherEncoder), remoteYInstance);
                }
            });
        }
    }

    /**
     * Receive a message from another client. This message is only appended to the list of receiving messages.
     * TestConnector decides when this client actually reads this message.
     *
     * @param {Uint8Array}    message
     * @param {TestYInstance} remoteClient
     */
    public void _receive(byte[] message, TestYInstance remoteClient) {
        // map.setIfUndefined(this.receiving, remoteClient, () => /** @type {Array<Uint8Array>} */ ([])).push(message)
        this.receiving.computeIfAbsent(remoteClient, k -> new ArrayList<>()).add(message);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        TestYInstance that = (TestYInstance) o;
        return userID == that.userID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userID);
    }
}
