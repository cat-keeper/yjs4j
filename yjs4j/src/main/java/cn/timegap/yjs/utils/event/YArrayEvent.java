package cn.timegap.yjs.utils.event;

import cn.timegap.yjs.types.YArray;
import cn.timegap.yjs.utils.Transaction;

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
