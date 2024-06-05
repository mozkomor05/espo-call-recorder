package com.wpdistro.espocallrecorder;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.view.accessibility.AccessibilityEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.d("callRecorderLog", "onAccessibilityEvent");
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onCreate() {
    }
}
