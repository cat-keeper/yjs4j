package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.lib0.decoding.Decoder;

/**
 * @author zbs
 * @date 2025/7/29 17:03
 **/
public interface DSDecoder {
    Decoder getRestDecoder();

    void resetDsCurVal();

    long readDsClock();

    long readDsLen();

    ID readLeftId();

    ID readRightId();

    long readClient();

    int readInfo();

    String readString();

    boolean readParentInfo();

    long readTypeRef();

    long readLen();

    Object readAny();

    byte[] readBuf();

    Object readJson();

    String readKey();
}
