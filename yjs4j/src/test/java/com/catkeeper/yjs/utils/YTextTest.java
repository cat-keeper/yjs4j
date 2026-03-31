package com.catkeeper.yjs.utils;

import com.catkeeper.yjs.helper.TestConnector;
import com.catkeeper.yjs.helper.TestHelper;
import com.catkeeper.yjs.helper.TestYInstance;
import com.catkeeper.yjs.types.YText;
import com.catkeeper.yjs.utils.event.EventOperator;
import com.catkeeper.yjs.utils.event.YTextEvent;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Port of yjs y-text.tests.js
 */
class YTextTest {

    @SuppressWarnings("unchecked")
    @Test
    void testBasicInsertAndDelete() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YText text0 = (YText) result.get("text0");
        YText text1 = (YText) result.get("text1");

        AtomicReference<List<EventOperator>> deltaRef = new AtomicReference<>();
        text0.observe((event, tr) -> {
            deltaRef.set(((YTextEvent) event).getDelta());
        });

        text0.delete(0, 0);
        assertTrue(deltaRef.get() == null || deltaRef.get().isEmpty());
        text0.insert(0, "abc");
        assertNotNull(deltaRef.get());
        assertEquals(1, deltaRef.get().size());
        assertEquals("abc", deltaRef.get().get(0).getInsert());
        deltaRef.set(null);
        text0.delete(0, 1);
        assertNotNull(deltaRef.get());
        assertEquals(1, deltaRef.get().size());
        assertEquals(1L, deltaRef.get().get(0).delete);
        deltaRef.set(null);
        text0.delete(1, 1);
        assertNotNull(deltaRef.get());
        assertEquals(2, deltaRef.get().size());
        assertEquals(1L, deltaRef.get().get(0).retain);
        assertEquals(1L, deltaRef.get().get(1).delete);

        testConnector.flushAllMessages();
        assertEquals("b", text1.toString());
        assertEquals(text0.toString().length(), text0.getLength());
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBasicFormat() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YText text0 = (YText) result.get("text0");
        YText text1 = (YText) result.get("text1");

        AtomicReference<List<EventOperator>> deltaRef = new AtomicReference<>();
        text0.observe((event, tr) -> {
            deltaRef.set(((YTextEvent) event).getDelta());
        });

        text0.insert(0, "abc", Map.of("bold", true));
        assertNotNull(deltaRef.get());
        assertEquals(1, deltaRef.get().size());
        assertEquals("abc", deltaRef.get().get(0).getInsert());
        assertEquals(Map.of("bold", true), deltaRef.get().get(0).getAttributes());
        deltaRef.set(null);

        text0.delete(0, 1);
        assertNotNull(deltaRef.get());
        assertEquals(1, deltaRef.get().size());
        assertEquals(1L, deltaRef.get().get(0).delete);
        deltaRef.set(null);

        text0.delete(1, 1);
        assertNotNull(deltaRef.get());

        testConnector.flushAllMessages();
        assertEquals("b", text1.toString());
        List<EventOperator> delta = text1.toDelta();
        assertFalse(delta.isEmpty());
        assertEquals("b", delta.get(0).getInsert());
        assertEquals(Map.of("bold", true), delta.get(0).getAttributes());
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testToJson() {
        Doc doc = new Doc();
        YText text = doc.getText("text");
        text.insert(0, "abc", Map.of("bold", true));
        assertEquals("abc", text.toJson());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetDeltaWithEmbeds() {
        Map<String, Object> result = TestHelper.init(1, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YText text0 = (YText) result.get("text0");
        text0.applyDelta(List.of(
                createInsertOp(Map.of("linebreak", "s"))
        ), true);
        List<EventOperator> delta = text0.toDelta();
        assertEquals(1, delta.size());
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSnapshot() {
        Doc doc0 = new Doc(new DocOptions() {{ gc = false; }});
        YText text0 = doc0.getText("text");

        text0.applyDelta(List.of(createInsertOp("abcd")), true);
        Snapshot snap1 = Snapshot.snapshot(doc0);

        text0.applyDelta(List.of(
                createRetainOp(1),
                createInsertOp("x"),
                createDeleteOp(1)
        ), true);
        Snapshot snap2 = Snapshot.snapshot(doc0);

        text0.applyDelta(List.of(
                createRetainOp(2),
                createDeleteOp(3),
                createInsertOp("x"),
                createDeleteOp(1)
        ), true);

        List<EventOperator> delta1 = text0.toDelta(snap1, null, null);
        assertEquals(1, delta1.size(), "snap1 delta should have 1 element, got: " + delta1);
        assertEquals("abcd", delta1.get(0).getInsert());

        List<EventOperator> delta2 = text0.toDelta(snap2, null, null);
        assertEquals(1, delta2.size(), "snap2 delta should have 1 element, got: " + delta2);
        assertEquals("axcd", delta2.get(0).getInsert());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSnapshotDeleteAfter() {
        Doc doc0 = new Doc(new DocOptions() {{ gc = false; }});
        YText text0 = doc0.getText("text");

        text0.applyDelta(List.of(createInsertOp("abcd")), true);
        Snapshot snap1 = Snapshot.snapshot(doc0);
        text0.applyDelta(List.of(
                createRetainOp(4),
                createInsertOp("e")
        ), true);

        List<EventOperator> delta1 = text0.toDelta(snap1, null, null);
        assertEquals(1, delta1.size(), "snap1 delta should have 1 element, got: " + delta1);
        assertEquals("abcd", delta1.get(0).getInsert());
    }

    @Test
    void testFormattingRemoved() {
        Doc doc = new Doc();
        YText text = doc.getText("text");
        text.insert(0, "ab", Map.of("bold", true));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("bold", null);
        text.format(0, 2, attrs);
        List<EventOperator> delta = text.toDelta();
        assertEquals(1, delta.size());
        assertEquals("ab", delta.get(0).getInsert());
        assertNull(delta.get(0).getAttributes());
    }

    @Test
    void testFormattingRemovedInMidText() {
        Doc doc = new Doc();
        YText text = doc.getText("text");
        text.insert(0, "1234");
        text.insert(2, "ab", Map.of("bold", true));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("bold", null);
        text.format(2, 2, attrs);
        List<EventOperator> delta = text.toDelta();
        assertEquals(1, delta.size());
        assertEquals("12ab34", delta.get(0).getInsert());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testInsertAndDeleteAtRandomPositions() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YText text0 = (YText) result.get("text0");
        YText text1 = (YText) result.get("text1");

        Random random = new Random(42);
        int N = 100;
        text0.insert(0, "abc");
        testConnector.flushAllMessages();

        for (int i = 0; i < N; i++) {
            int pos = random.nextInt((int) text0.getLength() + 1);
            if (random.nextBoolean() && text0.getLength() > 0) {
                int delLen = Math.min(random.nextInt(3) + 1, (int) text0.getLength() - pos);
                if (delLen > 0) {
                    text0.delete(pos, delLen);
                }
            } else {
                String insertStr = String.valueOf((char) ('a' + random.nextInt(26)));
                text0.insert(pos, insertStr);
            }
        }
        testConnector.flushAllMessages();
        assertEquals(text0.toString(), text1.toString());
        TestHelper.compare(new ArrayList<>(users));
    }

    @Test
    void testSplitSurrogateCharacter() {
        Doc doc = new Doc();
        YText text = doc.getText("text");
        text.insert(0, "👾");
        assertEquals("👾", text.toString());
        assertEquals(2, text.getLength());
    }

    // Helper methods to create EventOperator for applyDelta
    private static EventOperator createInsertOp(Object insert) {
        EventOperator op = new EventOperator();
        op.insert = insert;
        op.isInsertDefined = true;
        return op;
    }

    private static EventOperator createRetainOp(long retain) {
        EventOperator op = new EventOperator();
        op.retain = retain;
        op.isRetainDefined = true;
        return op;
    }

    private static EventOperator createDeleteOp(long delete) {
        EventOperator op = new EventOperator();
        op.delete = delete;
        op.isDeleteDefined = true;
        return op;
    }
}
