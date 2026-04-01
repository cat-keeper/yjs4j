package cn.timegap.yjs.utils.event;

/**
 * @author zbs
 * @date 2025/7/31 10:05
 **/
public class EventAction {
    private String action;
    private Object oldValue;

    public EventAction() {
    }

    public EventAction(String action, Object oldValue) {
        this.action = action;
        this.oldValue = oldValue;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }
}
