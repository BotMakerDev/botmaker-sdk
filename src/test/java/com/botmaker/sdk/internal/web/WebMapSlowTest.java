// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.web;

import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Mark;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.bot.BotConstants;
import com.botmaker.sdk.internal.screens.LiveMoves;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.tools.Downloads;
import com.botmaker.shared.web.Budget;
import com.botmaker.shared.web.ChromiumInstall;
import com.botmaker.shared.web.Pace;
import com.botmaker.shared.web.Robots;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A website as a launch target, end to end in the pinned Chromium: a screen map written by hand for a fixture site
 * (its home, the case studies, a case study by its route {@code /case-studies/{slug}}, and a dialog over home) walked
 * whole by {@link MapWalk} through {@link WebSource}, then a way off the site and a form's save pressed: the guard
 * stops both, and the other site is never reached. Slow: Chromium is downloaded on the first run. No live step.
 */
@Tag("slow")
class WebMapSlowTest {

    private static Site site;
    private static Site other;
    private static WebSource web;
    private static final List<String> stops = new ArrayList<>();

    @BeforeAll
    static void start() throws IOException {
        other = new Site(Map.of());
        String otherBase = "http://elsewhere.test:" + other.port();
        site = new Site(pages(otherBase));
        String agent = Robots.userAgent("test");
        Robots.Fetcher http = Robots.http(agent);
        Robots robots = new Robots(agent, uri -> http.fetch(URI.create("http://127.0.0.1:" + uri.getPort()
                + uri.getPath())));
        // Paced at once: the pace's waits are the guard's own tests'; this walk would take minutes at a real pace.
        Pace.Clock quick = new Pace.Clock() {
            @Override
            public long nanos() {
                return System.nanoTime();
            }

            @Override
            public void sleep(Duration duration) {
            }
        };
        WebSource.Options options = new WebSource.Options(() -> ChromiumInstall.install(Downloads.Progress.IGNORED),
                List.of("--host-resolver-rules=MAP site.test 127.0.0.1, MAP elsewhere.test 127.0.0.1"), robots,
                new Pace(quick), new Budget(), stops::add);
        web = new WebSource("http://site.test:" + site.port() + "/", options);
        Source.set(web);
    }

    @AfterAll
    static void stop() {
        Source.set(null);
        if (web != null) web.close();
        if (site != null) site.close();
        if (other != null) other.close();
    }

    @Test
    void theMapIsWalkedWholeAndWhatWouldLeaveOrWriteIsStopped() {
        web.open();
        ScreenGraph map = map();
        RunReach reach = new RunReach();
        MapWalk.Summary summary = MapWalk.walk(map, new LiveMoves(map, reach, web::open),
                new MapWalk.Budgets(Duration.ofSeconds(30), Duration.ofMinutes(5), 2), System::currentTimeMillis);
        assertEquals(List.of(), summary.findings(), summary.said());
        assertEquals(map.screens().size(), summary.reached(), summary.said());
        assertEquals(summary.edges(), summary.ok(), summary.said());
        assertTrue(site.hits("/case-studies/alpha") > 0, "the case study was reached by its route");

        // A page no mapped way leads to is opened by its example's address.
        web.openPath("/");
        Map<String, MapScreen> byAddress = new LinkedHashMap<>(map.screens());
        byAddress.put("CASE", screen(Setup.named("Case study").route("/case-studies/{slug}")
                .examples(List.of("/case-studies/beta")), Map.of()));
        ScreenGraph unlinked = ScreenGraph.of(byAddress);
        ScreenWalk.Result opened = new ScreenWalk(unlinked, reach, ScreenWalk.STEP_MS, null).go("CASE", null);
        assertTrue(opened.arrived(), opened.why());
        assertEquals("/case-studies/beta", web.path().orElseThrow());

        web.openPath("/");
        web.click(centre("Jobs board"));
        web.click(centre("Save draft"));
        assertEquals("/", web.path().orElseThrow(), "neither press left home");
        assertEquals(0, other.hits("/jobs"));
        assertEquals(0, other.total(), "nothing reached the other site");
        assertEquals(0, site.writes());
        assertEquals(List.of(), stops, "the run was never stopped");
    }

    /** The middle of the node the tree names {@code label}. */
    private static Point centre(String label) {
        UiNode node = web.tree().orElseThrow().all().stream().filter(n -> n.label().equals(label)).findFirst()
                .orElseThrow(() -> new AssertionError(label + " is not on the page"));
        Rectangle box = node.bounds();
        return new Point(box.x + box.width / 2, box.y + box.height / 2);
    }

    /** The fixture site's map: four screens, each told by its route, the dialog by its element too. */
    private static ScreenGraph map() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", screen(Setup.named("Home").route("/").start(), Map.of(
                "cases", link("Case studies"), "details", button("Open details"))));
        screens.put("CASES", screen(Setup.named("Case studies").route("/case-studies")
                        .from(BotConstants.input("HOME", "cases")).back(BotConstants.input("CASES", "home")),
                Map.of("alpha", link("Alpha"), "home", link("Home"))));
        screens.put("CASE", screen(Setup.named("Case study").route("/case-studies/{slug}")
                        .examples(List.of("/case-studies/alpha")).from(BotConstants.input("CASES", "alpha"))
                        .back(BotConstants.input("CASE", "up")),
                Map.of("up", link("All case studies"))));
        screens.put("DETAILS", screen(Setup.named("Details").route("/").panelOf(BotConstants.screen("HOME"))
                        .from(BotConstants.input("HOME", "details")).back(BotConstants.input("DETAILS", "close")),
                Map.of("close", button("Close")))
                .withMark("dialog", Mark.of(Element.byRole(Role.DIALOG, Words.of("Details")))));
        return ScreenGraph.of(screens);
    }

    private static MapScreen screen(Setup setup, Map<String, Input> inputs) {
        return new MapScreen(setup, new LinkedHashMap<>(inputs));
    }

    private static Button link(String name) {
        return Button.of(Element.byRole(Role.LINK, Words.of(name)));
    }

    private static Button button(String name) {
        return Button.of(Element.byRole(Role.BUTTON, Words.of(name)));
    }

    private static Map<String, String> pages(String otherBase) {
        String head = "<!doctype html><html><head><meta charset=utf-8><title>Fixture</title></head><body>";
        return Map.of(
                "/", head + "<nav aria-label=Main><a href=/case-studies>Case studies</a> "
                        + "<a href='" + otherBase + "/jobs'>Jobs board</a></nav><main><h1>Agents that play</h1>"
                        + "<button id=open onclick=\"document.getElementById('d').hidden=false\">Open details</button>"
                        + "<form method=post action=/api/save><input name=t aria-label=Title>"
                        + "<button type=submit>Save draft</button></form>"
                        + "<div id=d role=dialog aria-label=Details hidden><p>All about us</p>"
                        + "<button onclick=\"document.getElementById('d').hidden=true\">Close</button></div>"
                        + "</main></body></html>",
                "/case-studies", head + "<h1>Case studies</h1><a href=/case-studies/alpha>Alpha</a> "
                        + "<a href=/case-studies/beta>Beta</a> <a href=/>Home</a></body></html>",
                "/case-studies/alpha", head + "<h1>Alpha</h1><a href=/case-studies>All case studies</a></body></html>",
                "/case-studies/beta", head + "<h1>Beta</h1><a href=/case-studies>All case studies</a></body></html>");
    }

    /** A loopback site serving {@code pages}, counting each path's hits and every write. */
    private static final class Site implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        private final AtomicInteger writes = new AtomicInteger();

        Site(Map<String, String> pages) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                hits.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
                if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) {
                    writes.incrementAndGet();
                }
                String page = pages.get(path);
                byte[] body = (page == null ? "not here" : page).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", page == null ? "text/plain" : "text/html");
                exchange.sendResponseHeaders(page == null ? 404 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        int hits(String path) {
            AtomicInteger count = hits.get(path);
            return count == null ? 0 : count.get();
        }

        int total() {
            return hits.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        int writes() {
            return writes.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
