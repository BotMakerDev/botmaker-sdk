package com.botmaker.sdk.internal.capture.linux;

import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.linux.LinuxController;
import com.botmaker.shared.capture.NativeControllerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Scanner;

/**
 * Test suite for Linux-specific functionality
 * Run this on a Linux system with X11 to verify the implementation
 */
public class LinuxControllerTest {

	private static final NativeController controller = NativeControllerFactory.get();
	private static final Scanner scanner = new Scanner(System.in);

	public static void main(String[] args) {
		System.out.println("=== Linux Screen Capture Test Suite ===");
		System.out.println();

		// Check if we're running on Linux
		if (!System.getProperty("os.name").toLowerCase().contains("linux")) {
			System.out.println("WARNING: This test is designed for Linux systems.");
			System.out.println("Current OS: " + System.getProperty("os.name"));
			System.out.println();
		}

		// Display test menu
		while (true) {
			System.out.println("\nAvailable Tests:");
			System.out.println("1. Test Get Foreground Window");
			System.out.println("2. Test Enumerate All Windows");
			System.out.println("3. Test Window Capture");
			System.out.println("4. Test Desktop Capture");
			System.out.println("5. Test Mouse Click (Window Relative)");
			System.out.println("6. Test Mouse Click (Screen Absolute)");
			System.out.println("7. Test Child Windows");
			System.out.println("8. Test X11 Environment");
			System.out.println("0. Exit");
			System.out.print("\nSelect test: ");

			int choice = scanner.nextInt();
			scanner.nextLine(); // Consume newline

			try {
				switch (choice) {
					case 1:
						testGetForegroundWindow();
						break;
					case 2:
						testEnumerateAllWindows();
						break;
					case 3:
						testWindowCapture();
						break;
					case 4:
						testDesktopCapture();
						break;
					case 5:
						testMouseClickWindow();
						break;
					case 6:
						testMouseClickScreen();
						break;
					case 7:
						testChildWindows();
						break;
					case 8:
						testX11Environment();
						break;
					case 0:
						System.out.println("Exiting...");
						return;
					default:
						System.out.println("Invalid choice.");
				}
			} catch (Exception e) {
				System.err.println("Test failed with error: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private static void testGetForegroundWindow() throws InterruptedException {
		System.out.println("\n=== Test: Get Foreground Window ===");
		System.out.println("Switch to a window in 3 seconds...");
		Thread.sleep(3000);

		GenericWindow window = controller.getForegroundWindow();

		if (window != null) {
			System.out.println("✓ Foreground window detected:");
			System.out.println("  Title: " + window.getTitle());
			System.out.println("  Position: " + window.getRect());
			System.out.println("  Size: " + window.getRect().width + "x" + window.getRect().height);
		} else {
			System.out.println("✗ Failed to get foreground window");
		}
	}

	private static void testEnumerateAllWindows() {
		System.out.println("\n=== Test: Enumerate All Windows ===");

		List<GenericWindow> windows = controller.getAllWindows();

		System.out.println("Found " + windows.size() + " windows:");
		for (int i = 0; i < windows.size(); i++) {
			GenericWindow window = windows.get(i);
			System.out.println((i + 1) + ". " + window.getTitle());
			System.out.println("   Geometry: " + window.getRect());
		}

		if (windows.isEmpty()) {
			System.out.println("✗ No windows found - check X11 connection");
		} else {
			System.out.println("✓ Successfully enumerated windows");
		}
	}

	private static void testWindowCapture() throws Exception {
		System.out.println("\n=== Test: Window Capture ===");
		System.out.println("Switch to the window you want to capture in 5 seconds...");
		Thread.sleep(5000);

		GenericWindow window = controller.getForegroundWindow();

		if (window == null) {
			System.out.println("✗ No window selected");
			return;
		}

		System.out.println("Capturing: " + window.getTitle());

		long startTime = System.nanoTime();
		BufferedImage image = controller.captureWindow(window);
		long captureTime = System.nanoTime() - startTime;

		if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
			File output = new File("/tmp/linux_window_capture.png");
			ImageIO.write(image, "png", output);
			System.out.println("✓ Window captured successfully!");
			System.out.println("  Image size: " + image.getWidth() + "x" + image.getHeight());
			System.out.println("  Capture time: " + (captureTime / 1_000_000) + "ms");
			System.out.println("  Saved to: " + output.getAbsolutePath());

			// Check if image has actual content
			boolean hasColor = false;
			for (int i = 0; i < 10; i++) {
				int x = (int) (Math.random() * image.getWidth());
				int y = (int) (Math.random() * image.getHeight());
				if ((image.getRGB(x, y) & 0x00FFFFFF) != 0) {
					hasColor = true;
					break;
				}
			}

			if (hasColor) {
				System.out.println("  ✓ Image contains actual content");
			} else {
				System.out.println("  ⚠ Image appears to be all black (possible capture issue)");
			}
		} else {
			System.out.println("✗ Failed to capture window");
		}
	}

	private static void testDesktopCapture() throws Exception {
		System.out.println("\n=== Test: Desktop Capture ===");

		long startTime = System.nanoTime();
		BufferedImage image = com.botmaker.shared.capture.ScreenCapture.captureDesktop();
		long captureTime = System.nanoTime() - startTime;

		if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
			File output = new File("/tmp/linux_desktop_capture.png");
			ImageIO.write(image, "png", output);
			System.out.println("✓ Desktop captured successfully!");
			System.out.println("  Image size: " + image.getWidth() + "x" + image.getHeight());
			System.out.println("  Capture time: " + (captureTime / 1_000_000) + "ms");
			System.out.println("  Saved to: " + output.getAbsolutePath());
		} else {
			System.out.println("✗ Failed to capture desktop");
		}
	}

	private static void testMouseClickWindow() throws InterruptedException {
		System.out.println("\n=== Test: Mouse Click (Window Relative) ===");
		System.out.println("Switch to target window in 3 seconds...");
		Thread.sleep(3000);

		GenericWindow window = controller.getForegroundWindow();

		if (window == null) {
			System.out.println("✗ No window selected");
			return;
		}

		System.out.println("Target window: " + window.getTitle());
		System.out.print("Enter relative X coordinate: ");
		int x = scanner.nextInt();
		System.out.print("Enter relative Y coordinate: ");
		int y = scanner.nextInt();
		scanner.nextLine(); // Consume newline

		System.out.println("Clicking at (" + x + ", " + y + ") in 2 seconds...");
		System.out.println("Note: Your mouse cursor should NOT move (using XTest)");
		Thread.sleep(2000);

		controller.postLeftClick(window, x, y);
		System.out.println("✓ Click sent!");
	}

	private static void testMouseClickScreen() throws InterruptedException {
		System.out.println("\n=== Test: Mouse Click (Screen Absolute) ===");
		System.out.println("Move your mouse to the target position...");
		System.out.println("Press ENTER when ready...");
		scanner.nextLine();

		Point mousePos = MouseInfo.getPointerInfo().getLocation();
		System.out.println("Current mouse position: (" + mousePos.x + ", " + mousePos.y + ")");
		System.out.println("Clicking at this position in 2 seconds...");
		System.out.println("Note: Your mouse cursor should NOT move (using XTest)");
		Thread.sleep(2000);

		controller.click(mousePos.x, mousePos.y, 1);
		System.out.println("✓ Click sent!");
	}

	private static void testChildWindows() throws InterruptedException {
		System.out.println("\n=== Test: Child Windows ===");
		System.out.println("Switch to parent window in 3 seconds...");
		Thread.sleep(3000);

		GenericWindow parent = controller.getForegroundWindow();

		if (parent == null) {
			System.out.println("✗ No parent window selected");
			return;
		}

		System.out.println("Parent window: " + parent.getTitle());

		List<GenericWindow> children = controller.getChildWindows(parent);

		System.out.println("Found " + children.size() + " child windows:");
		for (int i = 0; i < children.size(); i++) {
			GenericWindow child = children.get(i);
			System.out.println((i + 1) + ". " + child.getTitle());
			System.out.println("   Geometry: " + child.getRect());
		}

		if (children.isEmpty()) {
			System.out.println("(No child windows or window has no visible children)");
		} else {
			System.out.println("✓ Successfully enumerated child windows");
		}
	}

	private static void testX11Environment() {
		System.out.println("\n=== Test: X11 Environment ===");

		// Check environment variables
		String display = System.getenv("DISPLAY");
		System.out.println("DISPLAY: " + (display != null ? display : "NOT SET"));

		if (display == null || display.isEmpty()) {
			System.out.println("✗ DISPLAY environment variable not set!");
			System.out.println("  Set it with: export DISPLAY=:0");
		} else {
			System.out.println("✓ DISPLAY is set");
		}

		// Check OS
		String os = System.getProperty("os.name");
		System.out.println("OS: " + os);

		if (os.toLowerCase().contains("linux")) {
			System.out.println("✓ Running on Linux");
		} else {
			System.out.println("⚠ Not running on Linux");
		}

		// Check X11 session type
		String sessionType = System.getenv("XDG_SESSION_TYPE");
		System.out.println("Session Type: " + (sessionType != null ? sessionType : "unknown"));

		if ("x11".equals(sessionType)) {
			System.out.println("✓ Running native X11 session");
		} else if ("wayland".equals(sessionType)) {
			System.out.println("⚠ Running Wayland (XWayland should be available)");
			System.out.println("  Robot capture will still work via XWayland");
		}

		// Test basic X11 connection
		try {
			LinuxController linuxController = new LinuxController();
			GenericWindow window = linuxController.getForegroundWindow();

			if (window != null && !window.getTitle().equals("Mock Linux Window")) {
				System.out.println("✓ X11 connection successful");
				System.out.println("  Active window: " + window.getTitle());
			} else {
				System.out.println("⚠ X11 connection available but limited functionality");
			}
		} catch (Exception e) {
			System.out.println("✗ X11 connection failed: " + e.getMessage());
		}

		// Check library availability
		System.out.println("\nRequired libraries check:");
		checkLibrary("libX11.so");
		checkLibrary("libXtst.so");
	}

	private static void checkLibrary(String libName) {
		try {
			ProcessBuilder pb = new ProcessBuilder("/sbin/ldconfig", "-p");
			Process process = pb.start();
			Scanner libScanner = new Scanner(process.getInputStream());

			boolean found = false;
			while (libScanner.hasNextLine()) {
				if (libScanner.nextLine().contains(libName)) {
					found = true;
					break;
				}
			}
			libScanner.close();

			if (found) {
				System.out.println("  ✓ " + libName + " found");
			} else {
				System.out.println("  ✗ " + libName + " NOT found");
				System.out.println("    Install with: sudo apt install " +
					(libName.contains("X11") ? "libx11-6" : "libxtst-6"));
			}
		} catch (Exception e) {
			System.out.println("  ? " + libName + " (check failed: " + e.getMessage() + ")");
			System.out.println("    Try: find /usr/lib* -name \"" + libName + "*\" 2>/dev/null");
		}
	}
}