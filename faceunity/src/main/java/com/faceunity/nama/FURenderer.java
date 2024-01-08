package com.faceunity.nama;

import android.content.Context;
import android.util.Log;

import com.faceunity.core.callback.OperateCallback;
import com.faceunity.core.entity.FURenderInputData;
import com.faceunity.core.entity.FURenderOutputData;
import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.core.enumeration.FUAITypeEnum;
import com.faceunity.core.enumeration.FUTransformMatrixEnum;
import com.faceunity.core.faceunity.FUAIKit;
import com.faceunity.core.faceunity.FURenderConfig;
import com.faceunity.core.faceunity.FURenderKit;
import com.faceunity.core.faceunity.FURenderManager;
import com.faceunity.core.model.facebeauty.FaceBeautyBlurTypeEnum;
import com.faceunity.core.utils.FULogger;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.utils.FuDeviceUtils;

import org.jetbrains.annotations.NotNull;

import java.io.File;


/**
 * DESC：
 * Created on 2021/4/26
 */
public class FURenderer extends IFURenderer {


    public volatile static FURenderer INSTANCE;

    public static FURenderer getInstance() {
        if (INSTANCE == null) {
            synchronized (FURenderer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FURenderer();
                    INSTANCE.mFURenderKit = FURenderKit.getInstance();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 状态回调监听
     */
    private FURendererListener mFURendererListener;


    /* 特效FURenderKit*/
    private FURenderKit mFURenderKit;

    /* AI道具*/
    private String BUNDLE_AI_FACE = "model" + File.separator + "ai_face_processor.bundle";
    private String BUNDLE_AI_HUMAN = "model" + File.separator + "ai_human_processor.bundle";

    /* GL 线程 ID */
    private Long mGlThreadId = 0L;
    /* 渲染开关标识 */
    private volatile boolean mRendererSwitch = false;
    /*检测类型*/
    private FUAIProcessorEnum aIProcess = FUAIProcessorEnum.FACE_PROCESSOR;
    /*检测标识*/
    private int aIProcessTrackStatus = -1;

    /**
     * 初始化鉴权
     *
     * @param context
     */
    @Override
    public void setup(Context context) {
        FURenderManager.setKitDebug(FULogger.LogLevel.TRACE);
        FURenderManager.setCoreDebug(FULogger.LogLevel.ERROR);
        FURenderManager.registerFURender(context, authpack.A(), new OperateCallback() {
            @Override
            public void onSuccess(int i, @NotNull String s) {
                if (i == FURenderConfig.OPERATE_SUCCESS_AUTH) {
                    mFURenderKit.getFUAIController().loadAIProcessor(BUNDLE_AI_FACE, FUAITypeEnum.FUAITYPE_FACEPROCESSOR);
                    mFURenderKit.getFUAIController().loadAIProcessor(BUNDLE_AI_HUMAN, FUAITypeEnum.FUAITYPE_HUMAN_PROCESSOR);
                }
            }

            @Override
            public void onFail(int i, @NotNull String s) {
            }
        });
    }

    /**
     * 获取 Nama SDK 版本号，例如 7_0_0_phy_8b882f6_91a980f
     *
     * @return version
     */
    public String getVersion() {
        return mFURenderKit.getVersion();
    }

    /**
     * 开启合成状态
     */
    @Override
    public void prepareRenderer(FURendererListener mFURendererListener) {
        this.mFURendererListener = mFURendererListener;
        mRendererSwitch = true;
        queueEvent(() -> mGlThreadId = Thread.currentThread().getId());
        if (mFURendererListener != null)
            mFURendererListener.onPrepare();
    }


    /**
     * 单输入接口，输入texture，必须在具有 GL 环境的线程调用
     *
     * @param texId  纹理 ID
     * @param width  宽
     * @param height 高
     * @return
     */
    public int onDrawFrameSingleInput(int texId, int width, int height) {
        prepareDrawFrame();
        if (!mRendererSwitch) {
            return texId;
        }
        FURenderInputData inputData = new FURenderInputData(width, height);
        inputData.setTexture(new FURenderInputData.FUTexture(inputTextureType, texId));
        FURenderInputData.FURenderConfig config = inputData.getRenderConfig();
        config.setExternalInputType(externalInputType);
        config.setInputOrientation(inputOrientation);
        config.setDeviceOrientation(deviceOrientation);
        config.setInputBufferMatrix(inputBufferMatrix);
        config.setInputTextureMatrix(inputTextureMatrix);
        config.setCameraFacing(cameraFacing);
        config.setOutputMatrix(outputMatrix);
        mCallStartTime = System.nanoTime();
        //高性能设备
        if (FUConfig.DEVICE_LEVEL > FuDeviceUtils.DEVICE_LEVEL_ONE) {
            cheekFaceNum();
        }
        FURenderOutputData outputData = mFURenderKit.renderWithInput(inputData);
        mSumCallTime += System.nanoTime() - mCallStartTime;
        if (outputData != null && outputData.getTexture() != null && outputData.getTexture().getTexId() > 0) {
            return outputData.getTexture().getTexId();
        }
        return 0;
    }

    /**
     * 检查当前人脸数量
     */
    private void cheekFaceNum() {
        //根据有无人脸 + 设备性能 判断开启的磨皮类型
        float faceProcessorGetConfidenceScore = FUAIKit.getInstance().getFaceProcessorGetConfidenceScore(0);
        if (faceProcessorGetConfidenceScore >= 0.95) {
            //高端手机并且检测到人脸开启均匀磨皮，人脸点位质
            if (FURenderKit.getInstance() != null && FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.EquallySkin) {
                FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.EquallySkin);
                FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(true);
            }
        } else {
            if (FURenderKit.getInstance() != null && FURenderKit.getInstance().getFaceBeauty() != null && FURenderKit.getInstance().getFaceBeauty().getBlurType() != FaceBeautyBlurTypeEnum.FineSkin) {
                FURenderKit.getInstance().getFaceBeauty().setBlurType(FaceBeautyBlurTypeEnum.FineSkin);
                FURenderKit.getInstance().getFaceBeauty().setEnableBlurUseMask(false);
            }
        }
    }

    public void setInputInfo(CameraFacingEnum cameraFacing) {
        setInputOrientation(180);
        setCameraFacing(cameraFacing);
        setInputBufferMatrix(FUTransformMatrixEnum.CCROT90_FLIPHORIZONTAL);
        setInputTextureMatrix(FUTransformMatrixEnum.CCROT90_FLIPHORIZONTAL);
        setOutputMatrix(FUTransformMatrixEnum.CCROT270);

    }

    /**
     * 双输入接口，输入 buffer 和 texture，必须在具有 GL 环境的线程调用
     * 由于省去数据拷贝，性能相对最优，优先推荐使用。
     * 缺点是无法保证 buffer 和纹理对齐，可能出现点位和效果对不上的情况。
     *
     * @param img    NV21 buffer
     * @param texId  纹理 ID
     * @param width  宽
     * @param height 高
     * @return
     */
    @Override
    public int onDrawFrameDualInput(byte[] img, int texId, int width, int height) {
        prepareDrawFrame();
        if (!mRendererSwitch) {
            return texId;
        }
        FURenderInputData inputData = new FURenderInputData(width, height);
        //buffer为空，单纹理输入
        inputData.setImageBuffer(new FURenderInputData.FUImageBuffer(inputBufferType, img));
        inputData.setTexture(new FURenderInputData.FUTexture(inputTextureType, texId));
        FURenderInputData.FURenderConfig config = inputData.getRenderConfig();
        config.setExternalInputType(externalInputType);
        config.setInputOrientation(inputOrientation);
        config.setDeviceOrientation(deviceOrientation);
        config.setInputBufferMatrix(inputBufferMatrix);
        config.setInputTextureMatrix(inputTextureMatrix);
        config.setOutputMatrix(outputMatrix);
        config.setCameraFacing(cameraFacing);
        FURenderOutputData outputData = mFURenderKit.renderWithInput(inputData);
        if (outputData.getTexture() != null && outputData.getTexture().getTexId() > 0) {
            return outputData.getTexture().getTexId();
        }
        return texId;
    }

    /**
     * 类似 GLSurfaceView 的 queueEvent 机制，把任务抛到 GL 线程执行。
     *
     * @param runnable
     */
    @Override
    public void queueEvent(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        if (mGlThreadId == Thread.currentThread().getId()) {
            runnable.run();
        }
    }


    /**
     * 释放资源
     */
    @Override
    public void release() {
        mRendererSwitch = false;
        mGlThreadId = 0L;
        mFURenderKit.release();
        aIProcessTrackStatus = -1;
        if (mFURendererListener != null) {
            mFURendererListener.onRelease();
            mFURendererListener = null;
        }
    }


    /**
     * 渲染前置执行
     *
     * @return
     */
    private void prepareDrawFrame() {
        benchmarkFPS();
        // AI检测
        trackStatus();
    }

    //region AI识别

    /**
     * 设置检测类型
     *
     * @param type
     */
    @Override
    public void setAIProcessTrackType(FUAIProcessorEnum type) {
        aIProcess = type;
        aIProcessTrackStatus = -1;
    }

    /**
     * 设置FPS检测
     *
     * @param enable
     */
    @Override
    public void setMarkFPSEnable(boolean enable) {
        mIsRunBenchmark = enable;
    }


    /**
     * AI识别数目检测
     */
    private void trackStatus() {
        int trackCount;
        if (aIProcess == FUAIProcessorEnum.HAND_GESTURE_PROCESSOR) {
            trackCount = mFURenderKit.getFUAIController().handProcessorGetNumResults();
        } else if (aIProcess == FUAIProcessorEnum.HUMAN_PROCESSOR) {
            trackCount = mFURenderKit.getFUAIController().humanProcessorGetNumResults();
        } else {
            trackCount = mFURenderKit.getFUAIController().isTracking();
        }
        if (trackCount != aIProcessTrackStatus) {
            aIProcessTrackStatus = trackCount;
        } else {
            return;
        }
        if (mFURendererListener != null) {
            mFURendererListener.onTrackStatusChanged(aIProcess, trackCount);
        }
    }
    //endregion AI识别

    //------------------------------FPS 渲染时长回调相关定义------------------------------------

    private static final int NANO_IN_ONE_MILLI_SECOND = 1_000_000;
    private static final int NANO_IN_ONE_SECOND = 1_000_000_000;
    private static final int FRAME_COUNT = 20;
    private boolean mIsRunBenchmark = false;
    private int mCurrentFrameCount;
    private long mLastFrameTimestamp;
    private long mSumCallTime;
    private long mCallStartTime;

    private void benchmarkFPS() {
        if (!mIsRunBenchmark) {
            return;
        }
        if (++mCurrentFrameCount == FRAME_COUNT) {
            long tmp = System.nanoTime();
            double fps = (double) NANO_IN_ONE_SECOND / ((double) (tmp - mLastFrameTimestamp) / FRAME_COUNT);
            double renderTime = (double) mSumCallTime / FRAME_COUNT / NANO_IN_ONE_MILLI_SECOND;
            mLastFrameTimestamp = tmp;
            mSumCallTime = 0;
            mCurrentFrameCount = 0;

            Log.d("test", "fps: " + fps + ",rendertime: " + renderTime);
            if (mFURendererListener != null) {
                mFURendererListener.onFpsChanged(fps, renderTime);
            }
        }
    }


}
