/*
 * Copyright (C) 2018,2020 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaomi.dolby;

import static com.xiaomi.dolby.DolbyAtmos.DsParam;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioManager.AudioPlaybackCallback;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import com.xiaomi.dolby.R;

import java.util.Arrays;
import java.util.List;

public final class DolbyUtils {

    private static final String TAG = "DolbyUtils";
    private static final int EFFECT_PRIORITY = 100;

    private static DolbyUtils mInstance;
    private DolbyAtmos mDolbyAtmos;
    private Context mContext;
    private AudioManager mAudioManager;
    private Handler mHandler = new Handler();
    private boolean mCallbacksRegistered = false;

    // Restore current profile on every media session
    private final AudioPlaybackCallback mPlaybackCallback = new AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            boolean isPlaying = configs.stream().anyMatch(
                    c -> c.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED);
            if (DEBUG) Log.d(TAG, "onPlaybackConfigChanged isPlaying=" + isPlaying);
            if (isPlaying)
                setCurrentProfile();
        }
    };

    // Restore current profile on audio device change
    private final AudioDeviceCallback mAudioDeviceCallback = new AudioDeviceCallback() {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (DEBUG) Log.d(TAG, "onAudioDevicesAdded");
            setCurrentProfile();
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (DEBUG) Log.d(TAG, "onAudioDevicesRemoved");
            setCurrentProfile();
        }
    };

    public DolbyUtils(Context context) {
        mContext = context;
        mDolbyAtmos = new DolbyAtmos(EFFECT_PRIORITY, 0);
        mAudioManager = context.getSystemService(AudioManager.class);
        if (DEBUG) Log.d(TAG, "initalized");
    }

    public static synchronized DolbyUtils getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new DolbyUtils(context);
        }
        return mInstance;
    }

    public void onBootCompleted() {
        if (DEBUG) Log.d(TAG, "onBootCompleted");

        // Restore current profile now and on certain audio changes.
        final boolean dsOn = getDsOn();
        mDolbyAtmos.setEnabled(dsOn);
        registerCallbacks(dsOn);
        if (dsOn)
            setCurrentProfile();

        // Restore speaker virtualizer, because for some reason it isn't
        // enabled automatically at boot.
        final AudioDeviceAttributes device =
                mAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA).get(0);
        final boolean isOnSpeaker = (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        final boolean spkVirtEnabled = getSpeakerVirtualizerEnabled();
        if (DEBUG) Log.d(TAG, "isOnSpeaker=" + isOnSpeaker + " spkVirtEnabled=" + spkVirtEnabled);
        if (isOnSpeaker && spkVirtEnabled) {
            setSpeakerVirtualizerEnabled(false);
            setSpeakerVirtualizerEnabled(true);
            if (DEBUG) Log.d(TAG, "re-enabled speaker virtualizer");
        }
    }

    private void checkEffect() {
        if (!mDolbyAtmos.hasControl()) {
            Log.w(TAG, "lost control, recreating effect");
            mDolbyAtmos.release();
            mDolbyAtmos = new DolbyAtmos(EFFECT_PRIORITY, 0);
        }
    }

    private void setCurrentProfile() {
        if (!getDsOn()) {
            if (DEBUG) Log.d(TAG, "setCurrentProfile: skip, dolby is off");
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        int profile = Integer.parseInt(prefs.getString(
                DolbySettingsFragment.PREF_PROFILE, "0" /*dynamic*/));
        setProfile(profile);
    }

    private void registerCallbacks(boolean register) {
        if (DEBUG) Log.d(TAG, "registerCallbacks(" + register + ") mCallbacksRegistered=" + mCallbacksRegistered);
        if (register && !mCallbacksRegistered) {
            mAudioManager.registerAudioPlaybackCallback(mPlaybackCallback, mHandler);
            mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, mHandler);
            mCallbacksRegistered = true;
        } else if (!register && mCallbacksRegistered) {
            mAudioManager.unregisterAudioPlaybackCallback(mPlaybackCallback);
            mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
            mCallbacksRegistered = false;
        }
    }

    public void setDsOn(boolean on) {
        checkEffect();
        Log.d(TAG, "setDsOn: " + on);
        mDolbyAtmos.setDsOn(on);
        registerCallbacks(on);
        if (on)
            setCurrentProfile();
    }

    public boolean getDsOn() {
        boolean on = mDolbyAtmos.getDsOn();
        Log.d(TAG, "getDsOn: " + on);
        return on;
    }

    public void setProfile(int index) {
        checkEffect();
        Log.d(TAG, "setProfile: " + index);
        mDolbyAtmos.setProfile(index);
    }

    public int getProfile() {
        int profile = mDolbyAtmos.getProfile();
        Log.d(TAG, "getProfile: " + profile);
        return profile;
    }

    public String getProfileName() {
        String profile = Integer.toString(mDolbyAtmos.getProfile());
        List<String> profiles = Arrays.asList(mContext.getResources().getStringArray(
                R.array.dolby_profile_values));
        int profileIndex = profiles.indexOf(profile);
        Log.d(TAG, "getProfileName: profile=" + profile + " index=" + profileIndex);
        return profileIndex == -1 ? null : mContext.getResources().getStringArray(
                R.array.dolby_profile_entries)[profileIndex];
    }

    public void resetProfileSpecificSettings() {
        checkEffect();
        mDolbyAtmos.resetProfileSpecificSettings();
    }

    public void setPreset(String preset) {
        checkEffect();
        int[] gains = Arrays.stream(preset.split(",")).mapToInt(Integer::parseInt).toArray();
        Log.d(TAG, "setPreset: " + Arrays.toString(gains));
        mDolbyAtmos.setDapParameter(DsParam.GEQ, gains);
    }

    public String getPreset() {
        int[] gains = mDolbyAtmos.getDapParameter(DsParam.GEQ);
        Log.d(TAG, "getPreset: " + Arrays.toString(gains));
        String[] preset = Arrays.stream(gains).mapToObj(String::valueOf).toArray(String[]::new);
        return String.join(",", preset);
    }

    public void setBassEnhancerEnabled(boolean enable) {
        checkEffect();
        Log.d(TAG, "setBassEnhancerEnabled: " + enable);
        mDolbyAtmos.setDapParameterBool(DsParam.BASS_ENHANCER, enable);
    }

    public boolean getBassEnhancerEnabled() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.BASS_ENHANCER);
        Log.d(TAG, "getBassEnhancerEnabled: " + enabled);
        return enabled;
    }

    public void setDialogueEnhancerAmount(int amount) {
        checkEffect();
        Log.d(TAG, "setDialogueEnhancerAmount: " + amount);
        mDolbyAtmos.setDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, amount > 0);
        mDolbyAtmos.setDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, amount);
    }

    public int getDialogueEnhancerAmount() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE);
        int amount = enabled ? mDolbyAtmos.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT) : 0;
        Log.d(TAG, "getDialogueEnhancerAmount: enabled=" + enabled + " amount=" + amount);
        return amount;
    }

    public void setStereoWideningAmount(int amount) {
        checkEffect();
        Log.d(TAG, "setStereoWideningAmount: " + amount);
        mDolbyAtmos.setDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, amount > 0);
        mDolbyAtmos.setDapParameterInt(DsParam.STEREO_WIDENING, amount);
    }

    public int getStereoWideningAmount() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER);
        int amount = enabled ? mDolbyAtmos.getDapParameterInt(DsParam.STEREO_WIDENING) : 0;
        Log.d(TAG, "getStereoWideningAmount: enabled=" + enabled + " amount=" + amount);
        return amount;
    }

    public void setVolumeLevelerEnabled(boolean enable) {
        checkEffect();
        Log.d(TAG, "setVolumeLevelerEnabled: " + enable);
        mDolbyAtmos.setDapParameterBool(DsParam.VOLUME_LEVELER, enable);
    }

    public boolean getVolumeLevelerEnabled() {
        boolean enabled = mDolbyAtmos.getDapParameterBool(DsParam.VOLUME_LEVELER);
        Log.d(TAG, "getVolumeLevelerEnabled: " + enabled);
        return enabled;
    }
}
