package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.utils.event.YEvent;

import java.util.*;

import static com.triibiotech.yjs.utils.ParentUtils.isParentOf;

/**
 * @author zbs
 * @date 2025/7/31 17:16
 **/
public class StackItem {
    public DeleteSet deletions;
    public DeleteSet insertions;
    public Map<String, Object> meta;


    public StackItem(DeleteSet deletions, DeleteSet insertions) {
        this.deletions = deletions;
        this.insertions = insertions;
        this.meta = new HashMap<>();
    }

    public DeleteSet getDeletions() {
        return deletions;
    }

    public void setDeletions(DeleteSet deletions) {
        this.deletions = deletions;
    }

    public DeleteSet getInsertions() {
        return insertions;
    }

    public void setInsertions(DeleteSet insertions) {
        this.insertions = insertions;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public static void clearUndoManagerStackItem(Transaction tr, UndoManager um, StackItem stackItem) {
        DeleteSet.iterateDeletedStructs(tr, stackItem.deletions, item -> {
            if (item instanceof Item && um.getScope().stream().anyMatch(type -> type == tr.doc || isParentOf((AbstractType<?>) type, (Item) item))) {
                Item.keepItem((Item) item, false);
            }
        });
    }

    public static StackItem popStackItem(UndoManager undoManager, List<StackItem> stack, String eventType) {

        final Transaction[] trHolder = new Transaction[1];
        Doc doc = undoManager.getDoc();
        List<Object> scope = undoManager.getScope();
        Transaction.transact(doc, transaction -> {
            while (!stack.isEmpty() && undoManager.getCurrStackItem() == null) {
                StructStore store = doc.store;
                StackItem stackItem = stack.remove(stack.size() - 1);
                Set<Item> itemsToRedo = new LinkedHashSet<>();
                List<Item> itemsToDelete = new ArrayList<>();
                boolean performedChange = false;

                DeleteSet.iterateDeletedStructs(transaction, stackItem.insertions, struct -> {
                    if (struct instanceof Item item) {
                        if (item.redone != null) {
                            Map<String, Object> res = Item.followRedone(store, item.id);
                            AbstractStruct resItem = (AbstractStruct) res.get("item");
                            if ((Long) res.get("diff") > 0) {
                                resItem = StructStore.getItemCleanStart(transaction, ID.createId(resItem.id.client, resItem.id.clock + (Long) res.get("diff")));
                            }
                            struct = resItem;
                        }
                        Item finalStruct = (Item) struct;
                        if (!item.isDeleted() && scope.stream().anyMatch(type -> type == transaction.doc || ParentUtils.isParentOf((AbstractType<?>) type, finalStruct))) {
                            itemsToDelete.add(item);
                        }
                    }
                });

                DeleteSet.iterateDeletedStructs(transaction, stackItem.deletions, struct -> {
                    if (struct instanceof Item item) {
                        boolean inScope = scope.stream().anyMatch(type -> type == transaction.doc || isParentOf((AbstractType<?>) type, item));
                        boolean isNotCreatedAndDeletedSame = !stackItem.insertions.isDeleted(item.id);
                        if (inScope && isNotCreatedAndDeletedSame) {
                            itemsToRedo.add(item);
                        }
                    }
                });

                for (Item item : itemsToRedo) {
                    boolean changed = Item.redoItem(transaction, item, itemsToRedo, stackItem.insertions, undoManager.getIgnoreRemoteMapChanges(), undoManager) != null;
                    performedChange = performedChange || changed;
                }

                // delete items in reverse
                for (int i = itemsToDelete.size() - 1; i >= 0; i--) {
                    Item item = itemsToDelete.get(i);
                    if (undoManager.getDeleteFilter().apply(item)) {
                        item.delete(transaction);
                        performedChange = true;
                    }
                }
                undoManager.setCurrStackItem(performedChange ? stackItem : null);
            }

            // Clean up search markers
            transaction.changed.forEach((type, subProps) -> {
                if (subProps.contains(null) && type.getSearchMarker() != null) {
                    type.getSearchMarker().clear();
                }
            });

            trHolder[0] = transaction;
            return true;
        }, undoManager, true);

        StackItem res = undoManager.getCurrStackItem();
        if (res != null) {
            Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes = trHolder[0].getChangedParentTypes();
            Param param = new Param(res, eventType, changedParentTypes, undoManager);
            undoManager.emit("stack-item-popped", param, undoManager);
            undoManager.setCurrStackItem(null);
        }
        return res;
    }

    public record Param(StackItem stackItem, String type, Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes,
                        Object origin) {
    }
}
