package com.triibiotech.yjs.utils.lib0.encoding;

/**
 * @author zbs
 * @date 2025/7/29 14:10
 **/
public class IntDiffOptRleEncoder {
    private Encoder encoder = new Encoder();
    private long s = 0;
    private int count = 0;
    private long diff = 0;

    public void write(long v) {
        if (diff == v - s) {
            s = v;
            count++;
        } else {
            flushIntDiffOptRleEncoder();
            count = 1;
            diff = v - s;
            s = v;
        }
    }

    private void flushIntDiffOptRleEncoder() {
        if (count > 0) {
            long encodedDiff = diff * 2 + (count == 1 ? 0 : 1);
            Encoder.writeVarInt(encoder, encodedDiff);
            if (count > 1) {
                Encoder.writeVarUint(encoder, count - 2);
            }
        }
    }

    public byte[] toUint8Array() {
        flushIntDiffOptRleEncoder();
        return Encoder.toUint8Array(encoder);
    }
}
