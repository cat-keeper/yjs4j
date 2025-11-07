package com.triibiotech.yjs.helper;

import cn.hutool.core.util.RandomUtil;
import com.triibiotech.yjs.protocol.sync.SyncProtocol;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.*;

/**
 * @author zbs
 * @date 2025/9/11 14:25
 **/
public class TestConnector {
    public Set<TestYInstance> allConns;
    public Set<TestYInstance> onlineConns;


    public TestConnector() {
        this.allConns = new HashSet<>();
        this.onlineConns = new HashSet<>();
    }

    public TestYInstance createY(long clientID) {
        return new TestYInstance(this, clientID);
    }

    /**
     * Choose random connection and flush a random message from a random sender.
     * <p>
     * If this function was unable to flush a message, because there are no more messages to flush, it returns false. true otherwise.
     *
     * @return {boolean}
     */
    public boolean flushRandomMessage() {
        List<TestYInstance> conns = this.onlineConns.stream().filter(conn -> !conn.receiving.isEmpty()).toList();
        if (conns.isEmpty()) {
            return false;
        }
        // 随机获取conns中的一个
        TestYInstance receiver = conns.get(RandomUtil.randomInt(0, conns.size()));
        Set<Map.Entry<TestYInstance, List<byte[]>>> entries = receiver.receiving.entrySet();
        // 随机获取entries中的一个
        Map.Entry<TestYInstance, List<byte[]>> entry = entries.stream().toList().get(RandomUtil.randomInt(0, entries.size()));
        TestYInstance sender = entry.getKey();
        List<byte[]> messages = entry.getValue();
        byte[] m = messages.removeFirst();
        if (messages.isEmpty()) {
            receiver.receiving.remove(sender);
        }
        if (m == null) {
            return this.flushRandomMessage();
        }
        Encoder encoder = Encoder.createEncoder();
        SyncProtocol.readSyncMessage(Decoder.createDecoder(m), encoder, receiver, receiver.tc);
        if (Encoder.length(encoder) > 0) {
            // send reply message
            sender._receive(Encoder.toUint8Array(encoder), receiver);
        }
        return true;
    }

    public boolean flushAllMessages() {
        boolean didSomething = false;
        while (this.flushRandomMessage()) {
            didSomething = true;
        }
        return didSomething;
    }

    public void reconnectAll() {
        this.allConns.forEach(TestYInstance::connect);
    }

    public void disconnectAll() {
        this.allConns.forEach(TestYInstance::disconnect);
    }

    public void syncAll() {
        this.reconnectAll();
        this.flushAllMessages();
    }

    public boolean disconnectRandom() {
        if (this.onlineConns.isEmpty()) {
            return false;
        }
        this.onlineConns.stream().toList().get(RandomUtil.randomInt(0, this.onlineConns.size())).disconnect();
        return true;
    }

    public boolean reconnectRandom() {
        List<TestYInstance> reconnectable = new ArrayList<>();
        this.allConns.forEach(conn -> {
            if (!this.onlineConns.contains(conn)) {
                reconnectable.add(conn);
            }
        });
        if (reconnectable.isEmpty()) {
            return false;
        }
        reconnectable.get(RandomUtil.randomInt(0, reconnectable.size())).connect();
        return true;
    }
}
