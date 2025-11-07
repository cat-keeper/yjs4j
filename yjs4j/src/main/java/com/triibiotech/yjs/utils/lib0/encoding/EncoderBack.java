package com.triibiotech.yjs.utils.lib0.encoding;//package com.triibiotech.yjs.utils.lib0.encoding;
//
//import com.triibiotech.yjs.utils.lib0.Binary;
//import com.triibiotech.yjs.utils.lib0.MathUtils;
//
//import java.nio.charset.StandardCharsets;
//import java.util.*;
//import java.util.function.Consumer;
//
///**
// * @author zbs
// * @date 2025/7/29 14:07
// **/
//@SuppressWarnings("unused")
//public class Encoder {
//    private int cpos = 0;
//    private byte[] cBuffer = new byte[100];
//    private final List<byte[]> buffers = new ArrayList<>();
//
//    public static Encoder createEncoder() {
//        return new Encoder();
//    }
//
//    public static byte[] encode(Consumer<Encoder> f) {
//        Encoder encoder = createEncoder();
//        f.accept(encoder);
//        return toUint8Array(encoder);
//    }
//
//    public static int length(Encoder encoder) {
//        int len = encoder.cpos;
//        for (byte[] buf : encoder.buffers) {
//            len += buf.length;
//        }
//        return len;
//    }
//
//    public static boolean hasContent(Encoder encoder) {
//        return encoder.cpos > 0 || !encoder.buffers.isEmpty();
//    }
//
//    public static byte[] toUint8Array(Encoder encoder) {
//        byte[] result = new byte[length(encoder)];
//        int curPos = 0;
//        for (byte[] buf : encoder.buffers) {
//            System.arraycopy(buf, 0, result, curPos, buf.length);
//            curPos += buf.length;
//        }
//        System.arraycopy(encoder.cBuffer, 0, result, curPos, encoder.cpos);
//        return result;
//    }
//
//    public static void verifyLen(Encoder encoder, int len) {
//        int bufferLen = encoder.cBuffer.length;
//        if (bufferLen - encoder.cpos < len) {
//            byte[] newBuf = new byte[encoder.cpos];
//            System.arraycopy(encoder.cBuffer, 0, newBuf, 0, encoder.cpos);
//            encoder.buffers.add(newBuf);
//            encoder.cBuffer = new byte[Math.max(bufferLen, len) * 2];
//            encoder.cpos = 0;
//        }
//    }
//
//    public static void write(Encoder encoder, long num) {
//        if (num < 0 || num > 255) {
//            throw new IllegalArgumentException("Only values in [0, 255] allowed for Uint8. Got: " + num);
//        }
//        int bufferLen = encoder.cBuffer.length;
//        if (encoder.cpos == bufferLen) {
//            encoder.buffers.add(encoder.cBuffer);
//            encoder.cBuffer = new byte[bufferLen * 2];
//            encoder.cpos = 0;
//        }
//        // 将数值转换为无符号的 8 位整数存储
//        encoder.cBuffer[encoder.cpos++] = (byte) (num & 0xFF);
//    }
//
//    public static void set(Encoder encoder, int pos, int num) {
//        byte[] buffer = null;
//        for (byte[] b : encoder.buffers) {
//            if (pos < b.length) {
//                buffer = b;
//                break;
//            } else {
//                pos -= b.length;
//            }
//        }
//        if (buffer == null) {
//            buffer = encoder.cBuffer;
//        }
//        buffer[pos] = (byte) num;
//    }
//
//    public static void writeUint8(Encoder encoder, int num) {
//        write(encoder, num);
//    }
//
//    public static void setUint8(Encoder encoder, int pos, int num) {
//        set(encoder, pos, num);
//    }
//
//    public static void writeUint16(Encoder encoder, int num) {
//        write(encoder, num & Binary.BITS8);
//        write(encoder, (num >>> 8) & Binary.BITS8);
//    }
//
//    public static void setUint16(Encoder encoder, int pos, int num) {
//        set(encoder, pos, num & Binary.BITS8);
//        set(encoder, pos + 1, (num >>> 8) & Binary.BITS8);
//    }
//
//    public static void writeUint32(Encoder encoder, long num) {
//        for (int i = 0; i < 4; i++) {
//            write(encoder, (int) (num & Binary.BITS8));
//            num >>>= 8;
//        }
//    }
//
//    public static void writeUint32BigEndian(Encoder encoder, long num) {
//        for (int i = 3; i >= 0; i--) {
//            write(encoder, (int) ((num >>> (8 * i)) & Binary.BITS8));
//        }
//    }
//
//    public static void setUint32(Encoder encoder, int pos, long num) {
//        for (int i = 0; i < 4; i++) {
//            set(encoder, pos + i, (int) (num & Binary.BITS8));
//            num >>>= 8;
//        }
//    }
//
//    public static void writeVarUint(Encoder encoder, long num) {
//        while (num > Binary.BITS7) {
//            write(encoder, Binary.BIT8 | (Binary.BITS7 & num));
//            num = (int) Math.floor(num / 128.0);
//        }
//        write(encoder, Binary.BITS7 & num);
//    }
//
//    public static void writeVarInt(Encoder encoder, long num) {
//        boolean isNegative = MathUtils.isNegativeZero(num);
//        if (isNegative) {
//            num = -num;
//        }
//        write(encoder, (num > Binary.BITS6 ? Binary.BIT8 : 0) |
//                (isNegative ? Binary.BIT7 : 0) |
//                (int) (Binary.BITS6 & num));
//        num = (int) Math.floor(num / 64.0);
//        while (num > 0) {
//            write(encoder, (num > Binary.BITS7 ? Binary.BIT8 : 0) |
//                    (int) (Binary.BITS7 & num));
//            num = (int) Math.floor(num / 128.0);
//        }
//    }
//
//    private static final int MAX_STR_BYTE_SIZE = 10000;
//
//    public static void writeVarString(Encoder encoder, String str) {
//        byte[] utf8Bytes = str.getBytes(StandardCharsets.UTF_8);
//        if (utf8Bytes.length < MAX_STR_BYTE_SIZE) {
//            // Fast path: write byte-by-byte
//            writeVarUint(encoder, utf8Bytes.length);
//            for (byte b : utf8Bytes) {
//                Encoder.write(encoder, b & 0xFF);
//            }
//        } else {
//            Encoder.writeVarUint8Array(encoder, utf8Bytes);
//        }
//    }
//
//    public static void writeTerminatedString(Encoder encoder, String str) {
//        writeTerminatedUint8Array(encoder, str.getBytes(StandardCharsets.UTF_8));
//    }
//
//    public static void writeTerminatedUint8Array(Encoder encoder, byte[] buf) {
//        for (byte b : buf) {
//            int unsignedB = b & 0xFF;
//            if (unsignedB == 0 || unsignedB == 1) {
//                write(encoder, 1);
//            }
//            write(encoder, unsignedB);
//        }
//        write(encoder, 0);
//    }
//
//    public static void writeBinaryEncoder(Encoder encoder, Encoder append) {
//        writeUint8Array(encoder, toUint8Array(append));
//    }
//
//    public static void writeUint8Array(Encoder encoder, byte[] uint8Array) {
//        int bufferLen = encoder.cBuffer.length;
//        int cpos = encoder.cpos;
//        int leftCopyLen = Math.min(bufferLen - cpos, uint8Array.length);
//        int rightCopyLen = uint8Array.length - leftCopyLen;
//
//        System.arraycopy(uint8Array, 0, encoder.cBuffer, cpos, leftCopyLen);
//        encoder.cpos += leftCopyLen;
//
//        if (rightCopyLen > 0) {
//            encoder.buffers.add(encoder.cBuffer);
//            encoder.cBuffer = new byte[Math.max(bufferLen * 2, rightCopyLen)];
//            System.arraycopy(uint8Array, leftCopyLen, encoder.cBuffer, 0, rightCopyLen);
//            encoder.cpos = rightCopyLen;
//        }
//    }
//
//    public static void writeVarUint8Array(Encoder encoder, byte[] uint8Array) {
//        writeVarUint(encoder, uint8Array.length);
//        writeUint8Array(encoder, uint8Array);
//    }
//
//    public static void writeFloat32(Encoder encoder, float num) {
//        int bits = Float.floatToIntBits(num);
//        writeUint32(encoder, bits);
//    }
//
//    public static void writeFloat64(Encoder encoder, double num) {
//        long bits = Double.doubleToLongBits(num);
//        for (int i = 0; i < 8; i++) {
//            write(encoder, (int) ((bits >>> (8 * i)) & 0xFF));
//        }
//    }
//
//    public static void writeBigInt64(Encoder encoder, long num) {
//        for (int i = 0; i < 8; i++) {
//            write(encoder, (int) ((num >>> (8 * i)) & 0xFF));
//        }
//    }
//
//    public static void writeBigUint64(Encoder encoder, long num) {
//        writeBigInt64(encoder, num);
//    }
//
//    /**
//     * 写任何
//     *
//     * @param encoder 编码器
//     * @param data    数据
//     */
//    public static void writeAny(Encoder encoder, Object data) {
//        if (data == null) {
//            // null
//            write(encoder, 126);
//            return;
//        }
//        // string
//        if (data instanceof String) {
//            write(encoder, 119);
//            writeVarString(encoder, (String) data);
//            return;
//        }
//        if (data instanceof Number num) {
//            // integer
//            if (data instanceof Integer || (data instanceof Long && num.longValue() <= Integer.MAX_VALUE && num.longValue() >= Integer.MIN_VALUE)) {
//                write(encoder, 125);
//                writeVarInt(encoder, num.intValue());
//                return;
//            }
//            // float32
//            if (data instanceof Float) {
//                write(encoder, 124);
//                writeFloat32(encoder, num.floatValue());
//                return;
//            }
//            // float64
//            if (data instanceof Double) {
//                write(encoder, 123);
//                writeFloat64(encoder, num.doubleValue());
//                return;
//            }
//            // bigint
//            if (data instanceof Long) {
//                write(encoder, 122);
//                writeBigInt64(encoder, (Long) data);
//                return;
//            }
//        }
//        if (data instanceof Boolean) {
//            // boolean
//            write(encoder, (Boolean) data ? 120 : 121);
//            return;
//        }
//        // object（本项目中使用map存储）
//        if (data instanceof Map<?, ?> map) {
//            if(map.isEmpty()) {
//                write(encoder, 126);
//                return;
//            }
//            write(encoder, 118);
//            Set<?> keys = map.keySet();
//            writeVarUint(encoder, keys.size());
//            for (Object key : keys) {
//                writeVarString(encoder, key.toString());
//                writeAny(encoder, map.get(key));
//            }
//            return;
//        }
//
//        // array
//        if (data instanceof List<?> list) {
//            write(encoder, 117);
//            writeVarUint(encoder, list.size());
//            for (Object item : list) {
//                writeAny(encoder, item);
//            }
//        }
//
//        // Uint8Array（byte[]）
//        if (data instanceof byte[]) {
//            write(encoder, 116);
//            writeVarUint8Array(encoder, (byte[]) data);
//            return;
//        }
//        if (data instanceof Optional<?>) {
//            write(encoder, 127);
//            return;
//        }
//        // undefined
//        write(encoder, 127);
//    }
//
//    private static boolean isFloat32(double num) {
//        return Float.intBitsToFloat(Float.floatToIntBits((float) num)) == num;
//    }
//}
