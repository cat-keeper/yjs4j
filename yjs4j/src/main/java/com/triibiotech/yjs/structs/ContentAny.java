package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

import java.util.Arrays;

/**
 * Content that represents any type of data
 * @author zbs
 */
public class ContentAny extends AbstractContent {

    private Object[] arr;

    public ContentAny(Object[] arr) {
        this.arr = arr;
    }

    public Object[] getArr() {
        return arr;
    }

    public void setArr(Object[] arr) {
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
        return new ContentAny(Arrays.copyOf(arr, arr.length));
    }

    @Override
    public AbstractContent splice(int offset) {
        Object[] rightArr = Arrays.copyOfRange(arr, offset, arr.length);
        this.arr = Arrays.copyOfRange(arr, 0, offset);
        return new ContentAny(rightArr);
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        if (!(right instanceof ContentAny)) {
            return false;
        }
        Object[] rightArr = ((ContentAny) right).arr;
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
            encoder.writeAny(c);
        }
    }

    @Override
    public int getRef() {
        return 8;
    }

    public static ContentAny readContentAny(DSDecoder decoder) {
        int len = Math.toIntExact(decoder.readLen());
        Object[] cs = new Object[len];
        for (int i = 0; i < len; i++) {
            cs[i] = decoder.readAny();
        }
        return new ContentAny(cs);
    }
}
