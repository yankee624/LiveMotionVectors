package com.example.androidh264codecproject.TFLite;

/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.example.androidh264codecproject.CameraApi;
import com.example.androidh264codecproject.OverlayView;
import com.example.androidh264codecproject.R;
import com.example.androidh264codecproject.boundingBox;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Vector;

public abstract class FaceDetector {
    /** Tag for the {@link Log}. */
    protected static final String TAG = "FaceDetector";

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;

    protected ByteBuffer imgData = null;
    protected int[] argbInts;
    protected byte[][] yuvBytes = new byte[3][];
    protected ByteBuffer rgbFloatBuffer;

    int h,w,c;
    int p;
    boolean use_padding = false;
    int out_h, out_w;
    public Vector<boundingBox> bboxes, bboxes_refined;
    float iou_thresh = 0.4f; // for NMS

    /** holds a gpu delegate */
    GpuDelegate gpuDelegate = null;
    NnApiDelegate nnApiDelegate = null;

    String model_name;

    boundingBox tmp, tmp2; // this is used for NMS
    long startTime, endTime; // this is used for timing measurements

    protected CameraApi a;
    protected OverlayView overlayView;

    protected HandlerThread inferenceThread;
    protected Handler inferenceHandler;

    public boolean isDetecting = false;

    FaceDetector(CameraApi a, OverlayView overlayView, int h, int w, int c, String model_name) throws IOException {
        this.a = a;
        this.h = h;
        this.w = w;
        this.c = c;
        this.model_name = model_name;
        this.overlayView = overlayView;

        startTime = SystemClock.uptimeMillis();
        tfliteModel = loadModelFile(a);

        // gpu not working for unknown reason
//        gpuDelegate = new GpuDelegate();
//        tfliteOptions.addDelegate(gpuDelegate);
        nnApiDelegate = new NnApiDelegate();
        tfliteOptions.addDelegate(nnApiDelegate);

        tflite = new Interpreter(tfliteModel, tfliteOptions);
        endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Created a Tensorflow Lite Face detector. loading time:, "+ Long.toString(endTime - startTime));

        bboxes = new Vector();
        bboxes_refined = new Vector();

        imgData = ByteBuffer.allocateDirect(1 * h * w * c * 4);
        imgData.order(ByteOrder.nativeOrder());
        rgbFloatBuffer = ByteBuffer.allocateDirect(h*w*3 * 4);
        rgbFloatBuffer.order(ByteOrder.nativeOrder());
        argbInts = new int[h*w];


        startInferenceThread();
    }



    public abstract void calc_bboxes();
    public abstract void detect(Image image);


    protected void startInferenceThread() {
        inferenceThread = new HandlerThread("Inference");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());
    }

    protected void stopInferenceThread() {
        inferenceThread.quitSafely();
        try {
            inferenceThread.join();
            inferenceThread = null;
            inferenceHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public int getMaxScoreBBoxIdx(Vector<boundingBox> bboxes){
        int idx = 0;
        float maxScore = 0.0f;
        float score;
        for(int i = 0; i < bboxes.size(); i++){
            score = bboxes.elementAt(i).score;
            if(score >= maxScore){
                maxScore = score;
                idx = i;

            }
        }
        return idx;
    }

    public void nonMaximumSuppression(){
        bboxes_refined.clear();
        int idx;
        int remaining, next_comp_idx;
        while(bboxes.size() > 0){
            idx = getMaxScoreBBoxIdx(bboxes);
            tmp = bboxes.elementAt(idx);
            bboxes.remove(idx);
            remaining = bboxes.size();
            next_comp_idx = 0;
            for(int i = 0; i <remaining; i++){
                tmp2 = bboxes.elementAt(next_comp_idx);
                if(tmp.calcIOU(tmp2) > iou_thresh){
                    bboxes.remove(next_comp_idx);
                }else{
                    next_comp_idx+=1;
                }
            }
            bboxes_refined.add(bboxes_refined.size(), tmp);
        }
    }

    private void recreateInterpreter() {
        if (tflite != null) {
            tflite.close();
            // TODO(b/120679982)
            // gpuDelegate.close();
            tflite = new Interpreter(tfliteModel, tfliteOptions);
        }
    }

    public void useGpu() {
        Log.d(TAG,"Using GPU");
//        if (gpuDelegate == null && GpuDelegateHelper.isGpuDelegateAvailable()) {
//            gpuDelegate = GpuDelegateHelper.createGpuDelegate();
//            tfliteOptions.addDelegate(gpuDelegate);
//            recreateInterpreter();
//        }
    }

    public void useCPU() {
        tfliteOptions.setUseNNAPI(false);
        recreateInterpreter();
    }

    public void useNNAPI() {
        tfliteOptions.setUseNNAPI(true);
        recreateInterpreter();
    }

    public void setNumThreads(int numThreads) {
        tfliteOptions.setNumThreads(numThreads);
        recreateInterpreter();
    }

    /** Closes tflite to release resources. */
    public void close() {
        tflite.close();
        tflite = null;
        tfliteModel = null;
    }


    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    /**
     * Get the name of the model file stored in Assets.
     *
     * @return
     */
    protected abstract String getModelPath();
}
