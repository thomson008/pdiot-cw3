package com.specknet.thingyapp.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.specknet.thingyapp.bluetooth.BluetoothService;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Utils {
    private static long lastProcessedMinute = -1;

    private static ArrayList<Long> frequencyTimestampsRespeck = new ArrayList<>();
    private static ArrayList<Long> frequencyTimestampsPhone = new ArrayList<>();

    public static void processThingyPacket(final byte[] values, BluetoothService bltService) {
        // got rid of respeck version check
        long currentProcessedMinute;
        int currentSequenceNumberInBatch = 0;

        long now = System.currentTimeMillis();
        frequencyTimestampsPhone.add(now);

        currentProcessedMinute = TimeUnit.MILLISECONDS.toMinutes(now);
        Log.i("Debug", "current min = " + currentProcessedMinute);

        if(currentProcessedMinute != lastProcessedMinute && lastProcessedMinute != -1) {
            float currentRespeckFreq = calculateThingyFrequency();
            Log.i("Debug", "current freq = " + currentRespeckFreq);

            float currentPhoneFreq = calculatePhoneFrequency();
            Log.i("Debug", "current freq = " + currentPhoneFreq);
        }

        long mPhoneTimestampLastPacketReceived = -1;
        long mPhoneTimestampCurrentPacketReceived = -1;
        long interpolatedPhoneTimestamp = (long) ((mPhoneTimestampCurrentPacketReceived - mPhoneTimestampLastPacketReceived) *
                (currentSequenceNumberInBatch * 1. / Constants.NUMBER_OF_SAMPLES_PER_BATCH)) + mPhoneTimestampLastPacketReceived;

        // for loop removed as data comes in 1 by 1
        // combine 2 seperate bytes into 1 16 bit integer
        final float x = combineAccelerationBytes(values[0], values[1]);
        final float y = combineAccelerationBytes(values[2], values[3]);
        final float z = combineAccelerationBytes(values[4], values[5]);

        Log.i("Debug", "(x = " + x + ", y = " + y + ", z = " + z + ")");

        Intent liveDataIntent = new Intent(Constants.ACTION_INNER_RESPECK_BROADCAST);
        liveDataIntent.putExtra(Constants.EXTRA_RESPECK_LIVE_X, x);
        liveDataIntent.putExtra(Constants.EXTRA_RESPECK_LIVE_Y, y);
        liveDataIntent.putExtra(Constants.EXTRA_RESPECK_LIVE_Z, z);
        liveDataIntent.putExtra(Constants.EXTRA_INTERPOLATED_TS, interpolatedPhoneTimestamp);

        bltService.sendBroadcast(liveDataIntent);

        lastProcessedMinute = currentProcessedMinute;
    }

    private static float combineAccelerationBytes(Byte lower, Byte upper) {
        short unsigned_lower = (short) (lower & 0xFF);
        short value = (short) ((upper << 8) | unsigned_lower);
        // Done in Nordic-Thingy repo (assuming for scaling)
        return (float) (value) / (1 << 10);
    }

    public static boolean isServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for(ActivityManager.RunningServiceInfo service: manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    public static float calculateThingyFrequency() {
        int num_freq = frequencyTimestampsRespeck.size();

        if(num_freq <= 1) {
            return 0;
        }

        long first_ts = frequencyTimestampsRespeck.get(0);
        long last_ts = frequencyTimestampsRespeck.get(num_freq - 1);

        float samplingFreq = ((num_freq * 1.f) / (last_ts - first_ts)) * 1000.f;
        Log.i("Debug", "samplingFrequencyRespeck = " + samplingFreq);

        frequencyTimestampsRespeck.clear();

        return samplingFreq;
    }

    public static float calculatePhoneFrequency() {
        int num_freq = frequencyTimestampsPhone.size();

        if(num_freq <= 1) {
            return 0;
        }

        long first_ts = frequencyTimestampsPhone.get(0);
        long last_ts = frequencyTimestampsPhone.get(num_freq - 1);

        float samplingFreq = ((num_freq * 1.f) / (last_ts - first_ts)) * 1000.f;
        Log.i("Debug", "samplingFrequencyPhone = " + samplingFreq);

        frequencyTimestampsPhone.clear();

        return samplingFreq;
    }
}
