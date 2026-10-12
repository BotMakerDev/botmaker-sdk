package com.botmaker.sdk.plugin.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the create prompt proposes: the class and the method's name in their own fields, never one text. */
class MethodPickerTest {

    @Test
    void aProposalIsSplitIntoItsClassAndItsMethod() {
        assertEquals(new MethodPicker.Named("Home", "goHome"), MethodPicker.split("Home::goHome"));
        assertEquals(new MethodPicker.Named("", "goHome"), MethodPicker.split(" goHome "));
        assertEquals(new MethodPicker.Named("", ""), MethodPicker.split(null));
    }
}
