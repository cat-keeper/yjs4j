package cn.timegap.yjs.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.timegap.yjs.types.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.timegap.yjs.structs.AbstractContent;
import cn.timegap.yjs.structs.ContentDoc;
import cn.timegap.yjs.structs.Item;



import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Doc（文档）是 Yjs 的顶层容器，代表一个可协同编辑的文档实例。
 *
 * <p>Doc 是所有共享数据类型（YText、YArray、YMap 等）的宿主。每个 Doc 拥有：
 * <ul>
 *   <li>一个全局唯一的 clientId，用于标识操作来源</li>
 *   <li>一个 StructStore，存储所有的 Item（操作记录）</li>
 *   <li>一个 share Map，存储所有命名的共享类型</li>
 * </ul>
 *
 * <p>Doc 的核心工作流程：
 * <ol>
 *   <li>通过 getText/getArray/getMap 获取共享类型</li>
 *   <li>对共享类型的修改会自动创建 Transaction</li>
 *   <li>Transaction 结束时触发 "update" 事件，产生二进制更新数据</li>
 *   <li>将更新数据发送给其他 Doc 实例，调用 applyUpdate 应用</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>{@code
 * Doc doc = new Doc();
 * YText text = doc.getText("content");
 * text.insert(0, "Hello");
 *
 * // 监听更新
 * doc.on("update", (update, origin) -> {
 *     // 将 update 发送给其他客户端
 * });
 * }</pre>
 *
 * <p>对应 JS 版本的 Y.Doc。
 *
 * @author zbs
 * @date 2025/07/29  12:04:59
 */
@SuppressWarnings("unused")
public class Doc extends ObservableV2<String> {
    /** 是否启用垃圾回收。开启后，被删除且不再被引用的 Item 会被压缩为 GC 节点以节省内存。默认 true。 */
    public Boolean gc;
    /** GC 过滤器。返回 false 的 Item 不会被垃圾回收，用于保留历史版本等场景。 */
    public Function<Item, Boolean> gcFilter;
    /** 客户端唯一标识。每个 Doc 实例随机生成，用于区分不同客户端的操作。 */
    public long clientId;
    /** 文档全局唯一标识符（UUID），用于在多文档场景中区分不同文档。 */
    public String guid;
    /** 文档集合标识，用于 provider 对文档进行分组管理。 */
    public String collectionid;
    /** 共享类型注册表。key 是类型名称，value 是对应的共享类型实例（YText、YArray、YMap 等）。 */
    public Map<String, AbstractType<?>> share = new ConcurrentHashMap<>();
    /** 结构存储。按 clientId 分组存储所有的 Item 和 GC 节点，是文档的底层数据存储。 */
    public StructStore store = new StructStore();
    /** 当前正在执行的事务。同一时刻只能有一个活跃事务，嵌套调用会复用同一个事务。 */
    public Transaction transaction = null;
    /** 事务清理队列。事务结束后需要执行的清理操作（触发事件、合并结构等）会排入此队列。 */
    public List<Transaction> transactionCleanups = new ArrayList<>();
    /** 子文档集合。Yjs 支持文档嵌套，一个 Doc 可以作为另一个 Doc 的子文档。 */
    public Set<Doc> subDocs = new LinkedHashSet<>();
    /** 如果此文档是子文档，item 指向父文档中持有此子文档的 Item 节点。 */
    public Item item = null;
    public boolean shouldLoad;
    public boolean autoLoad;
    public Object meta;
    public boolean isLoaded = false;
    public boolean isSynced = false;
    public boolean isDestroyed = false;

    public CompletableFuture<Doc> whenLoaded;
    public Consumer<Doc> loadListener;

    public CompletableFuture<Void> whenSynced;
    public Consumer<Boolean> syncEventHandler;

    public Doc(String guid, String collectionid, boolean gc, Function<Item, Boolean> gcFilter,
               Object meta, boolean autoLoad, boolean shouldLoad) {
        this.gc = gc;
        this.gcFilter = gcFilter;
        this.guid = guid;
        this.collectionid = collectionid;
        this.meta = meta;
        this.autoLoad = autoLoad;
        this.shouldLoad = shouldLoad;
        this.clientId = generateNewClientId();
    }

    public Doc() {
        this(new DocOptions());
    }

    public Doc(DocOptions opts) {
        this.gc = opts.gc;
        this.gcFilter = opts.gcFilter;
        this.clientId = generateNewClientId();
        this.guid = opts.guid;
        this.collectionid = opts.collectionid;
        this.transaction = null;
        this.item = null;
        this.shouldLoad = opts.shouldLoad;
        this.autoLoad = opts.autoLoad;
        this.meta = opts.meta;
        this.isLoaded = false;
        this.isSynced = false;
        this.isDestroyed = false;
        this.whenLoaded = new CompletableFuture<>();

        // 设置加载监听器
        this.loadListener = doc -> {
            isLoaded = true;
            whenLoaded.complete(doc);
        };

        // 设置同步事件监听器
        this.syncEventHandler = isSynced -> {
            boolean newSyncedState = (isSynced == null) || isSynced;

            // 当连接丢失时，重新创建whenSynced Promise
            if (!newSyncedState && this.isSynced) {
                this.whenSynced = provideSyncedPromise();
            }

            // 更新同步状态
            this.isSynced = newSyncedState;

            // 如果同步完成且文档未加载，则触发加载事件
            if (this.isSynced && !this.isLoaded) {
                this.loadListener.accept(this);
            }
        };
    }

    private CompletableFuture<Void> provideSyncedPromise() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        // 注册事件监听器
        this.on("sync", this::eventHandler);
        return future;
    }

    private void eventHandler(Boolean isSynced) {
        boolean shouldResolve = (isSynced == null) || isSynced;
        if (shouldResolve) {
            // 移除事件监听器
            this.off("sync", this::eventHandler);
            this.whenSynced.complete(null);
        }
    }

    public static long generateNewClientId() {
        return Integer.toUnsignedLong(new Random().nextInt());
    }

    public void load() {
        final Item item = this.item;
        if (item != null && !this.shouldLoad) {
            Transaction.transact(((AbstractType<?>) item.parent).getDocument(), transaction -> {
                transaction.subDocsLoaded.add(this);
                return true;
            }, null, true);
        }
        this.shouldLoad = true;
        this.loadListener.accept(this);
    }

    public Set<Doc> getSubDocs() {
        return this.subDocs;
    }

    public Set<String> getSubDocGuids() {
        Set<String> guids = new LinkedHashSet<>();
        for (Doc doc : this.subDocs) {
            guids.add(doc.guid);
        }
        return guids;
    }

    /**
     * Changes that happen inside of a transaction are bundled. This means that
     * the observer fires _after_ the transaction is finished and that all changes
     * that happened inside of the transaction are sent as one message to the
     * other peers.
     *
     * @param f      The function that should be executed as a transaction
     * @param origin of who started the transaction. Will be stored on transaction.origin
     * @return T
     */
    public <T> T transact(Function<Transaction, T> f, Object origin) {
        return Transaction.transact(this, f, origin, true);
    }

    public <T> T transact(Function<Transaction, T> f) {
        return Transaction.transact(this, f, null, true);
    }

    /**
     * Define a shared data type.
     * Multiple calls of `ydoc.get(name, TypeConstructor)` yield the same result
     * and do not overwrite each other. I.e.
     * `ydoc.get(name, Y.Array) === ydoc.get(name, Y.Array)`
     * After this method is called, the type is also available on `ydoc.share.get(name)`.
     * *Best Practices:*
     * Define all types right after the Y.Doc instance is created and store them in a separate object.
     * Also use the typed methods `getText(name)`, `getArray(name)`, ..
     *
     * @param typeConstructor The constructor of the type definition. E.g. Y.Text, Y.Array, Y.Map, ...
     * @return {InstanceType<Type>} The created type. Constructed with TypeConstructor
     * @example const ydoc = new Y.Doc(..)
     * const appState = {
     * document: ydoc.getText('document')
     * comments: ydoc.getArray('comments')
     * }
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractType<?>> T get(String name, Class<T> typeConstructor) {
        AbstractType<?> existing = share.get(name);
        if (existing == null) {
            // 首次创建
            if (typeConstructor == null || typeConstructor == (Class<?>) AbstractType.class) {
                existing = new PlaceholderType();
            } else {
                existing = createInstance(typeConstructor);
            }
            existing.integrate(this, null);
            share.put(name, existing);
        } else if (typeConstructor != null && existing instanceof PlaceholderType) {
            if(typeConstructor == (Class<?>) AbstractType.class) {
                return (T) existing;
            }
            // 类型转换：从占位符转为具体类型
            T newInstance = createInstance(typeConstructor);
            BeanUtil.copyProperties(existing, newInstance);
            share.put(name, newInstance);
            existing = newInstance;
        }
        return (T) existing;
    }

    private <T extends AbstractType<?>> T createInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Failed to instantiate type: " + e);
            throw new RuntimeException("Failed to instantiate type: " + clazz.getSimpleName(), e);
        }
    }

    public <T> YArray<T> getArray(String name) {
        return this.get(name != null ? name : "", YArray.class);
    }

    public YText getText(String name) {
        return this.get(name != null ? name : "", YText.class);
    }


    @SuppressWarnings("unchecked")
    public <T> YMap<T> getMap(String name) {
        return (YMap<T>) this.get(name != null ? name : "", YMap.class);
    }

    public YXmlElement getXmlElement(String name) {
        return this.get(name != null ? name : "", YXmlElement.class);
    }

    public YXmlFragment getXmlFragment(String name) {
        return this.get(name != null ? name : "", YXmlFragment.class);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> doc = new HashMap<>();
        this.share.forEach((key, value) -> {
            doc.put(key, value.toJson());
        });
        return doc;
    }

    public Map<String, Object> getContent() {
        Map<String, Object> doc = new HashMap<>();
        this.share.forEach((key, value) -> {
            Class<? extends AbstractType> aClass = analysisType(value);
            AbstractType type = JSON.parseObject(JSON.toJSONString(value), aClass);
            doc.put(key, type.toJson());
        });
        return doc;
    }


    private static Class<? extends AbstractType> analysisType(Object object) {
        JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(object));
        if (jsonObject.containsKey("map")) {
            return YMap.class;
        }
        if (jsonObject.containsKey("start")) {
            return YText.class;
        }
        if (jsonObject.containsKey("prelimContent") && jsonObject.containsKey("searchMarker")) {
            return YArray.class;
        }
        if (jsonObject.containsKey("prelimContent")) {
            return YXmlFragment.class;
        }
        if (jsonObject.containsKey("nodeName")) {
            return YXmlElement.class;
        }
        if (jsonObject.containsKey("hookName")) {
            return YXmlHook.class;
        }
        return YText.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy() {
        this.isDestroyed = true;
        for (Doc subdoc : new ArrayList<>(this.subDocs)) {
            subdoc.destroy();
        }
        final Item item = this.item;
        if (item != null) {
            this.item = null;
            AbstractContent content = item.content;
            DocOptions opts = new DocOptions();
            opts.guid = this.guid;
            opts.shouldLoad = false;
            Map<String, Object> contentOpts = content instanceof ContentDoc ? ((ContentDoc) content).getOpts() : new HashMap<>();
            if (contentOpts.containsKey("collectionid")) {
                opts.collectionid = contentOpts.get("collectionid").toString();
            }
            if (contentOpts.containsKey("gc")) {
                opts.gc = (boolean) contentOpts.get("gc");
            }
            if (contentOpts.containsKey("gcFilter")) {
                opts.gcFilter = (Function<Item, Boolean>) contentOpts.get("gcFilter");
            }
            if (contentOpts.containsKey("meta")) {
                opts.meta = contentOpts.get("meta");
            }
            if (contentOpts.containsKey("autoLoad")) {
                opts.autoLoad = (boolean) contentOpts.get("autoLoad");
            }
            Doc contentDoc = new Doc(opts);
            contentDoc.item = item;
            content.setDoc(contentDoc);
            Transaction.transact(((AbstractType<?>) item.parent).getDocument(), transaction -> {
                final Doc doc = content.getDoc();
                if (!item.isDeleted()) {
                    transaction.subDocsAdded.add(doc);
                }
                transaction.subDocsRemoved.add(this);
                return true;
            }, null, true);
        }
        this.emit("destroyed", true);
        this.emit("destroy", this);
        super.destroy();
    }

    public StructStore getStore() {
        return store;
    }

    public long getClientId() {
        return clientId;
    }

    public String getGuid() {
        return guid;
    }


    public Transaction getTransaction() {
        return transaction;
    }

    public Map<String, AbstractType<?>> getShare() {
        return share;
    }

    public Boolean getGc() {
        return gc;
    }

    public void setGc(Boolean gc) {
        this.gc = gc;
    }

    public Function<Item, Boolean> getGcFilter() {
        return gcFilter;
    }

    public void setGcFilter(Function<Item, Boolean> gcFilter) {
        this.gcFilter = gcFilter;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getCollectionid() {
        return collectionid;
    }

    public void setCollectionid(String collectionid) {
        this.collectionid = collectionid;
    }

    public void setShare(Map<String, AbstractType<?>> share) {
        this.share = share;
    }

    public void setStore(StructStore store) {
        this.store = store;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public List<Transaction> getTransactionCleanups() {
        return transactionCleanups;
    }

    public void setTransactionCleanups(List<Transaction> transactionCleanups) {
        this.transactionCleanups = transactionCleanups;
    }

    public void setSubDocs(Set<Doc> subDocs) {
        this.subDocs = subDocs;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public boolean isShouldLoad() {
        return shouldLoad;
    }

    public void setShouldLoad(boolean shouldLoad) {
        this.shouldLoad = shouldLoad;
    }

    public boolean isAutoLoad() {
        return autoLoad;
    }

    public void setAutoLoad(boolean autoLoad) {
        this.autoLoad = autoLoad;
    }

    public Object getMeta() {
        return meta;
    }

    public void setMeta(Object meta) {
        this.meta = meta;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void setLoaded(boolean loaded) {
        isLoaded = loaded;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public void setDestroyed(boolean destroyed) {
        isDestroyed = destroyed;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Doc doc)) {
            return false;
        }
        return clientId == doc.clientId && shouldLoad == doc.shouldLoad && autoLoad == doc.autoLoad && isLoaded == doc.isLoaded && isSynced == doc.isSynced && isDestroyed == doc.isDestroyed && Objects.equals(gc, doc.gc) && Objects.equals(gcFilter, doc.gcFilter) && Objects.equals(guid, doc.guid) && Objects.equals(collectionid, doc.collectionid) && Objects.equals(share, doc.share) && Objects.equals(store, doc.store) && Objects.equals(transaction, doc.transaction) && Objects.equals(transactionCleanups, doc.transactionCleanups) && Objects.equals(subDocs, doc.subDocs) && Objects.equals(item, doc.item) && Objects.equals(meta, doc.meta);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
