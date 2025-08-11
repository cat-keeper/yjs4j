package com.triibiotech.yjs.utils.lib0.decoding;

/**
 * @author zbs
 * @date 2025/7/29 14:48
 **/
public class IntDiffDecoder extends Decoder {
    private long s;

    public IntDiffDecoder(byte[] uint8Array, long start) {
        super(uint8Array);
        this.s = start;
    }

    public long read() {
        s += readVarInt(this);
        return s;
    }
}
