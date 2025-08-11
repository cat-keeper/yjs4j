package com.triibiotech.yjs.utils.event;

import cn.hutool.core.util.ObjectUtil;
import com.triibiotech.yjs.structs.*;
import com.triibiotech.yjs.types.ItemTextListPosition;
import com.triibiotech.yjs.types.YText;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.Transaction;

import java.util.*;

public class YTextEvent extends YEvent<YText> {

    /**
     * Whether the children changed.
     */
    private boolean childListChanged = false;
    public Set<String> keysChanged;

    public YTextEvent(YText ytext, Transaction transaction, Set<String> subs) {
        super(ytext, transaction);
        keysChanged = new java.util.LinkedHashSet<>();
        subs.forEach(sub -> {
            if (sub == null) {
                this.childListChanged = true;
            } else {
                this.keysChanged.add(sub);
            }
        });
    }

    @Override
    public Changes getChanges() {
        if (this.changes == null) {
            Set<Item> added = new LinkedHashSet<>();
            Set<Item> deleted = new LinkedHashSet<>();
            this.changes = new Changes(added, deleted, this.getKeys(), this.getDelta());
        }
        return this.changes;
    }

    @Override
    public List<EventOperator> getDelta() {
        if (this.delta == null) {
            Doc y = this.target.doc;
            OpParams params = new OpParams(null, "", 0, 0);
            Transaction.transact(y, (transaction) -> {
                Map<String, Object> oldAttributes = new HashMap<>();

                Item item = target.getStart();
                // 遍历 Y.Text 链表
                while (item != null) {
                    if (item.getContent() instanceof ContentType || item.getContent() instanceof ContentEmbed) {
                        if (this.adds(item)) {
                            if (!this.deletes(item)) {
                                addOp(params);
                                params.action = "insert";
                                params.insert = item.getContent().getContent()[0];
                                addOp(params);
                            }
                        } else if (this.deletes(item)) {
                            if (!"delete".equals(params.action)) {
                                addOp(params);
                                params.action = "delete";
                            }
                            params.deleteLen += 1;
                        } else if (!item.isDeleted()) {
                            if (!"retain".equals(params.action)) {
                                addOp(params);
                                params.action = "retain";
                            }
                            params.retain += 1;
                        }
                        break;
                    } else if (item.getContent() instanceof ContentString content) {
                        if (this.adds(item)) {
                            if (!this.deletes(item)) {
                                if (!"insert".equals(params.action)) {
                                    addOp(params);
                                    params.action = "insert";
                                }
                                params.insert = params.insert.toString() + content.getStr();
                            }
                        } else if (this.deletes(item)) {
                            if (!"delete".equals(params.action)) {
                                addOp(params);
                                params.action = "delete";
                            }
                            params.deleteLen += item.getLength();
                        } else if (!item.isDeleted()) {
                            if (!"retain".equals(params.action)) {
                                addOp(params);
                                params.action = "retain";
                            }
                            params.retain += item.getLength();
                        }

                    } else if (item.getContent() instanceof ContentFormat content) {
                        String key = content.getKey();
                        Object value = content.getValue();

                        if (this.adds(item)) {
                            if (!this.deletes(item)) {
                                Object curVal = params.currentAttributes.getOrDefault(key, null);
                                if (!ObjectUtil.equal(curVal, value)) {
                                    if ("retain".equals(params.action)) {
                                        addOp(params);
                                    }
                                    if (ObjectUtil.equal(oldAttributes.getOrDefault(key, null), value)) {
                                        params.attributes.remove(key);
                                    } else {
                                        params.attributes.put(key, value);
                                    }
                                } else if (value != null) {
                                    item.delete(transaction);
                                }
                            }

                        } else if (this.deletes(item)) {
                            oldAttributes.put(key, value);
                            Object curVal = params.currentAttributes.getOrDefault(key, null);
                            if (!ObjectUtil.equal(curVal, value)) {
                                if ("retain".equals(params.action)) {
                                    addOp(params);
                                }
                                params.attributes.put(key, curVal);
                            }

                        } else if (!item.isDeleted()) {
                            oldAttributes.put(key, value);
                            Object attr = params.attributes.get(key);
                            if (attr != null) {
                                if (!ObjectUtil.equal(attr, value)) {
                                    if ("retain".equals(params.action)) {
                                        addOp(params);
                                    }
                                    if (value == null) {
                                        params.attributes.remove(key);
                                    } else {
                                        params.attributes.put(key, value);
                                    }
                                } else {
                                    item.delete(transaction);
                                }
                            }
                        }

                        if (!item.isDeleted()) {
                            if ("insert".equals(params.action)) {
                                addOp(params);
                            }
                            ItemTextListPosition.updateCurrentAttributes(params.currentAttributes, content);
                        }
                    }

                    item = (Item) item.right;
                }

                addOp(params);
                // 移除末尾的 retain（如果无属性）
                while (!delta.isEmpty()) {
                    EventOperator last = delta.get(delta.size() - 1);
                    if (last.getRetain() != null && last.getAttributes() == null) {
                        delta.remove(delta.size() - 1);
                    } else {
                        break;
                    }
                }

                return true;
            }, null, true);
            this.delta = params.delta;
        }
        return delta;
    }

    static class OpParams {
        String action;
        Object insert;
        long retain;
        long deleteLen;
        Map<String, Object> currentAttributes;
        Map<String, Object> attributes;
        List<EventOperator> delta;

        public OpParams(String action, Object insert, long retain, long deleteLen) {
            this.action = action;
            this.insert = insert;
            this.retain = retain;
            this.deleteLen = deleteLen;
            this.currentAttributes = new HashMap<>();
            this.attributes = new HashMap<>();
            this.delta = new ArrayList<>();
        }
    }

    private static void addOp(OpParams params) {
        if (params.action == null) {
            return;
        }
        EventOperator op = null;
        switch (params.action) {
            case "delete":
                if (params.deleteLen > 0) {
                    op = new EventOperator();
                    op.setDelete(params.deleteLen);
                }
                params.deleteLen = 0;
                break;
            case "insert":
                if (params.insert != null) {
                    op = new EventOperator();
                    op.setInsert(params.insert);
                    if (!params.currentAttributes.isEmpty()) {
                        op.setAttributes(new HashMap<>(params.currentAttributes));
                    }
                }
                params.insert = "";
                break;
            case "retain":
                if (params.retain > 0) {
                    op = new EventOperator();
                    op.setRetain(params.retain);
                    if (!params.attributes.isEmpty()) {
                        op.setAttributes(new HashMap<>(params.attributes));
                    }
                }
                params.retain = 0;
                break;
        }

        if (op != null) {
            params.delta.add(op);
        }
        params.action = null;
    }
}
