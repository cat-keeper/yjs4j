package com.triibiotech.yjs.utils.lib0.decoding;

import com.triibiotech.yjs.utils.lib0.Binary;
import com.triibiotech.yjs.utils.lib0.NumberUtils;
import com.triibiotech.yjs.utils.lib0.encoding.Encoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author zbs
 * @date 2025/7/29 14:47
 **/
@SuppressWarnings("unused")
public class Decoder {
    private byte[] arr;
    private int pos;

    public Decoder(byte[] uint8Array) {
        this.arr = uint8Array;
        this.pos = 0;
    }

    public byte[] getArr() {
        return arr;
    }

    public void setArr(byte[] arr) {
        this.arr = arr;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public static Decoder createDecoder(byte[] uint8Array) {
        return new Decoder(uint8Array);
    }

    public static boolean hasContent(Decoder decoder) {
        return decoder.pos != decoder.arr.length;
    }

    public static Decoder clone(Decoder decoder, int newPos) {
        Decoder d = createDecoder(decoder.arr);
        d.pos = newPos;
        return d;
    }

    public static Decoder clone(Decoder decoder) {
        return clone(decoder, decoder.pos);
    }

    public static byte[] readUint8Array(Decoder decoder, int len) {
        byte[] view = new byte[len];
        System.arraycopy(decoder.arr, decoder.pos, view, 0, len);
        decoder.pos += len;
        return view;
    }

    public static byte[] readVarUint8Array(Decoder decoder) {
        return readUint8Array(decoder, (int) readVarUint(decoder));
    }

    public static byte[] readTailAsUint8Array(Decoder decoder) {
        return readUint8Array(decoder, decoder.arr.length - decoder.pos);
    }

    public static int skip8(Decoder decoder) {
        return decoder.pos++;
    }

    public static int readUint8(Decoder decoder) {
        return decoder.arr[decoder.pos++] & 0xFF;
    }

    public static int readUint16(Decoder decoder) {
        int uint = (decoder.arr[decoder.pos] & 0xFF) +
                ((decoder.arr[decoder.pos + 1] & 0xFF) << 8);
        decoder.pos += 2;
        return uint;
    }

    public static long readUint32(Decoder decoder) {
        long uint = (decoder.arr[decoder.pos] & 0xFF) +
                ((decoder.arr[decoder.pos + 1] & 0xFF) << 8) +
                ((decoder.arr[decoder.pos + 2] & 0xFF) << 16) +
                ((long) (decoder.arr[decoder.pos + 3] & 0xFF) << 24);
        decoder.pos += 4;
        return uint & 0xFFFFFFFFL;
    }

    public static long readUint32BigEndian(Decoder decoder) {
        long uint = ((decoder.arr[decoder.pos + 3] & 0xFF)) +
                ((decoder.arr[decoder.pos + 2] & 0xFF) << 8) +
                ((decoder.arr[decoder.pos + 1] & 0xFF) << 16) +
                ((long) (decoder.arr[decoder.pos] & 0xFF) << 24);
        decoder.pos += 4;
        return uint & 0xFFFFFFFFL;
    }

    public static int peekUint8(Decoder decoder) {
        return decoder.arr[decoder.pos] & 0xFF;
    }

    public static int peekUint16(Decoder decoder) {
        return (decoder.arr[decoder.pos] & 0xFF) +
                ((decoder.arr[decoder.pos + 1] & 0xFF) << 8);
    }

    public static long peekUint32(Decoder decoder) {
        return ((decoder.arr[decoder.pos] & 0xFF) +
                ((decoder.arr[decoder.pos + 1] & 0xFF) << 8) +
                ((decoder.arr[decoder.pos + 2] & 0xFF) << 16) +
                ((long) (decoder.arr[decoder.pos + 3] & 0xFF) << 24)) & 0xFFFFFFFFL;
    }

    public static long readVarUint(Decoder decoder) {
        long num = 0;
        long mult = 1;
        int len = decoder.arr.length;
        while (decoder.pos < len) {
            byte r = decoder.arr[decoder.pos++];
            num = num + (r & Binary.BITS7) * mult;
            mult *= 128;
            if (Byte.toUnsignedInt(r) < Binary.BIT8) {
                return num;
            }
            if (num > NumberUtils.MAX_SAFE_INTEGER) {
                throw new RuntimeException("Integer out of Range");
            }
        }
        throw new RuntimeException("Unexpected end of array");
    }

    public static long readVarInt(Decoder decoder) {
        int r = decoder.arr[decoder.pos++] & 0xFF;
        long num = r & Binary.BITS6;
        long mult = 64;
        int sign = (r & Binary.BIT7) > 0 ? -1 : 1;
        if ((r & Binary.BIT8) == 0) {
            return sign * num;
        }
        int len = decoder.arr.length;
        while (decoder.pos < len) {
            r = decoder.arr[decoder.pos++] & 0xFF;
            num = num + (r & Binary.BITS7) * mult;
            mult *= 128;
            if (r < Binary.BIT8) {
                return sign * num;
            }
            if (num > NumberUtils.MAX_SAFE_INTEGER) {
                throw new RuntimeException("Integer out of Range");
            }
        }
        throw new RuntimeException("Unexpected end of array");
    }

    public static long peekVarUint(Decoder decoder) {
        int pos = decoder.pos;
        long s = readVarUint(decoder);
        decoder.pos = pos;
        return s;
    }

    public static long peekVarInt(Decoder decoder) {
        int pos = decoder.pos;
        long s = readVarInt(decoder);
        decoder.pos = pos;
        return s;
    }

    public static String readVarString(Decoder decoder) {
        byte[] bytes = readVarUint8Array(decoder);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static byte[] readTerminatedUint8Array(Decoder decoder) {
        Encoder encoder = Encoder.createEncoder();
        int b;
        while (true) {
            b = readUint8(decoder);
            if (b == 0) {
                return Encoder.toUint8Array(encoder);
            }
            if (b == 1) {
                b = readUint8(decoder);
            }
            Encoder.write(encoder, b);
        }
    }

    public static String readTerminatedString(Decoder decoder) {
        byte[] bytes = readTerminatedUint8Array(decoder);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String peekVarString(Decoder decoder) {
        int pos = decoder.pos;
        String s = readVarString(decoder);
        decoder.pos = pos;
        return s;
    }

    public static ByteBuffer readFromDataView(Decoder decoder, int len) {
        ByteBuffer bb = ByteBuffer.wrap(decoder.arr, decoder.pos, len);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        decoder.pos += len;
        return bb;
    }

    public static float readFloat32(Decoder decoder) {
        return readFromDataView(decoder, 4).getFloat();
    }

    public static double readFloat64(Decoder decoder) {
        return readFromDataView(decoder, 8).getDouble();
    }

    public static long readBigInt64(Decoder decoder) {
        return readFromDataView(decoder, 8).getLong();
    }

    public static long readBigUint64(Decoder decoder) {
        return readFromDataView(decoder, 8).getLong();
    }

    public static Object readAny(Decoder decoder) {
        int type = 127 - readUint8(decoder);
        switch (type) {
            // undefined
            case 0:
                return Optional.empty();
            // null
            case 1:
                return null;
            // integer
            case 2:
                return readVarInt(decoder);
            // float32
            case 3:
                return readFloat32(decoder);
            // float64
            case 4:
                return readFloat64(decoder);
            // bigint
            case 5:
                return readBigInt64(decoder);
            // boolean
            case 6:
                return false;
            // boolean
            case 7:
                return true;
            // string
            case 8:
                return readVarString(decoder);
            // object（此处使用map存储）
            case 9: {
                int len = (int) readVarUint(decoder);
                Map<String, Object> obj = new HashMap<>();
                for (int i = 0; i < len; i++) {
                    String key = readVarString(decoder);
                    obj.put(key, readAny(decoder));
                }
                return obj;
            }
            // array
            case 10: {
                int len = (int) readVarUint(decoder);
                List<Object> arr = new ArrayList<>();
                for (int i = 0; i < len; i++) {
                    arr.add(readAny(decoder));
                }
                return arr;
            }
            // Uint8Array（byte[]）
            case 11:
                return readVarUint8Array(decoder);
            default:
                return null;
        }
    }
}
