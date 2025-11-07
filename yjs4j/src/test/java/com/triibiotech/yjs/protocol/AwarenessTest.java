package com.triibiotech.yjs.protocol;

import com.alibaba.fastjson2.JSONObject;
import com.triibiotech.yjs.protocol.awareness.Awareness;
import com.triibiotech.yjs.protocol.awareness.AwarenessEventParams;
import com.triibiotech.yjs.utils.Doc;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author zbs
 * @date 2025/8/3 16:39
 **/
public class AwarenessTest {


    @Test
    public void testAwareness() throws InterruptedException {
        System.out.println("Testing basic awareness functionality...");

        // Create two documents with specific client IDs
        Doc doc1 = new Doc();
        doc1.setClientId(0);
        Doc doc2 = new Doc();
        doc2.setClientId(1);

        Awareness aw1 = new Awareness(doc1);
        Awareness aw2 = new Awareness(doc2);

        // Set up cross-awareness updates
        aw1.on("update", params -> {
            if (params[0] instanceof AwarenessEventParams eventParams) {
                List<Long> allClients = new ArrayList<>();
                allClients.addAll(eventParams.added);
                allClients.addAll(eventParams.updated);
                allClients.addAll(eventParams.removed);

                if (!allClients.isEmpty()) {
                    byte[] enc = aw1.encodeAwarenessUpdate(allClients, null);
                    aw2.applyAwarenessUpdate(enc, null);
                }
            }
        });

        // Track changes
        final AwarenessEventParams[] lastChangeLocal = {null};
        final AwarenessEventParams[] lastChange = {null};

        aw1.on("change", (params) -> {
            lastChangeLocal[0] = (AwarenessEventParams) params[0];
        });

        aw2.on("change", (params) -> {
            lastChange[0] = (AwarenessEventParams) params[0];
        });

        // Test 1: Set initial state
        JSONObject state1 = new JSONObject();
        state1.put("x", 3);
        aw1.setLocalState(state1);

        // Wait a bit for async processing
        Thread.sleep(100);

        // Verify state propagation
        JSONObject remoteState = aw2.getStates().get(0L);
        Assertions.assertEquals(state1.get("x"), remoteState.get("x"));
        Assertions.assertEquals(1L, aw2.getMeta(0).clock());
        Assertions.assertTrue(lastChange[0].added.contains(0L), "Should have added client 0");
        Assertions.assertTrue(lastChangeLocal[0].updated.contains(0L), "Local should show update for client 0");

        System.out.println("✓ Initial state test passed");

        // Test 2: Update state
        lastChange[0] = null;
        lastChangeLocal[0] = null;

        JSONObject state2 = new JSONObject();
        state2.put("x", 4);
        aw1.setLocalState(state2);

        Thread.sleep(100);

        Object updatedState = aw2.getStates().get(0L);
        @SuppressWarnings("unchecked")
        Map<String, Object> stateMap = (Map<String, Object>) updatedState;
        assert stateMap.get("x").equals(4) : "State should be updated to x=4";
        assert lastChangeLocal[0].updated.contains(0L) : "Local should show update";
        assert Objects.equals(lastChangeLocal[0].added, lastChange[0].added) : "Change events should match";

        System.out.println("✓ State update test passed");

        // Test 3: Set same state (no change)
        lastChange[0] = null;
        lastChangeLocal[0] = null;

        aw1.setLocalState(state2); // Same state
        Thread.sleep(100);

        assert lastChange[0] == null : "No change event should be fired for same state";
        assert aw2.getMeta(0).clock() == 3 : "Clock should increment to 3";

        System.out.println("✓ Same state test passed");

        // Test 4: Remove state
        aw1.setLocalState(null);
        Thread.sleep(100);

        assert lastChange[0].removed.size() == 1 : "Should have one removed client";
        assert aw1.getStates().get(0L) == null : "Local state should be null";
        assert Objects.equals(lastChangeLocal[0].removed, lastChange[0].removed) : "Remove events should match";

        System.out.println("✓ Remove state test passed");

        // Cleanup
        aw1.destroy();
        aw2.destroy();
    }

    @Test
    void testAwarenessTimer() throws InterruptedException {
        System.out.println("Testing awareness timer functionality...");

        Doc doc = new Doc();
        Awareness awareness = new Awareness(doc);

        // Set initial state
        JSONObject initialState = new JSONObject();
        initialState.put("user", "test-user");
        awareness.setLocalState(initialState);

        long initialTime = awareness.getMeta(awareness.getClientId()).lastUpdated();
        System.out.println("Initial timestamp: " + initialTime);

        // Wait for timer to trigger (should renew state after 15 seconds)
        System.out.println("Waiting 16 seconds for timer to renew state...");
        Thread.sleep(16000);

        long renewedTime = awareness.getMeta(awareness.getClientId()).lastUpdated();
        System.out.println("Renewed timestamp: " + renewedTime);

        assert renewedTime > initialTime : "State should be renewed by timer";
        System.out.println("✓ Timer renewal test passed");

        // Test timeout removal with mock remote client
        long remoteClientId = 999L;
        awareness.states.put(remoteClientId, JSONObject.from(Map.of("user", "remote")));
        awareness.meta.put(remoteClientId, new Awareness.MetaClientState(1,
                System.currentTimeMillis() - Awareness.OUTDATED_TIMEOUT - 1000)); // Expired

        CountDownLatch latch = new CountDownLatch(1);
        awareness.on("change", (params) -> {
            AwarenessEventParams eventParams = (AwarenessEventParams) params[0];
            if (eventParams.removed.contains(remoteClientId)) {
                latch.countDown();
            }
        });

        // Wait for cleanup (timer runs every 3 seconds)
        boolean removed = latch.await(5, TimeUnit.SECONDS);
        assert removed : "Remote client should be removed by timeout";
        assert !awareness.getStates().containsKey(remoteClientId) : "Remote client should be cleaned up";

        System.out.println("✓ Timer cleanup test passed");

        awareness.destroy();
    }
}
