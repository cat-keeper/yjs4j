package com.triibiotech.yjs.utils.lib0.encoding;

/**
 * @author zbs
 * @date 2025/7/29 14:11
 **/
public class RleIntDiffEncoder extends Encoder {
    private long s;
    private int count = 0;

    public RleIntDiffEncoder(long start) {
        this.s = start;
    }

    public void write(long v) {
        if (s == v && count > 0) {
            count++;
        } else {
            if (count > 0) {
                writeVarUint(this, count - 1);
            }
            count = 1;
            writeVarInt(this, v - s);
            s = v;
        }
    }
}
