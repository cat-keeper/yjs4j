package com.triibiotech.yjs.utils.lib0.decoding;

import com.triibiotech.yjs.utils.lib0.MathUtils;

/**
 * @author zbs
 * @date 2025/7/29 14:49
 **/
public class UintOptRleDecoder extends Decoder {
    private long s = 0;
    private int count = 0;

    public UintOptRleDecoder(byte[] uint8Array) {
        super(uint8Array);
    }

    public long read() {
        if (count == 0) {
            s = readVarInt(this);
            boolean isNegative = MathUtils.isNegativeZero(s);
            count = 1;
            if (isNegative) {
                s = -s;
                count = (int) readVarUint(this) + 2;
            }
        }
        count--;
        return s;
    }
}
