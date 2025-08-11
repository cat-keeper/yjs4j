package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.decoding.Decoder;

/**
 * @author zbs
 * @date 2025/7/29 15:47
 **/
public abstract class DSDecoderV2 implements DSDecoder {
    protected final Decoder restDecoder;
    protected long dsCurrVal;

    @Override
    public Decoder getRestDecoder() {
        return restDecoder;
    }

    public long getDsCurrVal() {
        return dsCurrVal;
    }

    public DSDecoderV2(Decoder restDecoder) {
        dsCurrVal = 0;
        this.restDecoder = restDecoder;
    }

    @Override
    public void resetDsCurVal() {
        this.dsCurrVal = 0;
    }

    @Override
    public long readDsClock() {
        this.dsCurrVal += Decoder.readVarUint(this.restDecoder);
        return this.dsCurrVal;
    }

    @Override
    public long readDsLen() {
        long diff = Decoder.readVarUint(this.restDecoder) + 1;
        this.dsCurrVal += diff;
        return diff;
    }

}
