package com.wpdistro.espocallrecorder;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.CallLog;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class CallService extends Service {
    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private boolean isIncoming;

    private RecordingAccessibilityService recordingService = null;
    private EspoAPI espoAPI = null;


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        recordingService = RecordingAccessibilityService.getSharedInstance();
        espoAPI = EspoAPI.fromConfig(getSharedPreferences("apiDetails", Context.MODE_PRIVATE));

        final IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.NEW_OUTGOING_CALL");
        filter.addAction("android.intent.action.PHONE_STATE");
        this.registerReceiver(new CallReceiver(), filter);

        return super.onStartCommand(intent, flags, startId);
    }

    public void postCallActions(Context ctx, boolean isIncoming) {
        File audioFile = recordingService.getLastAudioFile();

        if (espoAPI == null) {
            Toast.makeText(ctx, "Espo API not initialized, please open the CallRecorder app.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!audioFile.exists()) {
            return;
        }

        String number = null;
        Uri allCalls = Uri.parse("content://call_log/calls");
        Cursor c = getContentResolver().query(allCalls, null, null, null, CallLog.Calls.DATE + " DESC");

        try {
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
                    JSONObject obj = response.getJSONArray("list").getJSONObject(0);
                    long duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    long dateStart = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));

                    espoAPI.uploadCall(ctx, obj, isIncoming, duration, dateStart, audioFile);
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

    public abstract class PhoneCallReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (recordingService == null) {
                recordingService = RecordingAccessibilityService.getSharedInstance();

                if (recordingService == null) {
                    Toast.makeText(context, "CallRecord ERROR: make sure the accessibility service is enabled.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            if (intent.getAction().equals("android.intent.action.NEW_OUTGOING_CALL")) {
//                savedNumber = intent.getStringExtra("android.intent.extra.PHONE_NUMBER");
            } else {
                String stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int state = TelephonyManager.CALL_STATE_IDLE;

                if (stateStr.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                    state = TelephonyManager.CALL_STATE_OFFHOOK;
                } else if (stateStr.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                    state = TelephonyManager.CALL_STATE_RINGING;
                }

                onCallStateChanged(context, state);
            }
        }

        protected abstract void onIncomingCallReceived(Context ctx);

        protected abstract void onIncomingCallAnswered(Context ctx);

        protected abstract void onIncomingCallEnded(Context ctx);

        protected abstract void onOutgoingCallStarted(Context ctx);

        protected abstract void onOutgoingCallEnded(Context ctx);

        protected abstract void onMissedCall(Context ctx);

        public void onCallStateChanged(Context context, int state) {
            if (lastState == state) {
                return;
            }

            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    isIncoming = true;
                    onIncomingCallReceived(context);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                        isIncoming = false;
                        onOutgoingCallStarted(context);

                    } else {
                        isIncoming = true;
                        onIncomingCallAnswered(context);
                    }

                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        onMissedCall(context);
                    } else if (isIncoming) {
                        onIncomingCallEnded(context);
                    } else {
                        onOutgoingCallEnded(context);
                    }
                    break;
            }
            lastState = state;
        }

    }

    public class CallReceiver extends PhoneCallReceiver {

        @Override
        protected void onIncomingCallReceived(Context ctx) {
            Toast.makeText(ctx, "Call is automatically being recorded. All private call recordings will be deleted!", Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onIncomingCallAnswered(Context ctx) {
            recordingService.startRecording(ctx);
        }

        @Override
        protected void onIncomingCallEnded(Context ctx) {
            recordingService.stopRecording(ctx);
            postCallActions(ctx, true);
        }

        @Override
        protected void onOutgoingCallStarted(Context ctx) {
            recordingService.startRecording(ctx);
        }

        @Override
        protected void onOutgoingCallEnded(Context ctx) {
            recordingService.stopRecording(ctx);
            postCallActions(ctx, false);
        }

        @Override
        protected void onMissedCall(Context ctx) {
        }
    }

}
