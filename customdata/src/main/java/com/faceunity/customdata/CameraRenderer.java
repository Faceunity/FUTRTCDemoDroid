package com.faceunity.customdata;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import com.faceunity.customdata.gles.ProgramTexture2d;
import com.faceunity.customdata.gles.ProgramTextureOES;
import com.faceunity.customdata.gles.core.GlUtil;
import com.faceunity.customdata.utils.CameraUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Camera 管理和 Surface 渲染
 *
 * @author Richie on 2019.12.20
 */
public class CameraRenderer implements GLTextureView.Renderer, Camera.PreviewCallback {
    private static final String TAG = "CameraRenderer";
    private static final int DEFAULT_CAMERA_WIDTH = 1280;
    private static final int DEFAULT_CAMERA_HEIGHT = 720;
    private static final int PREVIEW_BUFFER_COUNT = 3;
    private Activity mActivity;
    private GLTextureView mGlTextureView;
    private OnRendererStatusListener mOnRendererStatusListener;
    private int mViewWidth;
    private int mViewHeight;
    private Camera mCamera;
    private boolean mIsStoppedPreview;
    private byte[][] mPreviewCallbackBuffer;
    private int mCameraWidth = DEFAULT_CAMERA_WIDTH;
    private int mCameraHeight = DEFAULT_CAMERA_HEIGHT;
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private int mCameraOrientation = 270;
    private int mCameraTextureId;
    private byte[] mCameraNv21Byte;
    private byte[] mNv21ByteCopy;
    private float[] mTexMatrix = {0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f};
    private SurfaceTexture mSurfaceTexture;
    private boolean mIsPreviewing;
    private float[] mMvpMatrix;
    private ProgramTexture2d mProgramTexture2d;
    private ProgramTextureOES mProgramTextureOes;
    private Handler mBackgroundHandler;

    public CameraRenderer(Activity activity, GLTextureView glTextureView, OnRendererStatusListener onRendererStatusListener) {
        mActivity = activity;
        mGlTextureView = glTextureView;
        mOnRendererStatusListener = onRendererStatusListener;
    }

    public void onResume() {
        Log.d(TAG, "onResume: ");
        startBackgroundThread();
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                openCamera(mCameraFacing);
                startPreview();
            }
        });
        mGlTextureView.onResume();
    }

    public void onPause() {
        Log.d(TAG, "onPause: ");
        final CountDownLatch count = new CountDownLatch(1);
        mGlTextureView.queueEvent(new Runnable() {
            @Override
            public void run() {
                destroyGlSurface();
                count.countDown();
            }
        });
        try {
            count.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // ignored
        }
        mGlTextureView.onPause();
        if (mBackgroundHandler != null) {
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    releaseCamera();
                }
            });
        }
        stopBackgroundThread();
    }

    public void switchCamera() {
        Log.d(TAG, "switchCamera: ");
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                mIsStoppedPreview = true;
                boolean isFront = mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
                mCameraFacing = isFront ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
                releaseCamera();
                openCamera(mCameraFacing);
                startPreview();
                mIsStoppedPreview = false;
            }
        });
    }

    @Override
    public void onSurfaceCreated(android.opengl.EGLConfig eglConfig) {
        Log.d(TAG, "onSurfaceCreated. Thread:" + Thread.currentThread().getName());
        Log.i(TAG, "GLES INFO vendor: " + GLES20.glGetString(GLES20.GL_VENDOR) + ", renderer: " + GLES20.glGetString(GLES20.GL_RENDERER)
                + ", version: " + GLES20.glGetString(GLES20.GL_VERSION));
        mProgramTexture2d = new ProgramTexture2d();
        mProgramTextureOes = new ProgramTextureOES();
        mCameraTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                startPreview();
            }
        });
        mOnRendererStatusListener.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
        GLES20.glViewport(0, 0, width, height);
        confirmMvpMatrix();
        Log.d(TAG, "onSurfaceChanged. viewWidth:" + width + ", viewHeight:" + height
                + ". cameraOrientation:" + mCameraOrientation + ", cameraWidth:" + mCameraWidth
                + ", cameraHeight:" + mCameraHeight + ", textureId:" + mCameraTextureId);
        mOnRendererStatusListener.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_STENCIL_BUFFER_BIT);
        if (mProgramTexture2d == null || mSurfaceTexture == null || mIsStoppedPreview) {
            return;
        }

        try {
            mSurfaceTexture.updateTexImage();
            mSurfaceTexture.getTransformMatrix(mTexMatrix);
        } catch (Exception e) {
            Log.e(TAG, "onDrawFrame: ", e);
        }

        int fuTexId = 0;
        byte[] nv21ByteCopy = mNv21ByteCopy;
        float[] mvpMatrix = mMvpMatrix;
        byte[] cameraNv21Byte = mCameraNv21Byte;
        if (cameraNv21Byte != null) {
            if (nv21ByteCopy == null) {
                nv21ByteCopy = new byte[cameraNv21Byte.length];
                mNv21ByteCopy = nv21ByteCopy;
            }
            System.arraycopy(cameraNv21Byte, 0, nv21ByteCopy, 0, cameraNv21Byte.length);
            fuTexId = mOnRendererStatusListener.onDrawFrame(nv21ByteCopy, mCameraTextureId,
                    mCameraWidth, mCameraHeight, mCameraFacing, mvpMatrix, mTexMatrix, mSurfaceTexture.getTimestamp());
        }
        if (fuTexId > 0) {
            mProgramTexture2d.drawFrame(fuTexId, mTexMatrix, mvpMatrix);
        } else {
            mProgramTextureOes.drawFrame(mCameraTextureId, mTexMatrix, mvpMatrix);
        }

        mGlTextureView.requestRender();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        mCameraNv21Byte = data;
        mCamera.addCallbackBuffer(data);
        mGlTextureView.requestRender();
    }

    private void openCamera(final int cameraFacing) {
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
            int numCameras = Camera.getNumberOfCameras();
            if (numCameras <= 0) {
                throw new RuntimeException("No cameras");
            }
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == cameraFacing) {
                    cameraId = i;
                    mCamera = Camera.open(i);
                    mCameraFacing = cameraFacing;
                    break;
                }
            }
            if (mCamera == null) {
                cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
                Camera.getCameraInfo(cameraId, info);
                mCamera = Camera.open(cameraId);
                mCameraFacing = cameraId;
            }
            mCameraOrientation = info.orientation;
            CameraUtils.setCameraDisplayOrientation(mActivity, cameraId, mCamera);
            Camera.Parameters parameters = mCamera.getParameters();
            CameraUtils.setFocusModes(parameters);
            int[] size = CameraUtils.choosePreviewSize(parameters, mCameraWidth, mCameraHeight);
            mCameraWidth = size[0];
            mCameraHeight = size[1];
            parameters.setPreviewFormat(ImageFormat.NV21);
            CameraUtils.setVideoStabilization(parameters);
            mCamera.setParameters(parameters);

            Log.d(TAG, "openCamera. facing: " + (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK
                    ? "back" : "front") + ", orientation:" + mCameraOrientation + ", cameraWidth:" + mCameraWidth
                    + ", cameraHeight:" + mCameraHeight);
        } catch (Exception e) {
            Log.e(TAG, "openCamera: ", e);
            releaseCamera();
        }
        confirmMvpMatrix();
    }

    private void startPreview() {
        if (mCameraTextureId <= 0 || mCamera == null || mIsPreviewing) {
            return;
        }
        if (mPreviewCallbackBuffer == null) {
            mPreviewCallbackBuffer = new byte[PREVIEW_BUFFER_COUNT][mCameraWidth * mCameraHeight
                    * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8];
        }
        if (mSurfaceTexture == null) {
            mSurfaceTexture = new SurfaceTexture(mCameraTextureId);
        }
        try {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(this);
            for (byte[] bytes : mPreviewCallbackBuffer) {
                mCamera.addCallbackBuffer(bytes);
            }
            mCamera.setPreviewTexture(mSurfaceTexture);
            mCamera.startPreview();
            mIsPreviewing = true;
            Log.d(TAG, "startPreview: cameraTexId:" + mCameraTextureId);
            mOnRendererStatusListener.onCameraChanged(mCameraFacing, mCameraOrientation);
        } catch (Exception e) {
            Log.e(TAG, "startPreview: ", e);
        }
    }

    private void releaseCamera() {
        Log.d(TAG, "releaseCamera()");
        try {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewTexture(null);
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "releaseCamera: ", e);
        }
        mCameraNv21Byte = null;
        mNv21ByteCopy = null;
        mIsPreviewing = false;
    }

    private void confirmMvpMatrix() {
        mMvpMatrix = GlUtil.changeMvpMatrixCrop(GlUtil.IDENTITY_MATRIX, mViewWidth, mViewHeight, mCameraHeight, mCameraWidth);
    }

    private void destroyGlSurface() {
        Log.d(TAG, "destroyGlSurface: ");
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mCameraTextureId > 0) {
            GLES20.glDeleteTextures(1, new int[]{mCameraTextureId}, 0);
            mCameraTextureId = 0;
        }
        if (mProgramTexture2d != null) {
            mProgramTexture2d.release();
            mProgramTexture2d = null;
        }
        if (mProgramTextureOes != null) {
            mProgramTextureOes.release();
            mProgramTextureOes = null;
        }

        mOnRendererStatusListener.onSurfaceDestroy();
    }

    private void startBackgroundThread() {
        HandlerThread backgroundThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        mBackgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundHandler != null) {
            mBackgroundHandler.getLooper().quitSafely();
            mBackgroundHandler = null;
        }
    }

    public interface OnRendererStatusListener {
        /**
         * Called when surface is created or recreated.
         */
        void onSurfaceCreated();

        /**
         * Called when surface'size changed.
         *
         * @param viewWidth
         * @param viewHeight
         */
        void onSurfaceChanged(int viewWidth, int viewHeight);

        /**
         * Called when drawing current frame
         *
         * @param nv21Byte
         * @param texId
         * @param cameraWidth
         * @param cameraHeight
         * @param cameraFacing
         * @param mvpMatrix
         * @param texMatrix
         * @param timeStamp
         * @return
         */
        int onDrawFrame(byte[] nv21Byte, int texId, int cameraWidth, int cameraHeight, int cameraFacing,
                        float[] mvpMatrix, float[] texMatrix, long timeStamp);

        /**
         * Called when surface is destroyed
         */
        void onSurfaceDestroy();

        /**
         * Called when camera changed
         *
         * @param cameraFacing
         * @param cameraOrientation
         */
        void onCameraChanged(int cameraFacing, int cameraOrientation);
    }

}
