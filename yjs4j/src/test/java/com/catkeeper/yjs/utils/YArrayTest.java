package com.catkeeper.yjs.utils;

import com.catkeeper.yjs.helper.TestConnector;
import com.catkeeper.yjs.helper.TestHelper;
import com.catkeeper.yjs.helper.TestYInstance;
import com.catkeeper.yjs.types.AbstractType;
import com.catkeeper.yjs.types.YArray;
import com.catkeeper.yjs.types.YMap;
import com.catkeeper.yjs.utils.encoding.EncodingUtil;
import com.catkeeper.yjs.utils.event.YEvent;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Port of yjs y-array.tests.js
 */
@SuppressWarnings("unchecked")
class YArrayTest {

    @Test
    void testBasicUpdate() {
        Doc doc1 = new Doc();
        Doc doc2 = new Doc();
        doc1.getArray("array").insert(0, "hi");
        byte[] update = EncodingUtil.encodeStateAsUpdate(doc1, null);
        EncodingUtil.applyUpdate(doc2, update);
        assertEquals(List.of("hi"), doc2.getArray("array").toJson());
    }

    @Test
    void testSlice() {
        Doc doc1 = new Doc();
        YArray<Object> arr = doc1.getArray("array");
        arr.insert(0, 1, 2, 3);
        assertEquals(List.of(1, 2, 3), AbstractType.typeListSlice(arr, 0, arr.getLength()));
        assertEquals(List.of(2, 3), AbstractType.typeListSlice(arr, 1, arr.getLength()));
        assertEquals(List.of(1, 2), AbstractType.typeListSlice(arr, 0, arr.getLength() - 1));
        arr.insert(0, 0);
        assertEquals(List.of(0, 1, 2, 3), AbstractType.typeListSlice(arr, 0, arr.getLength()));
        assertEquals(List.of(0, 1), AbstractType.typeListSlice(arr, 0, 2));
    }

    @Test
    void testLengthIssue() {
        Doc doc1 = new Doc();
        YArray<Object> arr = doc1.getArray("array");
        arr.push(0, 1, 2, 3);
        arr.delete(0, 1);
        arr.insert(0, 0);
        assertEquals(arr.getLength(), arr.toArray().size());
        doc1.transact(tr -> {
            arr.delete(1, 1);
            assertEquals(arr.getLength(), arr.toArray().size());
            arr.insert(1, 1);
            assertEquals(arr.getLength(), arr.toArray().size());
            arr.delete(2, 1);
            assertEquals(arr.getLength(), arr.toArray().size());
            arr.insert(2, 2);
            assertEquals(arr.getLength(), arr.toArray().size());
            return null;
        });
        assertEquals(arr.getLength(), arr.toArray().size());
        arr.delete(1, 1);
        assertEquals(arr.getLength(), arr.toArray().size());
        arr.insert(1, 1);
        assertEquals(arr.getLength(), arr.toArray().size());
    }

    @Test
    void testDeleteInsert() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        array0.delete(0, 0);
        array0.insert(0, "A");
        array0.delete(1, 0);
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testInsertThreeElementsTryRegetProperty() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        array0.insert(0, 1, true, false);
        assertEquals("[1, true, false]", array0.toJson().toString());
        testConnector.flushAllMessages();
        assertEquals("[1, true, false]", array1.toJson().toString());
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testConcurrentInsertWithThreeConflicts() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        YArray<Object> array2 = (YArray<Object>) result.get("array2");
        array0.insert(0, 0);
        array1.insert(0, 1);
        array2.insert(0, 2);
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testConcurrentInsertDeleteWithThreeConflicts() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        YArray<Object> array2 = (YArray<Object>) result.get("array2");
        array0.insert(0, "x", "y", "z");
        testConnector.flushAllMessages();
        array0.insert(1, 0);
        array1.delete(0, 1);
        array1.delete(1, 1);
        array2.insert(1, 2);
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testInsertionsInLateSync() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        YArray<Object> array2 = (YArray<Object>) result.get("array2");
        array0.insert(0, "x", "y");
        testConnector.flushAllMessages();
        users.get(1).disconnect();
        users.get(2).disconnect();
        array0.insert(1, "user0");
        array1.insert(1, "user1");
        array2.insert(1, "user2");
        users.get(1).connect();
        users.get(2).connect();
        testConnector.flushAllMessages();
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testDisconnectReallyPreventsSendingMessages() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        array0.insert(0, "x", "y");
        testConnector.flushAllMessages();
        users.get(1).disconnect();
        users.get(2).disconnect();
        array0.insert(1, "user0");
        array1.insert(1, "user1");
        assertEquals(List.of("x", "user0", "y"), array0.toJson());
        assertEquals(List.of("x", "user1", "y"), array1.toJson());
        users.get(1).connect();
        users.get(2).connect();
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testDeletionsInLateSync() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        array0.insert(0, "x", "y");
        testConnector.flushAllMessages();
        users.get(1).disconnect();
        array1.delete(1, 1);
        array0.delete(0, 2);
        users.get(1).connect();
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testInsertThenMergeDeleteOnSync() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        array0.insert(0, "x", "y", "z");
        testConnector.flushAllMessages();
        users.get(0).disconnect();
        array1.delete(0, 3);
        users.get(0).connect();
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testInsertAndDeleteEvents() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        AtomicBoolean eventFired = new AtomicBoolean(false);
        array0.observe((event, tr) -> eventFired.set(true));
        array0.insert(0, 0, 1, 2);
        assertTrue(eventFired.get());
        eventFired.set(false);
        array0.delete(0, 1);
        assertTrue(eventFired.get());
        eventFired.set(false);
        array0.delete(0, 2);
        assertTrue(eventFired.get());
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testInsertAndDeleteEventsForTypes() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        AtomicBoolean eventFired = new AtomicBoolean(false);
        array0.observe((event, tr) -> eventFired.set(true));
        array0.insert(0, new YArray<>());
        assertTrue(eventFired.get());
        eventFired.set(false);
        array0.delete(0, 1);
        assertTrue(eventFired.get());
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testNewChildDoesNotEmitEventInTransaction() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        AtomicBoolean fired = new AtomicBoolean(false);
        users.get(0).transact(tr -> {
            YMap<Object> newMap = new YMap<>();
            newMap.observe((event, transaction) -> fired.set(true));
            array0.insert(0, newMap);
            newMap.set("tst", 42);
            return null;
        });
        assertFalse(fired.get(), "Event does not trigger");
    }

    @Test
    void testGarbageCollector() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        array0.insert(0, "x", "y", "z");
        testConnector.flushAllMessages();
        users.get(0).disconnect();
        array0.delete(0, 3);
        users.get(0).connect();
        testConnector.flushAllMessages();
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testEventTargetIsSetCorrectlyOnLocal() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        AtomicReference<YEvent<?>> eventRef = new AtomicReference<>();
        array0.observe((event, tr) -> eventRef.set(event));
        array0.insert(0, "stuff");
        assertSame(array0, eventRef.get().target, "target property is set correctly");
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testEventTargetIsSetCorrectlyOnRemote() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YArray<Object> array0 = (YArray<Object>) result.get("array0");
        YArray<Object> array1 = (YArray<Object>) result.get("array1");
        AtomicReference<YEvent<?>> eventRef = new AtomicReference<>();
        array0.observe((event, tr) -> eventRef.set(event));
        array1.insert(0, "stuff");
        testConnector.flushAllMessages();
        assertSame(array0, eventRef.get().target, "target property is set correctly");
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testIteratingArrayContainingTypes() {
        Doc y = new Doc();
        YArray<Object> arr = y.getArray("arr");
        int numItems = 10;
        for (int i = 0; i < numItems; i++) {
            YMap<Object> map = new YMap<>();
            map.set("value", i);
            arr.push(map);
        }
        int cnt = 0;
        for (Object item : arr.toArray()) {
            YMap<Object> map = (YMap<Object>) item;
            assertEquals(cnt++, map.get("value"), "value is correct");
        }
        y.destroy();
    }
}
