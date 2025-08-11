package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.types.YArray;
import com.triibiotech.yjs.types.YMap;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zbs
 * @date 2025/7/31 15:22
 **/
public class PermanentUserData {
    private YMap<String> yUsers;
    private Doc doc;
    private Map<Long, String> clients;
    private Map<String, DeleteSet> dss;

    public PermanentUserData(Doc doc) {
        this.doc = doc;
        this.yUsers = doc.getMap("users");
        this.clients = new HashMap<>();
        this.dss = new HashMap<>();
        YMap<String> storeType = doc.getMap("users");
        storeType.observe((event, transaction) -> {
            event.getKeysChanged().forEach(userDescription ->
                    initUser((YMap<String>) yUsers.get(userDescription), userDescription)
            );
        });
        this.yUsers.forEach(
            new AbstractType.TriConsumer<String, String, YMap<String>>() {
                @Override
                public void accept(String obj, String key, YMap<String> newValue) {
                    initUser(newValue, key);
                }
        });
    }

    private void initUser(YMap<String> user, String userDescription) {
        YArray<?> ds = (YArray<?>) user.get("ds");
        YArray<?> ids = (YArray<?>) user.get("ids");
        ds.observe((event, transaction) -> {
            event.changes.added.forEach(item -> {
                new ArrayList<>(List.of(item.content.getContent())).forEach(encodedDs -> {
                    if (encodedDs instanceof byte[]) {
                        List<DeleteSet> deleteSets = new ArrayList<>();
                        if (this.dss.get(userDescription) != null) {
                            deleteSets.add(this.dss.get(userDescription));
                        } else {
                            deleteSets.add(DeleteSet.createDeleteSet());
                        }
                        deleteSets.add(DeleteSet.readDeleteSet(new UpdateDecoderV1(Decoder.createDecoder((byte[]) encodedDs))));
                        this.dss.put(userDescription, DeleteSet.mergeDeleteSets(deleteSets));
                    }
                });
            });
        });
        this.dss.put(
                userDescription,
                DeleteSet.mergeDeleteSets(
                        ds.map((encodedDs, index, type) ->
                                DeleteSet.readDeleteSet(new UpdateDecoderV1(Decoder.createDecoder((byte[]) encodedDs))))
                ));
        ids.observe((event, transaction) ->
                event.changes.added.forEach(item -> {
                    Object[] content = item.content.getContent();
                    for (Object o : content) {
                        addClientId((long) o, userDescription);
                    }
                })
        );
        ids.forEach((o, index, type) -> addClientId((long) o, userDescription));
    }

    public void setUserMapping(Doc doc, long clientId, String userDescription) {

    }

    private void addClientId(long clientId, String userDescription) {
        this.clients.put(clientId, userDescription);
    }
}
