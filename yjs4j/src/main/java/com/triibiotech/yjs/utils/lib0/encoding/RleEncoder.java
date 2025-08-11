package com.triibiotech.yjs.utils.lib0.encoding;


import java.util.function.BiConsumer;

/**
 * @author zbs
 * @date 2025/7/29 14:11
 **/
public class RleEncoder<T> extends Encoder {
    private final BiConsumer<Encoder, T> writer;
    private T s = null;
    private int count = 0;

    public RleEncoder(BiConsumer<Encoder, T> writer) {
        this.writer = writer;
    }

    public void write(T v) {
        if (s != null && s.equals(v)) {
            count++;
        } else {
            if (count > 0) {
                writeVarUint(this, count - 1);
            }
            count = 1;
            writer.accept(this, v);
            s = v;
        }
    }
}
