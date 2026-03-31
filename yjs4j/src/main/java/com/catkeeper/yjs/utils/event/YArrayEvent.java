package com.catkeeper.yjs.utils.event;

import com.catkeeper.yjs.types.YArray;
import com.catkeeper.yjs.utils.Transaction;

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
