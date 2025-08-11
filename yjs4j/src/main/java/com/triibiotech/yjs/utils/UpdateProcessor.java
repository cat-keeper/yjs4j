package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.*;
import com.triibiotech.yjs.types.AbstractType;
import com.triibiotech.yjs.types.YXmlElement;
import com.triibiotech.yjs.types.YXmlHook;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.lib0.decoding.Decoder;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Processes update messages and integrates them into the document
 */
public class UpdateProcessor {

    public static void logUpdate(byte[] update) {
        logUpdateV2(update, UpdateDecoderV1.class);
    }

    public static void logUpdateV2(byte[] update, Class<? extends DSDecoder> decoderClass) {
        if (decoderClass == null) {
            decoderClass = UpdateDecoderV2.class;
        }
        try {
            List<AbstractStruct> structs = new ArrayList<>();
            DSDecoder updateDecoder = decoderClass.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
            LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder, false);
            for (AbstractStruct curr = lazyDecoder.current(); curr != null; curr = lazyDecoder.next()) {
                structs.add(curr);
            }
            System.out.println("Structs: " + structs);
            DeleteSet ds = DeleteSet.readDeleteSet(updateDecoder);
            System.out.println("DeleteSet: " + ds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> decodeUpdate(byte[] update) {
        return decodeUpdateV2(update, UpdateDecoderV1.class);
    }

    public static Map<String, Object> decodeUpdateV2(byte[] update, Class<? extends DSDecoder> decoderClass) {
        if (decoderClass == null) {
            decoderClass = UpdateDecoderV2.class;
        }
        try {
            List<AbstractStruct> structs = new ArrayList<>();
            DSDecoder updateDecoder = decoderClass.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
            LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder, false);
            for (AbstractStruct curr = lazyDecoder.current(); curr != null; curr = lazyDecoder.next()) {
                structs.add(curr);
            }
            return Map.of("structs", structs, "ds", DeleteSet.readDeleteSet(updateDecoder));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] encodeStateVectorFromUpdateV2(byte[] update, Class<? extends DSEncoder> YEncoder, Class<? extends DSDecoder> YDecoder) {
        try {
            if (YDecoder == null) {
                YDecoder = UpdateDecoderV2.class;
            }
            // 创建 encoder 和 decoder 实例
            DSEncoder encoder = YEncoder.getDeclaredConstructor().newInstance();
            DSDecoder dsDecoder = YDecoder.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
            LazyStructReader updateDecoder = new LazyStructReader(dsDecoder, false);
            AbstractStruct curr = updateDecoder.current();

            if (curr != null) {
                int size = 0;
                long currClient = curr.getId().getClient();
                boolean stopCounting = curr.getId().getClock() != 0;
                long currClock = stopCounting ? 0 : curr.getId().getClock() + curr.getLength();

                while ((curr = updateDecoder.next()) != null) {
                    long idClient = curr.getId().getClient();
                    long idClock = curr.getId().getClock();

                    if (idClient != currClient) {
                        if (currClock != 0) {
                            size++;
                            Encoder.writeVarUint(encoder.getRestEncoder(), currClient);
                            Encoder.writeVarUint(encoder.getRestEncoder(), currClock);
                        }
                        currClient = idClient;
                        currClock = 0;
                        stopCounting = idClock != 0;
                    }

                    if (curr instanceof Skip) {
                        stopCounting = true;
                    }

                    if (!stopCounting) {
                        currClock = idClock + curr.getLength();
                    }
                }

                if (currClock != 0) {
                    size++;
                    Encoder.writeVarUint(encoder.getRestEncoder(), currClient);
                    Encoder.writeVarUint(encoder.getRestEncoder(), currClock);
                }

                // 编码 state vector size 和内容
                Encoder enc = Encoder.createEncoder();
                Encoder.writeVarUint(enc, size);
                Encoder.writeBinaryEncoder(enc, encoder.getRestEncoder());
                encoder.setRestEncoder(enc);
                return encoder.toUint8Array();
            } else {
                Encoder.writeVarUint(encoder.getRestEncoder(), 0);
                return encoder.toUint8Array();
            }
        } catch (Exception e) {
            throw new RuntimeException("encodeStateVectorFromUpdateV2 failed", e);
        }
    }

    public static byte[] encodeStateVectorFromUpdate(byte[] update) {
        return encodeStateVectorFromUpdateV2(update, UpdateEncoderV1.class, UpdateDecoderV1.class);
    }

    public static class UpdateMeta {
        private Map<Long, Long> from;
        private Map<Long, Long> to;

        public UpdateMeta(Map<Long, Long> from, Map<Long, Long> to) {
            this.from = from;
            this.to = to;
        }

        public Map<Long, Long> getFrom() {
            return from;
        }

        public void setFrom(Map<Long, Long> from) {
            this.from = from;
        }

        public Map<Long, Long> getTo() {
            return to;
        }

        public void setTo(Map<Long, Long> to) {
            this.to = to;
        }
    }

    public static UpdateMeta parseUpdateMetaV2(byte[] update, Class<? extends DSDecoder> YDecoder) {
        if (YDecoder == null) {
            YDecoder = UpdateDecoderV2.class;
        }
        try {
            Map<Long, Long> from = new HashMap<>();
            Map<Long, Long> to = new HashMap<>();
            DSDecoder dsDecoder = YDecoder.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
            LazyStructReader updateDecoder = new LazyStructReader(dsDecoder, false);
            AbstractStruct curr = updateDecoder.current();
            if (curr != null) {
                long currClient = curr.getId().getClient();
                long currClock = curr.getId().getClock();
                from.put(currClient, currClock);
                for (; curr != null; curr = updateDecoder.next()) {
                    if (currClient != curr.id.client) {
                        // We found a new client
                        // write the end to `to`
                        to.put(currClient, currClock);
                        // write the beginning to `from`
                        from.put(curr.id.client, curr.id.clock);
                        // update currClient
                        currClient = curr.id.client;
                    }
                    currClock = curr.id.clock + curr.length;
                }
                // write the end to `to`
                to.put(currClient, currClock);
            }
            return new UpdateMeta(from, to);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static UpdateMeta parseUpdateMeta(byte[] update) {
        return parseUpdateMetaV2(update, UpdateDecoderV1.class);
    }


    public static AbstractStruct sliceStruct(AbstractStruct left, long diff) {
        if (left instanceof GC) {
            long client = left.getId().getClient();
            long clock = left.getId().getClock();
            return new GC(ID.createId(client, clock + diff), left.length - diff);
        } else if (left instanceof Skip) {
            long client = left.getId().getClient();
            long clock = left.getId().getClock();
            return new Skip(ID.createId(client, clock + diff), left.length - diff);
        } else if (left instanceof Item leftItem) {
            long client = leftItem.getId().getClient();
            long clock = leftItem.getId().getClock();
            return new Item(ID.createId(client, clock + diff), null, ID.createId(client, clock + diff - 1), null, leftItem.rightOrigin, leftItem.parent, leftItem.parentSub, leftItem.content.splice((int) diff));
        }
        return null;
    }

    public static byte[] mergeUpdates(List<byte[]> updates) {
        return mergeUpdatesV2(updates, UpdateDecoderV1.class, UpdateEncoderV1.class);
    }

    public static byte[] mergeUpdatesV2(List<byte[]> updates, Class<? extends DSDecoder> YDecoder, Class<? extends DSEncoder> YEncoder) {
        if (updates.size() == 1) {
            return updates.get(0);
        }
        if (YEncoder == null) {
            YEncoder = UpdateEncoderV2.class;
        }
        if (YDecoder == null) {
            YDecoder = UpdateDecoderV2.class;
        }

        Class<? extends DSDecoder> finalYDecoder = YDecoder;
        List<DSDecoder> updateDecoders = updates.stream().map(data -> {
            Decoder decoder = Decoder.createDecoder(data);
            try {
                return finalYDecoder.getDeclaredConstructor(Decoder.class).newInstance(decoder);
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate decoder", e);
            }
        }).collect(Collectors.toList());

        // 同理创建 encoder
        DSEncoder updateEncoder;
        try {
            updateEncoder = YEncoder.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate encoder", e);
        }

        List<LazyStructReader> lazyStructDecoders = updateDecoders.stream().map(dec -> new LazyStructReader(dec, true)).collect(Collectors.toList());

        LazyStruct currWrite = null;
        LazyStructWriter lazyStructEncoder = new LazyStructWriter(updateEncoder);

        while (true) {
            lazyStructDecoders = lazyStructDecoders.stream()
                    .filter(reader -> reader.current() != null)
                    .sorted((dec1, dec2) -> {
                AbstractStruct s1 = dec1.current();
                AbstractStruct s2 = dec2.current();
                if (s1.getId().getClient() == s2.getId().getClient()) {
                    long clockDiff = s1.getId().getClock() - s2.getId().getClock();
                    return clockDiff != 0 ? Math.toIntExact(clockDiff) : (s1.getClass().equals(s2.getClass()) ? 0 : (s1 instanceof Skip ? 1 : -1));
                }
                return Math.toIntExact(s2.getId().getClient() - s1.getId().getClient());
            }).toList();

            if (lazyStructDecoders.isEmpty()) {
                break;
            }

            LazyStructReader currDecoder = lazyStructDecoders.get(0);
            long firstClient = currDecoder.current().getId().getClient();

            if (currWrite != null) {
                AbstractStruct curr = currDecoder.current();
                boolean iterated = false;

                while (curr != null && curr.getId().getClock() + curr.getLength() <= currWrite.struct.getId().getClock() + currWrite.struct.getLength() && curr.getId().getClient() >= currWrite.struct.getId().getClient()) {
                    curr = currDecoder.next();
                    iterated = true;
                }

                if (curr == null || curr.getId().getClient() != firstClient || (iterated && curr.getId().getClock() > currWrite.struct.getId().getClock() + currWrite.struct.getLength())) {
                    continue;
                }

                if (firstClient != currWrite.struct.getId().getClient()) {
                    writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                    currWrite = new LazyStruct(curr, 0);
                    currDecoder.next();
                } else {
                    if (currWrite.struct.getId().getClock() + currWrite.struct.getLength() < curr.getId().getClock()) {
                        if (currWrite.struct instanceof Skip) {
                            currWrite.struct.length = curr.id.clock + curr.length - currWrite.struct.id.clock;
                        } else {
                            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                            long diff = curr.id.clock - currWrite.struct.id.clock - currWrite.struct.length;
                            Skip skip = new Skip(ID.createId(firstClient, currWrite.struct.getId().getClock() + currWrite.struct.getLength()), diff);
                            currWrite = new LazyStruct(skip, 0);
                        }
                    } else {
                        long diff = currWrite.struct.getId().getClock() + currWrite.struct.getLength() - curr.getId().getClock();
                        if (diff > 0) {
                            if (currWrite.struct instanceof Skip) {
                                currWrite.struct.length -= diff;
                            } else {
                                curr = sliceStruct(curr, diff);
                            }
                        }
                        if (!currWrite.struct.mergeWith(curr)) {
                            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                            currWrite = new LazyStruct(curr, 0);
                            currDecoder.next();
                        }
                    }
                }
            } else {
                currWrite = new LazyStruct(currDecoder.current(), 0);
                currDecoder.next();
            }

            AbstractStruct next;
            while ((next = currDecoder.current()) != null && next.getId().getClient() == firstClient && next.getId().getClock() == currWrite.struct.getId().getClock() + currWrite.struct.getLength() && !(next instanceof Skip)) {
                writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                currWrite = new LazyStruct(next, 0);
                currDecoder.next();
            }
        }

        if (currWrite != null) {
            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
            currWrite = null;
        }
        finishLazyStructWriting(lazyStructEncoder);

        List<DeleteSet> dss = updateDecoders.stream().map(DeleteSet::readDeleteSet).collect(Collectors.toList());

        DeleteSet ds = DeleteSet.mergeDeleteSets(dss);
        DeleteSet.writeDeleteSet(updateEncoder, ds);

        return updateEncoder.toUint8Array();
    }

    static class LazyStruct {
        AbstractStruct struct;
        int offset;

        LazyStruct(AbstractStruct struct, int offset) {
            this.struct = struct;
            this.offset = offset;
        }
    }

    public static byte[] diffUpdateV2(byte[] update, byte[] sv, Class<? extends DSDecoder> YDecoder, Class<? extends DSEncoder> YEncoder) {
        if (YEncoder == null) {
            YEncoder = UpdateEncoderV2.class;
        }
        if (YDecoder == null) {
            YDecoder = UpdateDecoderV2.class;
        }
        Map<Long, Long> state = EncodingUtil.decodeStateVector(sv);
        DSEncoder encoder;
        try {
            encoder = YEncoder.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate encoder", e);
        }
        LazyStructWriter lazyStructWriter = new LazyStructWriter(encoder);
        DSDecoder decoder;
        try {
            decoder = YDecoder.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate decoder", e);
        }
        LazyStructReader reader = new LazyStructReader(decoder, false);
        while (reader.current() != null) {
            AbstractStruct curr = reader.current();
            long currClient = curr.id.client;
            long svClock = state.get(currClient) != null ? state.get(currClient) : 0;
            if (reader.current() instanceof Skip) {
                // the first written struct shouldn't be a skip
                reader.next();
                continue;
            }
            if (curr.id.clock + curr.length > svClock) {
                writeStructToLazyStructWriter(lazyStructWriter, curr, Math.max(svClock - curr.id.clock, 0));
                reader.next();
                while (reader.current() != null && reader.current().id.client == currClient) {
                    writeStructToLazyStructWriter(lazyStructWriter, reader.current(), 0);
                    reader.next();
                }
            } else {
                // read until something new comes up
                while (reader.current() != null && reader.current().id.client == currClient && reader.current().id.clock + reader.current().length <= svClock) {
                    reader.next();
                }
            }
        }
        finishLazyStructWriting(lazyStructWriter);
        // write ds
        DeleteSet ds = DeleteSet.readDeleteSet(decoder);
        DeleteSet.writeDeleteSet(encoder, ds);
        return encoder.toUint8Array();
    }

    public static byte[] diffUpdate(byte[] update, byte[] sv) {
        return diffUpdateV2(update, sv, UpdateDecoderV1.class, UpdateEncoderV2.class);
    }

    public static void flushLazyStructWriter(LazyStructWriter lazyWriter) {
        if (lazyWriter.getWritten() > 0) {
            lazyWriter.getClientStructs().add(new LazyStructWriter.ClientStruct(lazyWriter.getWritten(), Encoder.toUint8Array(lazyWriter.getEncoder().getRestEncoder())));
            lazyWriter.getEncoder().setRestEncoder(Encoder.createEncoder());
            lazyWriter.setWritten(0L);
        }
    }

    public static void writeStructToLazyStructWriter(LazyStructWriter lazyWriter, AbstractStruct struct, long offset) {
        // flush curr if we start another client
        if (lazyWriter.getWritten() > 0 && lazyWriter.getCurrClient() != struct.id.client) {
            flushLazyStructWriter(lazyWriter);
        }
        if (lazyWriter.getWritten() == 0) {
            lazyWriter.setCurrClient(struct.id.client);
            // write next client
            lazyWriter.getEncoder().writeClient(struct.id.client);
            // write startClock
            Encoder.writeVarUint(lazyWriter.getEncoder().getRestEncoder(), struct.id.clock + offset);
        }
        struct.write(lazyWriter.getEncoder(), offset);
        lazyWriter.setWritten(lazyWriter.getWritten() + 1);
    }

    public static void finishLazyStructWriting(LazyStructWriter lazyWriter) {
        flushLazyStructWriter(lazyWriter);

        // this is a fresh encoder because we called flushCurr
        Encoder restEncoder = lazyWriter.getEncoder().getRestEncoder();

        /*
         * Now we put all the fragments together.
         * This works similarly to `writeClientsStructs`
         */

        // write # states that were updated - i.e. the clients
        Encoder.writeVarUint(restEncoder, lazyWriter.getClientStructs().size());

        for (int i = 0; i < lazyWriter.getClientStructs().size(); i++) {
            LazyStructWriter.ClientStruct partStructs = lazyWriter.getClientStructs().get(i);
            /*
             * Works similarly to `writeStructs`
             */
            // write # encoded structs
            Encoder.writeVarUint(restEncoder, partStructs.written);
            // write the rest of the fragment
            Encoder.writeUint8Array(restEncoder, partStructs.restEncoder);
        }
    }

    public static byte[] convertUpdateFormat(byte[] update, Function<AbstractStruct, AbstractStruct> blockTransformer, Class<? extends DSDecoder> decoderClass, Class<? extends DSEncoder> encoderClass) {
        try {
            DSDecoder updateDecoder = decoderClass.getDeclaredConstructor(Decoder.class).newInstance(Decoder.createDecoder(update));
            LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder, false);

            // 创建 encoder 和 lazy writer
            DSEncoder updateEncoder = encoderClass.getDeclaredConstructor().newInstance();
            LazyStructWriter lazyWriter = new LazyStructWriter(updateEncoder);

            // 转换每个结构块并写入
            AbstractStruct curr = lazyDecoder.current();
            while (curr != null) {
                AbstractStruct transformed = blockTransformer.apply(curr);
                writeStructToLazyStructWriter(lazyWriter, transformed, 0);
                curr = lazyDecoder.next();
            }
            // 结束结构写入
            finishLazyStructWriting(lazyWriter);

            // 读取并写入 DeleteSet
            DeleteSet ds = DeleteSet.readDeleteSet(updateDecoder);
            DeleteSet.writeDeleteSet(updateEncoder, ds);

            return updateEncoder.toUint8Array();
        } catch (Exception e) {
            throw new RuntimeException("convertUpdateFormat failed", e);
        }
    }

    public static class ObfuscatorOptions {
        public boolean formatting = true;
        public boolean subDocs = true;
        public boolean yXml = true;

        public ObfuscatorOptions() {
        }

        public ObfuscatorOptions(boolean formatting, boolean subDocs, boolean yXml) {
            this.formatting = formatting;
            this.subDocs = subDocs;
            this.yXml = yXml;
        }
    }

    public static Function<AbstractStruct, AbstractStruct> createObfuscator(ObfuscatorOptions obfuscatorOptions) {
        final boolean formatting = obfuscatorOptions == null || obfuscatorOptions.formatting;
        final boolean subDocs = obfuscatorOptions == null || obfuscatorOptions.subDocs;
        final boolean yXml = obfuscatorOptions == null || obfuscatorOptions.yXml;

        final AtomicInteger counter = new AtomicInteger(0);

        final Map<String, String> mapKeyCache = new HashMap<>();
        final Map<String, String> nodeNameCache = new HashMap<>();
        final Map<Object, Object> formattingKeyCache = new HashMap<>();
        final Map<Object, Object> formattingValueCache = new HashMap<>();
        // end of a formatting range should always be the end of a formatting range
        formattingValueCache.put(null, null);

        return block -> {
            if (block instanceof GC || block instanceof Skip) {
                return block;
            } else if (block instanceof Item item) {
                AbstractContent content = item.getContent();
                if (content instanceof ContentDeleted) {
                    // do nothing
                } else if (content instanceof ContentType && yXml) {
                    AbstractType<?> type = ((ContentType) content).getType();
                    if (type instanceof YXmlElement) {
                        ((YXmlElement) type).setNodeName(nodeNameCache.computeIfAbsent(((YXmlElement) type).getNodeName(), k -> "node-" + counter.get()));
                    }
                    if (type instanceof YXmlHook) {
                        ((YXmlHook) type).setHookName(nodeNameCache.computeIfAbsent(((YXmlHook) type).getHookName(), k -> "hook-" + counter.get()));
                    }
                } else if (content instanceof ContentAny) {
                    Object[] value = new Object[((ContentAny) content).getArr().length];
                    for (int i = 0; i < value.length; i++) {
                        value[i] = counter.get();
                    }
                    ((ContentAny) content).setArr(value);
                } else if (content instanceof ContentBinary) {
                    ((ContentBinary) content).setContent(new byte[]{(byte) counter.get()});
                } else if (content instanceof ContentDoc contentDoc && subDocs) {
                    contentDoc.getDoc().setGuid(String.valueOf(counter.get()));
                    contentDoc.setOpts(new HashMap<>());
                } else if (content instanceof ContentEmbed) {
                    ((ContentEmbed) content).setEmbed(new HashMap<>());
                } else if (content instanceof ContentFormat c && formatting) {
                    c.setKey((String) formattingKeyCache.computeIfAbsent(c.getKey(), k -> String.valueOf(counter.get())));
                    c.setValue(formattingValueCache.computeIfAbsent(c.getValue(), k -> Collections.singletonMap("i", counter.get())));
                } else if (content instanceof ContentJSON) {
                    Object[] value = new Object[((ContentJSON) content).getArr().length];
                    for (int i = 0; i < value.length; i++) {
                        value[i] = counter.get();
                    }
                    ((ContentJSON) content).setArr(value);
                } else if (content instanceof ContentString c) {
                    int len = c.getStr().length();
                    String rep = String.valueOf(counter.get() % 10).repeat(len);
                    c.setStr(rep);
                } else {
                    throw new IllegalStateException("Unexpected content type: " + content.getClass().getName());
                }

                // parentSub 替换
                if (item.getParentSub() != null) {
                    item.setParentSub(mapKeyCache.computeIfAbsent(item.getParentSub(), k -> String.valueOf(counter.get())));
                }

                counter.incrementAndGet();
                return item;
            } else {
                throw new IllegalStateException("Unexpected block type: " + block.getClass().getName());
            }
        };
    }

    public static byte[] obfuscateUpdate(byte[] update, ObfuscatorOptions opts) {
        return convertUpdateFormat(update, createObfuscator(opts), UpdateDecoderV1.class, UpdateEncoderV1.class);
    }

    public static byte[] obfuscateUpdateV2(byte[] update, ObfuscatorOptions opts) {
        return convertUpdateFormat(update, createObfuscator(opts), UpdateDecoderV2.class, UpdateEncoderV2.class);
    }

    public static byte[] convertUpdateFormatV1ToV2(byte[] update) {
        return convertUpdateFormat(update, Function.identity(), UpdateDecoderV2.class, UpdateEncoderV2.class);
    }

    public static byte[] convertUpdateFormatV2ToV1(byte[] update) {
        return convertUpdateFormat(update, Function.identity(), UpdateDecoderV2.class, UpdateEncoderV2.class);
    }
}
