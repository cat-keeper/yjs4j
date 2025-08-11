package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zbs
 * @date 2025/8/4 17:16
 **/
public class EncodingUtilTest {

    /**
     * There is some custom encoding/decoding happening in PermanentUserData.
     * This is why it landed here.
     *
     */
    @Test
    void testPermanentUserData() {
        Doc ydoc1 = new Doc();
        Doc ydoc2 = new Doc();
        PermanentUserData pd1 = new PermanentUserData(ydoc1);
        PermanentUserData pd2 = new PermanentUserData(ydoc2);
        pd1.setUserMapping(ydoc1, ydoc1.clientId, "user a");
        pd2.setUserMapping(ydoc2, ydoc2.clientId, "user b");
//        ydoc1.getText().insert(0, 'xhi')
//        ydoc1.getText().delete(0, 1)
//        ydoc2.getText().insert(0, 'hxxi')
//        ydoc2.getText().delete(1, 2)
//        await promise.wait(10)
//        applyUpdate(ydoc2, encodeStateAsUpdate(ydoc1))
//        applyUpdate(ydoc1, encodeStateAsUpdate(ydoc2))
//
//        // now sync a third doc with same name as doc1 and then create PermanentUserData
//  const ydoc3 = new Doc()
//        applyUpdate(ydoc3, encodeStateAsUpdate(ydoc1))
//  const pd3 = new PermanentUserData(ydoc3)
//        pd3.setUserMapping(ydoc3, ydoc3.clientID, 'user a')
    }


    @Test
    void testDiffStateVectorOfUpdateIsEmpty() {
        Doc ydoc = new Doc();
        final byte[][] sv = {null};
        ydoc.getText("").insert(0, "a", null);
        ydoc.on("update", new ObservableV2.Handler1<byte[]>() {
            @Override
            public void apply(byte[] arg) {
                sv[0] = UpdateProcessor.encodeStateVectorFromUpdate(arg);
            }
        });
        ydoc.getText("").insert(0, "a", null);
        assert sv[0]!= null && sv[0].length == 1 && sv[0][0] == 0;
    }

    @Test
    void testDiffStateVectorOfUpdateIgnoresSkips() {
        Doc ydoc = new Doc();
        final List<List<byte[]>> updates = new ArrayList<>() {
            {
                add(new ArrayList<>());
            }
        };
        ydoc.on("update", new ObservableV2.Handler1<byte[]>() {
            @Override
            public void apply(byte[] arg) {
                updates.get(0).add(arg);
            }
        });
        ydoc.getText("").insert(0, "a", null);
        ydoc.getText("").insert(0, "b", null);
        ydoc.getText("").insert(0, "c", null);
        byte[] update13 = UpdateProcessor.mergeUpdates(List.of(updates.get(0).get(0), updates.get(0).get(2)));
        byte[] sv = UpdateProcessor.encodeStateVectorFromUpdate(update13);
        Map<Long, Long> state = EncodingUtil.decodeStateVector(sv);
        assert state.get(ydoc.clientId) == 1;
        assert state.size() == 1;
    }
}
