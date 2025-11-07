package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.types.ArraySearchMarker;
import com.triibiotech.yjs.utils.*;
import com.triibiotech.yjs.utils.lib0.Binary;

import java.util.*;

/**
 * Item represents any content in the Yjs document structure
 * Complete rewrite to match original Yjs Item.js functionality
 *
 * @author zbs
 */
@SuppressWarnings("unused")
public class Item extends AbstractStruct {

    /**
     * The item that was originally to the left of this item
     */
    public ID origin;

    /**
     * The item that was originally to the right of this item
     */
    public ID rightOrigin;

    /**
     * Parent type or ID
     */
    public Object parent;

    /**
     * If the parent refers to this item with some kind of key (e.g. YMap, the
     * key is specified here. The key is then used to refer to the list in which
     * to insert this item. If `parentSub = null` type._start is the list in
     * which to insert to. Otherwise it is `parent._map`.
     */
    public String parentSub;

    /**
     * If this type's effect is redone this type refers to the type that undid this operation
     */
    public ID redone;

    /**
     * The content of this item
     */
    public AbstractContent content;

    /**
     * Info flags: <br/>
     * bit1: keep, <br/>
     * bit2: countable, <br/>
     * bit3: deleted, <br/>
     * bit4: marker
     */
    private int info;

    /**
     * Constructor matching original Yjs Item
     */
    public Item(ID id, AbstractStruct left, ID origin, AbstractStruct right, ID rightOrigin,
                Object parent, String parentSub, AbstractContent content) {
        super(id, content == null ? 0 : content.getLength());
        this.origin = origin;
        this.left = left;
        this.right = right;
        this.rightOrigin = rightOrigin;
        this.parent = parent;
        this.parentSub = parentSub;
        this.redone = null;
        this.content = content;
        this.info = (content != null && content.isCountable()) ? Binary.BIT2 : 0;
    }


    public boolean marker() {
        return (this.info & Binary.BIT4) > 0;
    }

    public void setMarker(boolean isMarked) {
        if (marker() != isMarked) {
            this.info ^= Binary.BIT4;
        }
    }

    public boolean keep() {
        return (this.info & Binary.BIT1) > 0;
    }

    public void setKeep(boolean doKeep) {
        if (keep() != doKeep) {
            this.info ^= Binary.BIT1;
        }
    }

    public boolean countable() {
        return (this.info & Binary.BIT2) > 0;
    }

    @Override
    public boolean isDeleted() {
        return (this.info & Binary.BIT3) > 0;
    }

    public void setDelete(boolean doDelete) {
        if (isDeleted() != doDelete) {
            this.info ^= Binary.BIT3;
        }
    }

    public void markDeleted() {
        this.info |= Binary.BIT3;
    }

    /**
     * Get missing dependencies (matching JS getMissing)
     */
    @Override
    public Long getMissing(Transaction transaction, StructStore store) {
        if (this.origin != null && this.origin.client != this.id.client &&
                this.origin.clock >= store.getState(this.origin.client)) {
            return this.origin.client;
        }

        if (this.rightOrigin != null && this.rightOrigin.client != this.id.client &&
                this.rightOrigin.clock >= store.getState(this.rightOrigin.client)) {
            return this.rightOrigin.client;
        }

        if (this.parent != null && this.parent instanceof ID parentId) {
            if (this.id.client != parentId.client &&
                    parentId.clock >= store.getState(parentId.client)) {
                return parentId.client;
            }
        }

        if (this.origin != null) {
            this.left = store.getItemCleanEnd(transaction, this.origin);
            this.origin = this.left instanceof Item ? ((Item) this.left).getLastId() : null;
        }

        if (this.rightOrigin != null) {
            this.right = StructStore.getItemCleanStart(transaction, this.rightOrigin);
            this.rightOrigin = this.right.id;
        }
        if ((this.left != null && this.left instanceof GC) || this.right != null && this.right instanceof GC) {
            this.parent = null;
        } else if (this.parent == null) {
            // only set parent if this shouldn't be garbage collected
            if (this.left != null && (this.left instanceof Item leftItem)) {
                this.parent = leftItem.parent;
                this.parentSub = leftItem.parentSub;
            } else if (this.right != null && this.right instanceof Item rightItem) {
                this.parent = rightItem.parent;
                this.parentSub = rightItem.parentSub;
            }
        } else if (this.parent instanceof ID parentId) {
            AbstractStruct parentItem = store.find(parentId);
            if (parentItem instanceof GC) {
                this.parent = null;
            } else {
                AbstractContent abstractContent = ((Item) parentItem).content;
                if (abstractContent instanceof ContentType) {
                    this.parent = ((ContentType) abstractContent).getType();
                } else {
                    this.parent = null;
                }
            }
        }
        return null;
    }

    /**
     * @param transaction 事务
     * @param offset      抵消
     */
    @Override
    public void integrate(Transaction transaction, long offset) {
        if (offset > 0) {
            this.id.clock += offset;
            this.left = transaction.doc.store.getItemCleanEnd(transaction, ID.createId(this.id.client, this.id.clock - 1));
            this.origin = this.left instanceof Item leftItem ? leftItem.getLastId() : null;
            this.content = this.content.splice((int) offset);
            this.length -= offset;
        }

        if (this.parent != null) {
            boolean leftCondition = this.left == null
                    && (this.right == null || (this.right instanceof Item rItem && rItem.left != null));

            boolean rightCondition = this.left != null && (this.left instanceof Item lItem && lItem.right != this.right);
            if (leftCondition || rightCondition) {
                AbstractStruct left = this.left;
                AbstractStruct o = null;
                if (left != null) {
                    o = left.right;
                } else if (this.parentSub != null) {
                    if (this.parent instanceof AbstractType<?> pType) {
                        o = pType.getMap().get(this.parentSub);
                    }
                    while (o != null && o.left != null) {
                        o = o.left;
                    }
                } else {
                    o = this.parent instanceof AbstractType<?> pType ? pType.getStart() : null;
                }
                LinkedHashSet<Item> conflictingItems = new LinkedHashSet<>();
                LinkedHashSet<Item> itemsBeforeOrigin = new LinkedHashSet<>();
                while (o instanceof Item oItem && !Objects.equals(this.right, o)) {
                    itemsBeforeOrigin.add(oItem);
                    conflictingItems.add(oItem);
                    if (ID.compareIds(this.origin, oItem.origin)) {
                        // case 1
                        if (oItem.id.client < this.id.client) {
                            left = oItem;
                            conflictingItems.clear();
                        } else if (ID.compareIds(this.rightOrigin, oItem.rightOrigin)) {
                            // this and o are conflicting and point to the same integration points. The id decides which item comes first.
                            // Since this is to the left of o, we can break here
                            break;
                        }// else, o might be integrated before an item that this conflicts with. If so, we will find it in the next iterations
                    } else if (oItem.origin != null && itemsBeforeOrigin.contains((Item) transaction.doc.store.getItem(oItem.origin))) {
                        // case 2
                        if (!conflictingItems.contains((Item) transaction.doc.store.getItem(oItem.origin))) {
                            left = o;
                            conflictingItems.clear();
                        }
                    } else {
                        break;
                    }
                    o = oItem.right;
                }
                this.left = left;
            }

            if (this.left != null && this.left instanceof Item leftItem) {
                this.right = leftItem.right;
                leftItem.right = this;
            } else {
                Item r;
                if (this.parentSub != null && !this.parentSub.isEmpty()) {
                    r = this.parent instanceof AbstractType<?> pType ? pType.getMap().get(this.parentSub) : null;
                    while (r != null && r.left instanceof Item rLeft) {
                        r = rLeft;
                    }
                } else {
                    r = this.parent instanceof AbstractType<?> pType ? pType.getStart() : null;
                    if (this.parent instanceof AbstractType<?> pType) {
                        pType.setStart(this);
                    }
                }
                this.right = r;
            }
            if (this.right != null && this.right instanceof Item rightItem) {
                rightItem.left = this;
            } else if (this.parentSub != null) {
                if (this.parent instanceof AbstractType<?> pType) {
                    pType.getMap().put(this.parentSub, this);
                }
                if (this.left != null) {
                    this.left.delete(transaction);
                }
            }
            // adjust length of parent
            if (this.parentSub == null && this.countable() && !this.isDeleted()) {
                if (this.parent instanceof AbstractType<?> pType) {
                    pType.setLength(pType.getLength() + this.length);
                }

            }
            transaction.doc.store.addStruct(this);
            this.content.integrate(transaction, this);
            // add parent to transaction.changed
            if (this.parent instanceof AbstractType<?> pType) {
                transaction.addChangedTypeToTransaction(pType, this.parentSub);
            } else {
                transaction.addChangedTypeToTransaction(null, this.parentSub);
            }
            Item pItem = null;
            if (this.parent instanceof AbstractType<?> pType) {
                pItem = pType.getItem();
            }
            if ((pItem != null && pItem.isDeleted()) || (this.parentSub != null && this.right != null)) {
                // delete if parent is deleted or if this is not the current attribute value of parent
                this.delete(transaction);
            }
        } else {
            // parent is not defined. Integrate GC struct instead
            new GC(this.id, this.length).integrate(transaction, 0);
        }
    }


    public Item getNext() {
        AbstractStruct n = this.right;
        while (n != null && n.isDeleted()) {
            if (n instanceof Item nItem) {
                n = nItem.right;
            } else {
                n = null;
            }
        }
        return (Item) n;
    }

    public Item getPrev() {
        AbstractStruct n = this.left;
        while (n != null && n.isDeleted()) {
            if (n instanceof Item nItem) {
                n = nItem.left;
            } else {
                n = null;
            }
        }
        return (Item) n;
    }

    public ID getLastId() {
        // allocating ids is pretty costly because of the amount of ids created, so we try to reuse whenever possible
        return length == 1 ? id : ID.createId(id.client, id.clock + length - 1);
    }

    /**
     * Try to merge this item with the right item
     */
    @Override
    public boolean mergeWith(AbstractStruct right) {
        if (!(right instanceof Item rightItem)) {
            return false;
        }
        if (ID.compareIds(rightItem.origin, this.getLastId()) &&
                this.right == rightItem &&
                ID.compareIds(this.rightOrigin, rightItem.rightOrigin) &&
                this.id.client == rightItem.id.client &&
                this.id.clock + this.length == rightItem.id.clock &&
                this.isDeleted() == rightItem.isDeleted() &&
                this.redone == null && rightItem.redone == null &&
                this.content != null && rightItem.content != null &&
                this.content.getClass() == rightItem.content.getClass() &&
                this.content.mergeWith(rightItem.content)) {
            List<ArraySearchMarker> searchMarker = null;
            if (this.parent instanceof AbstractType<?> parentType) {
                searchMarker = parentType.getSearchMarker();
            }
            if (searchMarker != null && !searchMarker.isEmpty()) {
                searchMarker.forEach(marker -> {
                    if (marker.p == rightItem) {
                        // right is going to be "forgotten" so we need to update the marker
                        marker.p = this;
                        // adjust marker index
                        if (!this.isDeleted() && this.countable()) {
                            marker.index -= this.length;
                        }
                    }
                });
            }
            if (rightItem.keep()) {
                this.setKeep(true);
            }

            this.right = rightItem.right;
            if (this.right != null) {
                this.right.left = this;
            }
            this.length += rightItem.length;
            return true;
        }
        return false;
    }

    @Override
    public void delete(Transaction transaction) {
        if (!isDeleted()) {
            if (parent instanceof AbstractType<?> pType) {
                if (countable() && parentSub == null) {
                    pType.setLength(pType.getLength() - this.length);
                }
            }
            this.markDeleted();
            transaction.deleteSet.addToDeleteSet(this.id.client, this.id.clock, this.length);
            transaction.addChangedTypeToTransaction((parent instanceof AbstractType<?> pType ? pType : null), this.parentSub);
            this.content.delete(transaction);
        }
    }

    /**
     * Garbage collect this item (matching JS gc)
     */
    public void gc(StructStore store, boolean parentGcD) {
        if (!isDeleted()) {
            throw new RuntimeException("Cannot GC non-deleted item");
        }

        this.content.gc(store);
        if (parentGcD) {
            store.replaceStruct(this, new GC(this.id, this.length));
        } else {
            this.content = new ContentDeleted(this.length);
        }
    }


    /**
     * Transform the properties of this type to binary and write it to an
     * BinaryEncoder.
     * This is called when this Item is sent to a remote peer.
     */
    @Override
    public void write(DSEncoder encoder, long offset) {
        ID origin = offset > 0 ? ID.createId(this.id.client, this.id.clock + offset - 1) : this.origin;
        ID rightOrigin = this.rightOrigin;
        String parentSub = this.parentSub;

        int info = (this.content.getRef() & Binary.BITS5) |
                (origin == null ? 0 : Binary.BIT8) |
                (rightOrigin == null ? 0 : Binary.BIT7) |
                (parentSub == null ? 0 : Binary.BIT6);

        encoder.writeInfo(info);
        if (origin != null) {
            encoder.writeLeftID(origin);
        }
        if (rightOrigin != null) {
            encoder.writeRightID(rightOrigin);
        }
        if (origin == null && rightOrigin == null) {
            Object parent = this.parent;
            if (this.parent instanceof AbstractType<?> parentType) {
                Item parentItem = parentType.getItem();
                if (parentItem == null) {
                    String ykey = ID.findRootTypeKey(parentType);
                    // write parentYKey
                    encoder.writeParentInfo(true);
                    encoder.writeString(ykey);
                } else {
                    encoder.writeParentInfo(false);
                    encoder.writeLeftID(parentItem.id);
                }
            } else if (parent instanceof String strParent) {
                encoder.writeParentInfo(true);
                encoder.writeString(strParent);
            } else if (parent instanceof ID idParent) {
                encoder.writeParentInfo(false);
                encoder.writeLeftID(idParent);
            } else {
                throw new RuntimeException("Unexpected case: " + parent);
            }
            if (parentSub != null) {
                encoder.writeString(parentSub);
            }
        }
        this.content.write(encoder, offset);
    }

    public static AbstractContent readItemContent(DSDecoder decoder, int info) {
        int ref = info & Binary.BITS5;
        return switch (ref) {
            case 0 -> throw new RuntimeException("Unexpected case: info");
            case 1 -> ContentDeleted.readContentDeleted(decoder);
            case 2 -> ContentJSON.readContentJSON(decoder);
            case 3 -> ContentBinary.readContentBinary(decoder);
            case 4 -> ContentString.readContentString(decoder);
            case 5 -> ContentEmbed.readContentEmbed(decoder);
            case 6 -> ContentFormat.readContentFormat(decoder);
            case 7 -> ContentType.readContentType(decoder);
            case 8 -> ContentAny.readContentAny(decoder);
            case 9 -> ContentDoc.readContentDoc(decoder);
            default -> throw new RuntimeException("Unexpected case: ref");
        };
    }

    public static Map<String, Object> followRedone(StructStore store, ID id) {
        ID nextId = id;
        long diff = 0;
        AbstractStruct item;
        do {
            if (diff > 0) {
                nextId = ID.createId(nextId.client, nextId.clock + diff);
            }
            item = store.getItem(nextId);
            if (!(item instanceof Item)) {
                break;
            }
            diff = nextId.clock - item.id.clock;
            nextId = ((Item) item).redone;
        } while (nextId != null);

        return Map.of("item", item, "diff", diff);
    }

    /**
     * Make sure that neither item nor any of its parents is ever deleted.
     * <p>
     * This property does not persist when storing it into a database or when
     * sending it to other peers
     */
    public static void keepItem(Item item, boolean keep) {
        while (item != null && item.keep() != keep) {
            item.setKeep(keep);
            item = ((AbstractType<?>) item.parent).getItem();
        }
    }

    public static Item splitItem(Transaction transaction, AbstractStruct leftItem, long diff) {
        final long client = leftItem.id.client;
        final long clock = leftItem.id.clock;
        Item rightItem = new Item(
                ID.createId(client, clock + diff),
                leftItem,
                ID.createId(client, clock + diff - 1),
                leftItem.right,
                leftItem instanceof Item ? ((Item) leftItem).rightOrigin : null,
                leftItem instanceof Item ? ((Item) leftItem).parent : null,
                leftItem instanceof Item ? ((Item) leftItem).parentSub : null,
                leftItem instanceof Item ? ((Item) leftItem).content.splice((int) diff) : null
        );
        if (leftItem.isDeleted()) {
            rightItem.markDeleted();
        }
        if (leftItem instanceof Item && ((Item) leftItem).keep()) {
            rightItem.setKeep(true);
        }
        if (leftItem instanceof Item lItem && lItem.redone != null) {
            rightItem.redone = ID.createId(lItem.redone.client, lItem.redone.clock + diff);
        }
        // update left (do not set leftItem.rightOrigin as it will lead to problems when syncing)
        if (leftItem instanceof Item lItem) {
            lItem.right = rightItem;
        }
        // update right
        if (rightItem.right != null) {
            rightItem.right.left = rightItem;
        }
        // right is more specific.
        transaction._mergeStructs.addLast(rightItem);
        // update parent._map
        if (rightItem.parentSub != null && rightItem.right == null) {
            ((AbstractType<?>) (rightItem.parent)).getMap().put(rightItem.parentSub, rightItem);
        }
        leftItem.length = diff;
        return rightItem;
    }

    public static boolean isDeletedByUndoStack(List<StackItem> stack, ID id) {
        for (StackItem s : stack) {
            if (s.getDeletions().isDeleted(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Redoes the effect of this operation.
     *
     * @return {@link Item }
     */
    public static AbstractStruct redoItem(Transaction transaction, AbstractStruct abstractStruct, Set<Item> redoItems, DeleteSet itemsToDelete,
                                          boolean ignoreRemoteMapChanges, UndoManager um) {
        Doc doc = transaction.doc;
        StructStore store = doc.store;
        long ownClientId = doc.clientId;

        if (abstractStruct instanceof Item item && item.redone != null) {
            return StructStore.getItemCleanStart(transaction, item.redone);
        }

        Item parentItem = null;
        if (abstractStruct instanceof Item item) {
            Object parent = item.parent;
            if (parent instanceof AbstractType<?> pType) {
                parentItem = pType.getItem();
            }
        }
        Item left;
        Item right;

        // 处理父节点已删除情况
        if (parentItem != null && parentItem.isDeleted()) {
            if (parentItem.redone == null &&
                    (!redoItems.contains(parentItem) || redoItem(transaction, parentItem, redoItems, itemsToDelete, ignoreRemoteMapChanges, um) == null)) {
                return null;
            }
            while (parentItem.redone != null) {
                AbstractStruct struct = StructStore.getItemCleanStart(transaction, parentItem.redone);
                if (struct instanceof Item) {
                    parentItem = (Item) struct;
                } else {
                    break;
                }
            }
        }

        AbstractType<?> parentType = null;
        if (parentItem == null && abstractStruct instanceof Item item && item.parent instanceof AbstractType) {
            parentType = (AbstractType<?>) item.parent;
        }
        if (parentType == null && parentItem != null) {
            parentType = ((ContentType) parentItem.content).getType();
        }
//        assert parentType != null : "parentType should not be null";

        if (abstractStruct instanceof Item item && item.parentSub == null) {
            // Array 模式
            left = (Item) item.left;
            right = item;

            while (left != null) {
                Item leftTrace = left;
                while (getParentItem(leftTrace) != parentItem) {
                    if (leftTrace.redone == null) {
                        leftTrace = null;
                        break;
                    }
                    AbstractStruct struct = StructStore.getItemCleanStart(transaction, leftTrace.redone);
                    if (struct instanceof Item) {
                        leftTrace = (Item) struct;
                    } else {
                        leftTrace = null;
                        break;
                    }
                }
                if (leftTrace != null && getParentItem(leftTrace) == parentItem) {
                    left = leftTrace;
                    break;
                }
                left = (Item) left.left;
            }

            while (right != null) {
                Item rightTrace = right;
                while (getParentItem(rightTrace) != parentItem) {
                    if (rightTrace.redone == null) {
                        rightTrace = null;
                        break;
                    }
                    AbstractStruct struct = StructStore.getItemCleanStart(transaction, rightTrace.redone);
                    if (struct instanceof Item) {
                        rightTrace = (Item) struct;
                    } else {
                        rightTrace = null;
                        break;
                    }
                }
                if (rightTrace != null && getParentItem(rightTrace) == parentItem) {
                    right = rightTrace;
                    break;
                }
                right = (Item) right.right;
            }

        } else {
            // Map 模式
            right = null;
            if ((abstractStruct instanceof Item item) && item.right != null && !ignoreRemoteMapChanges) {
                left = item;
                while (left != null && left.right != null &&
                        (((Item) left.right).redone != null
                                || itemsToDelete.isDeleted(left.right.id)
                                || isDeletedByUndoStack(um.getUndoStack().stream().toList(), left.right.id)
                                || isDeletedByUndoStack(um.getRedoStack().stream().toList(), left.right.id))) {

                    left = left.right == null ? null : (Item) left.right;
                    while (left != null && left.redone != null) {
                        AbstractStruct struct = StructStore.getItemCleanStart(transaction, left.redone);
                        if (struct instanceof Item) {
                            left = (Item) struct;
                        } else {
                            left = null;
                            break;
                        }
                    }
                }

                if (left != null && left.right != null) {
                    // It is not possible to redo this item because it conflicts with a
                    // change from another client
                    return null;
                }
            } else {
                if (!(abstractStruct instanceof Item) || parentType == null) {
                    left = null;
                } else {
                    left = parentType.getMap().get(((Item) abstractStruct).parentSub);
                }
            }
        }

        long nextClock = store.getState(ownClientId);
        ID nextId = ID.createId(ownClientId, nextClock);

        Item redoneItem = new Item(
                nextId,
                left,
                left != null ? left.id : null,
                right,
                right != null ? right.id : null,
                parentType,
                abstractStruct instanceof Item item ? item.parentSub : null,
                abstractStruct instanceof Item item ? item.content.copy() : null
        );

        if (abstractStruct instanceof Item item) {
            item.redone = nextId;
        }
        keepItem(redoneItem, true);
        redoneItem.integrate(transaction, 0);
        return redoneItem;
    }

    private static Item getParentItem(Item item) {
        return item.parent instanceof AbstractType ? ((AbstractType<?>) item.parent).getItem() : null;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Item item = (Item) o;
        return info == item.info && Objects.equals(origin, item.origin) && Objects.equals(left, item.left) && Objects.equals(right, item.right) && Objects.equals(rightOrigin, item.rightOrigin) && Objects.equals(parent, item.parent) && Objects.equals(parentSub, item.parentSub) && Objects.equals(redone, item.redone) && Objects.equals(content, item.content);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public ID getOrigin() {
        return origin;
    }

    public void setOrigin(ID origin) {
        this.origin = origin;
    }

    public AbstractStruct getLeft() {
        return left;
    }

    public void setLeft(AbstractStruct left) {
        this.left = left;
    }

    public AbstractStruct getRight() {
        return right;
    }

    public void setRight(AbstractStruct right) {
        this.right = right;
    }

    public ID getRightOrigin() {
        return rightOrigin;
    }

    public void setRightOrigin(ID rightOrigin) {
        this.rightOrigin = rightOrigin;
    }

    public Object getParent() {
        return parent;
    }

    public void setParent(Object parent) {
        this.parent = parent;
    }

    public String getParentSub() {
        return parentSub;
    }

    public void setParentSub(String parentSub) {
        this.parentSub = parentSub;
    }

    public ID getRedone() {
        return redone;
    }

    public void setRedone(ID redone) {
        this.redone = redone;
    }

    public AbstractContent getContent() {
        return content;
    }

    public void setContent(AbstractContent content) {
        this.content = content;
    }

    public int getInfo() {
        return info;
    }

    public void setInfo(int info) {
        this.info = info;
    }
}
