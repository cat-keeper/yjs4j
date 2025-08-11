package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.*;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DocTest {

    @Test
    void testAfterTransactionRecursion() {
        Doc ydoc = new Doc();
        YXmlFragment yxml = ydoc.getXmlFragment("");
        ydoc.on("afterTransaction", new ObservableV2.Handler1<Transaction>() {
            @Override
            public void apply(Transaction transaction) {
                if (transaction.origin == "test") {
                    Object json = yxml.toJson();
                    System.out.println(json.toString());
                }
            }
        });
        ydoc.transact(tr -> {
            for (int i = 0; i < 15000; i++) {
                yxml.push(List.of(new YXmlText("a")));
            }
            return true;
        }, "test");
    }

    @Test
    void testOriginInTransaction() {
        Doc doc = new Doc();
        YText ytext = doc.getText("");
        List<Object> origins = new ArrayList<>();

        doc.on("afterTransaction", new ObservableV2.Handler1<Transaction>() {
            @Override
            public void apply(Transaction tr) {
                origins.add(tr.origin);
                if (origins.size() <= 1) {
                    ytext.toDelta(Snapshot.snapshot(doc), null, null);
                    doc.transact(transaction -> {
                        ytext.insert(0, "a", null);
                        return true;
                    }, "nested");
                }
            }
        });

        doc.transact(transaction -> {
            ytext.insert(0, "0", null);
            return true;
        }, "first");

        List<Object> expected = List.of("first", "cleanup", "nested");
        assertEquals(expected, origins);
    }

    @Test
    void testClientIdDuplicateChange() {
        Doc doc1 = new Doc();
        doc1.setClientId(0);
        Doc doc2 = new Doc();
        doc2.setClientId(0);
        assertEquals(doc2.getClientId(), doc1.getClientId());

        YArray<Integer> array = doc1.getArray("a");
        array.insert(0, 1, 2);

        byte[] update = EncodingUtil.encodeStateAsUpdate(doc1, null);
        EncodingUtil.applyUpdate(doc2, update);

        assertNotEquals(doc2.getClientId(), doc1.getClientId());
    }

    @Test
    void testGetTypeEmptyId() {
        Doc doc1 = new Doc();
        doc1.getText("").insert(0, "h", null);
        doc1.getText(null).insert(1, "i", null);
        Doc doc2 = new Doc();
        EncodingUtil.applyUpdate(doc2, EncodingUtil.encodeStateAsUpdate(doc1, null));
        assertEquals("hi", doc2.getText("").toString());
        assertEquals("hi", doc2.getText(null).toString());
    }

    @Test
    public void testToJSON() {
        Doc doc = new Doc();

        // 初始 toJSON 是空对象
        assertEquals(
                Map.of(),
                doc.toJson(),
                "doc.toJSON should yield empty object"
        );

        // 操作 array
        YArray<String> arr = doc.getArray("array");
        arr.push("test1");

        // 操作 map 和嵌套 map
        YMap<Object> map = doc.getMap("map");
        map.set("k1", "v1");

        YMap<Object> map2 = new YMap<>();
        map.set("k2", map2);
        map2.set("m2k1", "m2v1");

        // 构造预期结果
        Map<String, Object> expected = Map.of(
                "array", List.of("test1"),
                "map", Map.of(
                        "k1", "v1",
                        "k2", Map.of("m2k1", "m2v1")
                )
        );

        assertEquals(
                expected,
                doc.toJson(),
                "doc.toJSON should return correct nested structure"
        );
    }

    @Test
    public void testSubdoc() {
        // 主文档
        Doc doc = new Doc();
        doc.load(); // 不做任何事

        final List<List<String>> finalEvent = new ArrayList<>() {
            {
                add(new ArrayList<>());
                add(new ArrayList<>());
                add(new ArrayList<>());
            }
        };
        doc.on("subdocs", (ObservableV2.Handler1<Map<String, Set<Doc>>>) subdocs -> {
            finalEvent.set(0, subdocs.get("added").stream().map(Doc::getGuid).collect(Collectors.toList()));
            finalEvent.set(1, subdocs.get("removed").stream().map(Doc::getGuid).collect(Collectors.toList()));
            finalEvent.set(2, subdocs.get("loaded").stream().map(Doc::getGuid).collect(Collectors.toList()));
        });

        YMap<Doc> subdocs = doc.getMap("mysubdocs");

        // 新建子文档 A
        DocOptions docOptionsA = new DocOptions();
        docOptionsA.guid = "a";
        Doc docA = new Doc(docOptionsA);
        docA.load();
        subdocs.set("a", docA);

        assertEquals(List.of(List.of("a"), List.of(), List.of("a")), finalEvent);

        finalEvent.set(0, new ArrayList<>());
        finalEvent.set(1, new ArrayList<>());
        finalEvent.set(2, new ArrayList<>());
        ((Doc) subdocs.get("a")).load();
        assertEquals(0, finalEvent.get(0).size());
        assertEquals(0, finalEvent.get(1).size());
        assertEquals(0, finalEvent.get(2).size());

        finalEvent.set(0, new ArrayList<>());
        finalEvent.set(1, new ArrayList<>());
        finalEvent.set(2, new ArrayList<>());
        ((Doc) subdocs.get("a")).destroy();
        assertEquals(List.of(List.of("a"), List.of("a"), List.of()), finalEvent);

        ((Doc) subdocs.get("a")).load();
        assertEquals(List.of(List.of(), List.of(), List.of("a")), finalEvent);

        // 添加 b，但设置 shouldLoad = false
        DocOptions docOptionsB = new DocOptions();
        docOptionsB.guid = "a";
        docOptionsB.shouldLoad = false;
        Doc docB = new Doc(docOptionsB);
        subdocs.set("b", docB);
        assertEquals(List.of(List.of("a"), List.of(), List.of()), finalEvent);

        ((Doc) subdocs.get("b")).load();
        assertEquals(List.of(List.of(), List.of(), List.of("a")), finalEvent);

        DocOptions docOptionsC = new DocOptions();
        docOptionsC.guid = "c";
        Doc docC = new Doc(docOptionsC);
        docC.load();
        subdocs.set("c", docC);
        assertEquals(List.of(List.of("c"), List.of(), List.of("c")), finalEvent);
        assertEquals(List.of("a", "c"), new ArrayList<>(doc.getSubDocGuids()));

        Doc doc2 = new Doc();
        assertEquals(0, doc2.getSubDocs().size());
        final List<List<String>> finalEvent2 = new ArrayList<>() {
            {
                add(new ArrayList<>());
                add(new ArrayList<>());
                add(new ArrayList<>());
            }
        };
        doc2.on("subdocs", (ObservableV2.Handler1<Map<String, Set<Doc>>>) subdocs2 -> {
            finalEvent2.set(0, subdocs2.get("added").stream().map(Doc::getGuid).collect(Collectors.toList()));
            finalEvent2.set(1, subdocs2.get("removed").stream().map(Doc::getGuid).collect(Collectors.toList()));
            finalEvent2.set(2, subdocs2.get("loaded").stream().map(Doc::getGuid).collect(Collectors.toList()));
        });


        byte[] update = EncodingUtil.encodeStateAsUpdate(doc, null);
        EncodingUtil.applyUpdate(doc2, update);
        assertTrue(finalEvent2.get(0).containsAll(new HashSet<>(List.of("a", "a", "c"))));

        YMap<Doc> subdocs2 = doc2.getMap("mysubdocs");
        ((Doc) subdocs2.get("a")).load();
        assertEquals(List.of(List.of(), List.of(), List.of("a")), finalEvent2);

        assertEquals(List.of("a", "c"), new ArrayList<>(doc2.getSubDocGuids()));

        doc2.getMap("mysubdocs").delete("a");
        assertEquals(List.of(List.of(), List.of("a"), List.of()), finalEvent2);
        assertEquals(List.of("a", "c"), new ArrayList<>(doc2.getSubDocGuids())); // 仍然缓存 guid
    }

    @Test
    void testSubdocLoadEdgeCases() {
        Doc ydoc = new Doc();
        YArray<Doc> yarray = ydoc.getArray("");
        Doc subdoc1 = new Doc();
        final Map<String, Set<Doc>> lastEvent = new HashMap<>();
        ydoc.on("subdocs", (ObservableV2.Handler1<Map<String, Set<Doc>>>) lastEvent::putAll);
        yarray.insert(0, subdoc1);
        assertTrue(subdoc1.shouldLoad);
        assertFalse(subdoc1.autoLoad);
        assertTrue(lastEvent.get("loaded").contains(subdoc1));
        assertTrue(lastEvent.get("added").contains(subdoc1));
        subdoc1.destroy();
        Doc subdoc2 = yarray.get(0);
        assertNotSame(subdoc1, subdoc2);
        assertTrue(lastEvent.get("added").contains(subdoc2));
        assertFalse(lastEvent.get("loaded").contains(subdoc2));
        // load
        subdoc2.load();
        assertFalse(lastEvent.get("added").contains(subdoc2));
        assertTrue(lastEvent.get("loaded").contains(subdoc2));
        // apply from remote
        Doc ydoc2 = new Doc();
        lastEvent.clear();
        ydoc2.on("subdocs", (ObservableV2.Handler1<Map<String, Set<Doc>>>) lastEvent::putAll);
        EncodingUtil.applyUpdate(ydoc2, EncodingUtil.encodeStateAsUpdate(ydoc, null));
        Doc subdoc3 = (Doc) ydoc2.getArray("").get(0);
        assertFalse(subdoc3.shouldLoad);
        assertFalse(subdoc3.autoLoad);
        assertTrue(lastEvent.get("added").contains(subdoc3));
        assertFalse(lastEvent.get("loaded").contains(subdoc3));
        // load
        subdoc3.load();
        assertTrue(subdoc3.shouldLoad);
        assertFalse(lastEvent.get("added").contains(subdoc3));
        assertTrue(lastEvent.get("loaded").contains(subdoc3));
    }

    @Test
    void testSubdocLoadEdgeCasesAutoload() {
        Doc ydoc = new Doc();
        YArray<Doc> yarray = ydoc.getArray("");
        Doc subdoc1 = new Doc();
        subdoc1.setAutoLoad(true);
        final Map<String, Set<Doc>> lastEvent = new HashMap<>();
        ydoc.on("subdocs", (ObservableV2.Handler1<Map<String, Set<Doc>>>) lastEvent::putAll);
        yarray.insert(0, subdoc1);
        assertTrue(subdoc1.shouldLoad);
        assertTrue(subdoc1.autoLoad);
        assertTrue(lastEvent.get("loaded").contains(subdoc1));
        assertTrue(lastEvent.get("added").contains(subdoc1));
        // destroy and check whether lastEvent adds it again to added (it shouldn't)
        subdoc1.destroy();
        Doc subdoc2 = yarray.get(0);
        assertNotSame(subdoc1, subdoc2);
        assertTrue(lastEvent.get("added").contains(subdoc2));
        assertFalse(lastEvent.get("loaded").contains(subdoc2));
        // load
        subdoc2.load();
        assertFalse(lastEvent.get("added").contains(subdoc2));
        assertTrue(lastEvent.get("loaded").contains(subdoc2));
        // apply from remote
        Doc ydoc2 = new Doc();
        lastEvent.clear();
        ydoc2.on("subdocs", (ObservableV2.Handler1<Map<String, Set<Doc>>>) lastEvent::putAll);
        EncodingUtil.applyUpdate(ydoc2, EncodingUtil.encodeStateAsUpdate(ydoc, null));
        Doc subdoc3 = (Doc) ydoc2.getArray("").get(0);
        assertTrue(subdoc1.shouldLoad);
        assertTrue(subdoc1.autoLoad);
        assertTrue(lastEvent.get("added").contains(subdoc3));
        assertTrue(lastEvent.get("loaded").contains(subdoc3));
    }

    @Test
    void testSubdocsUndo() {
        //   const ydoc = new Y.Doc()
        //  const elems = ydoc.getXmlFragment()
        //  const undoManager = new Y.UndoManager(elems)
        //  const subdoc = new Y.Doc()
        //  // @ts-ignore
        //  elems.insert(0, [subdoc])
        //  undoManager.undo()
        //  undoManager.redo()
        //  t.assert(elems.length === 1)
        Doc ydoc = new Doc();
        YXmlFragment elems = ydoc.getXmlFragment("");
        UndoManager undoManager = new UndoManager(elems, new UndoManagerOptions());
        Doc subdoc = new Doc();
        elems.insert(0, List.of(subdoc));
        undoManager.undo();
        undoManager.redo();
        assertEquals(1, elems.getLength());
    }

    @Test
    void testLoadDocsEvent() throws ExecutionException, InterruptedException {
        Doc ydoc = new Doc();
        assertFalse(ydoc.isLoaded);
        AtomicBoolean loadedEvent = new AtomicBoolean(false);
        ydoc.on("load", () -> {
            loadedEvent.set(true);
        });
        ydoc.emit("load", ydoc);
        ydoc.loadListener.accept(ydoc);
        ydoc.whenLoaded.get();
        assertTrue(loadedEvent.get());
        assertTrue(ydoc.isLoaded);
    }

    // export const testLoadDocsEvent = async _tc => {
    //  const ydoc = new Y.Doc()
    //  t.assert(ydoc.isLoaded === false)
    //  let loadedEvent = false
    //  ydoc.on('load', () => {
    //    loadedEvent = true
    //  })
    //  ydoc.emit('load', [ydoc])
    //  await ydoc.whenLoaded
    //  t.assert(loadedEvent)
    //  t.assert(ydoc.isLoaded)
    //}


}
