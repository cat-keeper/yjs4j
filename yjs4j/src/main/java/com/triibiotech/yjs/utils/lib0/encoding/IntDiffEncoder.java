package com.triibiotech.yjs.utils.lib0.encoding;

/**
 * @author zbs
 * @date 2025/7/29 14:10
 **/
public class IntDiffEncoder extends Encoder {
    private long s;

    public IntDiffEncoder(long start) {
        this.s = start;
    }

    public void write(long v) {
        writeVarInt(this, v - s);
        s = v;
    }
}
