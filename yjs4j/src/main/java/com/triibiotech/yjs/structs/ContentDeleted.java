package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

/**
 * 内容已删除
 *
 * @author zbs
 * @date 2025/07/28  10:33:12
 */
public class ContentDeleted extends AbstractContent {
    private long length;

    public ContentDeleted(long length) {
        this.length = length;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public Object[] getContent() {
        return new Object[0];
    }

    @Override
    public boolean isCountable() {
        return false;
    }

    @Override
    public AbstractContent copy() {
        return new ContentDeleted(length);
    }

    @Override
    public AbstractContent splice(int offset) {
        ContentDeleted right = new ContentDeleted(length - offset);
        this.length = offset;
        return right;
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        this.length += right.getLength();
        return true;
    }

    @Override
    public void integrate(Transaction transaction, Item item) {
        transaction.deleteSet.addToDeleteSet(item.id.client, item.id.clock, this.length);
        item.markDeleted();
    }

    @Override
    public void delete(Transaction transaction) {
    }

    @Override
    public void gc(StructStore store) {
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeLen(this.length - offset);
    }

    @Override
    public int getRef() {
        return 1;
    }

    public static ContentDeleted readContentDeleted(DSDecoder decoder) {
        return new ContentDeleted(decoder.readLen());
    }
}
