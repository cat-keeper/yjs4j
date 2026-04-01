package cn.timegap.yjs.utils.event;

import cn.timegap.yjs.types.YXmlFragment;
import cn.timegap.yjs.utils.Transaction;

import java.util.Set;

/**
 * yxml事件
 *
 * @author zbs
 * @date 2025/07/31  14:43:06
 */
public class YXmlEvent extends YEvent<YXmlFragment> {

    /**
     * Whether the children changed
     */
    public boolean childListChanged = false;

    /**
     * Set of all changed attributes
     */
    public final Set<String> attributesChanged = new java.util.LinkedHashSet<>();

    public YXmlEvent(YXmlFragment target, Set<String> subs, Transaction transaction) {
        super(target, transaction);
        subs.forEach(sub -> {
            if (sub == null) {
                this.childListChanged = true;
            } else {
                this.attributesChanged.add(sub);
            }
        });
    }
}
