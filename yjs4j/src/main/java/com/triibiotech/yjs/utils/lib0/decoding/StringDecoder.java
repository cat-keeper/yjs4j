package com.triibiotech.yjs.utils.lib0.decoding;

/**
 * @author zbs
 * @date 2025/7/29 14:49
 **/
public class StringDecoder {
    private UintOptRleDecoder decoder;
    private String str;
    private int spos = 0;

    public StringDecoder(byte[] uint8Array) {
        this.decoder = new UintOptRleDecoder(uint8Array);
        this.str = Decoder.readVarString(decoder);
    }

    public String read() {
        int end = spos + (int) decoder.read();
        String res = str.substring(spos, end);
        spos = end;
        return res;
    }
}
