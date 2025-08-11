package com.triibiotech.yjs.utils.lib0.decoding;

/**
 * @author zbs
 * @date 2025/7/29 14:48
 **/
public class IntDiffOptRleDecoder extends Decoder {
    private long s = 0;
    private int count = 0;
    private long diff = 0;

    public IntDiffOptRleDecoder(byte[] uint8Array) {
        super(uint8Array);
    }

    public long read() {
        if (count == 0) {
            long diffValue = readVarInt(this);
            int hasCount = (int) (diffValue & 1);
            diff = (long) Math.floor(diffValue / 2.0);
            count = 1;
            if (hasCount != 0) {
                count = (int) readVarUint(this) + 2;
            }
        }
        s += diff;
        count--;
        return s;
    }
}
