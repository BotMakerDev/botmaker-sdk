package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.Session;
import com.botmaker.sdk.api.Size;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.launch.LaunchIsolation;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.session.ActiveSession;
import com.botmaker.shared.session.NestedSession;
import com.botmaker.shared.session.SessionBackends;

/**
 * The bot-runtime producer: the one place that, for an <em>isolated</em> bot, brings up a private nested
 * {@code :N} display, registers it with {@link ActiveSession} (so {@code Mouse}/{@code Keyboard}/{@code Source}
 * all follow it), and launches the target into it. The pilot's equivalent is Studio's {@code NestedSessionLauncher};
 * this is its bot-process twin, reached from the generated bot's {@code Target.start()}.
 *
 * <p><b>On by default, with an opt-out.</b> Isolation resolves through one ladder, highest first: an explicit
 * {@link Session} call in bot code → the {@code botmaker.session.isolated} system property →
 * {@code BOTMAKER_SESSION_ISOLATED} → the project's {@code session.isolated} key → {@code true}. Bot code sits at
 * the top so a bot can force its own behaviour on a machine whose environment disagrees; the project key sits at
 * the bottom because it is the weakest statement of intent. When isolation is
 * off, {@link #launchIsolated} returns {@code false} and the caller runs its normal global {@code :0} launch. The
 * backend is <em>auto-selected from the launch kind</em> via {@link SessionBackends} — a game (store launcher /
 * Proton / exe) gets gamescope for a real GPU, a plain command gets Xephyr — with {@code
 * botmaker.session.backend} as an explicit override; the display is sized from the project's authored
 * resolution, falling back to {@value #DEFAULT_WIDTH}x{@value #DEFAULT_HEIGHT}. When a game needs gamescope and
 * it isn't installed, bring-up is declined (loud install hint, graceful {@code :0}) rather than crashing on
 * Xephyr's software GL. The gate keeps the seam testable and reversible without touching the project file
 * format.
 */
public final class SessionBootstrap {

	/** System property (or {@code BOTMAKER_SESSION_ISOLATED} env) that opts a bot into a nested {@code :N} run. */
	public static final String ISOLATED_PROPERTY = "botmaker.session.isolated";
	/**
	 * System property that <em>overrides</em> the auto-selected backend when isolated: {@code gamescope} for 3D,
	 * {@code xephyr} for 2D. When unset the backend is chosen from the launch kind by
	 * {@link SessionBackends#preferredBackend(LaunchSpec)} — a game gets a GPU, a plain command gets Xephyr.
	 */
	public static final String BACKEND_PROPERTY = "botmaker.session.backend";

	/** Nested display size used when the project has no authored resolution. */
	public static final int DEFAULT_WIDTH = 1280;
	public static final int DEFAULT_HEIGHT = 720;

	private SessionBootstrap() {}

	/**
	 * Whether this bot runs isolated on a private display. <b>Default: the project's {@code session.isolated}
	 * setting, which itself defaults to {@code true}</b> ({@link ProjectDefaults#sessionIsolated()}) — so a bot
	 * run anywhere with its project file on the classpath isolates unless it opts out. The {@link
	 * #ISOLATED_PROPERTY} system property (or {@code BOTMAKER_SESSION_ISOLATED} env) is an explicit override in
	 * either direction, winning over the project setting when set to a recognised boolean.
	 */
	public static boolean isolationRequested() {
		Boolean override = Session.override();
		if (override == null) {
			override = overrideBool(System.getProperty(ISOLATED_PROPERTY));
		}
		if (override == null) {
			override = overrideBool(System.getenv("BOTMAKER_SESSION_ISOLATED"));
		}
		return override != null ? override : ProjectDefaults.sessionIsolated();
	}

	/**
	 * The backend to isolate {@code spec} on, highest precedence first: a bot's {@link Session#useBackend} pin,
	 * the {@link #BACKEND_PROPERTY} system property, the project's {@code session.backend} key, else the
	 * kind-driven choice from {@link SessionBackends#preferredBackend(LaunchSpec)} (a game → gamescope for a real
	 * GPU, a plain command → Xephyr). Auto-selecting by kind is what stops a store launcher SIGTRAPping on
	 * Xephyr's software GL.
	 *
	 * <p>Every rung parses through {@link NestedSession.Backend#fromId}, which is total and empty for anything
	 * that isn't a backend id — {@code "auto"} included. That is a fix, not just tidying: the previous
	 * {@code "gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR} mapped an explicit {@code auto} (and any typo)
	 * onto Xephyr, i.e. onto the software GL that crashes the games this whole ladder exists to run.
	 */
	public static NestedSession.Backend backend(LaunchSpec spec) {
		return NestedSession.Backend.fromId(Session.pinnedBackend())
			.or(() -> NestedSession.Backend.fromId(System.getProperty(BACKEND_PROPERTY)))
			.or(() -> NestedSession.Backend.fromId(ProjectDefaults.sessionBackend()))
			.orElseGet(() -> SessionBackends.preferredBackend(spec));
	}

	/** The nested-display options for {@code spec}: its selected backend at the project (or fallback) resolution. */
	public static NestedSession.Options options(LaunchSpec spec) {
		int[] size = size();
		return backend(spec) == NestedSession.Backend.GAMESCOPE
			? NestedSession.Options.gamescope(size[0], size[1])
			: NestedSession.Options.xephyr(size[0], size[1]);
	}

	/** The nested display size from the project's authored resolution, or the default when unset/non-positive. */
	static int[] size() {
		Size r = ProjectDefaults.defaultResolution();
		int w = r == null ? 0 : (int) r.width;
		int h = r == null ? 0 : (int) r.height;
		return new int[]{w > 0 ? w : DEFAULT_WIDTH, h > 0 ? h : DEFAULT_HEIGHT};
	}

	/**
	 * If this bot is isolated, bring up its nested display (once), register it, and launch {@code spec} into it —
	 * returning {@code true} so the caller skips its normal {@code :0} launch. Returns {@code false} when
	 * isolation isn't requested (caller runs its normal launch) <em>or</em> when bring-up fails (graceful
	 * fallback to {@code :0}). Idempotent: once a session is registered, later calls no-op and return {@code true}.
	 */
	public static boolean launchIsolated(LaunchSpec spec) {
		if (!isolationRequested() || spec == null) {
			return false;
		}
		if (ActiveSession.isActive()) {
			// Already brought up and launched on a prior call — don't relaunch.
			return true;
		}
		LaunchIsolation.Verdict verdict = LaunchIsolation.check(spec);
		if (!verdict.isolatable()) {
			// Asked before anything is spawned: a target that cannot be confined would otherwise cost the full
			// window budget and then land on :0 anyway, with a guess as the explanation.
			Debug.log("[Session] isolated launch declined — running on :0. " + verdict.reason());
			return false;
		}
		NestedSession.Backend chosen = backend(spec);
		if (!SessionBackends.isAvailable(chosen)) {
			// The backend this target needs isn't installed. For a game that means gamescope: falling back to
			// Xephyr is exactly the crash we're avoiding, so we run on :0 and tell the user what to install.
			Debug.log("[Session] isolated launch needs " + chosen + " but it isn't installed — running on :0. Hint: "
				+ SessionBackends.installHint(chosen));
			return false;
		}
		NestedSession session = null;
		try {
			session = NestedSession.start(options(spec));
			ActiveSession.set(session);
			session.launch(spec);
			if (session.attached() == null) {
				// Display came up but the game never mapped a window on :N — tear down and fall back to :0. What
				// actually happened is read off the process table rather than guessed at, in the same words
				// Studio uses (shared owns the wording).
				Debug.log("[Session] isolated launch: no window appeared on the nested display — falling back "
					+ "to :0. " + LaunchIsolation.noWindowDiagnosis(spec));
				ActiveSession.clear();
				session.close();
				return false;
			}
			Debug.log("[Session] isolated: running " + spec.spec() + " on nested " + chosen + " display");
			return true;
		} catch (Exception e) {
			String why = e.getMessage() == null ? e.toString() : e.getMessage();
			Debug.log("[Session] isolated bring-up failed: " + why + " — falling back to :0");
			ActiveSession.clear();
			if (session != null) {
				try { session.close(); } catch (Exception ignored) { /* best-effort teardown */ }
			}
			return false;
		}
	}

	/** A recognised boolean override ({@code true}/{@code false} and friends), or {@code null} when unset/unparseable. */
	private static Boolean overrideBool(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return switch (value.trim().toLowerCase()) {
			case "true", "1", "yes", "on" -> Boolean.TRUE;
			case "false", "0", "no", "off" -> Boolean.FALSE;
			default -> null;
		};
	}
}
