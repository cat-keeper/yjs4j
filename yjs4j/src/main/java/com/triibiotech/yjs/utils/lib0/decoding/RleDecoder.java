package com.triibiotech.yjs.utils.lib0.decoding;
import java.util.function.Function;

/**
 * @author zbs
 * @date 2025/7/29 14:48
 **/
public class RleDecoder<T> extends Decoder {
    private Function<Decoder, T> reader;
    private T s = null;
    private int count = 0;

    public RleDecoder(byte[] uint8Array, Function<Decoder, T> reader) {
        super(uint8Array);
        this.reader = reader;
    }

    public T read() {
        if (count == 0) {
            s = reader.apply(this);
            if (hasContent(this)) {
                count = (int) readVarUint(this) + 1;
            } else {
                // read the current value forever
                count = -1;
            }
        }
        count--;
        return s;
    }
}
