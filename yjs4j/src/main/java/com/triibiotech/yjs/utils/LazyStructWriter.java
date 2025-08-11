package com.triibiotech.yjs.utils;

import java.util.List;

/**
 * @author zbs
 * @date 2025/7/30 13:22
 **/
@SuppressWarnings("unused")
public class LazyStructWriter {
    private Long currClient;
    private Long startClock;
    private Long written;
    private DSEncoder encoder;
    private List<ClientStruct> clientStructs;

    public LazyStructWriter(DSEncoder encoder) {
        this.currClient = 0L;
        this.startClock = 0L;
        this.written = 0L;
        this.encoder = encoder;
        this.clientStructs = new java.util.ArrayList<>();
    }

    public static class ClientStruct {
        public Long written;
        public byte[] restEncoder;

        public ClientStruct(Long written, byte[] restEncoder) {
            this.written = written;
            this.restEncoder = restEncoder;
        }
    }

    public Long getCurrClient() {
        return currClient;
    }

    public void setCurrClient(Long currClient) {
        this.currClient = currClient;
    }

    public Long getStartClock() {
        return startClock;
    }

    public void setStartClock(Long startClock) {
        this.startClock = startClock;
    }

    public Long getWritten() {
        return written;
    }

    public void setWritten(Long written) {
        this.written = written;
    }

    public DSEncoder getEncoder() {
        return encoder;
    }

    public void setEncoder(DSEncoder encoder) {
        this.encoder = encoder;
    }

    public List<ClientStruct> getClientStructs() {
        return clientStructs;
    }

    public void setClientStructs(List<ClientStruct> clientStructs) {
        this.clientStructs = clientStructs;
    }
}
