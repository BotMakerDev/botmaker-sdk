package com.botmaker.sdk.internal.opencv;

import com.botmaker.shared.capture.ScreenCapture;
import com.botmaker.shared.opencv.OpenCvNative;
import com.botmaker.shared.opencv.OpencvManager;
import com.botmaker.shared.opencv.RawMatch;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;


public class OpencvTest {
    static{
        OpenCvNative.ensureLoaded();
    }
    private static final String BACKGROUNDS_DIR = "images/backgrounds";
    private static final String TEMPLATES_DIR = "images/templates";

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Record screen and save backgrounds
        recordScreenAndSave(10, 2);

        // 2. Extract templates from backgrounds
        extractTemplatesFromBackgrounds(3);

        // 3. Benchmark OpenCV functions
        benchmarkOpenCVFunctions();
    }

    public static void recordScreenAndSave(int screenshotCount, int intervalSeconds) throws InterruptedException, IOException {
        File backgroundsDir = new File(BACKGROUNDS_DIR);
        if (!backgroundsDir.exists()) {
            backgroundsDir.mkdirs();
        }

        for (int i = 0; i < screenshotCount; i++) {
            BufferedImage screenshot = ScreenCapture.captureDesktop();
            if (screenshot != null) {
                File outputFile = new File(BACKGROUNDS_DIR + "/background_" + i + ".png");
                ImageIO.write(screenshot, "png", outputFile);
                System.out.println("Saved background image: " + outputFile.getAbsolutePath());
            }
            TimeUnit.SECONDS.sleep(intervalSeconds);
        }
    }

    public static void extractTemplatesFromBackgrounds(int templatesPerBackground) throws IOException {
        File templatesDir = new File(TEMPLATES_DIR);
        if (!templatesDir.exists()) {
            templatesDir.mkdirs();
        }

        File backgroundsDir = new File(BACKGROUNDS_DIR);
        File[] backgroundFiles = backgroundsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (backgroundFiles == null) {
            System.out.println("No background images found in " + BACKGROUNDS_DIR);
            return;
        }

        Random random = new Random();
        int templateCount = 0;
        for (File backgroundFile : backgroundFiles) {
            BufferedImage backgroundImage = ImageIO.read(backgroundFile);
            for (int i = 0; i < templatesPerBackground; i++) {
                // Random size between 5% and 20% of the background dimensions
                double scale = 0.05 + (0.15 * random.nextDouble());
                int templateWidth = (int) (backgroundImage.getWidth() * scale);
                int templateHeight = (int) (backgroundImage.getHeight() * scale);

                int x = random.nextInt(backgroundImage.getWidth() - templateWidth);
                int y = random.nextInt(backgroundImage.getHeight() - templateHeight);
                BufferedImage templateImage = backgroundImage.getSubimage(x, y, templateWidth, templateHeight);

                File outputFile = new File(TEMPLATES_DIR + "/template_" + templateCount++ + ".png");
                ImageIO.write(templateImage, "png", outputFile);
                System.out.println("Saved template image: " + outputFile.getAbsolutePath());
            }
        }
    }

    public static void benchmarkOpenCVFunctions() {
        File backgroundsDir = new File(BACKGROUNDS_DIR);
        File[] backgroundFiles = backgroundsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        File templatesDir = new File(TEMPLATES_DIR);
        File[] templateFiles = templatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));

        if (backgroundFiles == null || templateFiles == null) {
            System.out.println("No background or template images found.");
            return;
        }

        List<Mat> backgrounds = new ArrayList<>();
        for (File file : backgroundFiles) {
            backgrounds.add(Imgcodecs.imread(file.getAbsolutePath()));
        }

        List<Mat> templates = new ArrayList<>();
        for (File file : templateFiles) {
            templates.add(Imgcodecs.imread(file.getAbsolutePath()));
        }

        System.out.println("--- Benchmarking Results ---");

        // --- Benchmarking findBestMatch ---
        long totalTimeBestMatch = 0;
        int bestMatchCount = 0;
        for (Mat background : backgrounds) {
            for (Mat template : templates) {
                long startTime = System.nanoTime();
                RawMatch result = OpencvManager.findBestMatch(template, background, false);
                long endTime = System.nanoTime();
                totalTimeBestMatch += (endTime - startTime);
                bestMatchCount++;
                if (result != null) {
                    System.out.printf("findBestMatch: Found match with score %.4f%n", result.score());
                }
            }
        }
        System.out.println("findBestMatch average time: " + (totalTimeBestMatch / bestMatchCount) / 1_000_000.0 + " ms");

        long totalTimeMultipleMatches = 0;
        int multipleMatchesCount = 0;
        for (Mat background : backgrounds) {
            for (Mat template : templates) {
                long startTime = System.nanoTime();
                List<RawMatch> results = OpencvManager.findMultipleMatches(template, background, false);
                long endTime = System.nanoTime();
                totalTimeMultipleMatches += (endTime - startTime);
                multipleMatchesCount++;
                for (RawMatch result : results) {
                    System.out.printf("findMultipleMatches: Found match with score %.4f%n", result.score());
                }
            }
        }
        System.out.println("findMultipleMatches average time: " + (totalTimeMultipleMatches / multipleMatchesCount) / 1_000_000.0 + " ms");

        backgrounds.forEach(Mat::release);
        templates.forEach(Mat::release);
    }
}
