// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import java.util.Locale;

/**
 * What a website's element is, as its page's accessibility tree says: {@code Element.byRole(Role.BUTTON,
 * Texts.GET_STARTED)} (2026-11-18). The roles a person finds a page's parts by, each spelled as the browser spells it
 * ({@link #id}). An app's screen states no role: an element with one is found on a website only.
 */
public enum Role {
    LINK("link", "Link"),
    BUTTON("button", "Button"),
    TAB("tab", "Tab"),
    MENU_ITEM("menuitem", "Menu item"),
    MENU_ITEM_CHECKBOX("menuitemcheckbox", "Menu checkbox"),
    MENU_ITEM_RADIO("menuitemradio", "Menu radio"),
    TEXTBOX("textbox", "Text field"),
    SEARCHBOX("searchbox", "Search field"),
    CHECKBOX("checkbox", "Checkbox"),
    RADIO("radio", "Radio button"),
    SWITCH("switch", "Switch"),
    COMBOBOX("combobox", "Combo box"),
    LISTBOX("listbox", "List box"),
    OPTION("option", "Option"),
    SLIDER("slider", "Slider"),
    SPINBUTTON("spinbutton", "Number field"),
    DIALOG("dialog", "Dialog"),
    ALERT_DIALOG("alertdialog", "Alert dialog"),
    HEADING("heading", "Heading"),
    IMAGE("image", "Image"),
    NAVIGATION("navigation", "Navigation"),
    MAIN("main", "Main"),
    BANNER("banner", "Banner"),
    CONTENT_INFO("contentinfo", "Footer"),
    REGION("region", "Region"),
    FORM("form", "Form"),
    GROUP("group", "Group"),
    ARTICLE("article", "Article"),
    LIST("list", "List"),
    LIST_ITEM("listitem", "List item"),
    TAB_LIST("tablist", "Tab list"),
    TAB_PANEL("tabpanel", "Tab panel"),
    MENU("menu", "Menu"),
    MENU_BAR("menubar", "Menu bar"),
    PARAGRAPH("paragraph", "Paragraph"),
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    Role(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** The browser's own name for the role. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The role the browser calls {@code id}, case aside; {@link #UNKNOWN} for one this version does not know. */
    public static Role fromId(String id) {
        if (id == null) return UNKNOWN;
        if (id.equals("img")) return IMAGE;
        for (Role role : values()) {
            if (role.id.toLowerCase(Locale.ROOT).equals(id.toLowerCase(Locale.ROOT))) return role;
        }
        return UNKNOWN;
    }
}
