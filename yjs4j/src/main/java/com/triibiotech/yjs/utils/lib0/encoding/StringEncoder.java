package com.triibiotech.yjs.utils.lib0.encoding;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zbs
 * @date 2025/7/29 14:12
 **/
public class StringEncoder {
    private List<String> sarr = new ArrayList<>();
    private StringBuilder s = new StringBuilder();
    private UintOptRleEncoder lensE = new UintOptRleEncoder();

    public void write(String string) {
        s.append(string);
        if (s.length() > 19) {
            sarr.add(s.toString());
            s = new StringBuilder();
        }
        lensE.write(string.length());
    }

    public byte[] toUint8Array() {
        Encoder encoder = new Encoder();
        sarr.add(s.toString());
        s = new StringBuilder();
        Encoder.writeVarString(encoder, String.join("", sarr));
        Encoder.writeUint8Array(encoder, lensE.toUint8Array());
        return Encoder.toUint8Array(encoder);
    }
}
