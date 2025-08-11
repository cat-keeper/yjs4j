package com.triibiotech.yjs.structs;

import com.alibaba.fastjson2.JSON;
import com.triibiotech.yjs.utils.*;

import java.util.Arrays;

/**
 * Content that represents JSON data
 *
 * @author zbs
 * @date 2025/07/30  15:40:59
 */
public class ContentJSON extends AbstractContent {

    private Object[] arr;

    public ContentJSON(Object[] arr) {
        this.arr = arr;
    }

    @Override
    public long getLength() {
        return arr.length;
    }

    @Override
    public Object[] getContent() {
        return arr;
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentJSON(this.arr);
    }

    @Override
    public AbstractContent splice(int offset) {
        Object[] rightArr = Arrays.copyOfRange(arr, offset, arr.length);
        this.arr = Arrays.copyOfRange(arr, 0, offset);
        return new ContentJSON(rightArr);
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        if (!(right instanceof ContentJSON)) {
            return false;
        }
        Object[] rightArr = ((ContentJSON) right).arr;
        Object[] newArr = new Object[arr.length + rightArr.length];
        System.arraycopy(arr, 0, newArr, 0, arr.length);
        System.arraycopy(rightArr, 0, newArr, arr.length, rightArr.length);
        this.arr = newArr;
        return true;
    }

    @Override
    public void integrate(Transaction transaction, Item item) {
        // No special integration needed
    }

    @Override
    public void delete(Transaction transaction) {
        // No special deletion needed
    }

    @Override
    public void gc(StructStore store) {
        // No special GC needed
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        int len = arr.length;
        encoder.writeLen(len - offset);
        for (int i = Math.toIntExact(offset); i < len; i++) {
            Object c = arr[i];
            String str = (c == null) ? "undefined" : JSON.toJSONString(c);
            encoder.writeString(str);
        }
    }

    @Override
    public int getRef() {
        return 2;
    }

    public Object[] getArr() {
        return arr;
    }

    public void setArr(Object[] arr) {
        this.arr = arr;
    }

    public static ContentJSON readContentJSON(DSDecoder decoder) {
        long len = decoder.readLen();
        Object[] cs = new Object[Math.toIntExact(len)];
        for (int i = 0; i < len; i++) {
            String c = decoder.readString();
            if ("undefined".equals(c)) {
                cs[i] = null;
            } else {
                // Simplified - would need full JSON parser
                cs[i] = JSON.parse(c);
            }
        }
        return new ContentJSON(cs);
    }
}
