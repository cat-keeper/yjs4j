package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.*;
import com.triibiotech.yjs.utils.lib0.Binary;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author zbs
 * @date 2025/7/30 12:04
 **/
@SuppressWarnings("unused")
public class LazyStructReader implements Iterator<AbstractStruct> {
    private final Iterator<AbstractStruct> gen;
    private AbstractStruct curr;
    private final boolean filterSkips;

    public LazyStructReader(DSDecoder decoder, boolean filterSkips) {
        this.gen = new LazyStructReaderGenerator(decoder);
        this.filterSkips = filterSkips;
        next(); // 初始化
    }

    @Override
    public boolean hasNext() {
        return curr != null;
    }

    @Override
    public AbstractStruct next() {
        AbstractStruct prev = curr;
        do {
            curr = gen.hasNext() ? gen.next() : null;
        } while (filterSkips && curr != null && curr instanceof Skip);
        return prev;
    }

    public AbstractStruct current() {
        return curr;
    }

    /**
     * 等价于 JS 的 lazyStructReaderGenerator
     */
    private static class LazyStructReaderGenerator implements Iterator<AbstractStruct> {
        private final DSDecoder decoder;
        private long outerCount;
        private long innerCount;

        private long numOfStateUpdates;
        // 记录当前结构的数量
        private long numberOfStructs;
        private long client;
        private long clock;
        private AbstractStruct nextStruct;

        public LazyStructReaderGenerator(DSDecoder decoder) {
            this.decoder = decoder;
            this.numOfStateUpdates = Decoder.readVarUint(decoder.getRestDecoder());
            this.outerCount = 0;
            prepareNext();
        }

        @Override
        public boolean hasNext() {
            return nextStruct != null;
        }

        @Override
        public AbstractStruct next() {
            if (nextStruct == null) {
                throw new NoSuchElementException();
            }
            AbstractStruct result = nextStruct;
            prepareNext();
            return result;
        }

        private void prepareNext() {
            while (true) {
                if (outerCount >= numOfStateUpdates) {
                    nextStruct = null;
                    return;
                }

                if (innerCount == 0) {
                    numberOfStructs = Decoder.readVarUint(decoder.getRestDecoder());
                    client = decoder.readClient();
                    clock = Decoder.readVarUint(decoder.getRestDecoder());
                    innerCount = numberOfStructs;
                }

                if (innerCount > 0) {
                    int info = decoder.readInfo();
                    if (info == 10) {
                        long len = Decoder.readVarUint(decoder.getRestDecoder());
                        nextStruct = new Skip(ID.createId(client, clock), len);
                        clock += len;
                    } else if ((Binary.BITS5 & info) != 0) {
                        boolean cantCopyParentInfo = (info & (Binary.BIT7 | Binary.BIT8)) == 0;
                        ID origin = (info & Binary.BIT8) == Binary.BIT8 ? decoder.readLeftId() : null;
                        ID rightOrigin = (info & Binary.BIT7) == Binary.BIT7 ? decoder.readRightId() : null;
                        Object parent = cantCopyParentInfo
                                ? (decoder.readParentInfo()
                                ? decoder.readString()
                                : decoder.readLeftId())
                                : null;
                        String parentSub = cantCopyParentInfo && (info & Binary.BIT6) == Binary.BIT6
                                ? decoder.readString()
                                : null;
                        AbstractContent content = Item.readItemContent(decoder, info);
                        Item item = new Item(
                                ID.createId(client, clock),
                                null,
                                origin,
                                null,
                                rightOrigin,
                                parent,
                                parentSub,
                                content
                        );
                        nextStruct = item;
                        clock += item.getLength();
                    } else {
                        long len = decoder.readLen();
                        nextStruct = new GC(ID.createId(client, clock), len);
                        clock += len;
                    }
                    innerCount--;
                    if (innerCount == 0) {
                        outerCount++;
                    }
                    return; // 每次只生成一个，保持调用顺序
                }
            }
        }
    }

}
