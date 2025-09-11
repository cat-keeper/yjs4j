package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.utils.event.YEvent;

import java.util.*;
import java.util.function.Function;

/**
 * 撤消管理器
 *
 * @author zbs
 * @date 2025/07/31  17:21:51
 */
@SuppressWarnings("unused")
public class UndoManager extends ObservableV2<String> {
    public List<Object> scope;
    public Doc doc;
    public Function<Item, Boolean> deleteFilter;
    public Set<Object> trackedOrigins;
    public Function<Transaction, Boolean> captureTransaction;
    public Deque<StackItem> undoStack;
    public Deque<StackItem> redoStack;
    public Boolean undoing;
    public Boolean redoing;
    public StackItem currStackItem;
    public Long lastChange;
    public Boolean ignoreRemoteMapChanges;
    public Long captureTimeout;

    public UndoManager(Object typeScope, UndoManagerOptions options) {
        try {
            if (typeScope instanceof List<?>) {
                this.doc = (Doc) ((List<?>) typeScope).get(0);
            } else {
                if (typeScope instanceof Doc) {
                    this.doc = (Doc) typeScope;
                } else {
                    this.doc = (Doc) typeScope.getClass().getField("doc").get(typeScope);
                }
            }
            this.scope = new ArrayList<>();
            this.addToScope(typeScope);
            this.deleteFilter = options.getDeleteFilter();
            options.getTrackedOrigins().add(this);
            this.trackedOrigins = options.getTrackedOrigins();
            this.captureTransaction = options.getCaptureTransaction();
            this.undoStack = new ArrayDeque<>();
            this.redoStack = new ArrayDeque<>();
            this.undoing = false;
            this.redoing = false;
            this.currStackItem = null;
            this.lastChange = 0L;
            this.ignoreRemoteMapChanges = options.isIgnoreRemoteMapChanges();
            this.captureTimeout = options.getCaptureTimeout();
            this.doc.on("afterTransaction", this::afterTransactionHandler);
            this.doc.on("destroy", this::destroy);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void afterTransactionHandler(Transaction transaction) {
        // 只跟踪特定事务
        if (!this.captureTransaction.apply(transaction)
                || scope.stream().noneMatch(type -> type == doc || (type instanceof AbstractType && transaction.getChangedParentTypes().containsKey((AbstractType<?>) type)))
                || (!trackedOrigins.contains(transaction.getOrigin()) &&
                (transaction.getOrigin() == null || !trackedOrigins.contains(transaction.getOrigin().getClass())))) {
            return;
        }

        boolean undoing = this.undoing;
        boolean redoing = this.redoing;
        Deque<StackItem> stack = undoing ? redoStack : undoStack;

        if (undoing) {
            stopCapturing(); // 下一次 undo 不应追加到上一个 stack item
        } else if (!redoing) {
            // 非 undo/redo，清除 redoStack
            clear(false, true);
        }

        DeleteSet insertions = new DeleteSet();
        for (Map.Entry<Long, Long> entry : transaction.getAfterState().entrySet()) {
            long client = entry.getKey();
            long endClock = entry.getValue();
            long startClock = transaction.getBeforeState().getOrDefault(client, 0L);
            long len = endClock - startClock;
            if (len > 0) {
                DeleteSet.addToDeleteSet(insertions, client, startClock, len);
            }
        }

        long now = System.currentTimeMillis();
        boolean didAdd = false;

        if (lastChange > 0 && now - lastChange < captureTimeout && !stack.isEmpty() && !undoing && !redoing) {
            // 追加更改到最后一个操作
            StackItem lastOp = stack.peekLast();
            lastOp.setDeletions(DeleteSet.mergeDeleteSets(Arrays.asList(lastOp.getDeletions(), transaction.getDeleteSet())));
            lastOp.setInsertions(DeleteSet.mergeDeleteSets(Arrays.asList(lastOp.getInsertions(), insertions)));
        } else {
            // 创建新的操作项
            stack.addLast(new StackItem(transaction.getDeleteSet(), insertions));
            didAdd = true;
        }

        if (!undoing && !redoing) {
            lastChange = now;
        }

        // 防止已删除的结构被 GC
        DeleteSet.iterateDeletedStructs(transaction, transaction.getDeleteSet(), item -> {
            if (item instanceof Item) {
                for (Object type : scope) {
                    if (transaction.getDoc() == type || ParentUtils.isParentOf((AbstractType<?>) type, (Item) item)) {
                        Item.keepItem((Item) item, true);
                        break;
                    }
                }
            }
        });

        StackItem lastItem = stack.peekLast();
        StackItemEvent changeEvent = new StackItemEvent(lastItem, transaction.getOrigin(),
                undoing ? "redo" : "undo", transaction.getChangedParentTypes());

        if (didAdd) {
            emit("stack-item-added", changeEvent, this);
        } else {
            emit("stack-item-updated", changeEvent, this);
        }
        this.doc.on("afterTransaction", this::afterTransactionHandler);
        this.doc.on("destroy", this::destroy);
    }

    public record StackItemEvent(StackItem item, Object origin, String action,
                                 Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes) {
    }


    public void addToScope(Object ytypes) {
        Set<Object> tmpSet = new LinkedHashSet<>(scope);
        List<Object> ytypesList = new ArrayList<>();
        if (ytypes instanceof List<?>) {
            ytypesList.addAll((List<?>) ytypes);
        } else {
            ytypesList.add(ytypes);
        }
        for (Object ytype : ytypesList) {
            if (!tmpSet.contains(ytype)) {
                tmpSet.add(ytype);
                if (ytype instanceof AbstractType<?>) {
                    if (((AbstractType<?>) ytype).getDoc() != doc) {
                        throw new RuntimeException("[yjs#509] Not same Y.Doc");
                    }
                } else if (ytype != doc) {
                    throw new RuntimeException("[yjs#509] Not same Y.Doc");
                }
                this.scope.add(ytype);
            }
        }
    }

    public void removeTrackedOrigin(Object origin) {
        this.trackedOrigins.remove(origin);
    }

    public void clear(boolean clearUndoStack, boolean clearRedoStack) {
        if ((clearUndoStack && this.canUndo()) || (clearRedoStack && this.canRedo())) {
            this.doc.transact(tr -> {
                if (clearUndoStack) {
                    this.undoStack.forEach(item -> StackItem.clearUndoManagerStackItem(tr, this, item));
                    this.undoStack = new ArrayDeque<>();
                }
                if (clearRedoStack) {
                    this.redoStack.forEach(item -> StackItem.clearUndoManagerStackItem(tr, this, item));
                    this.redoStack = new ArrayDeque<>();
                }
                Param param = new Param(clearUndoStack, clearRedoStack);
                this.emit("stack-cleared", List.of(param));
                return true;
            });
        }
    }

    public record Param(boolean clearUndoStack, boolean clearRedoStack) {
    }

    public void stopCapturing() {
        this.lastChange = 0L;
    }

    /**
     * Undo last changes on type.
     *
     * @return {StackItem?} Returns StackItem if a change was applied
     */
    public StackItem undo() {
        this.undoing = true;
        StackItem res;
        try {
            res = StackItem.popStackItem(this, new ArrayList<>(this.undoStack.stream().toList()), "undo");
        } finally {
            this.undoing = false;
        }
        return res;
    }

    public StackItem redo() {
        this.redoing = true;
        StackItem res;
        try {
            res = StackItem.popStackItem(this, new ArrayList<>(this.redoStack.stream().toList()), "redo");
        } finally {
            this.redoing = false;
        }
        return res;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    @Override
    public void destroy() {
        this.trackedOrigins.remove(this);
        this.doc.off("afterTransaction", this::afterTransactionHandler);
        super.destroy();
    }

    public List<Object> getScope() {
        return scope;
    }

    public void setScope(List<Object> scope) {
        this.scope = scope;
    }

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    public Function<Item, Boolean> getDeleteFilter() {
        return deleteFilter;
    }

    public void setDeleteFilter(Function<Item, Boolean> deleteFilter) {
        this.deleteFilter = deleteFilter;
    }

    public Set<Object> getTrackedOrigins() {
        return trackedOrigins;
    }

    public void setTrackedOrigins(Set<Object> trackedOrigins) {
        this.trackedOrigins = trackedOrigins;
    }

    public Function<Transaction, Boolean> getCaptureTransaction() {
        return captureTransaction;
    }

    public void setCaptureTransaction(Function<Transaction, Boolean> captureTransaction) {
        this.captureTransaction = captureTransaction;
    }

    public Deque<StackItem> getUndoStack() {
        return undoStack;
    }

    public void setUndoStack(Deque<StackItem> undoStack) {
        this.undoStack = undoStack;
    }

    public Deque<StackItem> getRedoStack() {
        return redoStack;
    }

    public void setRedoStack(Deque<StackItem> redoStack) {
        this.redoStack = redoStack;
    }

    public Boolean getUndoing() {
        return undoing;
    }

    public void setUndoing(Boolean undoing) {
        this.undoing = undoing;
    }

    public Boolean getRedoing() {
        return redoing;
    }

    public void setRedoing(Boolean redoing) {
        this.redoing = redoing;
    }

    public StackItem getCurrStackItem() {
        return currStackItem;
    }

    public void setCurrStackItem(StackItem currStackItem) {
        this.currStackItem = currStackItem;
    }

    public Long getLastChange() {
        return lastChange;
    }

    public void setLastChange(Long lastChange) {
        this.lastChange = lastChange;
    }

    public Boolean getIgnoreRemoteMapChanges() {
        return ignoreRemoteMapChanges;
    }

    public void setIgnoreRemoteMapChanges(Boolean ignoreRemoteMapChanges) {
        this.ignoreRemoteMapChanges = ignoreRemoteMapChanges;
    }

    public Long getCaptureTimeout() {
        return captureTimeout;
    }

    public void setCaptureTimeout(Long captureTimeout) {
        this.captureTimeout = captureTimeout;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UndoManager that)) return false;
        return Objects.equals(scope, that.scope) && Objects.equals(doc, that.doc) && Objects.equals(deleteFilter, that.deleteFilter) && Objects.equals(trackedOrigins, that.trackedOrigins) && Objects.equals(captureTransaction, that.captureTransaction) && Objects.equals(undoStack, that.undoStack) && Objects.equals(redoStack, that.redoStack) && Objects.equals(undoing, that.undoing) && Objects.equals(redoing, that.redoing) && Objects.equals(currStackItem, that.currStackItem) && Objects.equals(lastChange, that.lastChange) && Objects.equals(ignoreRemoteMapChanges, that.ignoreRemoteMapChanges) && Objects.equals(captureTimeout, that.captureTimeout);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
