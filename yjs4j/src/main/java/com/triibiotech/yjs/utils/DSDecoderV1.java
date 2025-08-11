package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.decoding.Decoder;

/**
 * @author zbs
 * @date 2025/7/29 15:47
 **/
public abstract class DSDecoderV1 implements DSDecoder {
    protected final Decoder restDecoder;

    public DSDecoderV1(Decoder restDecoder) {
        this.restDecoder = restDecoder;
    }

    @Override
    public Decoder getRestDecoder() {
        return restDecoder;
    }

    @Override
    public abstract void resetDsCurVal();

    @Override
    public long readDsClock() {
        return Decoder.readVarUint(this.restDecoder);
    }

    @Override
    public long readDsLen () {
        return Decoder.readVarUint(this.restDecoder);
    }

}
