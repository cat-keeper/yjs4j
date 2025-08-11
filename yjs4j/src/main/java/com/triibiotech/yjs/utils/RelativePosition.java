package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.AbstractStruct;
import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RelativePosition represents a position in a Yjs document that is relative to other content.
 * This allows positions to be maintained even when the document changes.
 * Matches the functionality of RelativePosition.js from the original Yjs implementation.
 *
 * @author zbs
 * @date 2025/07/31  15:49:27
 */
public class RelativePosition {

    /**
     * The type that this position is relative to
     */
    public ID type;

    /**
     * The type key if this position is in a map-like structure
     */
    public String tName;

    /**
     * The item that this position is relative to
     */
    public ID item;

    /**
     * The association type (0 = before, 1 = after)
     */
    public Long assoc;

    /**
     * Create a new RelativePosition
     */
    public RelativePosition(ID type, String tName, ID item, Long assoc) {
        this.type = type;
        this.tName = tName;
        this.item = item;
        this.assoc = assoc;
    }

    public ID getType() {
        return type;
    }

    public void setType(ID type) {
        this.type = type;
    }

    public String gettName() {
        return tName;
    }

    public void settName(String tName) {
        this.tName = tName;
    }

    public ID getItem() {
        return item;
    }

    public void setItem(ID item) {
        this.item = item;
    }

    public Long getAssoc() {
        return assoc;
    }

    public void setAssoc(Long assoc) {
        this.assoc = assoc;
    }

    public static Map<String, Object> relativePositionToJSON(RelativePosition pos) {
        Map<String, Object> json = new HashMap<>();
        if (pos.type != null) {
            json.put("type", pos.type);
        }
        if (pos.tName != null) {
            json.put("tName", pos.tName);
        }
        if (pos.item != null) {
            json.put("item", pos.item);
        }
        if (pos.assoc != null) {
            json.put("assoc", pos.assoc);
        }
        return json;
    }

    public static RelativePosition createRelativePositionFromJSON(Map<String, Object> json) {
        ID type = null;
        if (json.containsKey("type") && json.get("type") instanceof ID) {
            type = (ID) json.get("type");
        }
        ID item = null;
        if (json.containsKey("item") && json.get("item") instanceof ID) {
            item = (ID) json.get("item");
        }
        return new RelativePosition(
                type,
                json.containsKey("tname") ? json.get("tname").toString() : null,
                item,
                (Long) json.getOrDefault("assoc", 0L)
        );
    }

    /**
     * Represents an absolute position in the document
     *
     * @author zbs
     * @date 2025/07/31  16:17:10
     */
    public static class AbsolutePosition {
        public final AbstractType<?> type;
        public final Long index;
        public final Long assoc;

        public AbsolutePosition(AbstractType<?> type, Long index, Long assoc) {
            this.type = type;
            this.index = index;
            this.assoc = assoc == null ? 0 : assoc;
        }

        @Override
        public String toString() {
            return "AbsolutePosition{type=" + type + ", index=" + index + '}';
        }
    }

    /**
     * Create an AbsolutePosition from this RelativePosition
     */
    public static AbsolutePosition createAbsolutePosition(AbstractType<?> type, Long index, Long assoc) {
        if (assoc == null) {
            assoc = 0L;
        }
        return new AbsolutePosition(type, index, assoc);
    }

    /**
     * Create a RelativePosition from a type and index
     */
    public static RelativePosition createRelativePosition(AbstractType<?> type, ID item, Long assoc) {
        ID typeId = null;
        String tName = null;
        if (type.getItem() == null) {
            tName = ID.findRootTypeKey(type);
        } else {
            typeId = ID.createId(type.getItem().id.client, type.getItem().id.clock);
        }
        return new RelativePosition(typeId, tName, item, assoc);
    }

    /**
     * Create a RelativePosition from a type and index with association
     */
    public static RelativePosition createRelativePositionFromTypeIndex(AbstractType<?> type, Long index, Long assoc) {
        if (assoc == null) {
            assoc = 0L;
        }
        if (assoc < 0) {
            if (index == 0) {
                return createRelativePosition(type, null, assoc);
            }
            index--;
        }
        // 获取链表头
        Item t = type.getStart();
        while (t != null) {
            if (!t.isDeleted() && t.countable()) {
                if (t.getLength() > index) {
                    // 在当前节点内找到位置
                    // case 1: found position somewhere in the linked list
                    return createRelativePosition(type, ID.createId(t.id.client, t.id.clock + index), assoc);
                }
                index -= t.getLength();
            }

            if (t.getRight() == null && assoc < 0) {
                // 到达尾部，左关联，使用最后一个 id
                // left-associated position, return last available id
                return createRelativePosition(type, t.getLastId(), assoc);
            }
            // 遍历下一个 Item
            t = (Item) t.getRight();
        }
        // 找不到 id，说明是类型结尾处的右关联
        return createRelativePosition(type, null, assoc);
    }

    public static Encoder writeRelativePosition(Encoder encoder, RelativePosition rpos) {
        ID type = rpos.type;
        String tName = rpos.tName;
        ID item = rpos.item;
        Long assoc = rpos.assoc;
        if(item != null) {
            Encoder.writeVarUint(encoder, 0);
            ID.writeId(encoder, item);
        } else if (tName != null) {
            // case 2: found position at the end of the list and type is stored in y.share
            Encoder.writeUint8(encoder, 1);
            Encoder.writeVarString(encoder, tName);
        } else if (type != null) {
            // case 3: found position at the end of the list and type is attached to an item
            Encoder.writeUint8(encoder, 2);
            ID.writeId(encoder, type);
        } else {
            throw new RuntimeException("Unexpected case");
        }
        Encoder.writeVarInt(encoder, assoc);
        return encoder;
    }

    public static byte[] encodeRelativePosition(RelativePosition rpos) {
        Encoder encoder = Encoder.createEncoder();
        writeRelativePosition(encoder, rpos);
        return Encoder.toUint8Array(encoder);
    }

    public static RelativePosition readRelativePosition(Decoder decoder) {
        ID type = null;
        String tName = null;
        ID itemID = null;
        long read = Decoder.readVarUint(decoder);
        if(read == 0) {
            itemID = ID.readId(decoder);
        } else if (read == 1) {
            tName = Decoder.readVarString(decoder);
        } else if (read == 2) {
            type = ID.readId(decoder);
        }
        Long assoc = Decoder.hasContent(decoder) ? Decoder.readVarInt(decoder) : 0L;
        return new RelativePosition(type, tName, itemID, assoc);
    }

    public static RelativePosition decodeRelativePosition(byte[] data) {
        return readRelativePosition(Decoder.createDecoder(data));
    }

    public static Map<String, Object> getItemWithOffset(StructStore store, ID id) {
        AbstractStruct item = store.getItem(id);
        long diff = id.clock - item.id.clock;
        return Map.of("item", item, "diff", diff);
    }

    /**
     * Transform a relative position to an absolute position.
     * <p>
     * If you want to share the relative position with other users, you should set
     * `followUndoneDeletions` to false to get consistent results across all clients.
     * <p>
     * When calculating the absolute position, we try to follow the "undone deletions". This yields
     * better results for the user who performed undo. However, only the user who performed the undo
     * will get the better results, the other users don't know which operations recreated a deleted
     * range of content. There is more information in this ticket: https://github.com/yjs/yjs/issues/638
     */
    public static AbsolutePosition createAbsolutePositionFromRelativePosition(RelativePosition rpos, Doc doc, Boolean followUndoneDeletions) {
        if(followUndoneDeletions == null) {
            followUndoneDeletions = true;
        }
        StructStore store = doc.getStore();
        ID rightID = rpos.getItem();
        ID typeID = rpos.getType();
        String tname = rpos.gettName();
        Long assoc = rpos.getAssoc();

        AbstractType<?> type = null;
        long index = 0L;

        if (rightID != null) {
            if (store.getState(rightID.getClient()) <= rightID.getClock()) {
                return null;
            }

            Map<String, Object> res = followUndoneDeletions ? Item.followRedone(store, rightID) : getItemWithOffset(store, rightID);
            AbstractStruct struct = (AbstractStruct) res.get("item");
            if (!(struct instanceof Item right)) {
                return null;
            }
            type = (AbstractType<?>) right.getParent();

            if (type.getItem() == null || !type.getItem().isDeleted()) {
                index = (right.isDeleted() || !right.countable()) ? 0 : (((long)res.get("diff")) + (assoc >= 0 ? 0 : 1));
                Item n = (Item) right.getLeft();
                while (n != null) {
                    if (!n.isDeleted() && n.countable()) {
                        index += n.getLength();
                    }
                    n = (Item) n.getLeft();
                }
            }
        } else {
            if (tname != null) {
                type = doc.get(tname, AbstractType.class);
            } else if (typeID != null) {
                if (StructStore.getState(store, typeID.getClient()) <= typeID.getClock()) {
                    // type does not exist yet
                    return null;
                }
                AbstractStruct struct = followUndoneDeletions
                        ? (AbstractStruct) Item.followRedone(store, typeID).get("item")
                        : store.getItem(typeID);

                if (struct instanceof Item && ((Item) struct).getContent() instanceof ContentType) {
                    type = ((ContentType) ((Item) struct).getContent()).getType();
                } else {
                    // struct is garbage collected
                    return null;
                }
            } else {
                throw new IllegalStateException("Unexpected case");
            }

            index = assoc >= 0 ? type.getLength() : 0;
        }

        return createAbsolutePosition(type, index, rpos.assoc);
    }

    public static Boolean compareRelativePositions(RelativePosition a, RelativePosition b) {
        if(Objects.equals(a, b)) {
            return true;
        }
        if(a == null || b == null) {
            return false;
        }
        return Objects.equals(a.type, b.type) &&
                Objects.equals(a.tName, b.tName) &&
                Objects.equals(a.item, b.item) &&
                Objects.equals(a.assoc, b.assoc);
    }

}
