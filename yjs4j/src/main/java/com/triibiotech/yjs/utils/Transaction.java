package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.types.ItemTextListPosition;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.YEvent;

import java.util.*;
import java.util.function.Function;

/**
 * A transaction is created for every change on the Yjs model. It is possible
 * to bundle changes on the Yjs model in a single transaction to
 * minimize the number on messages sent and the number of observer calls.
 * If possible the user of this library should bundle as many changes as
 * possible.
 *
 * @author zbs
 * @date 2025/07/29  09:12:16
 */
@SuppressWarnings("unused")
public class Transaction {

    /**
     * The Yjs instance.
     */
    public Doc doc;

    /**
     * Describes the set of deleted items by ids
     */
    public DeleteSet deleteSet = new DeleteSet();

    /**
     * Holds the state before the transaction started.
     */
    public Map<Long, Long> beforeState;

    /**
     * Holds the state after the transaction.
     */
    public Map<Long, Long> afterState = new HashMap<>();

    /**
     * All types that were directly modified (property added or child
     * inserted/deleted). New types are not included in this Set.
     * Maps from type to parentSubs (`item.parentSub = null` for YArray)
     */
    public SafeLinkedHashMap<AbstractType<?>, Set<String>> changed = new SafeLinkedHashMap<>();

    /**
     * Stores the events for the types that observe also child elements.
     * It is mainly used by `observeDeep`.
     */
    public Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes = new HashMap<>();

    /**
     * Array of structs that need to be merged
     */
    public LinkedList<AbstractStruct> _mergeStructs = new LinkedList<>();

    /**
     * Origin of this transaction
     */
    public Object origin;

    /**
     * Stores meta information on the transaction
     */
    public Map<Object, Object> meta = new HashMap<>();

    /**
     * Whether this change originates from this doc.
     */
    public boolean local;

    /**
     * Set of subdocs that were added
     */
    public LinkedHashSet<Doc> subDocsAdded = new LinkedHashSet<>();

    /**
     * Set of subdocs that were removed
     */
    public LinkedHashSet<Doc> subDocsRemoved = new LinkedHashSet<>();

    /**
     * Set of subdocs that were loaded
     */
    public LinkedHashSet<Doc> subDocsLoaded = new LinkedHashSet<>();

    /**
     * Whether this transaction needs formatting cleanup
     */
    public boolean needFormattingCleanup = false;

    /**
     * @param doc    The document
     * @param origin Origin of this transaction
     * @param local  Whether this change originates from this doc
     */
    public Transaction(Doc doc, Object origin, boolean local) {
        this.doc = doc;
        this.origin = origin;
        this.local = local;
        this.beforeState = StructStore.getStateVector(doc.getStore());
    }


    /**
     * 将一次事务写入更新消息编码器。
     *
     * @param encoder     编码器（UpdateEncoderV1 或 V2）
     * @param transaction 当前事务
     * @return 是否写入了内容（即是否有变更）
     */
    public static boolean writeUpdateMessageFromTransaction(DSEncoder encoder, Transaction transaction) {
        DeleteSet deleteSet = transaction.getDeleteSet();

        // 没有删除，也没有 state 变化，直接返回 false
        if (deleteSet.getClients().isEmpty() &&
                !hasStateChanged(transaction.getBeforeState(), transaction.getAfterState())) {
            return false;
        }

        // 确保删除集合合并排序
        DeleteSet.sortAndMergeDeleteSet(deleteSet);

        // 写入结构体和删除集
        EncodingUtil.writeStructsFromTransaction(encoder, transaction);
        DeleteSet.writeDeleteSet(encoder, deleteSet);

        return true;
    }

    private static boolean hasStateChanged(Map<Long, Long> before, Map<Long, Long> after) {
        for (Map.Entry<Long, Long> entry : after.entrySet()) {
            long client = entry.getKey();
            long afterClock = entry.getValue();
            long beforeClock = before.getOrDefault(client, 0L);
            if (beforeClock != afterClock) {
                return true;
            }
        }
        return false;
    }

    public static ID nextId(Transaction transaction) {
        Doc doc = transaction.doc;
        return ID.createId(doc.clientId, doc.store.getState(doc.clientId));
    }

    /**
     * Get the next ID for this transaction
     */
    public ID nextId() {
        return nextId(this);
    }

    /**
     * If `type.parent` was added in current transaction, `type` technically
     * did not change, it was just added and we should not fire events for `type`.
     */
    public void addChangedTypeToTransaction(AbstractType<?> type, String parentSub) {
        Item item = type == null ? null : type.getItem();
        if (item == null ||
                (item.id.clock < (beforeState.get(item.id.client) == null ? 0 : beforeState.get(item.id.client)) && !item.isDeleted())) {
            changed.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(parentSub);
        }
    }

    /**
     * 向左合并结构体数组中的指定位置项
     *
     * @param structs 结构体数组（包含 Item / GC 等）
     * @param pos     当前位置
     * @return 实际合并掉的项数
     */
    public static int tryToMergeWithLefts(List<AbstractStruct> structs, int pos) {
        int i = pos;
        AbstractStruct right;
        AbstractStruct left;

        while (i > 0) {
            right = structs.get(i);
            left = structs.get(i - 1);
            // 检查 deleted 状态和类型是否相同
            if (left.isDeleted() == right.isDeleted() && left.getClass() == right.getClass()) {
                if (left.mergeWith(right)) {
                    // 右边是 Item 类型，并且有 parentSub，且是当前 parent._map 的值
                    if (right instanceof Item itemRight) {
                        if (itemRight.getParentSub() != null) {
                            AbstractType<?> parent = (AbstractType<?>) itemRight.getParent();
                            if (parent != null && parent.getMap().get(itemRight.getParentSub()) == itemRight) {
                                parent.getMap().put(itemRight.getParentSub(), (Item) left);
                            }
                        }
                    }

                    i--; // 合并成功，继续往左看
                    continue;
                }
            }
            break;
        }
        int merged = pos - i;
        if (merged > 0) {
            // 删除 merged 个元素
            for (int j = 0; j < merged; j++) {
                // 从右往左删
                structs.remove(i + 1);
            }
        }
        return merged;
    }

    public static void tryGcDeleteSet(DeleteSet ds, StructStore store, Function<Item, Boolean> gcFilter) {
        for (Map.Entry<Long, List<DeleteItem>> entry : ds.getClients().entrySet()) {
            long client = entry.getKey();
            List<DeleteItem> deleteItems = entry.getValue();
            List<AbstractStruct> structs = store.getClients().get(client);

            for (int di = deleteItems.size() - 1; di >= 0; di--) {
                DeleteItem del = deleteItems.get(di);
                long endClock = del.getClock() + del.getLen();

                for (int si = StructStore.findIndexSS(structs, del.getClock());
                     si < structs.size();
                     si++) {

                    AbstractStruct struct = structs.get(si);
                    if (struct.getId().getClock() >= endClock) {
                        break;
                    }

                    if (struct instanceof Item item && item.isDeleted() && !item.keep() && gcFilter.apply(item)) {
                        item.gc(store, false);
                    }
                }
            }
        }
    }

    public static void tryMergeDeleteSet(DeleteSet ds, StructStore store) {
        for (Map.Entry<Long, List<DeleteItem>> entry : ds.getClients().entrySet()) {
            long client = entry.getKey();
            List<DeleteItem> deleteItems = entry.getValue();
            List<AbstractStruct> structs = store.getClients().get(client);

            for (int di = deleteItems.size() - 1; di >= 0; di--) {
                DeleteItem del = deleteItems.get(di);
                long endClock = del.getClock() + del.getLen() - 1;

                int mostRightIndexToCheck = Math.min(structs.size() - 1,
                        1 + StructStore.findIndexSS(structs, endClock));

                for (int si = mostRightIndexToCheck;
                     si > 0 && structs.get(si).getId().getClock() >= del.getClock(); ) {

                    si -= 1 + tryToMergeWithLefts(structs, si);
                }
            }
        }
    }

    public static void tryGc(DeleteSet ds, StructStore store, Function<Item, Boolean> gcFilter) {
        tryGcDeleteSet(ds, store, gcFilter);
        tryMergeDeleteSet(ds, store);
    }

    private static void cleanupTransactions(List<Transaction> transactionCleanups, int i) {
        if (i < transactionCleanups.size()) {
            Transaction transaction = transactionCleanups.get(i);
            Doc doc = transaction.doc;
            StructStore store = doc.store;
            DeleteSet ds = transaction.deleteSet;
            LinkedList<AbstractStruct> mergeStructs = transaction._mergeStructs;
            try {
                // 1. 合并删除集
                DeleteSet.sortAndMergeDeleteSet(ds);
                transaction.afterState = StructStore.getStateVector(transaction.doc.store);
                // 2. 触发前置事件
                doc.emit("beforeObserverCalls", transaction, doc);
                // 3. 收集回调
                List<Runnable> fs = new ArrayList<>();
                // 3.1 changed 类型观察者回调
                transaction.changed.forEach((itemType, subs) ->
                        fs.add(() -> {
                            if (itemType.getItem() == null || !itemType.getItem().isDeleted()) {
                                itemType.callObserver(transaction, subs);
                            }
                        }));
                // 3.2 changedParentTypes 深度回调
                fs.add(() -> {
                    // deep observe events
                    transaction.changedParentTypes.forEach((type, events) -> {
                        // We need to think about the possibility that the user transforms the
                        // Y.Doc in the event.
                        if (type.getDeepEventHandler().getFunctionCount() > 0 && (type.getItem() == null || !type.getItem().isDeleted())) {
                            List<YEvent<?>> filteredEvents = new ArrayList<>(events
                                    .stream()
                                    .filter(ev -> ev.target.getItem() == null || !ev.target.getItem().isDeleted())
                                    .toList());

                            filteredEvents.forEach(event -> {
                                ((YEvent<AbstractType<?>>) event).setCurrentTarget(type);
                                event.setPath(null);
                            });
                            // sort events by path length so that top-level events are fired first.
                            filteredEvents.sort(Comparator.comparingInt(ev -> ev.getPath().size()));
                            // We don't need to check for events.length
                            // because we know it has at least one element
                            EventHandler.callEventHandlerListeners(type.getDeepEventHandler(), filteredEvents, transaction);
                        }
                    });
                });
                // 3.3 afterTransaction 回调
                fs.add(() -> doc.emit("afterTransaction", transaction, doc));
                // 4. 执行所有回调
                fs.forEach(Runnable::run);
                // 5. 清理富文本格式
                if (transaction.needFormattingCleanup) {
                    ItemTextListPosition.cleanupYTextAfterTransaction(transaction);
                }
            } finally {
                // 6. GC 清理
                if (doc.gc) {
                    Transaction.tryGcDeleteSet(ds, store, doc.gcFilter);
                }
                Transaction.tryMergeDeleteSet(ds, store);

                // 7. 遍历 store.clients 合并
                transaction.afterState.forEach((client, clock) -> {
                    long beforeClock = transaction.beforeState.getOrDefault(client, 0L);
                    if (beforeClock != clock) {
                        List<AbstractStruct> structs = (store.getClients().get(client));
                        // we iterate from right to left so we can safely remove entries
                        int firstChangePos = Math.max(StructStore.findIndexSS(structs, beforeClock), 1);
                        for (int j = structs.size() - 1; j >= firstChangePos; ) {
                            j -= 1 + Transaction.tryToMergeWithLefts(structs, j);
                        }
                    }
                });
                // 8. 合并 mergeStructs
                // try to merge mergeStructs
                for (int j = mergeStructs.size() - 1; j >= 0; j--) {
                    AbstractStruct struct = mergeStructs.get(j);
                    long client = struct.getId().getClient();
                    long clock = struct.getId().getClock();
                    List<AbstractStruct> structs = store.getClients().get(client);

                    int replacedStructPos = StructStore.findIndexSS(structs, clock);
                    if (replacedStructPos + 1 < structs.size()) {
                        if (Transaction.tryToMergeWithLefts(structs, replacedStructPos + 1) > 1) {
                            continue;
                        }
                    }
                    if (replacedStructPos > 0) {
                        Transaction.tryToMergeWithLefts(structs, replacedStructPos);
                    }
                }
                // 9. 客户端 ID 冲突修复
                Long beforeClient = transaction.getBeforeState().get(doc.getClientId());
                Long afterClient = transaction.getAfterState().get(doc.getClientId());
                if (!transaction.local && !Objects.equals(beforeClient, afterClient)) {
                    EncodingUtil.log.warn("[yjs] ⚠️ Changed the client-id because another client seems to be using it.");
                    doc.clientId = Doc.generateNewClientId();
                }
                // 10. afterTransactionCleanup
                doc.emit("afterTransactionCleanup", transaction, doc);
                // 11. 发送 update/updateV2
                if (doc.getObservers().containsKey("update")) {
                    UpdateEncoderV1 encoder = new UpdateEncoderV1();
                    if (writeUpdateMessageFromTransaction(encoder, transaction)) {
                        doc.emit("update", encoder.toUint8Array(), transaction.origin, doc, transaction);
                    }
                }
                if (doc.getObservers().containsKey("updateV2")) {
                    UpdateEncoderV2 encoder = new UpdateEncoderV2();
                    if (writeUpdateMessageFromTransaction(encoder, transaction)) {
                        doc.emit("updateV2", encoder.toUint8Array(), transaction.origin, doc, transaction);
                    }
                }
                // 12. subdocs 处理
                if (!transaction.subDocsAdded.isEmpty() || !transaction.subDocsRemoved.isEmpty() || !transaction.subDocsLoaded.isEmpty()) {
                    for (Doc subdoc : transaction.getSubDocsAdded()) {
                        subdoc.clientId = doc.clientId;
                        if (subdoc.collectionid == null) {
                            subdoc.collectionid = doc.collectionid;
                        }
                        doc.getSubDocs().add(subdoc);
                    }
                    for (Doc subdoc : transaction.getSubDocsRemoved()) {
                        doc.getSubDocs().remove(subdoc);
                    }
                    doc.emit("subdocs",
                            Map.of(
                                    "loaded", transaction.getSubDocsLoaded(),
                                    "added", transaction.getSubDocsAdded(),
                                    "removed", transaction.getSubDocsRemoved()),
                            doc,
                            transaction);
                    transaction.getSubDocsRemoved().forEach(Doc::destroy);
                }
                // 13. 多事务 cleanup 调度
                if (transactionCleanups.size() <= i + 1) {
                    doc.transactionCleanups = new ArrayList<>();
                    doc.emit("afterAllTransactions", doc, transactionCleanups);
                } else {
                    cleanupTransactions(transactionCleanups, i + 1);
                }
            }
        }
    }

    public static <T> T transact(Doc doc, Function<Transaction, T> f, Object origin, boolean local) {
        List<Transaction> transactionCleanups = doc.transactionCleanups;
        boolean initialCall = false;
        T result;
        if (doc.transaction == null) {
            initialCall = true;
            doc.transaction = new Transaction(doc, origin, local);
            transactionCleanups.add(doc.transaction);
            if (transactionCleanups.size() == 1) {
                doc.emit("beforeAllTransactions", doc);
            }
            doc.emit("beforeTransaction", doc.transaction, doc);
        }

        try {
            result = f.apply(doc.transaction);
        } finally {
            if (initialCall) {
                boolean finishCleanup = doc.transaction == transactionCleanups.getFirst();
                doc.transaction = null;
                if (finishCleanup) {
                    cleanupTransactions(transactionCleanups, 0);
                }
            }
        }

        return result;
    }


    public Doc getDoc() {
        return doc;
    }

    public DeleteSet getDeleteSet() {
        return deleteSet;
    }

    public Map<Long, Long> getBeforeState() {
        return beforeState;
    }

    public Map<Long, Long> getAfterState() {
        return afterState;
    }

    public SafeLinkedHashMap<AbstractType<?>, Set<String>> getChanged() {
        return changed;
    }

    public Map<AbstractType<?>, List<YEvent<?>>> getChangedParentTypes() {
        return changedParentTypes;
    }

    public LinkedList<AbstractStruct> get_mergeStructs() {
        return _mergeStructs;
    }

    public Object getOrigin() {
        return origin;
    }

    public Map<Object, Object> getMeta() {
        return meta;
    }

    public boolean isLocal() {
        return local;
    }

    public Set<Doc> getSubDocsAdded() {
        return subDocsAdded;
    }

    public Set<Doc> getSubDocsRemoved() {
        return subDocsRemoved;
    }

    public Set<Doc> getSubDocsLoaded() {
        return subDocsLoaded;
    }

    public boolean isNeedFormattingCleanup() {
        return needFormattingCleanup;
    }

    public void setNeedFormattingCleanup(boolean needFormattingCleanup) {
        this.needFormattingCleanup = needFormattingCleanup;
    }
}
