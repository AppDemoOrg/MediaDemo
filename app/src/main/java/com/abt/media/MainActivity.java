package com.abt.media;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements
        SurfaceHolder.Callback, Camera.PreviewCallback {

    @BindView(R.id.surface)
    SurfaceView mSurfaceView;
    @OnClick(R.id.button_start) void startBtn() {
        mCamera = getBackCamera();
        startCamera(mCamera);
        mAvcCodec = new AvcEncoder(mWidth, mHeight, mFrameRate, mBiteRate);
        mAvcCodec.startEncoderThread();
        Toast.makeText(this, "start to record video", Toast.LENGTH_SHORT).show();
    }
    @OnClick(R.id.button_stop) void stopBtn() {
        /*if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }*/
        if (null != mAvcCodec) {
            mAvcCodec.stopThread();
        }
        Toast.makeText(this, "stop to record video", Toast.LENGTH_SHORT).show();
    }

    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private Camera.Parameters mParameters;
    private int mWidth = 1280;
    private int mHeight = 720;
    private int mFrameRate = 15;
    private int mBiteRate = 8500*1000;
    private static int mYuvQueueSize = 10;
    private AvcEncoder mAvcCodec;
    public static ArrayBlockingQueue<byte[]> mYUVQueue = new ArrayBlockingQueue<byte[]>(mYuvQueueSize);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        supportAvcCodec();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = getBackCamera();
        startCamera(mCamera);
        mAvcCodec = new AvcEncoder(mWidth, mHeight, mFrameRate, mBiteRate);
        //mAvcCodec.startEncoderThread();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != mCamera) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            //mAvcCodec.stopThread();
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
        putYUVData(data,data.length);
    }

    public void putYUVData(byte[] buffer, int length) {
        if (mYUVQueue.size() >= 10) {
            mYUVQueue.poll();
        }
        mYUVQueue.add(buffer);
    }

    @SuppressLint("NewApi")
    private boolean supportAvcCodec() {
        if (Build.VERSION.SDK_INT>=18) {
            for (int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase("video/avc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void startCamera(Camera camera) {
        if (camera != null) {
            try {
                camera.setPreviewCallback(this);
                camera.setDisplayOrientation(90);
                if(mParameters == null){
                    mParameters = camera.getParameters();
                }
                mParameters = camera.getParameters();
                mParameters.setPreviewFormat(ImageFormat.NV21);
                mParameters.setPreviewSize(mWidth, mHeight);
                camera.setParameters(mParameters);
                camera.setPreviewDisplay(mSurfaceHolder);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(9)
    private Camera getBackCamera() {
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c; // returns null if mCamera is unavailable
    }

}
