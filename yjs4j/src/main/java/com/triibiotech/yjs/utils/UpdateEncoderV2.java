package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.encoding.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 更新编码器v2
 *
 * @author zbs
 * @date 2025/07/29  15:22:37
 */
public class UpdateEncoderV2 extends DSEncoderV2 {
    private final Map<String, Integer> keyMap = new HashMap<>();
    private Integer keyClock;
    private final IntDiffOptRleEncoder keyClockEncoder;
    private final UintOptRleEncoder clientEncoder;
    private final IntDiffOptRleEncoder leftClockEncoder;
    private final IntDiffOptRleEncoder rightClockEncoder;
    private final RleEncoder<Long> infoEncoder;
    private final StringEncoder stringEncoder;
    private final RleEncoder<Long> parentInfoEncoder;
    private final UintOptRleEncoder typeRefEncoder;
    private final UintOptRleEncoder lenEncoder;

    public UpdateEncoderV2() {
        this.keyClock = 0;
        this.keyClockEncoder = new IntDiffOptRleEncoder();
        this.clientEncoder = new UintOptRleEncoder();
        this.leftClockEncoder = new IntDiffOptRleEncoder();
        this.rightClockEncoder = new IntDiffOptRleEncoder();
        this.infoEncoder = new RleEncoder<>(Encoder::write);
        this.stringEncoder = new StringEncoder();
        this.parentInfoEncoder = new RleEncoder<>(Encoder::write);
        this.typeRefEncoder = new UintOptRleEncoder();
        this.lenEncoder = new UintOptRleEncoder();
    }

    @Override
    public byte[] toUint8Array() {
        Encoder encoder = Encoder.createEncoder();
        Encoder.writeVarUint(encoder, 0);
        Encoder.writeVarUint8Array(encoder, this.keyClockEncoder.toUint8Array());
        Encoder.writeVarUint8Array(encoder, this.clientEncoder.toUint8Array());
        Encoder.writeVarUint8Array(encoder, this.leftClockEncoder.toUint8Array());
        Encoder.writeVarUint8Array(encoder, this.rightClockEncoder.toUint8Array());
        Encoder.writeVarUint8Array(encoder, Encoder.toUint8Array(this.infoEncoder));
        Encoder.writeVarUint8Array(encoder, this.stringEncoder.toUint8Array());
        Encoder.writeVarUint8Array(encoder, Encoder.toUint8Array(this.parentInfoEncoder));
        Encoder.writeVarUint8Array(encoder, this.typeRefEncoder.toUint8Array());
        Encoder.writeVarUint8Array(encoder, this.lenEncoder.toUint8Array());
        // @note The rest encoder is appended! (note the missing var);
        Encoder.writeUint8Array(encoder, Encoder.toUint8Array(this.restEncoder));
        return Encoder.toUint8Array(encoder);
    }

    @Override
    public void writeLeftID(ID id) {
        this.clientEncoder.write(id.client);
        this.leftClockEncoder.write(id.clock);
    }

    @Override
    public void writeRightID(ID id) {
        this.clientEncoder.write(id.client);
        this.rightClockEncoder.write(id.clock);
    }

    @Override
    public void writeClient(long client) {
        this.clientEncoder.write(client);
    }

    @Override
    public void writeInfo(long info) {
        this.infoEncoder.write(info);
    }

    @Override
    public void writeString(String s) {
        this.stringEncoder.write(s);
    }

    @Override
    public void writeParentInfo(Boolean isYKey) {
        this.parentInfoEncoder.write(isYKey ? 1L : 0L);
    }

    @Override
    public void writeTypeRef(long info) {
        this.typeRefEncoder.write(info);
    }

    @Override
    public void writeLen(long len) {
        this.lenEncoder.write(len);
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
        Encoder.writeAny(this.restEncoder, embed);
    }

    @Override
    public void writeKey(String key) {
        Integer clock = this.keyMap.get(key);
        if (clock == null) {
            this.keyClockEncoder.write(this.keyClock++);
            this.stringEncoder.write(key);
        } else {
            this.keyClockEncoder.write(clock);
        }
    }
}
