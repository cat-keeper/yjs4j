package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.*;

/**
 * Garbage collected struct - represents deleted content
 *
 * @author zbs
 * @date 2025/07/27 11:30:23
 */
public class GC extends AbstractStruct {

    public static final int STRUCT_GC_REF_NUMBER = 0;

    public GC(ID id, long length) {
        super(id, length);
    }

    @Override
    public boolean isDeleted() {
        return true;
    }

    @Override
    public boolean mergeWith(AbstractStruct right) {
        if (!(right instanceof GC)) {
            return false;
        }
        this.length += right.length;
        return true;
    }

    @Override
    public void integrate(Transaction transaction, long offset) {
        if (offset > 0) {
            this.id.clock += offset;
            this.length -= offset;
        }
        transaction.doc.getStore().addStruct(this);
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeInfo(STRUCT_GC_REF_NUMBER);
        encoder.writeLen(this.length - offset);
    }
}
