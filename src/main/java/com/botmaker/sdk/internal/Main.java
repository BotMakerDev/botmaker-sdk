package com.botmaker.sdk.internal;

import com.botmaker.sdk.internal.capture.ImageDisplay;
import com.botmaker.shared.opencv.OpenCvNative;

import static com.botmaker.sdk.internal.capture.CaptureTest.testLiveCapture;


public class Main {

    static {
        OpenCvNative.ensureLoaded();
    }

    public static void main(String[] args) {
        try {
            ImageDisplay imageDisplay = null;
            testLiveCapture();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void placeholder() {
        System.out.println("[Debug] Placeholder function executed.");
    }
}