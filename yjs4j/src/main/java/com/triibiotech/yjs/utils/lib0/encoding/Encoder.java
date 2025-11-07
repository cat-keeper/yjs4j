package com.triibiotech.yjs.utils.lib0.encoding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.triibiotech.yjs.utils.lib0.Binary.*;

/**
 * @author zbs
 * @date 2025/10/28 10:20
 **/
@SuppressWarnings("unused")
public class Encoder {
    private int cpos = 0;
    private ByteBuffer cbuf;
    private List<byte[]> bufs = new ArrayList<>();

    public Encoder() {
        this.cbuf = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static Encoder createEncoder() {
        return new Encoder();
    }

    public static byte[] encode(Consumer<Encoder> f) {
        Encoder encoder = createEncoder();
        f.accept(encoder);
        return toUint8Array(encoder);
    }

    public static int length(Encoder encoder) {
        int len = encoder.cpos;
        for (byte[] buf : encoder.bufs) {
            len += buf.length;
        }
        return len;
    }

    public static boolean hasContent(Encoder encoder) {
        return encoder.cpos > 0 || !encoder.bufs.isEmpty();
    }

    public static byte[] toUint8Array(Encoder encoder) {
        int totalLen = length(encoder);
        byte[] result = new byte[totalLen];
        int curPos = 0;

        for (byte[] buf : encoder.bufs) {
            System.arraycopy(buf, 0, result, curPos, buf.length);
            curPos += buf.length;
        }

        byte[] currentBuf = new byte[encoder.cpos];
        encoder.cbuf.position(0);
        encoder.cbuf.get(currentBuf, 0, encoder.cpos);
        System.arraycopy(currentBuf, 0, result, curPos, encoder.cpos);

        return result;
    }

    private static void verifyLen(Encoder encoder, int len) {
        int bufferLen = encoder.cbuf.capacity();
        if (bufferLen - encoder.cpos < len) {
            byte[] oldBuf = new byte[encoder.cpos];
            encoder.cbuf.position(0);
            encoder.cbuf.get(oldBuf);
            encoder.bufs.add(oldBuf);

            // 保持小端序
            encoder.cbuf = ByteBuffer.allocate(Math.max(bufferLen, len) * 2)
                    .order(ByteOrder.LITTLE_ENDIAN);
            encoder.cpos = 0;
        }
    }

    public static void write(Encoder encoder, long num) {
        int bufferLen = encoder.cbuf.capacity();
        if (encoder.cpos == bufferLen) {
            byte[] oldBuf = new byte[bufferLen];
            encoder.cbuf.position(0);
            encoder.cbuf.get(oldBuf);
            encoder.bufs.add(oldBuf);

            // 保持小端序
            encoder.cbuf = ByteBuffer.allocate(bufferLen * 2).order(ByteOrder.LITTLE_ENDIAN);
            encoder.cpos = 0;
        }
        encoder.cbuf.put(encoder.cpos++, (byte) num);
    }

    public static void set(Encoder encoder, int pos, int num) {
        byte[] buffer = null;
        int originalPos = pos;

        for (byte[] b : encoder.bufs) {
            if (pos < b.length) {
                buffer = b;
                break;
            } else {
                pos -= b.length;
            }
        }

        if (buffer == null) {
            encoder.cbuf.put(pos, (byte) num);
        } else {
            buffer[pos] = (byte) num;
        }
    }

    public static void writeUint8(Encoder encoder, int num) {
        write(encoder, num);
    }


    public static void setUint8(Encoder encoder, int pos, int num) {
        set(encoder, pos, num);
    }

    public static void writeUint16(Encoder encoder, int num) {
        write(encoder, num & BITS8);
        write(encoder, (num >>> 8) & BITS8);
    }

    public static void setUint16(Encoder encoder, int pos, int num) {
        set(encoder, pos, num & BITS8);
        set(encoder, pos + 1, (num >>> 8) & BITS8);
    }

    public static void writeUint32(Encoder encoder, long num) {
        for (int i = 0; i < 4; i++) {
            write(encoder, (int) (num & BITS8));
            num >>>= 8;
        }
    }

    public static void writeUint32BigEndian(Encoder encoder, long num) {
        for (int i = 3; i >= 0; i--) {
            write(encoder, (int) ((num >>> (8 * i)) & BITS8));
        }
    }

    public static void setUint32(Encoder encoder, int pos, long num) {
        for (int i = 0; i < 4; i++) {
            set(encoder, pos + i, (int) (num & BITS8));
            num >>>= 8;
        }
    }

    public static void writeVarUint(Encoder encoder, long num) {
        while (num > BITS7) {
            write(encoder, BIT8 | (int) (BITS7 & num));
            num = num / 128;
        }
        write(encoder, (int) (BITS7 & num));
    }

    public static void writeVarInt(Encoder encoder, long num) {
        boolean isNegative = num < 0;
        if (isNegative) {
            num = -num;
        }

        write(encoder, (num > BITS6 ? BIT8 : 0) | (isNegative ? BIT7 : 0) | (int) (BITS6 & num));
        num = num / 64;

        while (num > 0) {
            write(encoder, (num > BITS7 ? BIT8 : 0) | (int) (BITS7 & num));
            num = num / 128;
        }
    }

    public static void writeVarString(Encoder encoder, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarUint(encoder, bytes.length);
        writeUint8Array(encoder, bytes);
    }

    public static void writeBinaryEncoder(Encoder encoder, Encoder append) {
        writeUint8Array(encoder, toUint8Array(append));
    }

    public static void writeUint8Array(Encoder encoder, byte[] bytes) {
        int bufferLen = encoder.cbuf.capacity();
        int leftCopyLen = Math.min(bufferLen - encoder.cpos, bytes.length);
        int rightCopyLen = bytes.length - leftCopyLen;

        encoder.cbuf.position(encoder.cpos);
        encoder.cbuf.put(bytes, 0, leftCopyLen);
        encoder.cpos += leftCopyLen;

        if (rightCopyLen > 0) {
            byte[] oldBuf = new byte[bufferLen];
            encoder.cbuf.position(0);
            encoder.cbuf.get(oldBuf);
            encoder.bufs.add(oldBuf);

            encoder.cbuf = ByteBuffer.allocate(Math.max(bufferLen * 2, rightCopyLen))
                    .order(ByteOrder.LITTLE_ENDIAN);
            encoder.cbuf.put(bytes, leftCopyLen, rightCopyLen);
            encoder.cpos = rightCopyLen;
        }
    }

    public static void writeVarUint8Array(Encoder encoder, byte[] bytes) {
        writeVarUint(encoder, bytes.length);
        writeUint8Array(encoder, bytes);
    }

    // writeFloat32和writeFloat64方法会自动使用小端序
    public static void writeFloat32(Encoder encoder, float num) {
        verifyLen(encoder, 4);
        encoder.cbuf.position(encoder.cpos);
        encoder.cbuf.putFloat(num);
        encoder.cpos += 4;
    }

    public static void writeFloat64(Encoder encoder, double num) {
        verifyLen(encoder, 8);
        encoder.cbuf.position(encoder.cpos);
        encoder.cbuf.putDouble(num);
        encoder.cpos += 8;
    }

    public static void writeBigInt64(Encoder encoder, long num) {
        verifyLen(encoder, 8);
        encoder.cbuf.position(encoder.cpos);
        encoder.cbuf.putLong(num);
        encoder.cpos += 8;
    }

    public static void writeAny(Encoder encoder, Object data) {
        if (data == null) {
            // TYPE 126: null
            write(encoder, 126);
        } else if (data instanceof String) {
            // TYPE 119: STRING
            write(encoder, 119);
            writeVarString(encoder, (String) data);
        } else if (data instanceof Number num) {
            double doubleVal = num.doubleValue();
            if (data instanceof java.math.BigInteger) {
                // TYPE 122: BigInt
                write(encoder, 122);
                writeBigInt64(encoder, num.longValue());
            } else if (isInteger(doubleVal) && Math.abs(doubleVal) <= BITS31) {
                // TYPE 125: INTEGER
                write(encoder, 125);
                writeVarInt(encoder, num.longValue());
            } else if (isFloat32(doubleVal)) {
                // TYPE 124: FLOAT32
                write(encoder, 124);
                writeFloat32(encoder, (float) doubleVal);
            } else {
                // TYPE 123: FLOAT64
                write(encoder, 123);
                writeFloat64(encoder, doubleVal);
            }

        } else if (data instanceof Boolean) {
            // boolean
            write(encoder, (Boolean) data ? 120 : 121);
            return;
        } else if (data instanceof byte[]) {
            // TYPE 116: ArrayBuffer
            write(encoder, 116);
            writeVarUint8Array(encoder, (byte[]) data);
        } else if (data instanceof List<?> list) {
            // TYPE 117: Array
            write(encoder, 117);
            writeVarUint(encoder, list.size());
            for (Object item : list) {
                writeAny(encoder, item);
            }
        } else if (data instanceof Map<?, ?> map) {
            // TYPE 118: Object
            write(encoder, 118);
            Set<?> keys = map.keySet();
            writeVarUint(encoder, keys.size());
            for (Object key : keys) {
                writeVarString(encoder, key.toString());
                writeAny(encoder, map.get(key));
            }
        } else {
            // TYPE 127: undefined
            write(encoder, 127);
        }
    }

    private static boolean isInteger(double num) {
        return num == Math.floor(num) && !Double.isInfinite(num);
    }

    private static boolean isFloat32(double num) {
        float floatVal = (float) num;
        return (double) floatVal == num;
    }

}
