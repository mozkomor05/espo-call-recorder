package com.wpdistro.callrecorder;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingAccessibilityService extends AccessibilityService {
    private MediaRecorder recorder = null;
    private boolean isRecordStarted = false;
    private File audioFile = null;

    private static RecordingAccessibilityService instance = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.d("callRecorderLog", "onAccessibilityEvent");
    }

    @Override
    public void onInterrupt() {

    }

    public static RecordingAccessibilityService getSharedInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        super.onServiceConnected();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        //Log.d("callRecorderLog", "Shrinidhi: onCreate");
    }

    public void startRecording(Context ctx) {
        try {
            if (isRecordStarted) {
                try {
                    recorder.stop();
                } catch (RuntimeException e) {
                    audioFile.delete();
                }

                releaseMediaRecorder();
                isRecordStarted = false;
            } else {
                if (prepareAudioRecorder(ctx)) {
                    recorder.start();
                    isRecordStarted = true;
                } else {
                    releaseMediaRecorder();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            releaseMediaRecorder();
        }
    }

    public void stopRecording(Context ctx) {
        if (recorder != null && isRecordStarted) {
            releaseMediaRecorder();
            isRecordStarted = false;
        }
    }

    private void releaseMediaRecorder() {
        if (recorder != null) {
            recorder.reset();
            recorder.release();
            recorder = null;
        }
    }

    private boolean prepareAudioRecorder(Context ctx) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/recordings");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            audioFile = File.createTempFile(new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(new Date()), ".m4a", dir);

            recorder = new MediaRecorder();
            recorder.setAudioSource(Build.VERSION.SDK_INT >= 29 ? MediaRecorder.AudioSource.VOICE_RECOGNITION : MediaRecorder.AudioSource.VOICE_COMMUNICATION);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            recorder.setAudioEncodingBitRate(16 * 44100);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(audioFile.getAbsolutePath());

            try {
                recorder.prepare();
            } catch (IllegalStateException | IOException e) {
                releaseMediaRecorder();
                return false;
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public File getLastAudioFile() {
        return audioFile;
    }
}
