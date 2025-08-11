package com.triibiotech.yjs.utils.lib0.decoding;

/**
 * @author zbs
 * @date 2025/7/29 14:49
 **/
public class RleIntDiffDecoder extends Decoder {
    private long s;
    private int count = 0;

    public RleIntDiffDecoder(byte[] uint8Array, long start) {
        super(uint8Array);
        this.s = start;
    }

    public long read() {
        if (count == 0) {
            s += readVarInt(this);
            if (hasContent(this)) {
                count = (int) readVarUint(this) + 1;
            } else {
                count = -1; // read the current value forever
            }
        }
        count--;
        return s;
    }
}
