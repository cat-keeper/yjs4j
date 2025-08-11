package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.*;

/**
 * String content implementation
 *
 * @author zbs
 * @date 2025/07/28  10:52:31
 */
public class ContentString extends AbstractContent {

    private String str;

    public ContentString(String str) {
        this.str = str != null ? str : "";
    }

    @Override
    public long getLength() {
        return str.length();
    }

    @Override
    public Object[] getContent() {
        return str.split("");
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentString(str);
    }

    @Override
    public AbstractContent splice(int offset) {
        ContentString right = new ContentString(str.substring(offset));
        str = str.substring(0, offset);

        // Prevent encoding invalid documents because of splitting of surrogate pairs
        if (offset > 0) {
            char firstChar = str.charAt(offset - 1);
            if (Character.isHighSurrogate(firstChar)) {
                // Replace invalid character with unicode replacement character
                str = str.substring(0, offset - 1) + "\uFFFD";
                right.str = "\uFFFD" + right.str.substring(1);
            }
        }
        return right;
    }

    @Override
    public boolean mergeWith(AbstractContent other) {
        if (other instanceof ContentString otherStr) {
            str += otherStr.str;
            return true;
        }
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) {
        // String content integration
    }

    @Override
    public void delete(Transaction transaction) {
        // String content deletion
    }

    @Override
    public void gc(StructStore store) {
        // String content garbage collection - no special handling needed
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeString(offset == 0 ? str : str.substring((int) offset));
    }

    @Override
    public int getRef() {
        return 4;
    }

    public static ContentString readContentString(DSDecoder decoder) {
        return new ContentString(decoder.readString());
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }
}
