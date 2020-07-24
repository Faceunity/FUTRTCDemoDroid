package com.faceunity.customdata;

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import com.faceunity.nama.FURenderer;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;

/**
 * @author Richie on 2020.07.17
 */
public class RendererStatusListener implements CameraRenderer.OnRendererStatusListener, SensorObserver.OnAccelerometerChangedListener {
    private static final String TAG = "RendererStatusListener";
    private FURenderer mFuRenderer;
    private SensorObserver mSensorObserver;
    private TRTCCloud mTRTCCloud;
    private Handler mSendDataHandler;
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private byte[] mReadback;
    private Runnable mRunnable;
    // vivo v3 phone is shit!
    private boolean mIsVivoV3;

    public RendererStatusListener(Context context, FURenderer fuRenderer) {
        mFuRenderer = fuRenderer;
        mSensorObserver = new SensorObserver(context);
        mSensorObserver.setOnAccelerometerChangedListener(this);
        mTRTCCloud = TRTCCloud.sharedInstance(context);
        String model = Build.MODEL;
        mIsVivoV3 = "vivo V3".equals(model) || ("vivo V3Max A").equals(model);
    }

    @Override
    public void onSurfaceCreated() {
        mFuRenderer.onSurfaceCreated();
        mSensorObserver.register();
        if (!mIsVivoV3) {
            HandlerThread handlerThread = new HandlerThread(RendererStatusListener.TAG);
            handlerThread.start();
            mSendDataHandler = new Handler(handlerThread.getLooper());
        }
    }

    @Override
    public void onSurfaceChanged(int viewWidth, int viewHeight) {

    }

    @Override
    public int onDrawFrame(final byte[] nv21Byte, int texId, final int cameraWidth, final int cameraHeight, int cameraFacing, float[] mvpMatrix, float[] texMatrix, long timeStamp) {
        final int outTexId;
        byte[] readback = mReadback;
        if (readback == null) {
            readback = new byte[cameraWidth * cameraHeight * 3 / 2];
            mReadback = readback;
            mRunnable = new Runnable() {
                private TRTCCloudDef.TRTCVideoFrame mVideoFrame;
                private byte[] mTemp = new byte[mReadback.length];

                {
                    TRTCCloudDef.TRTCVideoFrame videoFrame = new TRTCCloudDef.TRTCVideoFrame();
                    videoFrame.width = cameraHeight;
                    videoFrame.height = cameraWidth;
                    videoFrame.data = mTemp;
                    videoFrame.pixelFormat = TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_NV21;
                    videoFrame.bufferType = TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY;
                    mVideoFrame = videoFrame;
                }

                @Override
                public void run() {
                    System.arraycopy(mReadback, 0, mTemp, 0, mTemp.length);
                    mTRTCCloud.sendCustomVideoData(mVideoFrame);
                }
            };
        }
        outTexId = mFuRenderer.onDrawFrameDualInput(nv21Byte, texId, cameraWidth, cameraHeight, readback, cameraWidth, cameraHeight);
        mFuRenderer.rotateImage(readback, cameraWidth, cameraHeight, mCameraFacing);
        // 异步推流性能更好，此处是为了 vivo v3 兼容性
        if (mIsVivoV3) {
            mRunnable.run();
        } else {
            mSendDataHandler.post(mRunnable);
        }
        return outTexId;
    }

    @Override
    public void onSurfaceDestroy() {
        mFuRenderer.onSurfaceDestroyed();
        mSensorObserver.unregister();
        if (mSendDataHandler != null) {
            mSendDataHandler.getLooper().quitSafely();
            mSendDataHandler = null;
        }
    }

    @Override
    public void onCameraChanged(int cameraFacing, int cameraOrientation) {
        mCameraFacing = cameraFacing;
        mFuRenderer.onCameraChanged(cameraFacing, cameraOrientation);
    }

    @Override
    public void onAccelerometerChanged(float x, float y, float z) {
        if (Math.abs(x) > 3 || Math.abs(y) > 3) {
            if (Math.abs(x) > Math.abs(y)) {
                mFuRenderer.onDeviceOrientationChanged(x > 0 ? 0 : 180);
            } else {
                mFuRenderer.onDeviceOrientationChanged(y > 0 ? 90 : 270);
            }
        }
    }

}
