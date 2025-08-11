package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

/**
 * 更新编码器
 *
 * @author zbs
 * @date 2025/07/29  15:16:57
 */
public abstract class DSEncoderV1 implements DSEncoder {
    Encoder restEncoder;

    public DSEncoderV1() {
        restEncoder = Encoder.createEncoder();
    }

    @Override
    public Encoder getRestEncoder() {
        return restEncoder;
    }

    public void setRestEncoder(Encoder restEncoder) {
        this.restEncoder = restEncoder;
    }

    @Override
    public byte[] toUint8Array() {
        return Encoder.toUint8Array(restEncoder);
    }


    @Override
    public abstract void resetDsCurVal();

    @Override
    public void writeDsClock(long clock) {
        Encoder.writeVarUint(restEncoder, clock);
    }

    public void writeDsLen(long len) {
        Encoder.writeVarUint(this.restEncoder, len);
    }
}
