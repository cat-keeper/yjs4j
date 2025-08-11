package com.triibiotech.yjs.utils;

/**
 * @author zbs
 * @date 2025/7/30 16:52
 **/
public class AbstractConnector extends ObservableV2<String> {
    private Doc doc;
    private Object awareness;

    public AbstractConnector(Doc doc, Object awareness) {
        this.doc = doc;
        this.awareness = awareness;
    }

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    public Object getAwareness() {
        return awareness;
    }

    public void setAwareness(Object awareness) {
        this.awareness = awareness;
    }
}
