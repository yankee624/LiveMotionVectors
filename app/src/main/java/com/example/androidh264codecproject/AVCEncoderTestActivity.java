package com.example.androidh264codecproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.example.androidh264codecproject.decoder.FFmpegAVCDecoderCallback;
import com.example.androidh264codecproject.encoder.EncodeMode;

public class AVCEncoderTestActivity extends Activity {

    static {
        System.loadLibrary("motion_search_jni");
        System.loadLibrary("ffmpeg_h264_decoder_jni");
        System.loadLibrary("ffmpeg_avi_decoder_jni");

        /* FFmpeg Library */
        System.loadLibrary("avcodec");
        System.loadLibrary("avdevice");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("postproc");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avcencoder_test);

        checkStoragePermission();
        initUI();
    }

    private void initUI() {

        Button syncButton = (Button) findViewById(R.id.sync_button);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AVCEncoderTestThread t = new AVCEncoderTestThread();
                t.setEncodeMode(EncodeMode.SYNC_MODE);
                t.start();
            }
        });

        Button asyncButton = (Button) findViewById(R.id.async_button);
        asyncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AVCEncoderTestThread t = new AVCEncoderTestThread();
                t.setEncodeMode(EncodeMode.ASYNC_MODE);
                t.start();
            }
        });
    }

    private void checkStoragePermission() {

        if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission
                    .WRITE_EXTERNAL_STORAGE)) {
                //Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }

            this.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            print("pass");

            //Toast.makeText(this, "授权成功！", Toast.LENGTH_SHORT).show();
            //Log.e(TAG_SERVICE, "checkPermission: 已经授权！");
        }
    }

    private void print(String msg){
        Log.e("tag", msg);
    }

}
