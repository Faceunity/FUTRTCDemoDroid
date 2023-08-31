package com.tencent.trtc.thirdbeauty;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.basic.TRTCBaseActivity;
import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.data.FaceUnityDataFactory;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.profile.CSVUtils;
import com.faceunity.nama.profile.Constant;
import com.faceunity.nama.ui.FaceUnityView;
import com.tencent.liteav.TXLiteAVCode;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;
import com.tencent.trtc.debug.GenerateTestUserSig;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TRTC 第三方美颜页面
 * 接入步骤如下：
 * - 下载 https://github.com/Faceunity/FUTRTCDemoDroid 工程，将 faceunity 模块添加到工程中；
 * - 如需指定应用的 so 架构，可以修改当前模块 build.gradle：
 * android {
 * // ...
 * defaultConfig {
 * // ...
 * ndk {
 * abiFilters 'armeabi-v7a', 'arm64-v8a'
 * }
 * }
 * }
 * - 根据需要初始化美颜模块 {@link FURenderer}:
 * FURenderer.setup(getApplicationContext());
 * mFURenderer = new FURenderer.Builder(getApplicationContext())
 * .setCreateEglContext(false)
 * .setInputTextureType(0)    //TRTC 这里用的0 TEXTURE_2D
 * .setCreateFaceBeauty(true)
 * .build();
 * - TRTC 设置 {@link TRTCCloud#setLocalVideoProcessListener} 回调, 详见API说明文档 {https://liteav.sdk.qcloud
 * .com/doc/api/zh-cn/group__TRTCCloud__android.html#a0b565dc8c77df7fb826f0c45d8ad2d85}
 * - 在 {@link TRTCCloudListener.TRTCVideoFrameListener#onProcessVideoFrame} 回调方法中使用第三方美颜处理视频数据，详见API说明文档 {https
 * ://liteav.sdk.qcloud.com/doc/api/zh-cn/group__TRTCCloudListener__android.html#a22afb08b2a1a18563c7be28c904b166a}
 * Third-Party Beauty Filters
 * The steps are detailed below:
 * - Download FaceUnity at https://github.com/Faceunity/FUTRTCDemoDroid and import it to your project.
 * You can modify `build.gradle` of the current module to specify SO architecture for the app:
 * android {
 * defaultConfig {
 * ndk {
 * abiFilters 'armeabi-v7a', 'arm64-v8a'
 * - Initialize the beauty filter module {@link FURenderer} as needed:
 * FURenderer.setup(getApplicationContext());
 * mFURenderer = new FURenderer.Builder(getApplicationContext())
 * .setCreateEglContext(false)
 * .setInputTextureType(0)    // In TRTC, the parameter is `0` (TEXTURE_2D)
 * .setCreateFaceBeauty(true)
 * .build();
 * - For how to set the callback using {@link TRTCCloud#setLocalVideoProcessListener}, see the API document {https
 * ://liteav.sdk.qcloud.com/doc/api/zh-cn/group__TRTCCloud__android.html#a0b565dc8c77df7fb826f0c45d8ad2d85}.
 * - For how to use third-party beauty filters to process video data in the
 * {@link TRTCCloudListener.TRTCVideoFrameListener#onProcessVideoFrame} callback, see the API document {https
 * ://liteav.sdk.qcloud.com/doc/api/zh-cn/group__TRTCCloudListener__android.html#a22afb08b2a1a18563c7be28c904b166a}.
 */
public class ThirdBeautyFaceUnityActivity extends TRTCBaseActivity implements View.OnClickListener, SensorEventListener {

    private static final String TAG = ThirdBeautyFaceUnityActivity.class.getName();

    private ImageView mImageBack;
    private TextView mTextTitle;
    private Button mButtonStartPush;
    private EditText mEditRoomId;
    private EditText mEditUserId;
    private SeekBar mSeekBlurLevel;
    private TextView mTextBlurLevel;
    private TextView mtvTracking;
    private TXCloudVideoView mTXCloudPreviewView;
    private List<TXCloudVideoView> mRemoteVideoList;

    private TRTCCloud mTRTCCloud;
    private List<String> mRemoteUserIdList;
    private boolean mStartPushFlag = false;

    /*相芯科技相关*/
    private FaceUnityDataFactory mFaceUnityDataFactory;
    private FURenderer mFURenderer;

    private boolean hasRelease;

    private SensorManager mSensorManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third_beauty);

        getSupportActionBar().hide();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        FURenderer.getInstance().setup(getApplicationContext());
        mTRTCCloud = TRTCCloud.sharedInstance(getApplicationContext());
        mTRTCCloud.setListener(new TRTCCloudImplListener(ThirdBeautyFaceUnityActivity.this));
        if (checkPermission()) {
            initView();
            initData();
        }
    }

    private void initFuView() {
        mFURenderer = FURenderer.getInstance();
        FaceUnityView faceUnityView = findViewById(R.id.fu_view);
        faceUnityView.setVisibility(View.VISIBLE);
        mFaceUnityDataFactory = new FaceUnityDataFactory(-1);
        faceUnityView.bindDataFactory(mFaceUnityDataFactory);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void enterRoom(String roomId, String userId) {
        TRTCCloudDef.TRTCParams mTRTCParams = new TRTCCloudDef.TRTCParams();
        mTRTCParams.sdkAppId = GenerateTestUserSig.SDKAPPID;
        mTRTCParams.userId = userId;
        mTRTCParams.roomId = Integer.parseInt(roomId);
        mTRTCParams.userSig = GenerateTestUserSig.genTestUserSig(mTRTCParams.userId);
        mTRTCParams.role = TRTCCloudDef.TRTCRoleAnchor;

        mTRTCCloud.startLocalPreview(true, mTXCloudPreviewView);
        mTRTCCloud.startLocalAudio(TRTCCloudDef.TRTC_AUDIO_QUALITY_DEFAULT);
        mTRTCCloud.enterRoom(mTRTCParams, TRTCCloudDef.TRTC_APP_SCENE_LIVE);

        TRTCCloudDef.TRTCVideoEncParam encParam = new TRTCCloudDef.TRTCVideoEncParam();
        encParam.videoResolution = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_1280_720;
        encParam.videoBitrate = 1200;
        encParam.videoResolutionMode = TRTCCloudDef.TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT;
        encParam.videoFps = 25;
        mTRTCCloud.setVideoEncoderParam(encParam);
    }

    private void exitRoom() {
        if (mTRTCCloud != null) {
            mTRTCCloud.stopAllRemoteView();
            mTRTCCloud.stopLocalAudio();
            mTRTCCloud.stopLocalPreview();
            mTRTCCloud.exitRoom();
        }
    }

    private void destroyRoom() {
        if (mTRTCCloud != null) {
            exitRoom();
            mTRTCCloud.setListener(null);
        }
        mTRTCCloud = null;
        TRTCCloud.destroySharedInstance();
    }

    private void initView() {
        mRemoteUserIdList = new ArrayList<>();
        mRemoteVideoList = new ArrayList<>();

        mImageBack = findViewById(R.id.iv_back);
        mTextTitle = findViewById(R.id.tv_room_number);
        mButtonStartPush = findViewById(R.id.btn_start_push);
        mEditRoomId = findViewById(R.id.et_room_id);
        mEditUserId = findViewById(R.id.et_user_id);
        mSeekBlurLevel = findViewById(R.id.sb_blur_level);
        mTextBlurLevel = findViewById(R.id.tv_blur_level);
        mTXCloudPreviewView = findViewById(R.id.txcvv_main_local);
        mtvTracking = findViewById(R.id.tv_tracking);

        mRemoteVideoList.add((TXCloudVideoView) findViewById(R.id.txcvv_video_remote1));
        mRemoteVideoList.add((TXCloudVideoView) findViewById(R.id.txcvv_video_remote2));
        mRemoteVideoList.add((TXCloudVideoView) findViewById(R.id.txcvv_video_remote3));
        mRemoteVideoList.add((TXCloudVideoView) findViewById(R.id.txcvv_video_remote4));
        mRemoteVideoList.add((TXCloudVideoView) findViewById(R.id.txcvv_video_remote5));
        mRemoteVideoList.add((TXCloudVideoView) findViewById(R.id.txcvv_video_remote6));

        mImageBack.setOnClickListener(this);
        mButtonStartPush.setOnClickListener(this);

        String time = String.valueOf(System.currentTimeMillis());
        String userId = time.substring(time.length() - 8);
        mEditUserId.setText(userId);
        mTextTitle.setText(getString(R.string.thirdbeauty_room_id) + ":" + mEditRoomId.getText().toString());
        initFuView();
    }

    private void initData() {
        //1. 设置 TRTCVideoFrameListener 回调, 详见API说明文档 {https://liteav.sdk.qcloud.com/doc/api/zh-cn/group__TRTCCloud__android.html#a0b565dc8c77df7fb826f0c45d8ad2d85}
        mTRTCCloud.setLocalVideoProcessListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D,
                TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE, new TRTCCloudListener.TRTCVideoFrameListener() {
                    @Override
                    public void onGLContextCreated() {
                        if (mFURenderer != null) {
                            mFaceUnityDataFactory.bindCurrentRenderer();
                        }
                        initCsvUtil();
                        hasRelease = false;
                        mFURenderer.prepareRenderer(new FURendererListener() {
                            @Override
                            public void onPrepare() {
                            }

                            @Override
                            public void onTrackStatusChanged(FUAIProcessorEnum type, int status) {
                                runOnUiThread(() -> {
                                    mtvTracking.setText(type == FUAIProcessorEnum.FACE_PROCESSOR ? R.string.toast_not_detect_face : R.string.toast_not_detect_body);
                                    mtvTracking.setVisibility(status > 0 ? View.INVISIBLE : View.VISIBLE);
                                });
                            }

                            @Override
                            public void onFpsChanged(double fps, double callTime) {

                            }

                            @Override
                            public void onRelease() {
                                hasRelease = true;
                            }
                        });
                    }

                    @Override
                    public int onProcessVideoFrame(TRTCCloudDef.TRTCVideoFrame srcFrame,
                                                   TRTCCloudDef.TRTCVideoFrame dstFrame) {
                        //3. 调用第三方美颜模块处理, 详见API说明文档 {https://liteav.sdk.qcloud.com/doc/api/zh-cn/group__TRTCCloudListener__android.html#a22afb08b2a1a18563c7be28c904b166a}
                        if (mCSVUtils != null) {
                            start = System.nanoTime();
                        }
                        mFURenderer.setInputInfo(CameraFacingEnum.CAMERA_FRONT);
                        dstFrame.texture.textureId = mFURenderer
                                .onDrawFrameSingleInput(srcFrame.texture.textureId, srcFrame.width, srcFrame.height);
                        if (mCSVUtils != null) {
                            long renderTime = System.nanoTime() - start;
                            mCSVUtils.writeCsv(null, renderTime);
                        }
                        return 0;
                    }

                    @Override
                    public void onGLContextDestory() {
                        //4. GLContext 销毁
                        mFURenderer.release();
                    }
                });
    }

    long start = 0;
    private CSVUtils mCSVUtils;

    private void initCsvUtil() {
        mCSVUtils = new CSVUtils(getApplicationContext());
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String dateStrDir = format.format(new Date(System.currentTimeMillis()));
        dateStrDir = dateStrDir.replaceAll("-", "").replaceAll("_", "");
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
        String dateStrFile = df.format(new Date());
        String filePath = Constant.filePath + dateStrDir + File.separator + "excel-" + dateStrFile + ".csv";
        Log.d(TAG, "initLog: CSV file path:" + filePath);
        StringBuilder headerInfo = new StringBuilder();
        headerInfo.append("version：").append(FURenderer.getInstance().getVersion()).append(CSVUtils.COMMA)
                .append("机型：").append(android.os.Build.MANUFACTURER).append(android.os.Build.MODEL).append(CSVUtils.COMMA)
                .append("处理方式：双输入纹理输出").append(CSVUtils.COMMA)
                .append("编码方式：硬件编码").append(CSVUtils.COMMA);
//                .append("编码分辨率：").append(ENCODE_FRAME_WIDTH).append("x").append(ENCODE_FRAME_HEIGHT).append(CSVUtils.COMMA)
//                .append("编码帧率：").append(ENCODE_FRAME_FPS).append(CSVUtils.COMMA)
//                .append("编码码率：").append(ENCODE_FRAME_BITRATE).append(CSVUtils.COMMA)
//                .append("预览分辨率：").append(CAPTURE_WIDTH).append("x").append(CAPTURE_HEIGHT).append(CSVUtils.COMMA)
//                .append("预览帧率：").append(CAPTURE_FRAME_RATE).append(CSVUtils.COMMA);
        mCSVUtils.initHeader(filePath, headerInfo);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.iv_back) {
            finish();
        } else if (id == R.id.btn_start_push) {
            String roomId = mEditRoomId.getText().toString();
            String userId = mEditUserId.getText().toString();
            if (!mStartPushFlag) {
                if (!TextUtils.isEmpty(roomId) && !TextUtils.isEmpty(userId)) {
                    mButtonStartPush.setText(R.string.thirdbeauty_stop_push);
                    enterRoom(roomId, userId);
                    mStartPushFlag = true;
                } else {
                    Toast.makeText(ThirdBeautyFaceUnityActivity.this,
                            getString(R.string.thirdbeauty_please_input_roomid_and_userid), Toast.LENGTH_SHORT).show();
                }
            } else {
                mButtonStartPush.setText(R.string.thirdbeauty_start_push);
                exitRoom();
                mStartPushFlag = false;
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y)) {
                    if (mFURenderer != null) {
                        mFURenderer.setDeviceOrientation(x > 0 ? 0 : 180);
                    }
                } else {
                    if (mFURenderer != null) {
                        mFURenderer.setDeviceOrientation(y > 0 ? 90 : 270);
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected class TRTCCloudImplListener extends TRTCCloudListener {

        private WeakReference<ThirdBeautyFaceUnityActivity> mContext;

        public TRTCCloudImplListener(ThirdBeautyFaceUnityActivity activity) {
            super();
            mContext = new WeakReference<>(activity);
        }

        @Override
        public void onUserVideoAvailable(String userId, boolean available) {
            if (available) {
                mRemoteUserIdList.add(userId);
            } else {
                if (mRemoteUserIdList.contains(userId)) {
                    mRemoteUserIdList.remove(userId);
                    mTRTCCloud.stopRemoteView(userId, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                }
            }
            refreshRemoteVideo();
        }

        private void refreshRemoteVideo() {
            if (mRemoteUserIdList.size() > 0) {
                for (int i = 0; i < mRemoteUserIdList.size() || i < 6; i++) {
                    if (i < mRemoteUserIdList.size() && !TextUtils.isEmpty(mRemoteUserIdList.get(i))) {
                        mRemoteVideoList.get(i).setVisibility(View.VISIBLE);
                        mTRTCCloud.startRemoteView(mRemoteUserIdList.get(i), TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG,
                                mRemoteVideoList.get(i));
                    } else {
                        mRemoteVideoList.get(i).setVisibility(View.GONE);
                    }
                }
            } else {
                for (int i = 0; i < 6; i++) {
                    mRemoteVideoList.get(i).setVisibility(View.GONE);
                }
            }
        }

        @Override
        public void onExitRoom(int i) {
            mRemoteUserIdList.clear();
            refreshRemoteVideo();
        }

        @Override
        public void onError(int errCode, String errMsg, Bundle extraInfo) {
            Log.d(TAG, "sdk callback onError");
            ThirdBeautyFaceUnityActivity activity = mContext.get();
            if (activity != null) {
                Toast.makeText(activity, "onError: " + errMsg + "[" + errCode + "]", Toast.LENGTH_SHORT).show();
                if (errCode == TXLiteAVCode.ERR_ROOM_ENTER_FAIL) {
                    activity.exitRoom();
                }
            }
        }
    }

    @Override
    protected void onPermissionGranted() {
        initView();
        initData();
    }

    @Override
    protected void onDestroy() {
        if (!hasRelease) {
            mFURenderer.release();
        }
        destroyRoom();
        super.onDestroy();
    }
}
