package com.triibiotech.yjs.utils.lib0;

/**
 * @author zbs
 * @date 2025/7/29 14:05
 **/
public class MathUtils {

    public static boolean isNegativeZero(double n) {
        return n != 0 ? n < 0 : 1.0 / n < 0;
    }
}
