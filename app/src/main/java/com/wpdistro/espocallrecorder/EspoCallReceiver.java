package com.wpdistro.espocallrecorder;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EspoCallReceiver extends PhoneCallReceiver {
    private MediaRecorder recorder = null;
    private boolean isRecordStarted = false;
    private File audioFile = null;

    public void postCallActions(Context ctx, boolean isIncoming) {
        EspoAPI espoAPI = EspoAPI.fromConfig(ctx.getSharedPreferences("apiDetails", Context.MODE_PRIVATE));

        if (!audioFile.exists()) {
            return;
        }

        String number = null;
        Uri allCalls = Uri.parse("content://call_log/calls");
        Cursor c = ctx.getContentResolver().query(allCalls, null, null, null, CallLog.Calls.DATE + " DESC");

        try {
            assert c != null;
            if (c.moveToFirst()) {
                number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
            }

            if (number == null) {
                c.close();
                audioFile.delete();
                return;
            }

            espoAPI.checkPhoneNumber(ctx, number, response -> {
                try {
                    Toast.makeText(ctx, R.string.toast_call_being_uploaded, Toast.LENGTH_LONG).show();
                    JSONObject obj = response.getJSONArray("list").getJSONObject(0);
                    long duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    long dateStart = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));

                    espoAPI.uploadCall(ctx, obj, audioFile, isIncoming, duration, dateStart);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }, error -> {
                c.close();
                audioFile.delete();
            });
        } catch (Exception e) {
            c.close();
            audioFile.delete();
        }
    }

    @Override
    protected void onIncomingCallReceived(Context ctx) {
        Toast.makeText(ctx, R.string.toast_call_being_recorded, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onIncomingCallAnswered(Context ctx) {
        this.startRecording(ctx);
    }

    private void startRecording(Context ctx) {
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

    private void stopRecording(Context ctx) {
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
            File dir = new File(ctx.getFilesDir(), "recordings");

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

    @Override
    protected void onIncomingCallEnded(Context ctx) {
        this.stopRecording(ctx);
        this.postCallActions(ctx, true);
    }

    @Override
    protected void onOutgoingCallStarted(Context ctx) {
        this.startRecording(ctx);
    }

    @Override
    protected void onOutgoingCallEnded(Context ctx) {
        this.stopRecording(ctx);
        this.postCallActions(ctx, false);
    }

    @Override
    protected void onMissedCall(Context ctx) {
    }
}
