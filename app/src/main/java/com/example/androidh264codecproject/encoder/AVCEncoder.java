package com.example.androidh264codecproject.encoder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.androidh264codecproject.CameraApi;
import com.example.androidh264codecproject.OverlayView;
import com.example.androidh264codecproject.R;
import com.example.androidh264codecproject.decoder.DecoderCallback;
import com.example.androidh264codecproject.decoder.FFmpegAVCDecoderCallback;
import com.example.androidh264codecproject.decoder.FFmpegAVIDecoder;
import com.example.androidh264codecproject.yuvtools.YUVI420FileReader;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

public class AVCEncoder {

    private static final String TAG = "MediaCodec";

    private static final int TIMEOUT_USEC = 500; //microsecond
    private static final int DEFAULT_OUT_DATA_SIZE = 4096;

    private int mWidth;
    private int mHeight;
    private int mFrameRate;

    private boolean isEncoding = false;


    private long mTotalStartTime;

    private MediaCodec mMediaCodec;
    private byte[] configByte = null;
    private MediaCodec.BufferInfo bufferInfo;

    private YUVI420FileReader mYUVFileReader = null;
    private String mOutputPath;
    private String mOutputFilename = null;
    private BufferedOutputStream mOutputStream = null;

    private HandlerThread encodingThread;
    private Handler encodingHandler;

    private DecoderCallback mDecoderCallback = null;
    private FFmpegAVIDecoder mAVIDecoder = null;

    private OverlayView overlayView;
    private float[] mvPoints;

    byte[] input = null;
    boolean isFirstFrame = true;

    private CameraApi a;


    public AVCEncoder(CameraApi a, OverlayView overlayView, int width, int height, int frameRate, int bitrate) {
        this.a = a;
        this.overlayView = overlayView;
        mWidth     = width;
        mHeight    = height;
        mFrameRate = frameRate;
        input =new byte[mWidth * mHeight * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];

        final String MIME     = "video/avc";
        final float  GOP_SIZE = 12.0f;

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                               MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        //mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        mediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, GOP_SIZE / frameRate);

        //mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);

        // If not set KEY_I_FRAME_INTERVAL, NullPointerException will occur
        //int keyIFrameInterval = mediaFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL);
        //Log.i(TAG, String.format(Locale.CHINA, "keyIFrameInterval: %d", keyIFrameInterval));
        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIME);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mDecoderCallback = new FFmpegAVCDecoderCallback(width, height);
    }



    public void setDecoderCallback(DecoderCallback callback) {
        mDecoderCallback = callback;
    }

    public void setFFmpegAVIDecoder(FFmpegAVIDecoder decoder) {
        mAVIDecoder = decoder;
    }

    private String buildOutputFilePath() {
        if (mOutputFilename == null)
            return mOutputPath;
        else
            return mOutputPath + File.separator + mOutputFilename;
    }

    private void createOutputH264File() {
        File file = new File(buildOutputFilePath());
        file.deleteOnExit();
        try {
            mOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void startEncodingThread() {
        encodingThread = new HandlerThread("Encoding");
        encodingThread.start();
        encodingHandler = new Handler(encodingThread.getLooper());
    }

    protected void stopEncodingThread() {
        encodingThread.quitSafely();
        try {
            encodingThread.join();
            encodingThread = null;
            encodingHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        mMediaCodec.start();
        mDecoderCallback = new FFmpegAVCDecoderCallback(mWidth, mHeight);
        bufferInfo = new MediaCodec.BufferInfo();
        startEncodingThread();
        Log.i(TAG, "AVC encoder start");
    }

    public void startAsync() {
        mTotalStartTime = System.currentTimeMillis();

        mMediaCodec.setCallback(new AVCCallback());
        mMediaCodec.start();
    }

    public void stop() {
        try {
            mMediaCodec.stop();
            mMediaCodec.release();

            if (mDecoderCallback != null)
                mDecoderCallback.close();
            if (mAVIDecoder != null)
                mAVIDecoder.close();

            stopEncodingThread();

            Log.d(TAG, "Stop codec success");

        } catch (Exception e){
            e.printStackTrace();
        }
    }




    private class EncodeRunnable implements Runnable {
        private Image image;
        private long frameIdx;

        public EncodeRunnable(Image image, long frameIdx) {
            this.image = image;
            this.frameIdx = frameIdx;
        }

        @Override
        public void run() {
            long startMs = System.currentTimeMillis();


            isEncoding = true;
            long pts = 0;
            long readFrameTime = 0;
            byte[] outData;
            byte[] keyFrameData;

//            imageToMat(image, input);

            try {
                int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    if (image != null) {
                        pts = computePresentationTime(frameIdx);
                        Log.e(TAG, "Input pts: "+pts);

                        // Put data in inputImage, instead of inputBuffer (faster)
                        Image codecInputImage = mMediaCodec.getInputImage(inputBufferIndex);
                        // If pixelStride different, we need to fill the buffer differently
                        // Maybe one-by-one filling is needed since we need to skip some bytes according to stride
                        // Ex) if CodecImage's stride=1, CameraImage's stride=2 (for u,v planes), fill in like below comments.
                        // This is very slow. Need to figure out how to do this fast
                        assert(codecInputImage.getPlanes()[0].getPixelStride() == image.getPlanes()[0].getPixelStride());
                        assert(codecInputImage.getPlanes()[1].getPixelStride() == image.getPlanes()[1].getPixelStride());
                        assert(codecInputImage.getPlanes()[2].getPixelStride() == image.getPlanes()[2].getPixelStride());

                        codecInputImage.getPlanes()[0].getBuffer().put(image.getPlanes()[0].getBuffer().duplicate());
                        codecInputImage.getPlanes()[1].getBuffer().put(image.getPlanes()[1].getBuffer().duplicate());
                        codecInputImage.getPlanes()[2].getBuffer().put(image.getPlanes()[2].getBuffer().duplicate());


//                        ByteBuffer uCodec = codecInputImage.getPlanes()[1].getBuffer();
//                        ByteBuffer vCodec = codecInputImage.getPlanes()[2].getBuffer();
//
//                        ByteBuffer uCamera = image.getPlanes()[1].getBuffer();
//                        ByteBuffer vCamera = image.getPlanes()[2].getBuffer();
//                        codecInputImage.getPlanes()[0].getBuffer().put(image.getPlanes()[0].getBuffer());
//                        for(int i=0; i<uCamera.limit()/2; i++) {
//                            uCodec.put(uCamera.get());
//                            uCamera.get();
//                        }
//                        for(int i=0; i<vCamera.limit()/2; i++) {
//                            vCodec.put(vCamera.get());
//                            vCamera.get();
//                        }



                        // ByteBuffer version: Need input in byte[] format
//                        ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
//                        if (inputBuffer != null) {
//                            inputBuffer.clear();
//                            inputBuffer.put(input);
//                        }

                        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                    } else {
                        // Set the flag of end of stream
                        mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                                BUFFER_FLAG_END_OF_STREAM);
                    }
                }

                long queueInMs = System.currentTimeMillis() - startMs;
                startMs = System.currentTimeMillis();


                int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(
                        bufferInfo, TIMEOUT_USEC);


                while (outputBufferIndex >= 0) {
                    Log.e(TAG, "Encoded pts: "+bufferInfo.presentationTimeUs);
                    if ((bufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "End of output buffer");
                        break;
                    }

                    ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
//                    if (outputBuffer != null) {
//                        // NOTE: bufferInfo.size may change here
//                        Log.e(TAG, String.format("buffer, outdata size buffer capa: %d %d %d",bufferInfo.size, outData.length, outputBuffer.capacity()));
//                        if (bufferInfo.size > outData.length){
//                            outData = new byte[bufferInfo.size];
//                        }
//
//                        outputBuffer.get(outData, 0, bufferInfo.size);
//                    }

                    // P-frame
                    if (bufferInfo.flags == 0) {
                        outData = new byte[bufferInfo.size];
                        outputBuffer.get(outData, 0, bufferInfo.size);
                        if (mOutputStream != null)
                            mOutputStream.write(outData, 0, outData.length);
                        if (mDecoderCallback != null)
                            mDecoderCallback.call(outData, outData.length);
                    // I-frame
                    } else if (bufferInfo.flags == BUFFER_FLAG_KEY_FRAME) {
                        assert(configByte != null);
                        int outSize = bufferInfo.size + configByte.length;
                        keyFrameData = new byte[outSize];
                        // Config data
                        System.arraycopy(configByte, 0, keyFrameData, 0, configByte.length);
                        // Frame data
                        outputBuffer.get(keyFrameData, configByte.length, bufferInfo.size);

                        if (mDecoderCallback != null)
                            mDecoderCallback.call(keyFrameData, outSize);
                    // Config information (Only at the beginning)
                    } else if (bufferInfo.flags == BUFFER_FLAG_CODEC_CONFIG) {
                        configByte = new byte[bufferInfo.size];
                        outputBuffer.get(configByte, 0, bufferInfo.size);
                    }


                    mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);

                    // Already encoded until current input frame
                    // There are no more encoding results to get
                    if(bufferInfo.presentationTimeUs == pts) {
                        break;
                    }

                    outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

                }

                if(configByte != null) isFirstFrame = false;

                long queueOutMs = System.currentTimeMillis() - startMs;
                Log.i(TAG, String.format(
                        "Frame %d encoding finished, read %d ms, qIn %d ms, qOut %d ms",
                        frameIdx, readFrameTime, queueInMs, queueOutMs));



            } catch (Throwable t) {
                t.printStackTrace();
            }

            MotionVectorList mvList = mDecoderCallback.getDecoder().getMotionVectorList();
            overlayView.mvList = mvList;
            overlayView.frameIdx = frameIdx;
            overlayView.postInvalidate();

            isEncoding = false;
            a.tryCloseImage(image);


        }
    }



    public void run(Image image, long frameIdx) {
        // Since encoding has very low latency, we can just perform encode-decode-extract_mv every frame
//        if(isEncoding){
//            image.close();
//            return;
//        }
        encodingHandler.post(new EncodeRunnable(image, frameIdx));
    }

    // YUV420 image to byte[]
    // Not accurate, Slow
    public static byte[] imageToMat(Image image, byte[] output) {

        Image.Plane[] planes = image.getPlanes();

        ByteBuffer buffer0 = planes[0].getBuffer();
        ByteBuffer buffer1 = planes[1].getBuffer();
        ByteBuffer buffer2 = planes[2].getBuffer();

        int offset = 0;

        int width = image.getWidth();
        int height = image.getHeight();

//        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
        byte[] rowData1 = new byte[planes[1].getRowStride()];
        byte[] rowData2 = new byte[planes[2].getRowStride()];


        int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8; // 12 / 8 -> 1?
        // loop via rows of u/v channels

        int offsetY = 0;

        int sizeY =  width * height * bytesPerPixel;
        int sizeUV = (width * height * bytesPerPixel) / 4;

        for (int row = 0; row < height ; row++) {

            // fill data for Y channel, two row
            {
                int length = bytesPerPixel * width;
                buffer0.get(output, offsetY, length);

                if ( height - row != 1)
                    buffer0.position(buffer0.position()  +  planes[0].getRowStride() - length);

                offsetY += length;
            }

            if (row >= height/2)
                continue;

            {
                int uvlength = planes[1].getRowStride();

                if ( (height / 2 - row) == 1 ) {
                    uvlength = width / 2 - planes[1].getPixelStride() + 1;
                }

                buffer1.get(rowData1, 0, uvlength);
                buffer2.get(rowData2, 0, uvlength);

                // fill data for u/v channels
                for (int col = 0; col < width / 2; ++col) {
                    // u channel
                    output[sizeY + (row * width)/2 + col] = rowData1[col * planes[1].getPixelStride()];

                    // v channel
                    output[sizeY + sizeUV + (row * width)/2 + col] = rowData2[col * planes[2].getPixelStride()];
                }
            }

        }

        return output;
    }




    /*private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height) {
        if(nv21 == null || nv12 == null)
            return;
        int frameSize = width*height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize/2; j+=2) {
            nv12[frameSize + j-1] = nv21[j+frameSize];
        }
        for (j = 0; j < frameSize/2; j+=2) {
            nv12[frameSize + j] = nv21[j+frameSize-1];
        }
    }*/

    /**
     * @Description: Generates the presentation time for frame N,
     *               in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / mFrameRate;
    }
//
//    private class EncodeRunnable implements Runnable {
//
//
//        @Override
//        public void run() {
//            isRunning = true;
//            byte[] input = null;
//            long pts = 0;
//            long generateIndex = 0;
//            int frameIdx = 0;
//            long startMs, encTimeSum = 0;
//            long readFrameTime = 0;
//            byte[] configByte = null;
//            byte[] outData = new byte[DEFAULT_OUT_DATA_SIZE];
//            byte[] keyFrameData = new byte[DEFAULT_OUT_DATA_SIZE];
//
//            while (isRunning) {
//                /*
//                // Read frame from MainActivity
//                if (MainActivity.YUVQueue.size() >0){
//                    input = MainActivity.YUVQueue.poll();
//                    byte[] yuv420sp = new byte[mWidth * mHeight *3/2];
//                    NV21ToNV12(input,yuv420sp, mWidth, mHeight);
//                    input = yuv420sp;
//                }*/
//
//                // Read YUV frame from file
//                try {
//                    startMs = System.currentTimeMillis();
//                    input = mYUVFileReader.readFrameData();
//                    // input = YUVFormatTransformTool.I420ToYV12(input, mWidth, mHeight);
//                    readFrameTime = System.currentTimeMillis() - startMs;
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    input = null;
//                }
//
//                try {
//                    startMs = System.currentTimeMillis();
//
//                    //ByteBuffer[] inputBuffers  = mMediaCodec.getInputBuffers();
//                    //ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
//                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
//                    if (inputBufferIndex >= 0) {
//                        if (input != null) {
//                            pts = computePresentationTime(generateIndex);
//                            //ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//                            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
//                            if (inputBuffer != null) {
//                                inputBuffer.clear();
//                                inputBuffer.put(input);
//                            }
//                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
//                            generateIndex += 1;
//                        } else {
//                            // Set the flag of end of stream
//                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
//                                                         BUFFER_FLAG_END_OF_STREAM);
//                        }
//                    }
//
//                    long queueInMs = System.currentTimeMillis() - startMs;
//
//                    startMs = System.currentTimeMillis();
//
//                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(
//                                                            bufferInfo, TIMEOUT_USEC);
//                    while (outputBufferIndex >= 0) {
//                        if ((bufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
//                            Log.d(TAG, "End of output buffer");
//                            break;
//                        }
//
//                        //Log.i("AVCEncoder",
//                        //      "Get H264 Buffer Success! flag = "+
//                        //      bufferInfo.flags+
//                        //      ",pts = "+bufferInfo.presentationTimeUs+"");
//
//                        //ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//                        ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
//                        if (outputBuffer != null) {
//                            // NOTE: bufferInfo.size may change here
//                            if (bufferInfo.size > outData.length)
//                                outData = new byte[bufferInfo.size];
//                            outputBuffer.get(outData, 0, bufferInfo.size);
//                        }
//
//                        if (bufferInfo.flags == 0) {
//                            if (mOutputStream != null)
//                                mOutputStream.write(outData, 0, outData.length);
//                            if (mDecoderCallback != null)
//                                mDecoderCallback.call(outData, outData.length);
//                        } else if (bufferInfo.flags == BUFFER_FLAG_KEY_FRAME) {
//                            assert(configByte != null);
//                            int outSize = bufferInfo.size + configByte.length;
//                            if (outSize > keyFrameData.length)
//                                keyFrameData = new byte[outSize];
//                            // Config data
//                            System.arraycopy(configByte, 0,
//                                    keyFrameData, 0, configByte.length);
//                            // Frame data
//                            System.arraycopy(outData,    0,
//                                    keyFrameData, configByte.length, bufferInfo.size);
//
//                            if (mOutputStream != null)
//                                mOutputStream.write(keyFrameData, 0, keyFrameData.length);
//                            if (mDecoderCallback != null)
//                                mDecoderCallback.call(keyFrameData, outSize);
//                        } else if (bufferInfo.flags == BUFFER_FLAG_CODEC_CONFIG) {
//                            configByte = new byte[bufferInfo.size];
//                            //configByte = outData; // BUG: Must create a buffer for configByte
//                            System.arraycopy(outData, 0, configByte, 0, bufferInfo.size);
//                        }
//
//                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
//                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(
//                                                            bufferInfo, TIMEOUT_USEC);
//                    }
//
//                    long queueOutMs = System.currentTimeMillis() - startMs;
//                    Log.i(TAG, String.format(
//                            "Frame %d encoding finished, read %d ms, qIn %d ms, qOut %d ms",
//                            frameIdx, readFrameTime, queueInMs, queueOutMs));
//
//                    encTimeSum += (queueInMs + queueOutMs);
//
//                    // No more frame to be encoded, exit
//                    if (input == null) {
//                        Log.d(TAG, "End of input frames");
//                        break;
//                    }
//
//                    frameIdx ++;
//                } catch (Throwable t) {
//                    t.printStackTrace();
//                }
//            }
//
//            Log.i(TAG, String.format(Locale.CHINA,
//                    "Encoding finished, avg %.2f FPS", 1000.0f * frameIdx / encTimeSum));
//            stop();
//        }
//    }
//
    private class AVCCallback extends MediaCodec.Callback {

        private long lastEncTime = -1;
        private int inFrameIndex = 0;
        private int outFrameIndex = 0;

        private boolean endInputBuffer  = false;
        private boolean endOutputBuffer = false;

        private int mGenerateIndex;
        private byte[] mConfigByte;
        private byte[] mOutData;
        private byte[] mKeyFrameData;

        private AVCCallback() {
            mGenerateIndex = 0;
            mConfigByte    = new byte[DEFAULT_OUT_DATA_SIZE];
            mOutData       = new byte[DEFAULT_OUT_DATA_SIZE];
            mKeyFrameData  = new byte[DEFAULT_OUT_DATA_SIZE];
        }

        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {

            byte[] input = null;

            try {
                Thread.sleep(40);  // 40ms for 25FPS
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (mYUVFileReader != null) {
                    input = mYUVFileReader.readFrameData();
                } else if (mAVIDecoder != null) {
                    // Read frame from AVI decoder
                    input = mAVIDecoder.getFrameData();
                } else {
                    Log.e(TAG, "No frame data source");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (index >= 0) {
                if (input != null) {
                    long pts = computePresentationTime(mGenerateIndex);
                    ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        inputBuffer.put(input);
                    }
                    codec.queueInputBuffer(index, 0, input.length, pts, 0);
                    mGenerateIndex ++;
                    inFrameIndex ++;
                } else {
                    if (!endInputBuffer) {
                        codec.queueInputBuffer(index, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM);
                        //Log.d(TAG, "End of input buffer");
                        endInputBuffer = true;
                    }
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index,
                                            @NonNull MediaCodec.BufferInfo info) {
            if (index >= 0) {
                if ((info.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                    //Log.d(TAG, "End of output buffer");
                    stop();
                } else {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    if (outputBuffer != null) {
                        if (mOutData.length < info.size)
                            mOutData = new byte[info.size];
                        outputBuffer.get(mOutData, 0, info.size);
                    }

                    int outSize = 0;
                    if (info.flags == 0) {
                        outSize = mOutData.length;
                    } else if (info.flags == BUFFER_FLAG_KEY_FRAME) {
                        outSize = info.size + mConfigByte.length;
                        if (outSize > mKeyFrameData.length)
                            mKeyFrameData = new byte[outSize];
                        System.arraycopy(mConfigByte, 0, mKeyFrameData, 0, mConfigByte.length);
                        System.arraycopy(mOutData, 0, mKeyFrameData, mConfigByte.length, info.size);
                    } else if (info.flags == BUFFER_FLAG_CODEC_CONFIG) {
                        outSize     = info.size;
                        mConfigByte = new byte[outSize];
                        System.arraycopy(mOutData, 0, mConfigByte, 0, info.size);
                    }

                    if (lastEncTime > 0) {
                        long encTime = System.currentTimeMillis() - lastEncTime;
                        Log.i(TAG, String.format(Locale.CHINA, "out-frame %d, in-frame %d, async enc time %d ms",
                                outFrameIndex, inFrameIndex, encTime));
                    }

                    try {
                        if (info.flags == 0) {
                            if (mOutputStream != null)
                                mOutputStream.write(mOutData, 0, mOutData.length);
                            if (mDecoderCallback != null)
                                mDecoderCallback.call(mOutData, mOutData.length);
                        } else if (info.flags == BUFFER_FLAG_KEY_FRAME) {
                            if (mOutputStream != null)
                                mOutputStream.write(mKeyFrameData, 0, mKeyFrameData.length);
                            if (mDecoderCallback != null)
                                mDecoderCallback.call(mKeyFrameData, outSize);
                        }

                        if (outFrameIndex == inFrameIndex && endInputBuffer) {
                            Log.i(TAG, String.format(Locale.CHINA,
                                    "Total time %d ms", System.currentTimeMillis() - mTotalStartTime));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    codec.releaseOutputBuffer(index, false);

                    outFrameIndex ++;
                    lastEncTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {}

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {}
    }

}
