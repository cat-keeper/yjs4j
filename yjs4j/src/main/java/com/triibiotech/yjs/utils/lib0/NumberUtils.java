package com.triibiotech.yjs.utils.lib0;

/**
 * @author zbs
 * @date 2025/7/29 14:05
 **/
public class NumberUtils {
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;
    public static final long MIN_SAFE_INTEGER = -9007199254740991L;
    public static final int LOWEST_INT32 = 1 << 31;
    public static final int HIGHEST_INT32 = Binary.BITS31;
    public static final long HIGHEST_UINT32 = Binary.BITS32;

    public static boolean isInteger(double num) {
        return Double.isFinite(num) && (int) Math.floor(num) == num;
    }
}
