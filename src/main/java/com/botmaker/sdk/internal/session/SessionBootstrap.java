package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.Debug;
import com.botmaker.sdk.api.Size;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.session.ActiveSession;
import com.botmaker.shared.session.NestedSession;

/**
 * The bot-runtime producer: the one place that, for an <em>isolated</em> bot, brings up a private nested
 * {@code :N} display, registers it with {@link ActiveSession} (so {@code Mouse}/{@code Keyboard}/{@code Source}
 * all follow it), and launches the target into it. The pilot's equivalent is Studio's {@code NestedSessionLauncher};
 * this is its bot-process twin, reached from the generated bot's {@code Target.start()}.
 *
 * <p><b>Gated, and off by default.</b> Isolation is opt-in via the {@code botmaker.session.isolated} system
 * property (or {@code BOTMAKER_SESSION_ISOLATED} env), so a plain bot keeps today's global {@code :0} behaviour
 * byte-for-byte — {@link #launchIsolated} returns {@code false} and the caller runs its normal launch. The
 * backend is Xephyr (2D) unless {@code botmaker.session.backend=gamescope} selects the hardware-3D path; the
 * display is sized from the project's authored resolution, falling back to {@value #DEFAULT_WIDTH}x{@value
 * #DEFAULT_HEIGHT}. This is deliberately minimal — a persisted project setting and full Studio UX for isolated
 * runs are a follow-up; the gate keeps the seam testable and reversible without touching Studio or the project
 * file format.
 */
public final class SessionBootstrap {

	/** System property (or {@code BOTMAKER_SESSION_ISOLATED} env) that opts a bot into a nested {@code :N} run. */
	public static final String ISOLATED_PROPERTY = "botmaker.session.isolated";
	/** System property selecting the backend when isolated: {@code gamescope} for 3D, else Xephyr. */
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

	/** The requested backend — {@link NestedSession.Backend#GAMESCOPE} only when explicitly selected, else Xephyr. */
	public static NestedSession.Backend backend() {
		String v = System.getProperty(BACKEND_PROPERTY);
		return "gamescope".equalsIgnoreCase(v == null ? null : v.trim())
			? NestedSession.Backend.GAMESCOPE
			: NestedSession.Backend.XEPHYR;
	}

	/** The nested-display options for this bot: the requested backend at the project (or fallback) resolution. */
	public static NestedSession.Options options() {
		int[] size = size();
		return backend() == NestedSession.Backend.GAMESCOPE
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
		NestedSession session = null;
		try {
			session = NestedSession.start(options());
			ActiveSession.set(session);
			session.launch(spec);
			if (session.attached() == null) {
				// Display came up but the game never mapped a window on :N — tear down and fall back to :0.
				Debug.log("[Session] isolated launch: no window appeared on the nested display — falling back to :0");
				ActiveSession.clear();
				session.close();
				return false;
			}
			Debug.log("[Session] isolated: running " + spec.spec() + " on nested " + backend() + " display");
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
