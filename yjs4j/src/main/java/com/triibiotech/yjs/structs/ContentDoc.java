package com.triibiotech.yjs.structs;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.triibiotech.yjs.utils.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Content that represents a sub-document
 *
 * @author zbs
 * @date 2025/07/26 19:53:01
 */
public class ContentDoc extends AbstractContent {

    private Map<String, Object> opts;

    public ContentDoc(Doc doc) {
        if (doc.getTransaction() != null) {
            System.err.println("This document was already integrated as a sub-document. You should create a second instance instead with the same guid.");
        }
        this.doc = doc;
        this.opts = new HashMap<>();

        if (!doc.getGc()) {
            opts.put("gc", false);
        }
        if (doc.isAutoLoad()) {
            opts.put("autoLoad", true);
        }
        if (doc.meta != null) {
            opts.put("meta", doc.meta);
        }
    }

    @Override
    public long getLength() {
        return 1;
    }

    @Override
    public Object[] getContent() {
        return new Object[]{doc};
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        DocOptions docOpts = new DocOptions();
        docOpts.guid = this.doc.getGuid();
        docOpts.guid = this.doc.getGuid();
        if (this.opts.containsKey("shouldLoad")) {
            docOpts.shouldLoad = true;
        } else {
            docOpts.shouldLoad = this.opts.containsKey("autoLoad");
        }

        return new ContentDoc(createDocFromOpts(this.doc.guid, this.opts));
    }

    @Override
    public AbstractContent splice(int offset) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) {
        this.doc.setItem(item);
        transaction.getSubDocsAdded().add(this.doc);
        if (this.doc.shouldLoad) {
            transaction.getSubDocsLoaded().add(this.doc);
        }
    }

    @Override
    public void delete(Transaction transaction) {
        if (transaction.subDocsAdded.contains(this.doc)) {
            transaction.subDocsAdded.remove(this.doc);
        } else {
            transaction.subDocsRemoved.add(this.doc);
        }
    }

    @Override
    public void gc(StructStore store) {
        // No special GC needed
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeString(this.doc.getGuid());
        encoder.writeAny(this.opts);
    }

    @Override
    public int getRef() {
        return 9;
    }

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    public Map<String, Object> getOpts() {
        return opts;
    }

    public void setOpts(Map<String, Object> opts) {
        this.opts = opts;
    }

    private static Doc createDocFromOpts(String guid, Map<String, Object> opts) {
        boolean shouldLoad = false;

        if (opts.containsKey("shouldLoad") && Boolean.TRUE.equals(opts.get("shouldLoad"))) {
            shouldLoad = true;
        } else if (opts.containsKey("autoLoad") && Boolean.TRUE.equals(opts.get("autoLoad"))) {
            shouldLoad = true;
        }

        DocOptions docOpts = new DocOptions();
        docOpts.guid = guid;
        docOpts.shouldLoad = shouldLoad;

        // 创建 Doc 实例，假设 Doc 有一个接收 guid, Map 参数的构造方法
        return new Doc(docOpts);
    }

    public static ContentDoc readContentDoc(DSDecoder decoder) {
        String guid = decoder.readString();
        Map<String, Object> opts = new HashMap<>();
        Object object = decoder.readAny();
        if (object != null) {
            opts = JSON.parseObject(JSON.toJSONString(object), new TypeReference<>() {
            });
        }
        return new ContentDoc(createDocFromOpts(guid, opts));
    }
}
