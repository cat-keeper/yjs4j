package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

/**
 * Content that represents binary data
 *
 * @author zbs
 * @date 2025/07/26 19:44:24
 */
public class ContentBinary extends AbstractContent {

    private byte[] content;

    public ContentBinary(byte[] content) {
        this.content = content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    @Override
    public long getLength() {
        return 1;
    }

    @Override
    public Object[] getContent() {
        return new Object[]{content};
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentBinary(content);
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
    }

    @Override
    public void delete(Transaction transaction) {
    }

    @Override
    public void gc(StructStore store) {
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeBuf(this.content);
    }

    @Override
    public int getRef() {
        return 3;
    }

    public static ContentBinary readContentBinary(DSDecoder decoder) {
        return new ContentBinary(decoder.readBuf());
    }
}
