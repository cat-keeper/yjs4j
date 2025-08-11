package com.triibiotech.yjs.utils;

import com.alibaba.fastjson2.JSON;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

/**
 * 更新编码器v1
 *
 * @author zbs
 * @date 2025/07/29  15:22:37
 */
public class UpdateEncoderV1 extends DSEncoderV1 {

    @Override
    public void writeLeftID(ID id) {
        Encoder.writeVarUint(this.restEncoder, id.client);
        Encoder.writeVarUint(this.restEncoder, id.clock);
    }

    @Override
    public void writeRightID(ID id) {
        Encoder.writeVarUint(this.restEncoder, id.client);
        Encoder.writeVarUint(this.restEncoder, id.clock);
    }

    @Override
    public void writeClient(long client) {
        Encoder.writeVarUint(this.restEncoder, client);
    }

    @Override
    public void writeInfo(long info) {
        Encoder.writeUint8(this.restEncoder, (int) info);
    }

    @Override
    public void writeString(String s) {
        Encoder.writeVarString(this.restEncoder, s);
    }

    @Override
    public void writeParentInfo(Boolean isYKey) {
        Encoder.writeVarUint(this.restEncoder, isYKey ? 1 : 0);
    }

    @Override
    public void writeTypeRef(long info) {
        Encoder.writeVarUint(this.restEncoder, info);
    }

    @Override
    public void writeLen(long len) {
        Encoder.writeVarUint(this.restEncoder, len);
    }

    @Override
    public void writeAny(Object any) {
        Encoder.writeAny(this.restEncoder, any);
    }

    @Override
    public void writeBuf(byte[] buf) {
        Encoder.writeVarUint8Array(this.restEncoder, buf);
    }

    @Override
    public void writeJSON(Object embed) {
        Encoder.writeVarString(this.restEncoder, JSON.toJSONString(embed));
    }

    @Override
    public void writeKey(String key) {
        Encoder.writeVarString(this.restEncoder, key);
    }

    /**
     * Reset delete set current value (no-op for V1)
     */
    @Override
    public void resetDsCurVal() {
        // no-op for V1
    }
}
