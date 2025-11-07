package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.types.YText;
import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

/**
 * Content that represents formatting information for rich text
 *
 * @author zbs
 * @date 2025/07/30  15:40:31
 */
public class ContentFormat extends AbstractContent {

    private String key;
    private Object value;

    public ContentFormat(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public long getLength() {
        return 1;
    }

    @Override
    public Object[] getContent() {
        return new Object[]{};
    }

    @Override
    public boolean isCountable() {
        return false;
    }

    @Override
    public AbstractContent copy() {
        return new ContentFormat(key, value);
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
        if(item.parent instanceof AbstractType<?> pType) {
            pType.setSearchMarker(null);
            if(pType instanceof YText pText) {
                pText.setHasFormatting(true);
            }
        }
    }

    @Override
    public void delete(Transaction transaction) {
    }

    @Override
    public void gc(StructStore store) {
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeKey(this.key);
        encoder.writeJSON(this.value);
    }

    @Override
    public int getRef() {
        return 6;
    }

    public static ContentFormat readContentFormat(DSDecoder decoder) {
        return new ContentFormat(decoder.readKey(), decoder.readJson());
    }

    public String getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
