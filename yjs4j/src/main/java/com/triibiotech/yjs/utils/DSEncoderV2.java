package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

/**
 * 更新编码器
 *
 * @author zbs
 * @date 2025/07/29  15:16:57
 */
public abstract class DSEncoderV2 implements DSEncoder {
    Encoder restEncoder;
    long dsCurrVal;

    public DSEncoderV2() {
        restEncoder = Encoder.createEncoder();
        dsCurrVal = 0;
    }

    @Override
    public Encoder getRestEncoder() {
        return restEncoder;
    }

    public void setRestEncoder(Encoder restEncoder) {
        this.restEncoder = restEncoder;
    }

    public long getDsCurrVal() {
        return dsCurrVal;
    }

    @Override
    public byte[] toUint8Array() {
        return Encoder.toUint8Array(restEncoder);
    }

    @Override
    public void resetDsCurVal() {
        this.dsCurrVal = 0;
    }

    @Override
    public void writeDsClock(long clock) {
        long diff = clock - this.dsCurrVal;
        this.dsCurrVal = clock;
        Encoder.writeVarUint(this.restEncoder, diff);
    }

    @Override
    public void writeDsLen(long len) {
        if (len == 0) {
            throw new IllegalArgumentException("Length cannot be 0");
        }
        Encoder.writeVarUint(this.restEncoder, len - 1);
        this.dsCurrVal += len;
    }
}
