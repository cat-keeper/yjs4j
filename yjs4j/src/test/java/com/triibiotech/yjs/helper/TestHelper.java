package com.triibiotech.yjs.helper;

import cn.hutool.core.util.RandomUtil;
import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.types.YArray;
import com.triibiotech.yjs.types.YMap;
import com.triibiotech.yjs.types.YXmlElement;
import com.triibiotech.yjs.utils.*;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.EventOperator;
import org.junit.jupiter.api.Assertions;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author zbs
 * @date 2025/9/11 14:36
 **/
public class TestHelper {
    public static void broadcastMessage(TestYInstance y, byte[] m) {
        if (y.tc.onlineConns.contains(y)) {
            y.tc.onlineConns.forEach(remoteYInstance -> {
                if (remoteYInstance != y) {
                    remoteYInstance._receive(m, y);
                }
            });
        }
    }

    public static Map<String, Object> init(Integer users, Function<TestYInstance, ?> initTestObject) {
        if(users == null) {
            users = 5;
        }
        Map<String, Object> result = new HashMap<>();
        List<TestYInstance> usersResult = new ArrayList<>();
        result.put("users", usersResult);

        TestConnector testConnector = new TestConnector();
        result.put("testConnector", testConnector);
        for (int i = 0; i < users; i++) {
            TestYInstance y = testConnector.createY(i);
            y.clientId = i;
            usersResult.add(y);
            result.put("array" + i, y.getArray("array"));
            result.put("map" + i, y.getMap("map"));
            result.put("xml" + i, y.get("xml", YXmlElement.class));
            result.put("text" + i, y.getText("text"));
        }
        testConnector.syncAll();
        if (initTestObject != null) {
            List<?> list = usersResult.stream().map(initTestObject).toList();
            result.put("testObjects", list);
        } else {
            result.put("testObjects", usersResult.stream().map(y -> null).toList());
        }
        return result;
    }

    public static void compare(List<TestYInstance> users) {
        users.forEach(TestYInstance::connect);
        while (users.getFirst().tc.flushAllMessages()) {
            // do nothing
        }
        List<TestYInstance> mergedDocs = users.stream().map(user -> {
            TestYInstance doc = new TestYInstance(new TestConnector(), RandomUtil.randomLong());
            EncodingUtil.applyUpdate(doc, UpdateProcessor.mergeUpdates(user.updates));
            return doc;
        }).toList();
        users.addAll(mergedDocs);
        List<List<Object>> userArrayValues = users.stream().map(u -> u.getArray("array").toJson()).toList();
        List<Map<String, Object>> userMapValues = users.stream().map(u -> u.getMap("map").toJson()).toList();
        List<String> userXmlValues = users.stream().map(u -> u.get("xml", YXmlElement.class).toString()).toList();
        List<List<EventOperator>> userTextValues = users.stream().map(u -> u.getText("text").toDelta()).toList();
        for (TestYInstance u : users) {
            Assertions.assertNull(u.store.pendingDs);
            Assertions.assertNull(u.store.pendingStructs);
        }
        List<Object> array = users.get(0).getArray("array").toArray();
        YArray<Object> yArray = users.get(0).getArray("array");

        Set<String> ymapkeys = users.get(0).getMap("map").keys();
        Assertions.assertEquals(ymapkeys.size(), userMapValues.get(0).size());
        ymapkeys.forEach(key -> {
            Assertions.assertTrue(userMapValues.get(0).containsKey(key));
        });
        Map<String, Object> mapRes = new HashMap<>();

        YMap<Object> map = users.get(0).getMap("map");
        map.forEach((value, key, yMap) -> {
            mapRes.put(key, value instanceof AbstractType<?> typeValue ? typeValue.toJson() : value);
        });
        Assertions.assertEquals(mapRes, userMapValues.get(0));
        for (int i = 0; i < users.size() - 1; i++) {
            Assertions.assertEquals(userArrayValues.get(i).size(), users.get(i).getArray("array").getLength());
            Assertions.assertEquals(userArrayValues.get(i), userArrayValues.get(i + 1));
            Assertions.assertEquals(userMapValues.get(i), userMapValues.get(i + 1));
            Assertions.assertEquals(userXmlValues.get(i), userXmlValues.get(i + 1));
            Assertions.assertEquals(
                    userTextValues.get(i).stream().map(a -> {
                        return a.insert.equals("string") ? a.getInsert().toString() : " ";
                    }).collect(Collectors.joining("")).length(),
                    users.get(i).getText("text").getLength()
            );
            Assertions.assertEquals(userTextValues.get(i), userTextValues.get(i + 1));
            Assertions.assertEquals(EncodingUtil.encodeStateVector(users.get(i)), EncodingUtil.encodeStateVector(users.get(i + 1)));
            DeleteSet.equalDeleteSets(DeleteSet.createDeleteSetFromStructStore(users.get(i).store), DeleteSet.createDeleteSetFromStructStore(users.get(i + 1).store));
            compareStructStores(users.get(i).store, users.get(i + 1).store);
            Assertions.assertEquals(Snapshot.encodeSnapshot(Snapshot.snapshot(users.get(i))), Snapshot.encodeSnapshot(Snapshot.snapshot(users.get(i + 1))));
        }
        users.forEach(TestYInstance::destroy);
    }

    private static boolean compareItemIDs(Item a, Item b) {
        return a == b || (a != null && b != null && ID.compareIds(a.id, b.id));
    }

    private static void compareStructStores(StructStore ss1, StructStore ss2) {
        Assertions.assertEquals(ss1.getClients().size(), ss2.getClients().size());
        ss1.getClients().forEach((client, structs1) -> {
            LinkedList<AbstractStruct> structs2 = ss2.getClients().get(client);
            Assertions.assertNotNull(structs2);
            Assertions.assertEquals(structs1.size(), structs2.size());
            for (int i = 0; i < structs1.size(); i++) {
                AbstractStruct s1 = structs1.get(i);
                AbstractStruct s2 = structs2.get(i);
                Assertions.assertFalse(s1.getClass() != s2.getClass() ||
                        !ID.compareIds(s1.id, s2.id) ||
                        s1.isDeleted() != s2.isDeleted() ||
                        s1.length != s2.length
                );
                if (s1 instanceof Item) {
                    Assertions.assertFalse(
                            !(s2 instanceof Item)
                                    || !((s1.left == null && s2.left == null) || (s1.left != null && s2.left != null && ID.compareIds(((Item) s1.left).getLastId(), ((Item) s2.left).getLastId())))
                                    || !compareItemIDs((Item) s1.right, (Item) s2.right)
                                    || !ID.compareIds(((Item) s1).origin, ((Item) s2).origin)
                                    || !ID.compareIds(((Item) s1).rightOrigin, ((Item) s2).rightOrigin)
                                    || !Objects.equals(((Item) s1).parentSub, ((Item) s2).parentSub)
                    );
                    Assertions.assertTrue(s1.left == null || s1.left.right == s1);
                    Assertions.assertTrue(s1.right == null || s1.right.left == s1);
                    Assertions.assertTrue(s2.left == null || s2.left.right == s2);
                    Assertions.assertTrue(s2.right == null || s2.right.left == s2);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static void applyRandomTests(int iterations, Function<TestYInstance, ?> initTestObject, List<BiFunction<Doc, Object, Void>> mods) {
        Map<String, Object> result = init(5, initTestObject);
        TestConnector testConnector = (TestConnector) result.get("testConnector");
        List<TestYInstance> users = (List<TestYInstance>) result.get("users");
        for (int i = 0; i < iterations; i++) {
            if (RandomUtil.randomInt(0, 101) <= 2) {
                // 2% chance to disconnect/reconnect a random user
                if (RandomUtil.randomBoolean()) {
                    testConnector.disconnectRandom();
                } else {
                    testConnector.reconnectRandom();
                }
            } else if (RandomUtil.randomInt(0, 101) <= 1) {
                // 1% chance to flush all
                testConnector.flushAllMessages();
            } else if (RandomUtil.randomInt(0, 101) <= 50) {
                // 50% chance to flush a random message
                testConnector.flushRandomMessage();
            }
            int user = RandomUtil.randomInt(0, users.size());
            BiFunction<Doc, Object, Void> mod = mods.get(RandomUtil.randomInt(0, mods.size()));
            List<Object> list = (List<Object>) result.get("testObjects");
            mod.apply(users.get(user), list.get(user));
        }

    }
}
