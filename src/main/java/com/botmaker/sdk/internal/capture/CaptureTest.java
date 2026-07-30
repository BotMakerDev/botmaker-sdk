package com.botmaker.sdk.internal.capture;

import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class CaptureTest {

	// Fetch the correct controller for the current OS (Windows or Linux)
	private static final NativeController osController = NativeControllerFactory.get();

	public static void main(String[] args){
		try {
			testLiveCapture();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void testLiveCapture() throws InterruptedException {
		// DPI awareness is a Windows-specific concept.
		// If you need it on Windows, it should be moved inside the WindowsController constructor.

		System.out.println("You have 5 seconds to bring the window you want to capture to the foreground...");
		Thread.sleep(5000);

		// Cross-platform window fetch
		GenericWindow selectedWindow = osController.getForegroundWindow();

		if (selectedWindow == null) {
			System.out.println("Could not get the foreground window.");
			return;
		}

		System.out.println("Capturing window: " + selectedWindow.getTitle());

		BufferedImage firstCapture = osController.captureWindow(selectedWindow);
		if (firstCapture != null) {
			try {
				ImageIO.write(firstCapture, "png", new File("capture.png"));
				System.out.println("Saved the first capture to capture.png");
			} catch (IOException e) {
				e.printStackTrace();
			}

			ImageDisplay display = new ImageDisplay();
			display.showImage(firstCapture);

			while (true) {
				BufferedImage screenshot = osController.captureWindow(selectedWindow);
				display.showImage(screenshot);
				Thread.sleep(100); // Capture roughly 10 times per second
			}
		}
	}

	private static void waitForKeyPress() {
		// Global keyhooks (GetAsyncKeyState) are extremely OS-dependent.
		// For a cross-platform test CLI, waiting for the user to press Enter is much safer.
		new Scanner(System.in).nextLine();
	}

	private static Point getMousePosition() {
		// Java standard library provides a cross-platform way to get the mouse position!
		return MouseInfo.getPointerInfo().getLocation();
	}

	public static void testCaptureChildWindow() throws InterruptedException {
		System.out.println("You have 5 seconds to bring the window you want to capture to the foreground...");
		Thread.sleep(5000);

		GenericWindow foregroundWindow = osController.getForegroundWindow();
		List<GenericWindow> childWindows = osController.getChildWindows(foregroundWindow);

		System.out.println("Available windows to capture:");
		System.out.println("0: " + foregroundWindow.getTitle() + " (Parent)");
		for (int i = 0; i < childWindows.size(); i++) {
			System.out.println((i + 1) + ": " + childWindows.get(i).getTitle());
		}

		System.out.print("Enter the number of the window to capture: ");
		Scanner scanner = new Scanner(System.in);
		int choice = scanner.nextInt();

		GenericWindow selectedWindow;
		if (choice == 0) {
			selectedWindow = foregroundWindow;
		} else if (choice > 0 && choice <= childWindows.size()) {
			selectedWindow = childWindows.get(choice - 1);
		} else {
			System.out.println("Invalid choice.");
			return;
		}

		BufferedImage image = osController.captureWindow(selectedWindow);
		if (image != null) {
			try {
				ImageIO.write(image, "png", new File("capture.png"));
				System.out.println("Saved the first capture to capture.png");
			} catch (IOException e) {
				e.printStackTrace();
			}

			ImageDisplay display = new ImageDisplay();
			while (true) {
				BufferedImage screenshot = osController.captureWindow(selectedWindow);
				display.showImage(screenshot);
			}
		}
	}

	public static void testPostLeftClick() throws InterruptedException {
		System.out.println("You have 5 seconds to bring the window you want to click on to the foreground...");
		Thread.sleep(5000);

		GenericWindow selectedWindow = osController.getForegroundWindow();

		if (selectedWindow == null) {
			System.out.println("Could not get the foreground window.");
			return;
		}

		System.out.println("Move your mouse to the desired click location, then press ENTER in the console.");
		waitForKeyPress();
		Point mousePos = getMousePosition();

		Rectangle windowRect = selectedWindow.getRect();

		int relativeX = mousePos.x - windowRect.x;
		int relativeY = mousePos.y - windowRect.y;

		System.out.println("Clicking on window: " + selectedWindow.getTitle() + " at relative coordinates (" + relativeX + ", " + relativeY + ") in 3 seconds...");
		Thread.sleep(3000);
		osController.postLeftClick(selectedWindow, relativeX, relativeY);
		System.out.println("Clicked!");
	}

	public static void testClickOnVirtualScreen() {
		System.out.println("Place le curseur, puis appuie sur ENTER dans la console");
		waitForKeyPress();
		Point p = getMousePosition();
		osController.click(p.x, p.y, 1);
		System.out.println("Click envoyé à ("+p.x+", "+p.y+")");
	}
}