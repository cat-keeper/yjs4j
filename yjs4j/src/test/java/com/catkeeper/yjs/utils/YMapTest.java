package com.catkeeper.yjs.utils;

import com.catkeeper.yjs.helper.TestConnector;
import com.catkeeper.yjs.helper.TestHelper;
import com.catkeeper.yjs.helper.TestYInstance;
import com.catkeeper.yjs.types.YArray;
import com.catkeeper.yjs.types.YMap;
import com.catkeeper.yjs.utils.event.YEvent;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Port of yjs y-map.tests.js
 */
class YMapTest {

    @Test
    void testIterators() {
        Doc ydoc = new Doc();
        YMap<Integer> ymap = ydoc.getMap("");
        // just checking that iterators don't throw
        List<Integer> vals = new ArrayList<>();
        for (Integer v : ymap.values()) {
            vals.add(v);
        }
        Set<String> keys = ymap.keys();
        assertTrue(vals.isEmpty());
        assertTrue(keys.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBasicMapTests() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        YMap<Object> map1 = (YMap<Object>) result.get("map1");
        YMap<Object> map2 = (YMap<Object>) result.get("map2");
        users.get(2).disconnect();

        map0.set("null", null);
        map0.set("number", 1);
        map0.set("string", "hello Y");
        map0.set("object", Map.of("key", Map.of("key2", "value")));
        map0.set("y-map", new YMap<>());
        map0.set("boolean1", true);
        map0.set("boolean0", false);
        YMap<Object> innerMap = (YMap<Object>) map0.get("y-map");
        innerMap.set("y-array", new YArray<>());
        YArray<Object> array = (YArray<Object>) innerMap.get("y-array");
        array.insert(0, 0);
        array.insert(0, -1);

        assertNull(map0.get("null"));
        assertEquals(1, map0.get("number"));
        assertEquals("hello Y", map0.get("string"));
        assertEquals(false, map0.get("boolean0"));
        assertEquals(true, map0.get("boolean1"));
        assertEquals(-1, ((YArray<Object>) ((YMap<Object>) map0.get("y-map")).get("y-array")).get(0));
        assertEquals(7, map0.getSize());

        users.get(2).connect();
        testConnector.flushAllMessages();

        assertNull(map1.get("null"));
        assertEquals(1, ((Number) map1.get("number")).intValue());
        assertEquals("hello Y", map1.get("string"));
        assertEquals(false, map1.get("boolean0"));
        assertEquals(true, map1.get("boolean1"));
        assertEquals(7, map1.getSize());

        // compare disconnected user
        assertNull(map2.get("null"));
        assertEquals(1, ((Number) map2.get("number")).intValue());
        assertEquals("hello Y", map2.get("string"));
        assertEquals(false, map2.get("boolean0"));
        assertEquals(true, map2.get("boolean1"));
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetAndSetOfMapProperty() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        map0.set("stuff", "stuffy");
        map0.set("null", null);
        assertEquals("stuffy", map0.get("stuff"));
        testConnector.flushAllMessages();
        for (TestYInstance user : users) {
            YMap<Object> u = user.getMap("map");
            assertEquals("stuffy", u.get("stuff"));
            assertNull(u.get("null"));
        }
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testYmapSetsYmap() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        YMap<Object> innerMap = (YMap<Object>) map0.set("Map", new YMap<>());
        assertSame(map0.get("Map"), innerMap);
        innerMap.set("one", 1);
        assertEquals((Object) 1, innerMap.get("one"));
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testYmapSetsYarray() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        YArray<Object> array = (YArray<Object>) map0.set("Array", new YArray<>());
        assertSame(array, map0.get("Array"));
        array.insert(0, 1, 2, 3);
        assertEquals("{Array=[1, 2, 3]}", map0.toJson().toString());
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetAndSetOfMapPropertySyncs() {
        Map<String, Object> result = TestHelper.init(2, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        map0.set("stuff", "stuffy");
        assertEquals("stuffy", map0.get("stuff"));
        testConnector.flushAllMessages();
        for (TestYInstance user : users) {
            YMap<Object> u = user.getMap("map");
            assertEquals("stuffy", u.get("stuff"));
        }
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetAndSetOfMapPropertyWithConflict() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        YMap<Object> map1 = (YMap<Object>) result.get("map1");
        map0.set("stuff", "c0");
        map1.set("stuff", "c1");
        testConnector.flushAllMessages();
        for (TestYInstance user : users) {
            YMap<Object> u = user.getMap("map");
            assertEquals("c1", u.get("stuff"));
        }
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSizeAndDeleteOfMapProperty() {
        Map<String, Object> result = TestHelper.init(1, null);
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        map0.set("stuff", "c0");
        map0.set("otherstuff", "c1");
        assertEquals(2, map0.getSize());
        map0.delete("stuff");
        assertEquals(1, map0.getSize());
        map0.delete("otherstuff");
        assertEquals(0, map0.getSize());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetAndSetAndDeleteOfMapProperty() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        YMap<Object> map1 = (YMap<Object>) result.get("map1");
        map0.set("stuff", "c0");
        map1.set("stuff", "c1");
        map1.delete("stuff");
        testConnector.flushAllMessages();
        for (TestYInstance user : users) {
            YMap<Object> u = user.getMap("map");
            assertNull(u.get("stuff"));
        }
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSetAndClearOfMapProperties() {
        Map<String, Object> result = TestHelper.init(1, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        map0.set("stuff", "c0");
        map0.set("otherstuff", "c1");
        map0.clear();
        testConnector.flushAllMessages();
        for (TestYInstance user : users) {
            YMap<Object> u = user.getMap("map");
            assertNull(u.get("stuff"));
            assertNull(u.get("otherstuff"));
            assertEquals(0, u.getSize());
        }
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testObserveDeepProperties() {
        Map<String, Object> result = TestHelper.init(4, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        YMap<Object> map1 = (YMap<Object>) result.get("map1");
        YMap<Object> map2 = (YMap<Object>) result.get("map2");
        YMap<Object> map3 = (YMap<Object>) result.get("map3");

        AtomicReference<List<YEvent<?>>> eventsRef = new AtomicReference<>(new ArrayList<>());
        map1.observeDeep((events, tr) -> eventsRef.set(events));

        map2.set("mapType", new YMap<>());
        testConnector.flushAllMessages();
        ((YMap<Object>) map1.get("mapType")).set("deepValue", "value");
        testConnector.flushAllMessages();

        List<YEvent<?>> events = eventsRef.get();
        assertFalse(events.isEmpty());
        TestHelper.compare(new ArrayList<>(users));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testYmapEventHasCorrectValueWhenSettingAPrimitive() {
        Map<String, Object> result = TestHelper.init(3, null);
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        YMap<Object> map0 = (YMap<Object>) result.get("map0");
        AtomicReference<YEvent<?>> eventRef = new AtomicReference<>();
        map0.observe((event, tr) -> eventRef.set(event));
        map0.set("stuff", 2);
        assertNotNull(eventRef.get());
        assertEquals((Object) 2, map0.get("stuff"));
        TestHelper.compare(new ArrayList<>(users));
    }
}
