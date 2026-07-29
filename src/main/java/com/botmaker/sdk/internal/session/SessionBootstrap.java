package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.Size;
import com.botmaker.sdk.internal.config.ProjectDefaults;
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
 * <p><b>Gated, and off by default.</b> Isolation is opt-in via the {@code botmaker.session.isolated} system
 * property (or {@code BOTMAKER_SESSION_ISOLATED} env), so a plain bot keeps today's global {@code :0} behaviour
 * byte-for-byte — {@link #launchIsolated} returns {@code false} and the caller runs its normal launch. The
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
	 * Whether this bot was asked to run isolated on a private display. True when {@link #ISOLATED_PROPERTY} is
	 * {@code "true"} (case-insensitive) or the {@code BOTMAKER_SESSION_ISOLATED} env var is set to {@code true}.
	 */
	public static boolean isolationRequested() {
		return isTrue(System.getProperty(ISOLATED_PROPERTY)) || isTrue(System.getenv("BOTMAKER_SESSION_ISOLATED"));
	}

	/**
	 * The backend to isolate {@code spec} on: the {@link #BACKEND_PROPERTY} override when set, else the
	 * kind-driven choice from {@link SessionBackends#preferredBackend(LaunchSpec)} (a game → gamescope for a real
	 * GPU, a plain command → Xephyr). Auto-selecting by kind is what stops a store launcher SIGTRAPping on
	 * Xephyr's software GL.
	 */
	public static NestedSession.Backend backend(LaunchSpec spec) {
		String v = System.getProperty(BACKEND_PROPERTY);
		if (v != null && !v.isBlank()) {
			return "gamescope".equalsIgnoreCase(v.trim())
				? NestedSession.Backend.GAMESCOPE
				: NestedSession.Backend.XEPHYR;
		}
		return SessionBackends.preferredBackend(spec);
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
				// Display came up but the game never mapped a window on :N — tear down and fall back to :0.
				Debug.log("[Session] isolated launch: no window appeared on the nested display — falling back to :0");
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

	private static boolean isTrue(String value) {
		return value != null && "true".equalsIgnoreCase(value.trim());
	}
}
