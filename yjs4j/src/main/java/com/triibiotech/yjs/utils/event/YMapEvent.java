package com.triibiotech.yjs.utils.event;

import com.triibiotech.yjs.types.YMap;
import com.triibiotech.yjs.utils.Transaction;

import java.util.Set;

/**
 * ymap事件
 *
 * @author zbs
 * @date 2025/07/31  13:55:12
 */
public class YMapEvent extends YEvent<YMap<?>> {

    private Set<String> keysChanged;

    public YMapEvent(YMap<?> ymap, Transaction transaction, Set<String> subs) {
        super(ymap, transaction);
        this.keysChanged = subs;
    }

    public Set<String> getKeysChanged() {
        return keysChanged;
    }

    public void setKeysChanged(Set<String> keysChanged) {
        this.keysChanged = keysChanged;
    }
}
