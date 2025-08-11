package com.triibiotech.yjs.utils.event;

/**
 * @author zbs
 * @date 2025/7/31 10:05
 **/
public class EventAction {
    private String action;
    private Object oldValue;
    private Object newValue;

    public EventAction() {
    }

    public EventAction(String action, Object oldValue) {
        this.action = action;
        this.oldValue = oldValue;
    }

    public EventAction(String action, Object oldValue, Object newValue) {
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
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

    public Object getNewValue() {
        return newValue;
    }

    public void setNewValue(Object newValue) {
        this.newValue = newValue;
    }
}
