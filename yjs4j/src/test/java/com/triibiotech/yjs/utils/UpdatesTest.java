package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.helper.TestYInstance;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author zbs
 * @date 2025/9/10 16:43
 **/
public class UpdatesTest {
    Doc fromUpdates(List<TestYInstance> users) {
        List<byte[]> updates = users.stream().map(user -> {
            return EncodingUtil.encodeStateAsUpdate(user, null);
        }).toList();
        Doc doc = new Doc();
        EncodingUtil.applyUpdate(doc, UpdateProcessor.mergeUpdates(updates));
        return doc;
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMergeUpdates() {
//        Map<String, Object> result = TestHelper.init(3, null);
//        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
//        YArray<?> array0 = (YArray<?>) result.get("array0");
//        YArray<?> array1 = (YArray<?>) result.get("array1");
//        array0.insert(0, 1);
//        array1.insert(0, 2);
//        TestHelper.compare(users);
//
//        Doc merged = fromUpdates(users);
//        List<Object> mArray = merged.getArray("array").toArray();
    }

}
