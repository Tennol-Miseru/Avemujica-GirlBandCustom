package com.miseru.avemujica.career;

/** Visual-only instrument finish IDs; 0 is the exact pre-1.1.117 texture. */
public final class InstrumentPalette {
    private InstrumentPalette() { }
    public static final String[] NAMES = {"原色", "曜黑", "珍珠白", "绯红", "琥珀橙", "翠绿", "青蓝", "宝蓝", "紫罗兰", "樱粉"};
    public static final int[] RGB = {0x96969B, 0x2A2C39, 0xE3E3DF, 0xCF3647, 0xE78F31,
            0x3BAE66, 0x3EBECB, 0x436FDC, 0x9753D8, 0xE6679D};
    public static final int COUNT = NAMES.length;
    public static boolean valid(int index) { return index >= 0 && index < COUNT; }
    public static int safe(int index) { return valid(index) ? index : 0; }
}
