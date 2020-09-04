package com.example.livemotionvectors.decoder;

public abstract class DecoderCallback {

    protected static final String TAG = "DecoderCallback";

    public abstract FFmpegAVCDecoder getDecoder();


    public abstract void call(byte[] encodedData, int size);



    public abstract void close();
}
