package com.triibiotech.yjs.utils.event;

import com.triibiotech.yjs.types.YArray;
import com.triibiotech.yjs.utils.Transaction;

/**
 * Event that describes the changes on a YArray
 *
 * @author zbs
 * @date 2025/07/28  13:42:09
 */
public class YArrayEvent<T> extends YEvent<YArray<T>> {

    public YArrayEvent(YArray<T> target, Transaction transaction) {
        super(target, transaction);
    }

}
