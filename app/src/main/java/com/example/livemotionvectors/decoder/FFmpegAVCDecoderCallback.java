package com.example.livemotionvectors.decoder;

import android.util.Log;

import com.example.livemotionvectors.OverlayView;
import com.example.livemotionvectors.process.AccumulateCPU;

public class FFmpegAVCDecoderCallback extends DecoderCallback {

    private static final int FRAME_TYPE_I = 1;
    private static final int FRAME_TYPE_P = 2;

    private boolean getMVMapFlag = true;
    private boolean saveMVMapFlag = false;
    private boolean accuMVFlag = false;
    private boolean getMVListFlag = false;
    private boolean getResidualFlag = false;
    private boolean accuResidualFlag = false;

    private FFmpegAVCDecoder decoder;
    private AccumulateCPU    accuMV;
    private AccumulateCPU    accuRes;

    public OverlayView overlayView;

    public FFmpegAVCDecoderCallback(int w, int h) {
        decoder = new FFmpegAVCDecoder(w, h);

//        accuMV  = new AccumulateCPU(w, h, 16);
//        accuRes = new AccumulateCPU(w, h, 16);
//        accuMV.resetAccumulatedMV();
//        accuRes.resetAccumulatedResidual();
    }

    public FFmpegAVCDecoder getDecoder() {
        return decoder;
    }

    @Override
    public void call(byte[] encodedData, int size) {
//        byte[] packetData = new byte[size];
//        System.arraycopy(encodedData, 0, packetData, 0, size);
        byte[] packetData = encodedData;

        long startMs = System.currentTimeMillis();
        if (decoder.decodeFrame(packetData)) {
            // NOTE: Exactly got a decoded frame
            Log.d(TAG, String.format("Decode time %d ms",
                    System.currentTimeMillis() - startMs));
//
//            if(decoder.getMotionVectorList() != null) {
//                Log.e(TAG, "mv count: "+decoder.getMotionVectorList().getCount());
//            }
//            else{
//                Log.e(TAG, "mv count: "+0);
//
//            }
//
//            int frameType = 0;
//
//            if (getMVMapFlag) {
//                startMs = System.currentTimeMillis();
//                MotionVectorMap mvMap = decoder.getMotionVectorMap();
//                Log.d(TAG, String.format("Get mv time %d ms",
//                        System.currentTimeMillis() - startMs));
//
//
//                if (mvMap != null) {
//                    Log.e(TAG, String.format("%d mvMap size, %d %d", mvMap.getData().length, mvMap.getWidth(), mvMap.getHeight()));
//
//                    frameType = FRAME_TYPE_P;
//
//                    if (saveMVMapFlag)
//                        MotionVectorMap.saveMotionVectorMap(
//                                mvMap,
//                                MotionVectorMap.DEFAULT_SAVE_FILEPATH,
//                                0);
//
//                    if (accuMVFlag) {
//                        startMs = System.currentTimeMillis();
//                        accuMV.accumulateMV(mvMap.getData(),
//                                AccumulateMode.BLOCK_LEVEL);
//                        Log.d(TAG, String.format(
//                                "Accu time %d ms",
//                                System.currentTimeMillis() - startMs));
//
//                    }
//                } else {
//                    Log.e(TAG, "I frame! no mv");
//                    frameType = FRAME_TYPE_I;
//                    if (accuMVFlag)
//                        accuMV.resetAccumulatedMV();
//                }
//            }
//
//            startMs = System.currentTimeMillis();
//
//            MotionVectorList mvList = null;
//            if (getMVListFlag) {
//                mvList = decoder.getMotionVectorList();
//
//
//                if (mvList != null) {
//                    Log.e(TAG, String.format("%d mvList size", mvList.getCount()));
//
//
//                    frameType = FRAME_TYPE_P;
//                    //Log.d(TAG, String.format(Locale.CHINA, "MV List Count: %d", mvList.getCount()));
//                    for (int i = 0; i < mvList.getCount(); i++) {
//                        MotionVectorListItem item = mvList.getItem(i);
//                        int mvX = item.getMvX();
//                        int mvY = item.getMvY();
//                        int posX = item.getPosX();
//                        int posY = item.getPosY();
//                        int sizeX = item.getSizeX();
//                        int sizeY = item.getSizeY();
//                    }
//                } else {
//                    frameType = FRAME_TYPE_I;
//                }
//            }
//
//            if (getResidualFlag) {
//                if (frameType == FRAME_TYPE_P) {
//                    ResidualMap resMap = decoder.getResidualMap();
//                    if (resMap != null && accuResidualFlag && mvList != null) {
//                        accuRes.accumulateResidual(resMap.getData(), mvList.getData(), mvList.getCount());
//                        Log.d(TAG, String.format(
//                                "accu res time %d ms",
//                                System.currentTimeMillis() - startMs));
//                    }
//                } else {
//                    if (accuResidualFlag) {
//                        int[] accuResArray = accuRes.getAccumulatedResidual();
//                        Log.d(TAG, String.format(
//                                "get accu res time %d ms",
//                                System.currentTimeMillis() - startMs));
//
//                        accuRes.resetAccumulatedResidual();
//                    }
//                }
//            }
        }
    }

    @Override
    public void close() {
        decoder.free();
//        accuMV.shutdown();
//        accuRes.shutdown();
    }
}
