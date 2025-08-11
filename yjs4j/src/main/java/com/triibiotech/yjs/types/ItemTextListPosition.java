package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.*;
import com.triibiotech.yjs.utils.*;

import java.util.*;

/**
 * @author zbs
 * @date 2025/7/29 9:31
 **/
public class ItemTextListPosition {
    public Item left;
    public Item right;
    public Long index;
    public Map<String, Object> currentAttributes;

    public ItemTextListPosition(Item left, Item right, Long index, Map<String, Object> currentAttributes) {
        this.left = left;
        this.right = right;
        this.index = index;
        this.currentAttributes = currentAttributes;
    }

    public Item getLeft() {
        return left;
    }

    public Item getRight() {
        return right;
    }

    public Long getIndex() {
        return index;
    }

    public Map<String, Object> getCurrentAttributes() {
        return currentAttributes;
    }

    /**
     * Only call this if you know that this.right is defined
     */
    public void forward() {
        if (this.right == null) {
            throw new RuntimeException("Unexpected case");
        }
        if (this.right.content instanceof ContentFormat) {
            if (!this.right.isDeleted()) {
                updateCurrentAttributes(this.currentAttributes, (ContentFormat) this.right.content);
            }
        } else {
            if (!this.right.isDeleted()) {
                this.index += this.right.length;
            }
        }
        this.left = this.right;
        this.right = (Item) this.right.right;
    }

    public static void updateCurrentAttributes(Map<String, Object> currentAttributes, ContentFormat format) {
        if (format.getValue() == null) {
            currentAttributes.remove(format.getKey());
        } else {
            currentAttributes.put(format.getKey(), format.getValue());
        }
    }

    public static ItemTextListPosition findNextPosition(Transaction transaction, ItemTextListPosition pos, Long count) {
        while (pos.right != null && count > 0) {
            if (pos.right.content instanceof ContentFormat) {
                if (!pos.right.isDeleted()) {
                    updateCurrentAttributes(pos.currentAttributes, (ContentFormat) pos.right.content);
                }
            } else {
                if (!pos.right.isDeleted()) {
                    if (count < pos.right.length) {
                        // split right
                        StructStore.getItemCleanStart(transaction, ID.createId(pos.right.id.client, pos.right.id.clock + count));
                    }
                    pos.index += pos.right.length;
                    count -= pos.right.length;
                }
            }
            pos.left = pos.right;
            pos.right = (Item) pos.right.right;
            // pos.forward() - we don't forward because that would halve the performance because we already do the checks above
        }
        return pos;
    }

    public static ItemTextListPosition findPosition(Transaction transaction, AbstractType<?> parent, Long index, Boolean useSearchMarker) {
        Map<String, Object> currentAttributes = new HashMap<>();
        ArraySearchMarker marker = useSearchMarker ? ArraySearchMarker.findMarker(parent, index) : null;
        if (marker != null) {
            ItemTextListPosition pos = new ItemTextListPosition((Item) marker.p.left, marker.p, marker.index, currentAttributes);
            return findNextPosition(transaction, pos, index - marker.index);
        } else {
            ItemTextListPosition pos = new ItemTextListPosition(null, parent.start, 0L, currentAttributes);
            return findNextPosition(transaction, pos, index);
        }
    }

    /**
     * Negate applied formats
     * 在当前位置插入被否定（撤销/覆盖）的格式属性（格式 key -> null）。
     */
    public static void insertNegatedAttributes(Transaction transaction, AbstractType<?> parent, ItemTextListPosition currPos, Map<String, Object> negatedAttributes) {
        // Step 1: 清除当前位置已存在、value 相同的 format 属性
        while (
                currPos.right != null &&
                        (currPos.right.isDeleted() ||
                                (currPos.right.getContent() instanceof ContentFormat cf && Objects.equals(negatedAttributes.get(cf.getKey()), cf.getValue()))
                        )) {
            if (!currPos.right.isDeleted()) {
                ContentFormat cf = (ContentFormat) currPos.right.getContent();
                negatedAttributes.remove(cf.getKey());
            }
            currPos.forward();
        }

        // Step 2: 执行插入格式 Item
        Doc doc = transaction.doc;
        long ownClientId = doc.getClientId();

        for (Map.Entry<String, Object> entry : negatedAttributes.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            Item left = currPos.left;
            Item right = currPos.right;

            ID id = ID.createId(ownClientId, doc.getStore().getState(ownClientId));
            ContentFormat content = new ContentFormat(key, val);
            Item nextFormat = new Item(id, left, (left != null ? left.getLastId() : null), right, (right != null ? right.id : null), parent, null, content);

            nextFormat.integrate(transaction, 0);

            // 移动指针
            currPos.right = nextFormat;
            currPos.forward();
        }
    }

    /**
     * 将 currPos 向右推进，跳过那些已被删除的或值与将要应用的格式属性相同的格式项，从而减少不必要的格式变更操作。
     *
     * @param currPos       curr pos
     * @param newAttributes 新属性
     */
    public static void minimizeAttributeChanges(ItemTextListPosition currPos, Map<String, Object> newAttributes) {
        // go right while attributes[right.key] === right.value (or right is deleted)
        while (true) {
            Item right = currPos.right;
            if (right == null) {
                break;
            }
            boolean shouldSkip = false;
            if (right.isDeleted()) {
                shouldSkip = true;
            } else if (right.getContent() instanceof ContentFormat cf) {
                Object expected = newAttributes.getOrDefault(cf.getKey(), null);
                Object actual = cf.getValue();
                if (Objects.equals(expected, actual)) {
                    shouldSkip = true;
                }
            }
            if (!shouldSkip) {
                break;
            }
            currPos.forward();
        }
    }

    /**
     * attributes 插入到当前位置（currPos），并生成“被覆盖的旧属性”（negatedAttributes）的映射，以便后续撤销或合并。
     *
     * @param transaction 交易记录
     * @param parent      父母
     * @param currPos     curr pos
     * @param attributes  属性
     * @return {@link Map }<{@link String }, {@link Object }>
     */
    public static Map<String, Object> insertAttributes(Transaction transaction, AbstractType<?> parent, ItemTextListPosition currPos, Map<String, Object> attributes) {
        Doc doc = transaction.getDoc();
        long ownClientId = doc.getClientId();
        Map<String, Object> negatedAttributes = new HashMap<>();

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object newVal = entry.getValue();

            Object currentVal = currPos.currentAttributes.getOrDefault(key, null);

            // 如果当前值与目标值不同，说明需要插入
            if (!Objects.equals(currentVal, newVal)) {
                // 存储被替代的属性
                negatedAttributes.put(key, currentVal);

                Item left = currPos.left;
                Item right = currPos.right;

                ID id = ID.createId(ownClientId, doc.getStore().getState(ownClientId));
                ContentFormat content = new ContentFormat(key, newVal);

                Item formatItem = new Item(
                        id,
                        left,
                        left != null ? left.getLastId() : null,
                        right,
                        right != null ? right.getId() : null,
                        parent,
                        null,
                        content
                );

                // 整合进文档
                formatItem.integrate(transaction, 0);

                // 更新 currPos
                currPos.right = formatItem;
                currPos.forward();
            }
        }

        return negatedAttributes;
    }

    /**
     * 插入文本
     * 将一段 text 插入到文档中的 currPos 位置；
     * 根据当前格式（attributes）决定插入时是否需要设置格式；
     * 插入完毕后，再补上 negatedAttributes（即被“否定”的格式）。
     *
     * @param transaction 交易记录
     * @param parent      父母
     * @param currPos     curr pos
     * @param text        文本
     * @param attributes  属性
     */
    public static void insertText(Transaction transaction, AbstractType<?> parent, ItemTextListPosition currPos, Object text, Map<String, Object> attributes) {
        // Step 1: 将 currentAttributes 中未在 attributes 的键置为 null
        for (String key : currPos.currentAttributes.keySet()) {
            if (!attributes.containsKey(key)) {
                attributes.put(key, null);
            }
        }

        Doc doc = transaction.getDoc();
        long ownClientId = doc.getClientId();

        // Step 2: 跳过已有格式
        minimizeAttributeChanges(currPos, attributes);

        // Step 3: 插入新格式属性
        Map<String, Object> negatedAttributes = insertAttributes(transaction, parent, currPos, attributes);

        // Step 4: 准备要插入的内容
        AbstractContent content;
        if (text instanceof String) {
            content = new ContentString((String) text);
        } else if (text instanceof AbstractType) {
            content = new ContentType((AbstractType<?>) text);
        } else {
            content = new ContentEmbed(text);
        }

        // Step 5: 插入内容项
        Item left = currPos.left;
        Item right = currPos.right;
        long index = currPos.index;

        if (parent.getSearchMarker() != null) {
            ArraySearchMarker.updateMarkerChanges(parent.getSearchMarker(), index, content.getLength());
        }

        ID id = ID.createId(ownClientId, doc.getStore().getState(ownClientId));
        Item textItem = new Item(
                id,
                left,
                (left != null ? left.getLastId() : null),
                right,
                (right != null ? right.getId() : null),
                parent,
                null,
                content
        );
        textItem.integrate(transaction, 0);

        currPos.right = textItem;
        currPos.index = index;
        currPos.forward();

        // Step 6: 插入被否定的格式（撤销旧格式）
        insertNegatedAttributes(transaction, parent, currPos, negatedAttributes);
    }

    /**
     * 设置文本格式
     * 用于将某一段文字区域应用格式（如 bold、italic、color 等），并处理格式更新、冗余去除、格式反转等逻辑
     *
     * @param transaction 交易记录
     * @param parent      父母
     * @param currPos     curr pos
     * @param length      长度
     * @param attributes  属性
     */
    public static void formatText(Transaction transaction, AbstractType<?> parent, ItemTextListPosition currPos, long length,
                                  Map<String, Object> attributes) {
        Doc doc = transaction.getDoc();
        long ownClientId = doc.getClientId();

        // 步骤 1：跳过已有格式
        minimizeAttributeChanges(currPos, attributes);

        // 步骤 2：插入目标格式，返回被覆盖的旧格式
        Map<String, Object> negatedAttributes = insertAttributes(transaction, parent, currPos, attributes);

        // 步骤 3：向右遍历处理格式、内容删除、冗余剔除
        while (currPos.right != null && (
                length > 0 || (
                        !negatedAttributes.isEmpty() &&
                                (currPos.right.isDeleted() || currPos.right.getContent() instanceof ContentFormat)
                )
        )) {
            if (!currPos.right.isDeleted()) {
                AbstractContent content = currPos.right.getContent();

                if (content instanceof ContentFormat cf) {
                    String key = cf.getKey();
                    Object value = cf.getValue();
                    Object attr = attributes.get(key);

                    if (attr != null) {
                        if (Objects.equals(attr, value)) {
                            // 跳过无变化项
                            negatedAttributes.remove(key);
                        } else {
                            if (length == 0) {
                                // 格式尾部差异不处理
                                break;
                            }
                            negatedAttributes.put(key, value);
                        }
                        currPos.right.delete(transaction);
                    } else {
                        currPos.currentAttributes.put(key, value);
                    }
                } else {
                    if (length < currPos.right.getLength()) {
                        StructStore.getItemCleanStart(transaction, new ID(
                                currPos.right.getId().getClient(),
                                currPos.right.getId().getClock() + length
                        ));
                    }
                    length -= currPos.right.getLength();
                }
            }

            currPos.forward();
        }

        // 步骤 4：如有剩余区域，补上换行
        if (length > 0) {
            String newlines = "\n".repeat((int) length);

            ID id = ID.createId(ownClientId, doc.getStore().getState(ownClientId));
            Item newlineItem = new Item(
                    id,
                    currPos.left,
                    currPos.left != null ? currPos.left.getLastId() : null,
                    currPos.right,
                    currPos.right != null ? currPos.right.getId() : null,
                    parent,
                    null,
                    new ContentString(newlines)
            );

            newlineItem.integrate(transaction, 0);
            currPos.right = newlineItem;
            currPos.forward();
        }

        // 步骤 5：插入反向格式（撤销格式覆盖）
        insertNegatedAttributes(transaction, parent, currPos, negatedAttributes);
    }

    /**
     * 清理格式间隙
     * 在 start 到 curr 范围内（不包含 curr），清除已经 被覆盖、冗余 或 不再需要的格式项，并对 currAttributes 做同步修复
     *
     * @param transaction     交易记录
     * @param start           开始
     * @param curr            curr
     * @param startAttributes 开始属性
     * @param currAttributes  curr属性
     * @return int
     */
    public static int cleanupFormattingGap(Transaction transaction, Item start, Item curr, Map<String, Object> startAttributes, Map<String, Object> currAttributes) {
        Item end = start;
        Map<String, ContentFormat> endFormats = new HashMap<>();

        // 步骤 1：找到下一个非 deleted 且 countable 的 content（即范围的实际末尾）
        while (end != null && (!end.countable() || end.isDeleted())) {
            if (!end.isDeleted() && end.getContent() instanceof ContentFormat cf) {
                endFormats.put(cf.getKey(), cf);
            }
            end = (Item) end.getRight();
        }

        int cleanups = 0;
        boolean reachedCurr = false;

        // 步骤 2：从 start 遍历到 end（不包含），清除冗余格式
        while (start != end) {
            if (start == curr) {
                reachedCurr = true;
            }

            if (!start.isDeleted()) {
                AbstractContent content = start.getContent();

                if (content instanceof ContentFormat cf) {
                    String key = cf.getKey();
                    Object value = cf.getValue();

                    Object startAttrValue = startAttributes.getOrDefault(key, null);
                    ContentFormat endFormat = endFormats.get(key);

                    boolean isOverwritten = endFormat != content;
                    boolean isRedundant = Objects.equals(startAttrValue, value);

                    if (isOverwritten || isRedundant) {
                        // 冗余格式，执行删除
                        start.delete(transaction);
                        cleanups++;

                        if (!reachedCurr) {
                            Object currVal = currAttributes.getOrDefault(key, null);
                            if (Objects.equals(currVal, value) && !Objects.equals(startAttrValue, value)) {
                                if (startAttrValue == null) {
                                    currAttributes.remove(key);
                                } else {
                                    currAttributes.put(key, startAttrValue);
                                }
                            }
                        }
                    }

                    if (!reachedCurr && !start.isDeleted()) {
                        // 保留有效格式，更新 currentAttributes
                        updateCurrentAttributes(currAttributes, cf);
                    }
                }
            }

            start = (Item) start.getRight();
        }

        return cleanups;
    }

    /**
     * 清理无上下文格式间隙
     * 从当前 item 开始，先向右跳过无效节点（deleted / !countable）
     * <p>
     * 然后 从右向左回退，遇到重复的 ContentFormat（格式键重复）就 删除
     * <p>
     * 用 Set 记录已经遇到过的 key，确保只保留最近一次生效的格式项（即最近靠右的）
     *
     * @param transaction 交易记录
     * @param item        项目
     */
    public static void cleanupContextlessFormattingGap(Transaction transaction, Item item) {
        // 向右跳过所有 deleted 或非 countable 的 item
        while (item != null && item.getRight() != null &&
                (item.getRight().isDeleted() || !((Item) item.getRight()).countable())) {
            item = (Item) item.getRight();
        }

        // 保存遇到的格式 key，避免重复格式保留
        Set<String> seenKeys = new LinkedHashSet<>();

        // 向左回溯，遇到 ContentFormat 且 key 重复的就删除
        while (item != null && (item.isDeleted() || !item.countable())) {
            AbstractContent content = item.getContent();

            if (!item.isDeleted() && content instanceof ContentFormat cf) {
                String key = cf.getKey();
                if (seenKeys.contains(key)) {
                    item.delete(transaction);
                } else {
                    seenKeys.add(key);
                }
            }

            item = (Item) item.getLeft();
        }
    }

    /**
     * 完整清理 YText 类型中的多余格式属性（ContentFormat）
     *
     * @param type 类型
     * @return int
     */
    public static int cleanupYTextFormatting(YText type) {
        final int[] res = {0};
        Doc doc = type.getDoc();
        // 在事务中执行
        doc.transact(transaction -> {
            Item start = type.getStart();  // 初始格式检查起点
            Item end = start;

            Map<String, Object> startAttributes = new HashMap<>();
            Map<String, Object> currentAttributes = new HashMap<>(startAttributes);

            while (end != null) {
                if (!end.isDeleted()) {
                    AbstractContent content = end.getContent();
                    if (content instanceof ContentFormat cf) {
                        updateCurrentAttributes(currentAttributes, cf);
                    } else {
                        res[0] += cleanupFormattingGap(transaction, start, end, startAttributes, currentAttributes);
                        startAttributes = new HashMap<>(currentAttributes);
                        start = end;
                    }
                }
                end = (Item) end.getRight();
            }
            return null;
        });

        return res[0];
    }

    public static void cleanupYTextAfterTransaction(Transaction transaction) {
        Set<YText> needFullCleanup = new LinkedHashSet<>();
        Doc doc = transaction.getDoc();
        // 找出所有新增的格式项
        for (Map.Entry<Long, Long> entry : transaction.getAfterState().entrySet()) {
            Long client = entry.getKey();
            Long afterClock = entry.getValue();
            Long beforeClock = transaction.getBeforeState().getOrDefault(client, 0L);

            if (Objects.equals(afterClock, beforeClock)) {
                continue;
            }

            LinkedList<AbstractStruct> structs = doc.getStore().getClientStructs(client);
            StructStore.iterateStructs(transaction, structs, beforeClock, afterClock, struct -> {
                if (!struct.isDeleted() && struct instanceof Item item && item.getContent() instanceof ContentFormat) {
                    needFullCleanup.add((YText) item.getParent());
                }
            });
        }

        // 再次开启事务做清理
        doc.transact(t -> {
            DeleteSet deleteSet = transaction.getDeleteSet();
            DeleteSet.iterateDeletedStructs(transaction, deleteSet, struct -> {
                if (struct instanceof GC) {
                    return;
                }
                if (struct instanceof Item item && item.getParent() instanceof YText parent) {
                    if (!parent.hasFormatting()) {
                        return;
                    }
                    if (needFullCleanup.contains(parent)) {
                        return;
                    }
                    if (item.getContent() instanceof ContentFormat) {
                        needFullCleanup.add(parent);
                    } else {
                        cleanupContextlessFormattingGap(t, item);
                    }
                }
            });

            for (YText yText : needFullCleanup) {
                cleanupYTextFormatting(yText);
            }
            return null;
        });
    }

    public static ItemTextListPosition deleteText(Transaction transaction, ItemTextListPosition currPos, long length) {
        long startLength = length;
        Map<String, Object> startAttrs = new HashMap<>(currPos.currentAttributes);
        Item start = currPos.getRight();

        while (length > 0 && currPos.getRight() != null) {
            Item item = currPos.getRight();
            if (!item.isDeleted()) {
                if (item.getContent() instanceof ContentString
                        || item.getContent() instanceof ContentEmbed
                        || item.getContent() instanceof ContentType) {

                    if (length < item.getLength()) {
                        StructStore.getItemCleanStart(transaction, ID.createId(item.getId().getClient(), item.getId().getClock() + length));
                    }
                    length -= item.getLength();
                    item.delete(transaction);
                }
            }
            currPos.forward();
        }

        if (start != null) {
            cleanupFormattingGap(transaction, start, currPos.getRight(), startAttrs, currPos.getCurrentAttributes());
        }

        AbstractType<?> parent = (AbstractType<?>) (currPos.getLeft() != null ? currPos.getLeft().getParent() : currPos.getRight().getParent());
        if (parent.getSearchMarker() != null && !parent.getSearchMarker().isEmpty()) {
            ArraySearchMarker.updateMarkerChanges(parent.getSearchMarker(), currPos.getIndex(), -startLength + length);
        }
        return currPos;
    }



}
