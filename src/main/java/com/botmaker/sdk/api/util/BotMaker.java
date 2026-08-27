package com.botmaker.sdk.api.util;


import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.util.Scanner;

/**
 * Single entry point for basic console IO from a bot.
 *
 * <p>Print and read are exposed as static methods so user code never has to manage a
 * {@link Scanner} or {@code System.out} directly — the visual Print / Read blocks compile down
 * to {@code BotMaker.print(...)} and {@code BotMaker.readX()} calls.
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): all five are offered. The four {@code readX}
 * methods look like an overload family and are not one — they differ in <em>return</em> type, not in
 * arguments, so no one of them can stand in for another and there is no spelling to prefer. This is the
 * {@code Keyboard} shape: a closed set of operations with nothing duplicated.
 */
@Palette(category = "util", categoryLabel = "Utilities", order = 80)
@Hidden("an import target: catalogued so the name resolves, not offered from a menu")
public class BotMaker {

    /** Lazily created so simply referencing print() does not open stdin. */
    private static Scanner scanner;

    private static Scanner scanner() {
        if (scanner == null) {
            scanner = new Scanner(System.in);
        }
        return scanner;
    }

    /**
     * Signals to the BotMaker Studio (which launches the bot) that the program is about to block waiting for
     * stdin, so it can show an input prompt. The marker is wrapped in SOH (0x01) control characters and a
     * newline; the Studio detects and strips it from the console. Harmless when run outside the Studio.
     */
    private static void signalInputExpected(String type) {
        System.out.print("\u0001BM-INPUT:" + type + "\u0001\n");
        System.out.flush();
    }

    /** Prints {@code value} followed by a newline. */
    public static void print(Object value) {
        System.out.println(value);
    }

    /** Reads a full line of text from standard input. */
    public static String readLine() {
        signalInputExpected("line");
        return scanner().nextLine();
    }

    /** Reads the next integer from standard input. */
    public static int readInt() {
        signalInputExpected("int");
        return scanner().nextInt();
    }

    /** Reads the next double from standard input. */
    public static double readDouble() {
        signalInputExpected("double");
        return scanner().nextDouble();
    }

    /** Reads the next boolean from standard input. */
    public static boolean readBoolean() {
        signalInputExpected("boolean");
        return scanner().nextBoolean();
    }
}
