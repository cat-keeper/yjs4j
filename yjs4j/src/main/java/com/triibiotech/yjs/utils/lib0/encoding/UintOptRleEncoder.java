package com.triibiotech.yjs.utils.lib0.encoding;

/**
 * @author zbs
 * @date 2025/7/29 14:12
 **/
public class UintOptRleEncoder {
    private Encoder encoder = new Encoder();
    private long s = 0;
    private int count = 0;

    public void write(long v) {
        if (s == v) {
            count++;
        } else {
            flushUintOptRleEncoder();
            count = 1;
            s = v;
        }
    }

    private void flushUintOptRleEncoder() {
        if (count > 0) {
            Encoder.writeVarInt(encoder, count == 1 ? s : -s);
            if (count > 1) {
                Encoder.writeVarUint(encoder, count - 2);
            }
        }
    }

    public byte[] toUint8Array() {
        flushUintOptRleEncoder();
        return Encoder.toUint8Array(encoder);
    }
}
