package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.decoding.*;

import java.util.ArrayList;
import java.util.List;

/**
 * UpdateDecoderV2 for reading Yjs update messages in V2 format.
 *
 * @author zbs
 * @date 2025/07/29  16:13:52
 */
public class UpdateDecoderV2 extends DSDecoderV2 {

    private final List<String> keys = new ArrayList<>();
    private final IntDiffOptRleDecoder keyClockDecoder;
    private final UintOptRleDecoder clientDecoder;
    private final IntDiffOptRleDecoder leftClockDecoder;
    private final IntDiffOptRleDecoder rightClockDecoder;
    private final RleDecoder<Integer> infoDecoder;
    private final StringDecoder stringDecoder;
    private final RleDecoder<Integer> parentInfoDecoder;
    private final UintOptRleDecoder typeRefDecoder;
    private final UintOptRleDecoder lenDecoder;

    public UpdateDecoderV2(Decoder decoder) {
        super(decoder);
        // read feature flag - currently unused
        Decoder.readVarUint(decoder);
        this.keyClockDecoder = new IntDiffOptRleDecoder(Decoder.readVarUint8Array(decoder));
        this.clientDecoder = new UintOptRleDecoder(Decoder.readVarUint8Array(decoder));
        this.leftClockDecoder = new IntDiffOptRleDecoder(Decoder.readVarUint8Array(decoder));
        this.rightClockDecoder = new IntDiffOptRleDecoder(Decoder.readVarUint8Array(decoder));
        this.infoDecoder = new RleDecoder<>(Decoder.readVarUint8Array(decoder), Decoder::readUint8);
        this.stringDecoder = new StringDecoder(Decoder.readVarUint8Array(decoder));
        this.parentInfoDecoder = new RleDecoder<>(Decoder.readVarUint8Array(decoder), Decoder::readUint8);
        this.typeRefDecoder = new UintOptRleDecoder(Decoder.readVarUint8Array(decoder));
        this.lenDecoder = new UintOptRleDecoder(Decoder.readVarUint8Array(decoder));
    }

    @Override
    public ID readLeftId() {
        return ID.createId(clientDecoder.read(), leftClockDecoder.read());
    }

    @Override
    public ID readRightId() {
        return ID.createId(clientDecoder.read(), rightClockDecoder.read());
    }

    @Override
    public long readClient() {
        return clientDecoder.read();
    }

    @Override
    public int readInfo() {
        return infoDecoder.read();
    }

    @Override
    public String readString() {
        return stringDecoder.read();
    }

    @Override
    public boolean readParentInfo() {
        return parentInfoDecoder.read() == 1;
    }

    @Override
    public long readTypeRef() {
        return typeRefDecoder.read();
    }

    @Override
    public long readLen() {
        return lenDecoder.read();
    }

    @Override
    public Object readAny() {
        return Decoder.readAny(this.restDecoder);
    }

    @Override
    public byte[] readBuf() {
        return Decoder.readVarUint8Array(this.restDecoder);
    }

    @Override
    public Object readJson() {
        return Decoder.readAny(this.restDecoder);
    }

    @Override
    public String readKey() {
        long keyClock = keyClockDecoder.read();
        if (keyClock < keys.size()) {
            return keys.get((int) keyClock);
        } else {
            String key = stringDecoder.read();
            keys.add(key);
            return key;
        }
    }
}
