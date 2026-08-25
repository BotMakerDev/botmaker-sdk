package com.botmaker.sdk.internal.authoring;

import com.botmaker.sdk.api.authoring.ProjectSpec;
import com.botmaker.sdk.api.authoring.SdkVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The generated project's {@code pom.xml} — what a bot depends on, and where those artifacts come from.
 *
 * <h2>Why this is the SDK's to write</h2>
 *
 * <p>It is the file that says which SDK the bot compiles against, and every other line in it is there
 * <em>because</em> of the SDK: JNA because shared's native window plumbing needs it at run time, Jackson
 * because a bot may want it, JUnit because a bot may test itself. An editor holding its own copy of that list
 * is an editor deciding what an SDK it did not write requires — which is how a generated project came to
 * declare dependencies nobody had checked against the version it pinned.
 *
 * <h2>An object graph, never a string</h2>
 *
 * <p>The model is assembled and handed to {@link MavenXpp3Writer}. No XML is spelled out here, so a project
 * name with an {@code &} in it cannot produce a pom that does not parse. That costs one dependency —
 * {@code org.apache.maven:maven-model}, declared {@code optional} so it never reaches a bot's classpath.
 */
public final class PomWriter {

    /** Maven coordinate of the BotMaker SDK, published from git tags via JitPack. */
    public static final String SDK_GROUP_ID = "com.github.LiQiyeDev";
    public static final String SDK_ARTIFACT_ID = "botmaker-sdk";

    /** Where a generated project's dependencies are fetched from, in the order they are tried. */
    private static final Map<String, String> REPOSITORIES = new LinkedHashMap<>();

    static {
        REPOSITORIES.put("central", "https://repo.maven.apache.org/maven2/");
        REPOSITORIES.put("jitpack", "https://jitpack.io");
        REPOSITORIES.put("google", "https://dl.google.com/dl/android/maven2/");
    }

    /** One entry of the dependency list every generated project starts with. */
    private record Dep(String groupId, String artifactId, String version, String scope) {}

    private static final List<Dep> DEPENDENCIES = List.of(
            new Dep(SDK_GROUP_ID, SDK_ARTIFACT_ID, null, null),
            new Dep("net.java.dev.jna", "jna", "5.13.0", null),
            new Dep("net.java.dev.jna", "jna-platform", "5.13.0", null),
            // Jackson is here for what the *user* might write, not for anything generated: since the
            // generator bakes values into Parameters as literals, no generated file reads JSON at run time.
            // Taking it away would break a bot that imports it, for a gain nobody would notice.
            new Dep("com.fasterxml.jackson.core", "jackson-databind", "2.15.2", null),
            new Dep("org.junit.jupiter", "junit-jupiter", "5.9.3", "test")
    );

    private PomWriter() {}

    /** The default repositories, {@code id -> url}, in declaration order. */
    static Map<String, String> repositories() {
        return Map.copyOf(REPOSITORIES);
    }

    /**
     * Whether {@code groupId:artifactId} is one of the dependencies every generated project is born with —
     * which is how an editor tells a library the <em>user</em> added from one it did not.
     */
    static boolean isDefault(String groupId, String artifactId) {
        return DEPENDENCIES.stream()
                .anyMatch(d -> d.groupId().equals(groupId) && d.artifactId().equals(artifactId));
    }

    /**
     * The whole {@code pom.xml} as text.
     *
     * <p>{@code spec.sdkPin()} pins the SDK; a blank pin means <em>the SDK doing the generating</em>, which
     * is the only honest default — a generator cannot write a project against a version it is not.
     */
    public static String pom(ProjectSpec spec, SdkVersion version) {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(spec.packageName());
        model.setArtifactId(spec.projectName());
        model.setVersion("0.0.1-SNAPSHOT");
        model.setPackaging("jar");

        Properties props = new Properties();
        props.setProperty("maven.compiler.release", String.valueOf(Runtime.version().feature()));
        props.setProperty("project.build.sourceEncoding", "UTF-8");
        model.setProperties(props);

        REPOSITORIES.forEach((id, url) -> {
            Repository repo = new Repository();
            repo.setId(id);
            repo.setUrl(url);
            model.addRepository(repo);
        });

        String pin = spec.sdkPin().isBlank() ? version.id() : spec.sdkPin().trim();
        for (Dep d : DEPENDENCIES) {
            Dependency dep = new Dependency();
            dep.setGroupId(d.groupId());
            dep.setArtifactId(d.artifactId());
            dep.setVersion(d.version() == null ? pin : d.version());
            if (d.scope() != null) dep.setScope(d.scope());
            model.addDependency(dep);
        }

        StringWriter out = new StringWriter();
        try {
            new MavenXpp3Writer().write(out, model);
        } catch (IOException impossible) {
            // A StringWriter does not fail. Wrapping rather than declaring keeps createProject's throws
            // clause about the filesystem, which is the only IO a caller can do anything about.
            throw new UncheckedIOException(impossible);
        }
        return out.toString();
    }
}
