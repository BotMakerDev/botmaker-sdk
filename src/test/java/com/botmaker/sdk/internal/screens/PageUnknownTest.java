// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageUnknownTest {

    private static final String EMAIL = "someone@example.com";
    private static final String TYPED = "typed-into-the-field";
    private static final UiTree PAGE = UiTree.parse("""
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.android.systemui"
                    content-desc="" bounds="[0,0][1080,60]">
                <node index="0" text="12:45" resource-id="com.android.systemui:id/clock" class="android.widget.TextView"
                      package="com.android.systemui" content-desc="" bounds="[20,0][120,60]"/>
              </node>
              <node index="1" text="" resource-id="" class="android.widget.FrameLayout" package="com.google.android.gms"
                    content-desc="" bounds="[0,60][1080,1920]">
                <node index="0" text="Welcome" resource-id="com.google.android.gms:id/title"
                      class="android.widget.TextView" package="com.google.android.gms" content-desc="" bounds="[40,100][1040,180]"/>
                <node index="1" text="%s" resource-id="" class="android.widget.TextView" package="com.google.android.gms"
                      content-desc="Account %s" bounds="[40,200][1040,260]"/>
                <node index="2" text="Enter the 6-digit code" resource-id="" class="android.view.View"
                      package="com.google.android.gms" content-desc="" bounds="[40,300][1040,360]"/>
                <node index="3" text="%s" resource-id="com.google.android.gms:id/code" class="android.widget.EditText"
                      package="com.google.android.gms" content-desc="" password="false" bounds="[40,400][1040,480]"/>
              </node>
            </hierarchy>
            """.formatted(EMAIL, EMAIL, TYPED));

    @TempDir
    Path run;

    private final AtomicLong clock = new AtomicLong(1_700_000_000_000L);
    private final AtomicInteger reads = new AtomicInteger();

    @AfterEach
    void reset() {
        UnknownPages.use(System::currentTimeMillis, Optional::empty, Optional::empty);
    }

    @Test
    void theMaskingLeavesNoAddressDigitOrFieldValue() {
        PageUnknown page = UnknownPages.masked(PAGE, 1);

        List<String> texts = texts(page.roots());
        assertTrue(texts.contains("Welcome"), texts.toString());
        for (String text : texts) {
            assertFalse(text.contains("@"), text);
            assertFalse(text.chars().anyMatch(Character::isDigit), text);
            assertFalse(text.contains(TYPED), text);
        }
        assertTrue(texts.contains("•".repeat(EMAIL.length())), "the address keeps its length");
    }

    @Test
    void theStatusBarIsDropped() {
        PageUnknown page = UnknownPages.masked(PAGE, 1);

        assertEquals(1, page.roots().size());
        assertEquals("FrameLayout", page.roots().getFirst().className());
        assertTrue(page.roots().getFirst().children().stream().anyMatch(n -> n.field() && n.id().equals("code")));
    }

    @Test
    void aRunLostTenSecondsSavesThePageOnce() throws Exception {
        use();

        UnknownPages.seen(false);
        clock.addAndGet(UnknownPages.LOST.toMillis() - 1);
        UnknownPages.seen(false);
        assertEquals(0, saved());

        clock.addAndGet(1);
        UnknownPages.seen(false);
        clock.addAndGet(UnknownPages.LOST.toMillis());
        UnknownPages.seen(false);
        assertEquals(1, saved());
        String written = Files.readString(only(), StandardCharsets.UTF_8);
        assertFalse(written.contains(EMAIL) || written.contains(TYPED), "the file holds the masked page only");
    }

    @Test
    void aKnownScreenBetweenStartsANewStretch() throws Exception {
        use();

        UnknownPages.seen(false);
        clock.addAndGet(UnknownPages.LOST.toMillis());
        UnknownPages.seen(false);
        UnknownPages.seen(true);
        UnknownPages.seen(false);
        clock.addAndGet(UnknownPages.LOST.toMillis());
        UnknownPages.seen(false);

        assertEquals(2, saved());
    }

    @Test
    void askingTheUserToLookSavesAtOnceAndNotAgain() throws Exception {
        use();

        UnknownPages.now();
        clock.addAndGet(UnknownPages.LOST.toMillis());
        UnknownPages.seen(false);
        UnknownPages.now();

        assertEquals(1, saved());
        assertEquals(1, reads.get(), "the tree is read for the save only");
    }

    @Test
    void aPageReadsBackAsItWasWritten() throws Exception {
        PageUnknown.Node field = new PageUnknown.Node("identifierId", "EditText", "", "Email or phone", true,
                new Rect(10, 20, 300, 40), List.of());
        PageUnknown.Node next = new PageUnknown.Node("", "Button", "Next", "", false, null, List.of());
        PageUnknown page = new PageUnknown(1_700_000_000_000L,
                List.of(new PageUnknown.Node("", "FrameLayout", "", "", false, null, List.of(field, next))));

        Path file = page.writeTo(run).orElseThrow();

        assertEquals("page-unknown-01.json", file.getFileName().toString());
        assertEquals(List.of(page), PageUnknown.readFrom(run));
    }

    @Test
    void aFileEditedByHandIsMaskedAgainAsItIsRead() throws Exception {
        Files.writeString(run.resolve("page-unknown-01.json"),
                "{\"t\":1,\"roots\":[{\"text\":\"someone@example.com\",\"field\":true},{\"label\":\"06 12\"}]}",
                StandardCharsets.UTF_8);

        PageUnknown page = PageUnknown.readFrom(run).getFirst();

        assertEquals("", page.roots().get(0).text());
        assertEquals("•".repeat(5), page.roots().get(1).label());
    }

    @Test
    void aRunKeepsTenPagesAtMost() throws Exception {
        PageUnknown page = new PageUnknown(1, List.of());
        for (int i = 0; i < PageUnknown.KEEP; i++) assertTrue(page.writeTo(run).isPresent());

        assertTrue(page.writeTo(run).isEmpty());
        assertEquals(PageUnknown.KEEP, PageUnknown.readFrom(run).size());
    }

    @Test
    void theRecentRunsPagesAreListedNewestRunFirst() throws Exception {
        Path runs = run.resolve(MissFrames.FOLDER);
        PageUnknown older = new PageUnknown(1, List.of());
        PageUnknown newer = new PageUnknown(2, List.of());
        older.writeTo(runs.resolve("20261115-100000"));
        newer.writeTo(runs.resolve("20261115-110000"));
        newer.writeTo(runs.resolve("20261115-110000"));
        Files.createDirectories(runs.resolve("20261115-120000"));

        List<PageUnknown.Saved> saved = PageUnknown.recent(run, 10);

        assertEquals(List.of("20261115-110000", "20261115-110000", "20261115-100000"),
                saved.stream().map(PageUnknown.Saved::run).toList());
        assertEquals("page-unknown-02.json", saved.get(1).file());
        assertEquals(2, PageUnknown.recent(run, 2).size(), "only the newest two runs are read, one of them empty");
        assertTrue(PageUnknown.recent(run.resolve("none"), 10).isEmpty());
    }

    @Test
    void aGapBetweenLooksStartsTheCountAgain() {
        use();

        UnknownPages.seen(false);
        clock.addAndGet(UnknownPages.LOST.toMillis() + 1);
        UnknownPages.seen(false);

        assertEquals(0, saved(), "lost only while looked at");
    }

    @Test
    void aNewRunStartsAfresh() throws Exception {
        Path second = run.resolve("second");
        AtomicReference<Path> folder = new AtomicReference<>(run);
        UnknownPages.use(clock::get, () -> Optional.of(folder.get()), () -> Optional.of(PAGE));

        UnknownPages.now();
        folder.set(second);
        UnknownPages.now();

        assertEquals(1, saved());
        assertEquals(1, PageUnknown.readFrom(second).size(), "the new run's page is saved too");
    }

    @Test
    void aTreeThatDoesNotReadIsTriedOnceAStretch() {
        UnknownPages.use(clock::get, () -> Optional.of(run), () -> {
            reads.incrementAndGet();
            return Optional.empty();
        });

        UnknownPages.seen(false);
        for (int i = 0; i < 5; i++) {
            clock.addAndGet(UnknownPages.LOST.toMillis());
            UnknownPages.seen(false);
        }

        assertEquals(1, reads.get());
    }

    @Test
    void theNextFileFollowsTheHighestNumberAndAHalfWrittenOneIsSkipped() throws Exception {
        Path one = run.resolve(MissFrames.FOLDER).resolve("20261115-100000");
        Files.createDirectories(one);
        Files.writeString(one.resolve("page-unknown-03.json"), "{\"t\":", StandardCharsets.UTF_8);

        Path file = new PageUnknown(1, List.of()).writeTo(one).orElseThrow();

        assertEquals("page-unknown-04.json", file.getFileName().toString());
        assertEquals(List.of("page-unknown-04.json"),
                PageUnknown.recent(run, 10).stream().map(PageUnknown.Saved::file).toList());
    }

    private void use() {
        UnknownPages.use(clock::get, () -> Optional.of(run), () -> {
            reads.incrementAndGet();
            return Optional.of(PAGE);
        });
    }

    private int saved() {
        return PageUnknown.readFrom(run).size();
    }

    private Path only() throws Exception {
        try (Stream<Path> files = Files.list(run)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json")).findFirst().orElseThrow();
        }
    }

    private static List<String> texts(List<PageUnknown.Node> nodes) {
        List<String> out = new ArrayList<>();
        for (PageUnknown.Node node : nodes) {
            out.add(node.text());
            out.add(node.label());
            out.add(node.id());
            out.addAll(texts(node.children()));
        }
        return out;
    }
}
