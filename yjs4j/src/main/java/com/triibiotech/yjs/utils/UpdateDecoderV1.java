package com.triibiotech.yjs.utils;

import com.alibaba.fastjson2.JSON;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;

/**
 * 更新解码器v1
 *
 * @author zbs
 * @date 2025/07/29  15:50:09
 */
public class UpdateDecoderV1 extends DSDecoderV1 {

    public UpdateDecoderV1(Decoder decoder) {
        super(decoder);
    }

    @Override
    public ID readLeftId() {
        return ID.createId(Decoder.readVarUint(this.restDecoder), Decoder.readVarUint(this.restDecoder));
    }

    @Override
    public ID readRightId() {
        return ID.createId(Decoder.readVarUint(this.restDecoder), Decoder.readVarUint(this.restDecoder));
    }

    @Override
    public long readClient() {
        return Decoder.readVarUint(this.restDecoder);
    }

    @Override
    public int readInfo() {
        return Decoder.readUint8(this.restDecoder);
    }

    @Override
    public String readString() {
        return Decoder.readVarString(this.restDecoder);
    }

    @Override
    public boolean readParentInfo() {
        return Decoder.readVarUint(this.restDecoder) == 1L;
    }

    @Override
    public long readTypeRef() {
        return Decoder.readVarUint(this.restDecoder);
    }

    @Override
    public long readLen() {
        return Decoder.readVarUint(this.restDecoder);
    }

    @Override
    public Object readAny() {
        return Decoder.readAny(this.restDecoder);
    }

    @Override
    public byte[] readBuf() {
        return Decoder.readVarUint8Array(this.restDecoder);
    }

    /**
     * Legacy implementation uses JSON parse. We use any-decoding in v2.
     *
     * @return {any}
     */
    @Override
    public Object readJson() {
        return JSON.parse(Decoder.readVarString(this.restDecoder));
    }

    @Override
    public String readKey() {
        return Decoder.readVarString(this.restDecoder);
    }

    @Override
    public void resetDsCurVal() {

    }
}
