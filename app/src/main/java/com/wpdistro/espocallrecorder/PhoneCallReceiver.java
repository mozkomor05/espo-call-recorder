package com.wpdistro.espocallrecorder;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public abstract class PhoneCallReceiver extends BroadcastReceiver {
    private static int lastState = TelephonyManager.CALL_STATE_IDLE;
    private static boolean isIncoming;

    @Override
    public void onReceive(Context context, Intent intent) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        onCallStateChanged(context, tm.getCallState());
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
