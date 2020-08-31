package com.example.androidh264codecproject.TFLite;

import android.app.Activity;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import com.example.androidh264codecproject.CameraApi;
import com.example.androidh264codecproject.OverlayView;
import com.example.androidh264codecproject.R;
import com.example.androidh264codecproject.boundingBox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class FaceDetectorRetina extends FaceDetector {
    protected final String TAG = "FaceDetectorRetina";


    public FaceDetectorRetina(CameraApi a, OverlayView overlayView, int h, int w, int c, float s, String model_name) throws IOException {
        super(a, overlayView, h, w, c, model_name);

        Log.d(TAG, String.format("Creating RetinaFaceDetector, input: %dx%d",h,w));

        scale = s;

        h_stride32 = (int)Math.ceil((double)h/32);
        w_stride32 = (int)Math.ceil((double)w/32);

        h_stride16 = (int)Math.ceil((double)h/16);
        w_stride16 = (int)Math.ceil((double)w/16);

        h_stride8 = (int)Math.ceil((double)h/8);
        w_stride8 = (int)Math.ceil((double)w/8);

        this.out_h = h/32;
        this.out_w = w/32;

        score_stride8 = new float[1][h_stride8][w_stride8][4];
        bbox_deltas_stride8 = new float[1][h_stride8][w_stride8][8];
        landmark_deltas_stride8 = new float[1][h_stride8][w_stride8][20];

        score_stride16 = new float[1][h_stride16][w_stride16][4];
        bbox_deltas_stride16 = new float[1][h_stride16][w_stride16][8];
        landmark_deltas_stride16 = new float[1][h_stride16][w_stride16][20];

        score_stride32 = new float[1][h_stride32][w_stride32][4];
        bbox_deltas_stride32 = new float[1][h_stride32][w_stride32][8];
        landmark_deltas_stride32 = new float[1][h_stride32][w_stride32][20];
    }


    @Override
    protected String getModelPath() {
        if(use_padding) return String.format("RetinaFace%s-%dx%d.tflite",model_name,h+2*p,w+2*p);
        else return String.format("RetinaFace%s-%dx%d.tflite",model_name,h,w);
    }

    float scale;

    int num_anchors_stride32 = 2;
    int h_stride32;
    int w_stride32;
    float[][] anchors_fpn_stride32 = {{-248.f, -248.f, 263.f, 263.f}, {-120.f, -120.f,  135.f,  135.f}};

    int num_anchors_stride16 = 2;
    int h_stride16;
    int w_stride16;
    float[][] anchors_fpn_stride16 = {{-56.f, -56.f,  71.f,  71.f}, {-24.f, -24.f,  39.f,  39.f}};

    int num_anchors_stride8 = 2;
    int h_stride8;
    int w_stride8;
    float[][] anchors_fpn_stride8 = {{-8.f, -8.f, 23.f, 23.f}, {0.f,  0.f, 15.f, 15.f}};

    float prob_thresh = 0.2f;

    float[][][][] score_stride8, bbox_deltas_stride8, landmark_deltas_stride8;
    float[][][][] score_stride16, bbox_deltas_stride16, landmark_deltas_stride16;
    float[][][][] score_stride32, bbox_deltas_stride32, landmark_deltas_stride32;

    //need anchors_fpn -> anchors
    @Override
    public void calc_bboxes(){
        float x1, y1, x2, y2;
        float width, height, ctr_x, ctr_y;
        float pred_w, pred_h, pred_ctr_x, pred_ctr_y;
        float score1, score2;
        float score;
        int stride;
        float[] landmarks = new float[10];

        ///*
        //stride 32
        stride = 32;
        for(int i = 0; i < h_stride32; i++){
            for(int j = 0; j < w_stride32; j++){
                for(int k = 0; k < num_anchors_stride32; k++) {
                    score1 = score_stride32[0][i][j][k];
                    score2 = score_stride32[0][i][j][num_anchors_stride32+k];
                    score = (float)Math.exp((double)score2)/((float)Math.exp((double)score1)+(float)Math.exp((double)score2));
                    if (score >= prob_thresh){
                        width = anchors_fpn_stride32[k][2] - anchors_fpn_stride32[k][0] + 1;
                        height = anchors_fpn_stride32[k][3] - anchors_fpn_stride32[k][1] + 1;
                        ctr_x = j*stride + anchors_fpn_stride32[k][0] + 0.5f*(width-1);
                        ctr_y = i*stride + anchors_fpn_stride32[k][1] + 0.5f*(height-1);

                        pred_ctr_x = bbox_deltas_stride32[0][i][j][4*k] * width + ctr_x;
                        pred_ctr_y = bbox_deltas_stride32[0][i][j][4*k+1] * height + ctr_y;
                        pred_w = (float)Math.exp((double)bbox_deltas_stride32[0][i][j][4*k+2]) * width;
                        pred_h = (float)Math.exp((double)bbox_deltas_stride32[0][i][j][4*k+3]) * height;

                        x1 = pred_ctr_x - 0.5f * (pred_w - 1);
                        y1 = pred_ctr_y - 0.5f * (pred_h - 1);
                        x2 = pred_ctr_x + 0.5f * (pred_w - 1);
                        y2 = pred_ctr_y + 0.5f * (pred_h - 1);

                        //calc landmarks
                        for(int l = 0; l < 5; l++){
                            landmarks[2*l] = landmark_deltas_stride32[0][i][j][10*k+2*l]*width + ctr_x;
                            landmarks[2*l+1] = landmark_deltas_stride32[0][i][j][10*k+2*l+1]*height + ctr_y;
                        }
                        bboxes.add(bboxes.size(), new boundingBox(scale*x1, scale*y1, scale*x2, scale*y2, score, landmarks));
                    }
                }
            }
        }

        //stride 16
        stride = 16;
        for(int i = 0; i < h_stride16; i++){
            for(int j = 0; j < w_stride16; j++){
                for(int k = 0; k < num_anchors_stride16; k++) {
                    score1 = score_stride16[0][i][j][k];
                    score2 = score_stride16[0][i][j][num_anchors_stride32+k];
                    score = (float)Math.exp((double)score2)/((float)Math.exp((double)score1)+(float)Math.exp((double)score2));
                    if (score >= prob_thresh){
                        width = anchors_fpn_stride16[k][2] - anchors_fpn_stride16[k][0] + 1;
                        height = anchors_fpn_stride16[k][3] - anchors_fpn_stride16[k][1] + 1;
                        ctr_x = j*stride + anchors_fpn_stride16[k][0] + 0.5f*(width-1);
                        ctr_y = i*stride + anchors_fpn_stride16[k][1] + 0.5f*(height-1);

                        pred_ctr_x = bbox_deltas_stride16[0][i][j][4*k] * width + ctr_x;
                        pred_ctr_y = bbox_deltas_stride16[0][i][j][4*k+1] * height + ctr_y;
                        pred_w = (float)Math.exp((double)bbox_deltas_stride16[0][i][j][4*k+2]) * width;
                        pred_h = (float)Math.exp((double)bbox_deltas_stride16[0][i][j][4*k+3]) * height;

                        x1 = pred_ctr_x - 0.5f * (pred_w - 1);
                        y1 = pred_ctr_y - 0.5f * (pred_h - 1);
                        x2 = pred_ctr_x + 0.5f * (pred_w - 1);
                        y2 = pred_ctr_y + 0.5f * (pred_h - 1);

                        //calc landmarks
                        for(int l = 0; l < 5; l++){
                            landmarks[2*l] = landmark_deltas_stride16[0][i][j][10*k+2*l]*width + ctr_x;
                            landmarks[2*l+1] = landmark_deltas_stride16[0][i][j][10*k+2*l+1]*height + ctr_y;
                        }
                        bboxes.add(bboxes.size(), new boundingBox(scale*x1, scale*y1, scale*x2, scale*y2, score, landmarks));
                    }
                }
            }
        }
        //*/

        //stride 8
        stride = 8;
        for(int i = 0; i < h_stride8; i++){
            for(int j = 0; j < w_stride8; j++){
                for(int k = 0; k < num_anchors_stride8; k++) {
                    score1 = score_stride8[0][i][j][k];
                    score2 = score_stride8[0][i][j][num_anchors_stride32+k];
                    score = (float)Math.exp((double)score2)/((float)Math.exp((double)score1)+(float)Math.exp((double)score2));
                    if (score >= prob_thresh){
                        width = anchors_fpn_stride8[k][2] - anchors_fpn_stride8[k][0] + 1;
                        height = anchors_fpn_stride8[k][3] - anchors_fpn_stride8[k][1] + 1;
                        ctr_x = j*stride + anchors_fpn_stride8[k][0] + 0.5f*(width-1);
                        ctr_y = i*stride + anchors_fpn_stride8[k][1] + 0.5f*(height-1);

                        pred_ctr_x = bbox_deltas_stride8[0][i][j][4*k] * width + ctr_x;
                        pred_ctr_y = bbox_deltas_stride8[0][i][j][4*k+1] * height + ctr_y;
                        pred_w = (float)Math.exp((double)bbox_deltas_stride8[0][i][j][4*k+2]) * width;
                        pred_h = (float)Math.exp((double)bbox_deltas_stride8[0][i][j][4*k+3]) * height;

                        x1 = pred_ctr_x - 0.5f * (pred_w - 1);
                        y1 = pred_ctr_y - 0.5f * (pred_h - 1);
                        x2 = pred_ctr_x + 0.5f * (pred_w - 1);
                        y2 = pred_ctr_y + 0.5f * (pred_h - 1);

                        //calc landmarks
                        for(int l = 0; l < 5; l++){
                            landmarks[2*l] = landmark_deltas_stride8[0][i][j][10*k+2*l]*width + ctr_x;
                            landmarks[2*l+1] = landmark_deltas_stride8[0][i][j][10*k+2*l+1]*height + ctr_y;
                        }
                        //if(x1 >= 0 && x2 < w && y1 >= 0 && y2 < h) {
                        bboxes.add(bboxes.size(), new boundingBox(scale*x1, scale*y1, scale*x2, scale*y2, score, landmarks));
                        //}
                    }
                }
            }
        }

    }


    // imgData: bytebuffer that has rgb values in float type
    @Override
    public void detect(final Image image) {
//        imgData.rewind();
//
//        startTime = SystemClock.uptimeMillis();
//        for(int i = 0; i < h; i++){
//            for(int j = 0; j < w; j++){
//                for(int k = 0; k < c; k++){
//                    // &0xff: zero-extends the byte to integer
//                    imgData.putFloat((float)(img_in[i*w*c + j*c + k]&0xff));
//                }
//            }
//        }

        inferenceHandler.post(new Runnable() {
            @Override
            public void run() {
                isDetecting = true;

                startTime = SystemClock.uptimeMillis();


                final Image.Plane[] planes = image.getPlanes();
                fillBytes(image.getPlanes(), yuvBytes);
                convertYUV420ToRGBFloat(yuvBytes[0],
                        yuvBytes[1],
                        yuvBytes[2],
                        image.getWidth(),
                        image.getHeight(),
                        planes[0].getRowStride(),
                        planes[1].getRowStride(),
                        planes[1].getPixelStride());




                Object[] inputArray = {rgbFloatBuffer};
                Map<Integer, Object> outputMap = new HashMap<>();

                outputMap.put(0, score_stride32);
                outputMap.put(1, bbox_deltas_stride32);
                outputMap.put(2, landmark_deltas_stride32);
                outputMap.put(3, score_stride16);
                outputMap.put(4, bbox_deltas_stride16);
                outputMap.put(5, landmark_deltas_stride16);
                outputMap.put(6, score_stride8);
                outputMap.put(7, bbox_deltas_stride8);
                outputMap.put(8, landmark_deltas_stride8);

                tflite.runForMultipleInputsOutputs(inputArray, outputMap);

                calc_bboxes();
                nonMaximumSuppression();
                endTime = SystemClock.uptimeMillis();
                Log.d(TAG, "inference done!, " + Long.toString(endTime - startTime));
                Log.d(TAG, String.format("detected %d faces!!!",bboxes_refined.size()));
                for (int i = 0; i < bboxes_refined.size(); i++) {
                    Log.d(TAG, String.format("%d %d %d %d", bboxes_refined.get(i).r0, bboxes_refined.get(i).r1, bboxes_refined.get(i).r2, bboxes_refined.get(i).r3));
                }
                overlayView.bboxes = bboxes_refined;
                overlayView.postInvalidate();

                isDetecting = false;
                a.tryCloseImage(image);

            }
        });
    }


    private static int YUV2RGB(int y, int u, int v) {
        // Adjust and check YUV values
        y = (y - 16) < 0 ? 0 : (y - 16);
        u -= 128;
        v -= 128;

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        final int kMaxChannelValue = 262143;
        // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
        r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
        g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
        b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }

    public void convertYUV420ToRGBFloat(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride) {
        int yp = 0;


        rgbFloatBuffer.rewind();
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                int y = 0xff & yData[pY + i];
                int u = 0xff & uData[uv_offset];
                int v = 0xff & vData[uv_offset];

                // Adjust and check YUV values
                y = (y - 16) < 0 ? 0 : (y - 16);
                u -= 128;
                v -= 128;

                // This is the floating point equivalent. We do the conversion in integer
                // because some Android devices do not have floating point in hardware.
                // nR = (int)(1.164 * nY + 2.018 * nU);
                // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
                // nB = (int)(1.164 * nY + 1.596 * nV);
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                final int kMaxChannelValue = 262143;
                // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
                r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
                g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
                b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

                argbInts[j*width + i] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);

                // Very slow
                rgbFloatBuffer.putFloat((float)(r >> 10));
                rgbFloatBuffer.putFloat((float)(g >> 10));
                rgbFloatBuffer.putFloat((float)(b >> 10));

            }
        }
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer().duplicate();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
}

