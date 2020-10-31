package com.specknet.harapp.live;

import android.util.Log;
import com.specknet.harapp.utils.RespeckData;
import org.tensorflow.lite.Interpreter;

import static com.specknet.harapp.MainActivityKt.associatedAxisLabels;
import static com.specknet.harapp.MainActivityKt.modelFile;

public class Model {
    private static int inputLength = 36;
    private static int outputLength = 15;
    private static Interpreter interpreter = new Interpreter(modelFile);
    private static float[][][] input = new float[1][inputLength][3];
    private static float[][] output = new float[1][outputLength];

    public static String getPrediction(RespeckData respeckData) {
        float accel_x = respeckData.getAccel_x();
        float accel_y = respeckData.getAccel_y();
        float accel_z = respeckData.getAccel_z();

        for (int i = 0; i < inputLength - 1; i++) {
            input[0][i] = input[0][i+1];
        }

        input[0][inputLength - 1] = new float[] {accel_x, accel_y, accel_z};

        return predict();
    }

    private static String predict() {
        interpreter.run(input, output);
        int classNumber = getClassNumber();
        String label = associatedAxisLabels.get(classNumber);
        Log.d("Prediction", label);
        return label;
    }

    private static int getClassNumber() {
        float max = output[0][0];
        int maxIdx = 0;

        for (int i = 1; i < outputLength; i++) {
            if (output[0][i] > max) {
                max = output[0][i];
                maxIdx = i;
            }
        }

        return maxIdx;
    }
}
