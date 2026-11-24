// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.internal.bot.ConstantNames;

import java.util.Locale;
import java.util.Set;

/**
 * Which class names a screen may not have (2026-11-24, the user's call): a screen is {@code static final class X extends
 * Screen}, so {@code X} may never be {@code Screen}, the class it extends, nor a type the {@code Screens} file imports or
 * {@code java.lang} holds, which the name would hide. One rule for every way a screen gets its name: Discover's
 * ({@code TreeAnswer.className}) and the constant an edit gives it ({@code ScreenBook.constantFor}).
 */
public final class ScreenClassNames {

    /** The base class of a screen: a screen of that name would extend itself. */
    private static final String BASE = "Screen";
    /** What an untitled page is called instead of {@code Screen}. */
    private static final String UNTITLED = "Page";
    /** The SDK types a bot's {@code Screens} file imports: a screen class named so would hide them. */
    private static final Set<String> IMPORTED = Set.of("Mark", "Do", "Setup", "Button", "Point", "Params", "SdkValue");

    private ScreenClassNames() {}

    /** Whether a screen class called {@code upperCamel} would extend itself, hide an import or hide a java.lang type. */
    public static boolean clashes(String upperCamel) {
        return BASE.equals(upperCamel) || IMPORTED.contains(upperCamel) || javaLang(upperCamel);
    }

    /** The class name {@code upperCamel} may have: {@code Page} for the base class's, {@code ...Screen} after a clash. */
    public static String className(String upperCamel) {
        if (BASE.equals(upperCamel)) return UNTITLED;
        return clashes(upperCamel) ? upperCamel + BASE : upperCamel;
    }

    /**
     * The constant whose class is free: {@code SCREEN} is {@code PAGE}, {@code MARK} is {@code MARK_SCREEN}; a constant
     * whose class clashes with nothing is returned as it is.
     */
    public static String constant(String constant) {
        String camel = camel(constant);
        String free = className(camel);
        return free.equals(camel) ? constant : ConstantNames.constantFor(free);
    }

    /** {@code SDK_VALUE} as the class name the host writes for it, {@code SdkValue}. */
    private static String camel(String constant) {
        StringBuilder out = new StringBuilder();
        for (String part : constant.split("_")) {
            if (part.isEmpty()) continue;
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static boolean javaLang(String name) {
        try {
            Class.forName("java.lang." + name, false, ScreenClassNames.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
