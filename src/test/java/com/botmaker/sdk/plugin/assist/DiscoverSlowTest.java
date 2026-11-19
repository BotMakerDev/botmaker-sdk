// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Guards;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.LivePlay;
import com.botmaker.sdk.internal.explore.tree.TreeAnswer;
import com.botmaker.sdk.internal.screens.LiveMoves;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.MapWalk;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.web.WebSource;
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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <em>Discover</em> on a fixture site from a blank map (web discovery phase 6c), in the pinned Chromium: the run settles
 * each page off its tree, the answers are written into an in-memory map as the plugin side writes them into the bot,
 * and each resume plays the new map. The three case studies end as one screen {@code /case-studies/{slug}}, the dialog
 * as a screen reached by its button, and the map is walked whole again. Nothing is written to the site and nothing
 * else is reached. Slow: Chromium is downloaded on the first run. No live step.
 */
@Tag("slow")
class DiscoverSlowTest {

    private static Site site;
    private static Site other;
    private static WebSource web;
    private static final List<String> stops = new ArrayList<>();

    @BeforeAll
    static void start() throws IOException {
        other = new Site(Map.of());
        site = new Site(pages("http://elsewhere.test:" + other.port()));
        String agent = Robots.userAgent("test");
        Robots.Fetcher http = Robots.http(agent);
        Robots robots = new Robots(agent, uri -> http.fetch(URI.create("http://127.0.0.1:" + uri.getPort()
                + uri.getPath())));
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
    void aBlankMapEndsWithTheSitesScreensAndIsWalkedAgain(@TempDir Path project) throws InterruptedException {
        web.open();
        AtomicReference<ScreenGraph> graph = new AtomicReference<>(ScreenGraph.of(Map.of()));
        Map<String, Element> elements = new LinkedHashMap<>();
        List<String> targets = new ArrayList<>();
        Journal journal = Journal.of(project);
        LivePlay play = LivePlay.over(graph.get(), web::open);
        AtomicInteger generation = new AtomicInteger();
        ExploreLoop.Reload reload = () -> {
            ScreenGraph now = graph.get();
            play.use(now, null);
            return new ExploreLoop.Reload.Reloaded(generation.incrementAndGet(), now, Guards.with(List.of()),
                    List.of());
        };
        ExploreLoop.Settings settings = new ExploreLoop.Settings(Duration.ofSeconds(2), Duration.ofSeconds(60), 0,
                Duration.ofSeconds(10), Duration.ofMinutes(3), Duration.ofMillis(100), 0);
        ExploreLoop loop = new ExploreLoop(graph.get(), play, reload, Guards.with(List.of()), journal, settings,
                System::currentTimeMillis).handOn(List.of(new TreeAnswer.NewTarget("web:" + web.url())));

        // The plugin side: what each settled stop says, written into the map, then resumed; stopped once it is whole.
        Thread watch = Thread.ofPlatform().daemon().start(() -> {
            Set<String> done = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                TreeAnswerWatch.round(journal, done, (id, answers) -> {
                    TreeAnswers.Planned planned = TreeAnswers.plan(graph.get(), answers, elements);
                    elements.putAll(planned.elements());
                    targets.addAll(planned.targets());
                    planned.skipped().forEach(why -> System.out.println("[test] " + id + " left out " + why));
                    graph.set(applied(graph.get(), planned));
                    return Optional.empty();
                });
                if (whole(graph.get()) && !journal.commandWaiting()) journal.command("stop");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        });
        try {
            loop.run();
        } finally {
            watch.interrupt();
            watch.join();
        }

        ScreenGraph map = graph.get();
        String said = map.screens().entrySet().stream().map(e -> e.getKey() + " " + e.getValue().setup().route()
                + " " + e.getValue().setup().examples() + " " + e.getValue().inputs().keySet()).toList().toString();
        assertTrue(whole(map), said);
        assertEquals(List.of("web:" + web.url()), targets, "the website is the bot's launch target");
        MapScreen kase = caseStudy(map).orElseThrow();
        assertTrue(kase.setup().examples().size() >= 2, said);
        assertEquals(1, map.screens().values().stream().filter(s -> s.setup().route() != null
                && s.setup().route().startsWith("/case-studies/")).count(), "one screen for the three: " + said);
        assertEquals(0, site.writes(), "nothing was written to the site");
        assertEquals(0, other.total(), "nothing else was reached");
        assertEquals(List.of(), stops, "the run was never stopped");

        // The map it wrote is walked whole again.
        web.openPath("/");
        MapWalk.Summary summary = MapWalk.walk(map, new LiveMoves(map, new RunReach(), web::open),
                new MapWalk.Budgets(Duration.ofSeconds(30), Duration.ofMinutes(5), 2), System::currentTimeMillis);
        assertEquals(map.screens().size(), summary.reached(), summary.said());
    }

    /** Whether {@code map} has the case study widened and the dialog reached by its button. */
    private static boolean whole(ScreenGraph map) {
        return caseStudy(map).isPresent() && map.screens().values().stream()
                .anyMatch(s -> s.setup().name().contains("Details") && !s.setup().ways().isEmpty());
    }

    private static Optional<MapScreen> caseStudy(ScreenGraph map) {
        return map.screens().values().stream().filter(s -> "/case-studies/{slug}".equals(s.setup().route()))
                .findFirst();
    }

    /** {@code graph} with {@code planned}'s screens added and its changes made, as the plugin writes them. */
    private static ScreenGraph applied(ScreenGraph graph, TreeAnswers.Planned planned) {
        Map<String, MapScreen> screens = new LinkedHashMap<>(graph.screens());
        for (TreeAnswers.Added added : planned.added()) screens.put(added.constant(), added.screen());
        for (TreeAnswers.Change change : planned.changes()) {
            screens.computeIfPresent(change.constant(), (c, s) -> change.change().apply(s));
        }
        return ScreenGraph.of(screens);
    }

    private static Map<String, String> pages(String otherBase) {
        String head = "<!doctype html><html><head><meta charset=utf-8><title>Fixture</title></head><body>";
        String kase = "<main><h1>%s</h1><p>How %s plays.</p><a href=/case-studies>All case studies</a></main>";
        return Map.of(
                "/", head + "<nav aria-label=Main><a href=/case-studies>Case studies</a> "
                        + "<a href='" + otherBase + "/jobs'>Jobs board</a></nav><main><h1>Agents that play</h1>"
                        + "<button id=open onclick=\"document.getElementById('d').hidden=false\">Open details</button>"
                        + "<form method=post action=/api/save><input name=t aria-label=Title>"
                        + "<button type=submit>Save draft</button></form>"
                        + "<div id=d role=dialog aria-label=Details hidden><h2>Details</h2><p>All about us</p>"
                        + "<button onclick=\"document.getElementById('d').hidden=true\">Close</button></div>"
                        + "</main></body></html>",
                "/case-studies", head + "<main><h1>Case studies</h1><a href=/case-studies/alpha>Alpha</a> "
                        + "<a href=/case-studies/beta>Beta</a> <a href=/case-studies/gamma>Gamma</a> "
                        + "<a href=/>Home</a></main></body></html>",
                "/case-studies/alpha", head + kase.formatted("Alpha", "Alpha") + "</body></html>",
                "/case-studies/beta", head + kase.formatted("Beta", "Beta") + "</body></html>",
                "/case-studies/gamma", head + kase.formatted("Gamma", "Gamma") + "</body></html>");
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
