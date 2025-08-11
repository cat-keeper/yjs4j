package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.YArray;
import com.triibiotech.yjs.types.YMap;
import com.triibiotech.yjs.types.YXmlElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author zbs
 * @date 2025/8/5 15:17
 **/
public class SnapshotTest {
    @Test
    void testBasic() {
        Doc ydoc = new Doc();
        ydoc.gc = false;
        ydoc.getText("").insert(0, "world!");
        Snapshot snapshot = Snapshot.snapshot(ydoc);
        ydoc.getText("").insert(0, "hello ");
        Doc restored = Snapshot.createDocFromSnapshot(ydoc, snapshot, null);
        Assertions.assertEquals("world!", restored.getText("").toString());
    }

    @Test
    void testBasicXmlAttributes() {
        Doc ydoc = new Doc();
        ydoc.gc = false;
        YXmlElement yxml = (YXmlElement) ydoc.getMap("").set("el", new YXmlElement("div"));
        Snapshot snapshot1 = Snapshot.snapshot(ydoc);
        yxml.setAttribute("a", "1");
        Snapshot snapshot2 = Snapshot.snapshot(ydoc);
        yxml.setAttribute("a", "2");
        Assertions.assertEquals("2", yxml.getAttributes(null).get("a"));
        Assertions.assertEquals("1", yxml.getAttributes(snapshot2).get("a"));
        Assertions.assertTrue(yxml.getAttributes(snapshot1).isEmpty());
    }

    @Test
    void testBasicRestoreSnapshot() {
        Doc doc = new Doc();
        doc.gc = false;
        doc.getArray("array").insert(0, "hello");
        Snapshot snap = Snapshot.snapshot(doc);
        doc.getArray("array").insert(1, "world");
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);
        List<Object> array = docRestored.getArray("array").toArray();
        Assertions.assertTrue(array.contains("hello") && array.size() == 1);
        List<Object> array1 = doc.getArray("array").toArray();
        Assertions.assertTrue(array1.contains("hello") && array1.contains("world") && array1.size() == 2);
    }

    @Test
    void testEmptyRestoreSnapshot() {
        Doc doc = new Doc();
        doc.gc = false;
        Snapshot snap = Snapshot.snapshot(doc);
        snap.sv.put(9999L, 0L);
        doc.getArray("").insert(0, "world");
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);

        Assertions.assertTrue(docRestored.getArray("").toArray().isEmpty());
        Assertions.assertTrue(doc.getArray("").toArray().contains("world"));

        Snapshot snap2 = Snapshot.snapshot(doc);
        Doc docRestored2 = Snapshot.createDocFromSnapshot(doc, snap2, null);
        Assertions.assertTrue(docRestored2.getArray("").toArray().contains("world"));
    }

    @Test
    void testRestoreSnapshotWithSubType() {
        Doc doc = new Doc();
        doc.gc = false;
        doc.getArray("array").insert(0, new YMap<String>());
        YMap<String> subMap = (YMap<String>) doc.getArray("array").get(0);
        subMap.set("key1", "value1");
        Snapshot snap = Snapshot.snapshot(doc);
        subMap.set("key2", "value2");
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);
        List<Object> json = docRestored.getArray("array").toJson();
        Assertions.assertTrue(json.get(0) instanceof Map<?, ?> map && map.get("key1").equals("value1"));
        List<Object> json1 = doc.getArray("array").toJson();
        Assertions.assertTrue(json1.get(0) instanceof Map<?, ?> map && map.get("key1").equals("value1") && map.get("key2").equals("value2"));
    }


    @Test
    void testRestoreDeletedItem1() {
        Doc doc = new Doc();
        doc.gc = false;
        doc.getArray("array").insert(0, "item1", "item2");
        Snapshot snap = Snapshot.snapshot(doc);
        doc.getArray("array").delete(0L);
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);
        List<Object> array = docRestored.getArray("array").toArray();
        Assertions.assertTrue(array.contains("item1") && array.contains("item2") && array.size() == 2);
        List<Object> array1 = doc.getArray("array").toArray();
        Assertions.assertTrue(array1.contains("item2") && array1.size() == 1);
    }

    @Test
    void testRestoreLeftItem() {
        Doc doc = new Doc();
        doc.gc = false;
        doc.getArray("array").insert(0, "item1");
        doc.getMap("map").set("test", 1);
        doc.getArray("array").insert(0, "item0");
        Snapshot snap = Snapshot.snapshot(doc);
        doc.getArray("array").delete(1L);
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);
        List<Object> array = docRestored.getArray("array").toArray();
        Assertions.assertTrue(array.contains("item0") && array.contains("item1") && array.size() == 2);
        List<Object> array1 = doc.getArray("array").toArray();
        Assertions.assertTrue(array1.contains("item0") && array1.size() == 1);
    }

    @Test
    void testDeletedItemsBase() {
        Doc doc = new Doc();
        doc.gc = false;
        doc.getArray("array").insert(0, "item1");
        doc.getArray("array").delete(0);
        Snapshot snap = Snapshot.snapshot(doc);
        doc.getArray("array").insert(0, "item0");
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);
        List<Object> array = docRestored.getArray("array").toArray();
        Assertions.assertTrue(array.isEmpty());
        List<Object> array1 = doc.getArray("array").toArray();
        Assertions.assertTrue(array1.contains("item0") && array1.size() == 1);
    }

    @Test
    void testDeletedItems2() {
        Doc doc = new Doc();
        doc.gc = false;
        doc.getArray("array").insert(0, "item1", "item2", "item3");
        doc.getArray("array").delete(1);
        Snapshot snap = Snapshot.snapshot(doc);
        doc.getArray("array").insert(0, "item0");
        Doc docRestored = Snapshot.createDocFromSnapshot(doc, snap, null);
        List<Object> array = docRestored.getArray("array").toArray();
        Assertions.assertTrue(array.contains("item1") && array.contains("item3") && array.size() == 2);
        List<Object> array1 = doc.getArray("array").toArray();
        Assertions.assertTrue(array1.contains("item0") && array.contains("item1") && array1.contains("item3") && array1.size() == 3);
    }

    @Test
    void testDependentChanges() {

    }

    @Test
    void testContainsUpdate() {
        Doc ydoc = new Doc();
        List<byte[]> updates = new ArrayList<>();
        ydoc.on("update", (ObservableV2.Handler) update -> {
            updates.add((byte[]) update[0]);
        });
        YArray<Integer> yarr = ydoc.getArray("");
        Snapshot snapshot1 = Snapshot.snapshot(ydoc);
        yarr.insert(0, 1);
        Snapshot snapshot2 = Snapshot.snapshot(ydoc);
        yarr.delete(0, 1);
        Snapshot snapshotFinal = Snapshot.snapshot(ydoc);

        boolean a = Snapshot.snapshotContainsUpdate(snapshot1, updates.get(0));
        Assertions.assertFalse(a);
        boolean b = Snapshot.snapshotContainsUpdate(snapshot2, updates.get(1));
        Assertions.assertFalse(b);
        boolean c = Snapshot.snapshotContainsUpdate(snapshot2, updates.get(0));
        Assertions.assertTrue(c);
        boolean d = Snapshot.snapshotContainsUpdate(snapshotFinal, updates.get(0));
        Assertions.assertTrue(d);
        boolean e = Snapshot.snapshotContainsUpdate(snapshotFinal, updates.get(1));
        Assertions.assertTrue(e);


    }

}
