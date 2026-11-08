package com.example.soteria;

import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;

/** Bound by Android's Telecom while Soteria is the default phone app; forwards calls to {@link CallManager}. */
public class SoteriaInCallService extends InCallService {

    @Override
    public void onCreate() {
        super.onCreate();
        CallManager.get(this).attach(this);
    }

    @Override
    public void onDestroy() {
        CallManager.get(this).detach(this);
        super.onDestroy();
    }

    @Override
    public void onCallAdded(Call call) {
        CallManager.get(this).onCallAdded(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        CallManager.get(this).onCallRemoved(call);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        CallManager.get(this).notifyAudioChanged();
    }
}
