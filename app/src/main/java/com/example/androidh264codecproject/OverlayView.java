package com.example.androidh264codecproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.example.androidh264codecproject.encoder.MotionVectorList;
import com.example.androidh264codecproject.encoder.MotionVectorListItem;

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class OverlayView extends SurfaceView {
    private final String TAG = "OverlayView";
    private final Paint paint;
    private final Paint box_paint;
    private final SurfaceHolder mHolder;
    private final Context context;

    private int imgWidth;
    private int imgHeight;

    public MotionVectorList mvList;
    public float[] mvPoints;
    public Vector<boundingBox> bboxes = null;

    public long frameIdx;

    public OverlayView(CameraApi context) {
        super(context.getBaseContext());
        setWillNotDraw(false);
        mHolder = getHolder();
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        this.context = context.getBaseContext();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.RED);
        paint.setTextSize(100);

        box_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        box_paint.setStyle(Paint.Style.STROKE);
        box_paint.setColor(Color.GREEN);
        box_paint.setStrokeWidth(8);

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Log.e(TAG, "overlay view created ");

    }

    public void setSize(int width, int height) {
        this.imgWidth = width;
        this.imgHeight = height;
        mvPoints = new float[width/8 * height/8 * 4];
        setMeasuredDimension(width, height);
        Log.e(TAG, String.format("set overlayview size %d %d",width, height));
    }


    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);
        canvas.drawText("frame "+frameIdx, 100,100, paint);

        float scaleX = (float)getWidth() / imgWidth;
        float scaleY = (float)getHeight() / imgHeight;

        // Draw Motion vectors
        if(mvList != null) {
            Log.e(TAG, "draw count: " +mvList.getCount());
            for(int i=0; i<mvList.getCount()*4; i+=4) {

                MotionVectorListItem mv = mvList.getItem(i/4);
                mvPoints[i] = mv.getPosX()*scaleX;
                mvPoints[i+1] = mv.getPosY()*scaleY;
                mvPoints[i+2] = mv.getPosX()*scaleX + mv.getMvX()*scaleX*5;
                mvPoints[i+3] = mv.getPosY()*scaleY + mv.getMvY()*scaleY*5;
            }

            canvas.drawLines(mvPoints, 0, mvList.getCount()*4, paint);
        }
        else{
            Log.e("T", "no motion vector");
        }

        // Draw BBoxes
//        if(bboxes != null) {
//            for(boundingBox bbox: bboxes){
//                canvas.drawRect(bbox.r0*scaleX, bbox.r1*scaleY, bbox.r2*scaleX, bbox.r3*scaleY, box_paint);
//            }
//        }



    }

//    public void draw(RectF rectF) {
//        final Canvas canvas = mHolder.lockCanvas();
//        if(canvas != null) {
////            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
//            canvas.drawRect(rectF, paint);
//            mHolder.unlockCanvasAndPost(canvas);
//        }
//    }

}
