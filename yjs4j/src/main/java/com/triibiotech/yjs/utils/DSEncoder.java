package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

/**
 * @author zbs
 * @date 2025/7/29 17:03
 **/
public interface DSEncoder {
    Encoder getRestEncoder();

    void setRestEncoder(Encoder restEncoder);

    byte[] toUint8Array();

    void writeLeftID(ID id);

    void writeRightID(ID id);

    void writeClient(long client);

    void writeInfo(long info);

    void writeString(String s);

    void writeParentInfo(Boolean isYKey);

    void writeTypeRef(long info);

    void writeLen(long len);

    void writeAny(Object any);

    void writeBuf(byte[] buf);

    void writeJSON(Object embed);

    void writeKey(String key);

    void resetDsCurVal();

    void writeDsClock(long clock);

    void writeDsLen(long len);
}
