package com.catkeeper.yjs.structs;

import com.catkeeper.yjs.utils.DSEncoder;
import com.catkeeper.yjs.utils.ID;
import com.catkeeper.yjs.utils.Transaction;
import com.catkeeper.yjs.utils.lib0.encoding.Encoder;

/**
 * Skip struct - represents skipped content in the document
 *
 * @author zbs
 * @date 2025/07/30 22:59:35
 */
public class Skip extends AbstractStruct {

    public static final int STRUCT_SKIP_REF_NUMBER = 10;

    public Skip(ID id, long length) {
        super(id, length);
    }

    @Override
    public boolean isDeleted() {
        return true;
    }

    @Override
    public boolean mergeWith(AbstractStruct right) {
        if (this.getClass() != right.getClass()) {
            return false;
        }
        this.length += right.length;
        return true;
    }

    @Override
    public void integrate(Transaction transaction, long offset) {
        // Skip structs cannot be integrated - this should throw an error
        throw new RuntimeException("Skip structs cannot be integrated");
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeInfo(STRUCT_SKIP_REF_NUMBER);
        Encoder.writeVarUint(encoder.getRestEncoder(), this.length - offset);
    }
}
