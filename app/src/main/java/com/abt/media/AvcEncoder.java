package com.abt.media;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by huangweiqi on 29/04/2018.
 */
public class AvcEncoder {

    private final static String TAG = "AvcEncoder";
    private int TIMEOUT_USE_SECOND = 12000;
    private MediaCodec mMediaCodec;
    private int mWidth;
    private int mHeight;
    private int mFrameRate;
    private byte[] mInfo = null;
    public byte[] mConfigByte;
    private static String mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test1.h264";
    private BufferedOutputStream mOutputStream;
    private FileOutputStream mOutStream;
    private ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;
    public boolean mIsRuning = false;
    private int mCount = 0;

    @SuppressLint("NewApi")
    public AvcEncoder(int width, int height, int frameRate, int bitRate) {
        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width*height*5);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        createFile();
    }

    private void createFile() {
        File file = new File(mPath);
        if(file.exists()){
            file.delete();
        }
        try {
            mOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    private void stopEncoder() {
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void stopThread(){
        mIsRuning = false;
        try {
            stopEncoder();
            mOutputStream.flush();
            mOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startEncoderThread() {
        Thread encoderThread = new Thread(new Runnable() {

            @SuppressLint("NewApi")
            @Override
            public void run() {
                mIsRuning = true;
                byte[] input = null;
                long pts = 0;
                long generateIndex = 0;

                while (mIsRuning) {
                    if (MainActivity.mYUVQueue.size() >0){
                        input = MainActivity.mYUVQueue.poll();
                        byte[] yuv420sp = new byte[mWidth * mHeight *3/2];
                        NV21ToNV12(input,yuv420sp, mWidth, mHeight);
                        input = yuv420sp;
                    }
                    if (input != null) {
                        try {
                            long startMs = System.currentTimeMillis();
                            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
                            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                            int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                            if (inputBufferIndex >= 0) {
                                pts = computePresentationTime(generateIndex);
                                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                inputBuffer.clear();
                                inputBuffer.put(input);
                                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                                generateIndex += 1;
                            }

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USE_SECOND);
                            while (outputBufferIndex >= 0) {
                                //Log.i("AvcEncoder", "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);
                                if(bufferInfo.flags == 2){
                                    mConfigByte = new byte[bufferInfo.size];
                                    mConfigByte = outData;
                                }else if(bufferInfo.flags == 1){
                                    byte[] keyframe = new byte[bufferInfo.size + mConfigByte.length];
                                    System.arraycopy(mConfigByte, 0, keyframe, 0, mConfigByte.length);
                                    System.arraycopy(outData, 0, keyframe, mConfigByte.length, outData.length);
                                    mOutputStream.write(keyframe, 0, keyframe.length);
                                }else{
                                    mOutputStream.write(outData, 0, outData.length);
                                }

                                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USE_SECOND);
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        encoderThread.start();
    }

    private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height) {
        if(nv21 == null || nv12 == null)return;
        int frameSize = width*height;
        int i = 0,j = 0;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for(i = 0; i < frameSize; i++){
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize/2; j+=2) {
            nv12[frameSize + j-1] = nv21[j+frameSize];
        }
        for (j = 0; j < frameSize/2; j+=2) {
            nv12[frameSize + j] = nv21[j+frameSize-1];
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / mFrameRate;
    }
}
