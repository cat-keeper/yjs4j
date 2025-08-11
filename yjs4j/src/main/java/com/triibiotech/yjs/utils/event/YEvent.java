package com.triibiotech.yjs.utils.event;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.utils.Transaction;

import java.util.*;

/**
 * Base class for all Yjs events
 *
 * @author zbs
 * @date 2025/07/28  15:15:51
 */
@SuppressWarnings("unused")
public abstract class YEvent<T extends AbstractType<?>> {

    /**
     * The type that this event was created on
     */
    public final T target;

    /**
     * The current target of the event
     */
    public T currentTarget;

    /**
     * The transaction that created this event
     */
    public final Transaction transaction;

    public Changes changes;

    public Map<String, EventAction> keys;
    public List<EventOperator> delta;
    public List<String> path;

    public YEvent(T target, Transaction transaction) {
        this.target = target;
        this.transaction = transaction;
        this.currentTarget = target;
    }

    /**
     * Computes the path from the root document to this event's target
     */
    public List<String> getPath() {
        if (this.path != null) {
            return this.path;
        }
        this.path = getPathTo(this.currentTarget, this.target);
        return this.path;
    }

    public List<String> getPathTo(T parent, T child) {
        List<String> path = new ArrayList<>();
        while (child.getItem() != null && child != parent) {
            if (child.getItem().parentSub != null) {
                path.add(0, child.getItem().parentSub);
            } else {
                long i = 0L;
                Item c = ((AbstractType<?>) child.getItem().parent).getStart();
                while (c != child.getItem() && c != null) {
                    if (!c.isDeleted() && c.countable()) {
                        i += c.length;
                    }
                    c = (Item) c.right;
                }
                path.add(0, i + "");
            }
            child = (T) child.getItem().parent;
        }
        return path;
    }

    public boolean deletes(AbstractStruct struct) {
        return this.transaction.deleteSet.isDeleted(struct.id);
    }

    public Map<String, EventAction> getKeys() {
        if (this.keys == null) {
            if (this.transaction.doc.transactionCleanups.isEmpty()) {
                throw new RuntimeException("You must not compute changes after the event-handler fired.");
            }
            Map<String, EventAction> keys = new HashMap<>();
            T target = this.target;
            Set<String> changed = this.transaction.changed.get(target);
            changed.forEach(key -> {
                if (key != null) {
                    Item item = target.getMap().get(key);
                    String action;
                    Object oldValue;
                    if (this.adds(item)) {
                        Item prev = (Item) item.left;
                        while (prev != null && this.adds(prev)) {
                            prev = (Item) prev.left;
                        }
                        if (this.deletes(item)) {
                            if (prev != null && this.deletes(prev)) {
                                action = "delete";
                                oldValue = prev.content.getContent()[prev.content.getContent().length - 1];
                            } else {
                                return;
                            }
                        } else {
                            if (prev != null && this.deletes(prev)) {
                                action = "update";
                                oldValue = prev.content.getContent()[prev.content.getContent().length - 1];
                            } else {
                                action = "add";
                                oldValue = null;
                            }
                        }
                    } else {
                        if (this.deletes(item)) {
                            action = "delete";
                            oldValue = item.content.getContent()[item.content.getContent().length - 1];
                        } else {
                            return;
                        }
                    }
                    keys.put(key, new EventAction(action, oldValue));
                }
            });
            this.keys = keys;
        }
        return this.keys;
    }

    public List<EventOperator> getDelta() {
        return this.getChanges().delta;
    }

    public boolean adds(AbstractStruct struct) {
        Long i = this.transaction.beforeState.get(struct.id.client);
        return struct.id.clock >= Objects.requireNonNullElse(i, 0L);
    }

    public Changes getChanges() {
        Changes changes = this.changes;
        if (changes != null) {
            return changes;
        }
        if (this.transaction.doc.transactionCleanups.isEmpty()) {
            throw new RuntimeException("You must not compute changes after the event-handler fired.");
        }
        T target = this.target;
        Set<Item> added = new LinkedHashSet<>();
        Set<Item> deleted = new LinkedHashSet<>();
        List<EventOperator> delta = new ArrayList<>();
        changes = new Changes(added, deleted, this.getKeys(), delta);
        Set<String> changed = this.transaction.changed.getOrDefault(target, new LinkedHashSet<>());
        if (changed.contains(null) || changed.contains("")) {
            EventOperator lastOp = null;
            for (Item item = target.getStart(); item != null; item = (Item) item.right) {
                if (item.isDeleted()) {
                    if (this.deletes(item) && !this.adds(item)) {
                        if (lastOp == null || !lastOp.isDeleteDefined) {
                            if (lastOp != null) {
                                delta.add(lastOp);
                            }
                            lastOp = new EventOperator();
                            lastOp.delete = 0L;
                            lastOp.isDeleteDefined = true;
                        }
                        lastOp.delete += item.length;
                        deleted.add(item);
                    } // else nop
                } else {
                    if (this.adds(item)) {
                        if (lastOp == null || !lastOp.isInsertDefined) {
                            if (lastOp != null) {
                                delta.add(lastOp);
                            }
                            lastOp = new EventOperator();
                            lastOp.insert = new ArrayList<>();
                            lastOp.isInsertDefined = true;
                        }
                        lastOp.insert = Arrays.asList(item.content.getContent());
                        added.add(item);
                    } else {
                        if (lastOp == null || !lastOp.isRetainDefined) {
                            if (lastOp != null) {
                                delta.add(lastOp);
                            }
                            lastOp = new EventOperator();
                            lastOp.retain = 0L;
                            lastOp.isRetainDefined = true;
                        }
                        lastOp.retain += item.length;
                    }
                }
            }

            if (lastOp != null && lastOp.isRetainDefined) {
                delta.add(lastOp);
            }
        }
        this.changes = changes;
        return changes;
    }

    public static class Changes {
        public Set<Item> added;
        public Set<Item> deleted;
        public Map<String, EventAction> keys;
        public List<EventOperator> delta;

        public Changes(Set<Item> added, Set<Item> deleted, Map<String, EventAction> keys, List<EventOperator> delta) {
            this.added = added;
            this.deleted = deleted;
            this.keys = keys;
            this.delta = delta;
        }
    }

    public T getTarget() {
        return target;
    }

    public T getCurrentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(T currentTarget) {
        this.currentTarget = currentTarget;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setChanges(Changes changes) {
        this.changes = changes;
    }

    public void setKeys(Map<String, EventAction> keys) {
        this.keys = keys;
    }


    public void setDelta(List<EventOperator> delta) {
        this.delta = delta;
    }


    public void setPath(List<String> path) {
        this.path = path;
    }
}
