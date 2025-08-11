package com.triibiotech.yjs.types;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.triibiotech.yjs.structs.*;
import com.triibiotech.yjs.utils.*;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.EventOperator;
import com.triibiotech.yjs.utils.event.YTextEvent;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Type that represents text with formatting information.
 * <p>
 * This type replaces y-richtext as this implementation is able to handle
 * block formats (format information on a paragraph), embeds (complex elements
 * like pictures and videos), and text formats (**bold**, *italic*).
 *
 * @author zbs
 * @date 2025/07/29  11:00:27
 */
public class YText extends AbstractType<YTextEvent> {

    public static final int Y_TEXT_REF_ID = 2;

    /**
     * Array of pending operations on this type
     */
    private List<Runnable> pending;
    /**
     * Whether this YText contains formatting attributes.
     * This flag is updated when a formatting item is integrated (see ContentFormat.integrate)
     */
    private boolean hasFormatting = false;

    public YText() {
        super();
        this.pending = new ArrayList<>();
        this.searchMarker = new ArrayList<>();
    }

    public YText(String string) {
        this();
        if (string != null) {
            this.pending.add(() -> this.insert(0, string, null));
        }
    }

    @Override
    public long getLength() {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
        return this.length;
    }

    @Override
    public void integrate(Doc y, Item item) {
        super.integrate(y, item);
        try {
            if (pending != null) {
                pending.forEach(Runnable::run);
            }
        } catch (Exception e) {
            EncodingUtil.log.error("Error during YText integration", e);
        }
        pending = null;
    }

    @Override
    public AbstractType<YTextEvent> copy() {
        return new YText();
    }

    @Override
    public AbstractType<YTextEvent> clone() {
        YText text = new YText();
        text.applyDelta(this.toDelta(null, null, null), true);
        return text;
    }

    @Override
    public void callObserver(Transaction transaction, Set<String> parentSubs) {
        super.callObserver(transaction, parentSubs);
        YTextEvent event = new YTextEvent(this, transaction, parentSubs);
        callTypeObservers(this, transaction, event);
        // If a remote change happened, we try to cleanup potential formatting duplicates.
        if (!transaction.local && this.hasFormatting) {
            transaction.needFormattingCleanup = true;
        }
    }

    @Override
    public String toString() {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }

        StringBuilder str = new StringBuilder();
        Item n = start;
        while (n != null) {
            if (!n.isDeleted() && n.countable() &&
                    n.content instanceof ContentString content) {
                str.append(content.getStr());
            }
            n = (Item) n.right;
        }
        return str.toString();
    }

    @Override
    public Object toJson() {
        return this.toString();
    }

    public void applyDelta(List<EventOperator> delta, boolean sanitize) {
        if (doc != null) {
            Transaction.transact(this.doc, transaction -> {
                ItemTextListPosition currPos = new ItemTextListPosition(null, this.start, 0L, new HashMap<>());
                for (int i = 0; i < delta.size(); i++) {
                    EventOperator op = delta.get(i);
                    if (op.insert != null) {
                        Object ins = (!sanitize && op.insert instanceof String && i == delta.size() - 1 && currPos.right == null && ((String) op.insert).endsWith("\n")
                                ? ((String) op.insert).substring(0, ((String) op.insert).length() - 1)
                                : op.insert);
                        if (!(ins instanceof String) || !((String) ins).isEmpty()) {
                            ItemTextListPosition.insertText(
                                    transaction,
                                    this,
                                    currPos,
                                    ins,
                                    op.attributes == null ? new HashMap<>() : (JSON.parseObject(JSON.toJSONString(op.attributes)))
                            );
                        }
                    } else if (op.retain != null) {
                        ItemTextListPosition.formatText(
                                transaction,
                                this,
                                currPos,
                                op.retain,
                                op.attributes == null ? new HashMap<>() : (JSON.parseObject(JSON.toJSONString(op.attributes)))
                        );
                    } else if (op.delete != null) {
                        ItemTextListPosition.deleteText(transaction, currPos, op.delete);
                    }
                }
                return true;
            }, null, true);
        } else {
            this.pending.add(() -> this.applyDelta(delta, true));
        }
    }

    public List<EventOperator> toDelta(Snapshot snapshot, Snapshot prevSnapshot, BiFunction<String, ID, Object> computeYChange) {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
        List<EventOperator> ops = new ArrayList<>();
        Map<String, Object> currentAttributes = new HashMap<>();
        Doc doc = this.doc;
        StringBuilder str = new StringBuilder();
        Item n = this.start;

        if (snapshot != null || prevSnapshot != null) {
            Transaction.transact(doc, transaction -> {
                if (snapshot != null) {
                    Snapshot.splitSnapshotAffectedStructs(transaction, snapshot);
                }
                if (prevSnapshot != null) {
                    Snapshot.splitSnapshotAffectedStructs(transaction, prevSnapshot);
                }
                computeDeltaOps(n, snapshot, prevSnapshot, currentAttributes, str, ops, computeYChange);
                return true;
            }, "cleanup", true);
        } else {
            computeDeltaOps(n, snapshot, prevSnapshot, currentAttributes, str, ops, computeYChange);
        }

        return ops;
    }

    private void packStrToOps(List<EventOperator> ops, StringBuilder strBuilder, Map<String, Object> currentAttributes) {
        if (!strBuilder.isEmpty()) {
            EventOperator op = new EventOperator();
            op.setInsert(strBuilder.toString());

            if (!currentAttributes.isEmpty()) {
                Map<String, Object> attributes = new HashMap<>(currentAttributes);
                op.setAttributes(attributes);
            }

            ops.add(op);
            strBuilder.setLength(0);
        }
    }

    /**
     * 根据两个文本快照计算它们之间的 Delta（操作变更列表）
     */
    private void computeDeltaOps(Item n, Snapshot snapshot, Snapshot prevSnapshot, Map<String, Object> currentAttributes,
                                 StringBuilder str, List<EventOperator> ops, BiFunction<String, ID, Object> computeChange) {
        while (n != null) {
            if (Snapshot.isVisible(n, snapshot) || (prevSnapshot != null && Snapshot.isVisible(n, prevSnapshot))) {
                AbstractContent content = n.content;
                if (content instanceof ContentString) {
                    Object cur = currentAttributes.get("ychange");
                    if (snapshot != null && !Snapshot.isVisible(n, snapshot)) {
                        boolean flag = cur == null;
                        if (cur != null) {
                            JSONObject object = JSON.parseObject(JSON.toJSONString(cur));
                            if (!Objects.equals(object.get("user"), n.id.client)) {
                                flag = true;
                            }
                            if (!Objects.equals(object.get("type"), "removed")) {
                                flag = true;
                            }
                        }
                        if (flag) {
                            packStrToOps(ops, str, currentAttributes);
                            currentAttributes.put("ychange", computeChange != null
                                    ? computeChange.apply("removed", n.id)
                                    : Map.of("type", "removed"));
                        }
                    } else if (prevSnapshot != null && !Snapshot.isVisible(n, prevSnapshot)) {
                        boolean flag = cur == null;
                        if (cur != null) {
                            JSONObject object = JSON.parseObject(JSON.toJSONString(cur));
                            if (!Objects.equals(object.get("user"), n.id.client)) {
                                flag = true;
                            }
                            if (!Objects.equals(object.get("type"), "added")) {
                                flag = true;
                            }
                        }
                        if (flag) {
                            packStrToOps(ops, str, currentAttributes);
                            currentAttributes.put("ychange", computeChange != null
                                    ? computeChange.apply("added", n.id)
                                    : Map.of("type", "added"));
                        }
                    } else if (cur != null) {
                        packStrToOps(ops, str, currentAttributes);
                        currentAttributes.remove("ychange");
                    }
                    str.append(((ContentString) content).getStr());
                } else if (content instanceof ContentEmbed || content instanceof ContentType) {
                    packStrToOps(ops, str, currentAttributes);
                    EventOperator op = new EventOperator();
                    op.setInsert(content.getContent()[0]);
                    if (!currentAttributes.isEmpty()) {
                        op.setAttributes(new HashMap<>(currentAttributes));
                    }
                    ops.add(op);
                } else if (content instanceof ContentFormat) {
                    if (Snapshot.isVisible(n, snapshot)) {
                        packStrToOps(ops, str, currentAttributes);
                        ItemTextListPosition.updateCurrentAttributes(currentAttributes, (ContentFormat) content);
                    }
                }
            }
            n = (Item) n.right;
        }
        packStrToOps(ops, str, currentAttributes);
    }

    public void insert(long index, String text) {
        this.insert(index, text, null);
    }

    public void insert(long index, String text, Map<String, Object> attributes) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (doc != null) {
            boolean useSearchMarker = attributes != null;
            Transaction.transact(doc, transaction -> {
                Map<String, Object> attr = new HashMap<>();
                ItemTextListPosition pos = ItemTextListPosition.findPosition(transaction, this, index, useSearchMarker);
                if (useSearchMarker) {
                    attr = new HashMap<>(pos.currentAttributes);
                }
                ItemTextListPosition.insertText(transaction, this, pos, text, attr);
                return attr;
            }, null, true);
        } else if (pending != null) {
            this.pending.add(() -> insert(index, text, attributes));
        }
    }

    public void insertEmbed(long index, Object embed, Map<String, Object> attributes) {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                ItemTextListPosition pos = ItemTextListPosition.findPosition(transaction, this, index, attributes == null);
                ItemTextListPosition.insertText(transaction, this, pos, embed, attributes != null ? attributes : new HashMap<>());
                return true;
            }, null, true);
        } else if (pending != null) {
            Map<String, Object> attrs = attributes != null ? attributes : new HashMap<>();
            pending.add(() -> insertEmbed(index, embed, attrs));
        }
    }

    public void delete(long index, long length) {
        if (length == 0) {
            return;
        }

        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                ItemTextListPosition.deleteText(transaction, ItemTextListPosition.findPosition(transaction, this, index, true), length);
                return true;
            }, null, true);
        } else if (pending != null) {
            pending.add(() -> this.delete(index, length));
        }
    }

    public void format(long index, long length, Map<String, Object> attributes) {
        if (length == 0) {
            return;
        }

        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                ItemTextListPosition pos = ItemTextListPosition.findPosition(transaction, this, index, false);
                if (pos.right != null) {
                    ItemTextListPosition.formatText(transaction, this, pos, length, attributes);
                }
                return true;
            }, null, true);
        } else if (pending != null) {
            pending.add(() -> this.format(index, length, attributes));
        }
    }

    public void removeAttribute(String attributeName) {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                typeMapDelete(transaction, this, attributeName);
                return true;
            }, null, true);
        } else if (pending != null) {
            pending.add(() -> this.removeAttribute(attributeName));
        }
    }

    public void setAttribute(String attributeName, Object attributeValue) {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                typeMapSet(transaction, this, attributeName, attributeValue);
                return true;
            }, null, true);
        } else if (pending != null) {
            pending.add(() -> this.setAttribute(attributeName, attributeValue));
        }
    }

    public Object getAttribute(String attributeName) {
        return typeMapGet(this, attributeName);
    }

    public Map<String, Object> getAttributes() {
        return typeMapGetAll(this);
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YTextRefID);
    }

    public boolean hasFormatting() {
        return hasFormatting;
    }

    public void setHasFormatting(boolean hasFormatting) {
        this.hasFormatting = hasFormatting;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        YText yText = (YText) o;
        return hasFormatting == yText.hasFormatting && Objects.equals(pending, yText.pending);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pending, hasFormatting);
    }

    public static AbstractType<?> readYText(DSDecoder decoder) {
        return new YText();
    }
}
