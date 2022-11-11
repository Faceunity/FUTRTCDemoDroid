package com.tencent.liteav.meeting.model.impl;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.EGL14;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.faceunity.core.enumeration.CameraFacingEnum;
import com.faceunity.core.enumeration.FUAIProcessorEnum;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.listener.FURendererListener;
import com.faceunity.nama.profile.CSVUtils;
import com.faceunity.nama.profile.Constant;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.meeting.model.TRTCMeeting;
import com.tencent.liteav.meeting.model.TRTCMeetingCallback;
import com.tencent.liteav.meeting.model.TRTCMeetingDef;
import com.tencent.liteav.meeting.model.TRTCMeetingDelegate;
import com.tencent.liteav.meeting.model.impl.base.TRTCLogger;
import com.tencent.liteav.meeting.model.impl.base.TXCallback;
import com.tencent.liteav.meeting.model.impl.base.TXUserInfo;
import com.tencent.liteav.meeting.model.impl.base.TXUserListCallback;
import com.tencent.liteav.meeting.model.impl.room.ITXRoomServiceDelegate;
import com.tencent.liteav.meeting.model.impl.room.impl.TXRoomService;
import com.tencent.liteav.meeting.model.impl.trtc.ITXTRTCMeetingDelegate;
import com.tencent.liteav.meeting.model.impl.trtc.TXTRTCMeeting;
import com.tencent.liteav.meeting.model.impl.trtc.TXTRTCMixUser;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TRTCMeeting 的实现类
 */
public class TRTCMeetingImpl extends TRTCMeeting implements ITXTRTCMeetingDelegate, ITXRoomServiceDelegate,
        SensorEventListener {
    private static final String TAG = "TRTCMeetingImpl";
    private static TRTCMeetingImpl sInstance;

    private TRTCMeetingDelegate mTRTCMeetingDelegate;
    // 所有调用都切到主线程使用，保证内部多线程安全问题
    private Handler mMainHandler;
    // 外部可指定的回调线程
    private Handler mDelegateHandler;
    private String mRecordingAudioPath;
    private Map<String, TRTCMeetingDef.UserInfo> mUserInfoMap;
    // 对外的辅流id - 真正的userid 隐射
    private Map<String, String> mSubStreamMap;
    private boolean mIsUseFrontCamera;
    private int mSdkAppId;
    private List<String> mUserIdList;
    private String mUserSig;
    private String mUserId;
    private int mRoomId;
    private boolean mIsGetLiveUrl;
    private TRTCCloud mTRTCCloud;

    private boolean mIsFuEffect;
    private SensorManager mSensorManager;
    private FURenderer mFURenderer;
    private Context mContext;

    private TRTCMeetingImpl(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());
        mDelegateHandler = new Handler(Looper.getMainLooper());
        mUserInfoMap = new HashMap<>();
        mSubStreamMap = new HashMap<>();
        mUserIdList = new ArrayList<>();
        TXTRTCMeeting.getInstance().init(context);
        TXTRTCMeeting.getInstance().setDelegate(this);
        TXRoomService.getInstance().init(context);
        TXRoomService.getInstance().setDelegate(this);
        mTRTCCloud = TRTCCloud.sharedInstance(context);
        clear();
    }

    private void clear() {
        mIsUseFrontCamera = true;
        mRecordingAudioPath = null;
        mRoomId = 0;
        mSubStreamMap.clear();
        mUserInfoMap.clear();
        mUserIdList.clear();
        mIsGetLiveUrl = false;
    }

    private void destroy() {
        clear();
        TRTCCloud.destroySharedInstance();
    }

    private void runOnMainThread(Runnable runnable) {
        Handler handler = mMainHandler;
        if (handler != null) {
            if (handler.getLooper() == Looper.myLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }
        } else {
            runnable.run();
        }
    }

    private void runOnDelegateThread(Runnable runnable) {
        Handler handler = mDelegateHandler;
        if (handler != null) {
            if (handler.getLooper() == Looper.myLooper()) {
                runnable.run();
            } else {
                handler.post(runnable);
            }
        } else {
            runnable.run();
        }
    }

    public static synchronized TRTCMeeting sharedInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TRTCMeetingImpl(context);
        }
        return sInstance;
    }

    public static void destroySharedInstance() {
        if (sInstance != null) {
            sInstance.destroy();
            sInstance = null;
        }
    }

    @Override
    public FURenderer createCustomRenderer(Activity activity, boolean isFrontCamera, boolean isFuEffect) {
        super.createCustomRenderer(activity, isFrontCamera, isFuEffect);
        mIsFuEffect = isFuEffect;
        if (mIsFuEffect) {
            mFURenderer = FURenderer.getInstance();
        }

        return mFURenderer;
    }

    @Override
    public void setDelegate(final TRTCMeetingDelegate delegate) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mTRTCMeetingDelegate = delegate;
            }
        });
    }

    @Override
    public void setDelegateHandler(Handler handler) {
        mDelegateHandler = handler;
    }

    @Override
    public void login(final int sdkAppId, final String userId, final String userSig, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mSdkAppId = sdkAppId;
                mUserSig = userSig;
                mUserId = userId;
                TXRoomService.getInstance().login(sdkAppId, userId, userSig, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void logout(final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().logout(new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void setSelfProfile(final String userName, final String avatarURL, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().setSelfProfile(userName, avatarURL, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void createMeeting(final int roomId, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                clear();
                //IM 先创建房间
                mRoomId = roomId;
                TXRoomService.getInstance().createRoom(String.valueOf(roomId), String.valueOf(roomId), "", new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (code == 0) {
                            // IM 创建房间成功进入trtc房间
                            TXTRTCMeeting.getInstance().enterRoom(mSdkAppId, String.valueOf(roomId), mUserId, mUserSig,
                                    new TXCallback() {
                                        @Override
                                        public void onCallback(int code, String msg) {
                                            if (callback != null) {
                                                callback.onCallback(code, msg);
                                            }
                                        }
                                    });
                        } else {
                            if (callback != null) {
                                callback.onCallback(code, msg);
                            }
                        }
                    }
                });
            }
        });
    }

    @Override
    public void destroyMeeting(int roomId, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().exitRoom(null);
                TXRoomService.getInstance().destroyRoom(new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        clear();
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void enterMeeting(final int roomId, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                clear();
                mRoomId = roomId;
                TXRoomService.getInstance().enterRoom(String.valueOf(roomId), new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                    }
                });
                TXTRTCMeeting.getInstance().enterRoom(mSdkAppId, String.valueOf(roomId), mUserId, mUserSig,
                        new TXCallback() {
                            @Override
                            public void onCallback(int code, String msg) {
                                if (callback != null) {
                                    callback.onCallback(code, msg);
                                }
                            }
                        });
            }
        });
    }

    @Override
    public void leaveMeeting(final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().exitRoom(null);
                if (TXRoomService.getInstance().isOwner()) {
                    TXRoomService.getInstance().destroyRoom(new TXCallback() {
                        @Override
                        public void onCallback(int code, String msg) {
                            clear();
                            if (callback != null) {
                                callback.onCallback(code, msg);
                            }
                        }
                    });
                } else {
                    TXRoomService.getInstance().exitRoom(new TXCallback() {
                        @Override
                        public void onCallback(int code, String msg) {
                            clear();
                            if (callback != null) {
                                callback.onCallback(code, msg);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void getUserInfoList(final TRTCMeetingCallback.UserListCallback userListCallback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().getUserInfo(mUserIdList, new TXUserListCallback() {
                    @Override
                    public void onCallback(int code, String msg, List<TXUserInfo> list) {
                        if (list != null) {
                            for (TXUserInfo info : list) {
                                String userId = info.userId;
                                TRTCMeetingDef.UserInfo userInfo = mUserInfoMap.get(userId);
                                if (userInfo == null) {
                                    userInfo = new TRTCMeetingDef.UserInfo();
                                    mUserInfoMap.put(info.userId, userInfo);
                                }
                                userInfo.userId = info.userId;
                                userInfo.userName = info.userName;
                                userInfo.userAvatar = info.avatarURL;
                            }
                        }
                        if (userListCallback != null) {
                            userListCallback.onCallback(code, msg, new ArrayList<>(mUserInfoMap.values()));
                        }
                    }
                });
            }
        });
    }

    @Override
    public void getUserInfo(final String userId, final TRTCMeetingCallback.UserListCallback userListCallback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                List<String> list = new ArrayList<>();
                final List<TRTCMeetingDef.UserInfo> callbackList = new ArrayList<>();
                //判断是否是辅流
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    //辅流直接从现有的取数据
                    TRTCMeetingDef.UserInfo mainUserInfo = mUserInfoMap.get(realUserId);
                    TRTCMeetingDef.UserInfo sub = new TRTCMeetingDef.UserInfo();
                    sub.userId = userId;
                    if (mainUserInfo != null) {
                        sub.userName = mainUserInfo.userName + "（辅流）";
                        sub.userName = mainUserInfo.userAvatar;
                    }
                    if (userListCallback != null) {
                        userListCallback.onCallback(0, "", callbackList);
                    }
                    return;
                }
                list.add(userId);
                // 不是辅流走这里
                TXRoomService.getInstance().getUserInfo(list, new TXUserListCallback() {
                    @Override
                    public void onCallback(int code, String msg, List<TXUserInfo> list) {
                        if (list != null) {
                            for (TXUserInfo info : list) {
                                String userId = info.userId;
                                TRTCMeetingDef.UserInfo userInfo = mUserInfoMap.get(userId);
                                if (userInfo == null) {
                                    userInfo = new TRTCMeetingDef.UserInfo();
                                    mUserInfoMap.put(info.userId, userInfo);
                                }
                                userInfo.userId = info.userId;
                                userInfo.userName = info.userName;
                                userInfo.userAvatar = info.avatarURL;
                                callbackList.add(userInfo);
                            }
                        }
                        if (userListCallback != null) {
                            userListCallback.onCallback(code, msg, callbackList);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void startRemoteView(final String userId, final TXCloudVideoView view, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                //判断是否辅流播放
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    TXTRTCMeeting.getInstance().startPlaySubStream(realUserId, view, new TXCallback() {
                        @Override
                        public void onCallback(int code, String msg) {
                            if (callback != null) {
                                callback.onCallback(code, msg);
                            }
                        }
                    });
                    return;
                }

                TXTRTCMeeting.getInstance().startPlay(userId, view, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void stopRemoteView(final String userId, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                //判断是否辅流播放
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    TXTRTCMeeting.getInstance().stopPlaySubStream(realUserId, new TXCallback() {
                        @Override
                        public void onCallback(int code, String msg) {
                            if (callback != null) {
                                callback.onCallback(code, msg);
                            }
                        }
                    });
                    return;
                }
                TXTRTCMeeting.getInstance().stopPlay(userId, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void setRemoteViewFillMode(final String userId, final int fillMode) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    TXTRTCMeeting.getInstance().setRemoteSubViewFillMode(realUserId, fillMode);
                    return;
                }
                TXTRTCMeeting.getInstance().setRemoteViewFillMode(userId, fillMode);
            }
        });
    }

    @Override
    public void setRemoteViewRotation(final String userId, final int rotation) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    TXTRTCMeeting.getInstance().setRemoteSubViewRotation(realUserId, rotation);
                    return;
                }
                TXTRTCMeeting.getInstance().setRemoteViewRotation(userId, rotation);
            }
        });
    }

    @Override
    public void muteRemoteAudio(final String userId, final boolean mute) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                // 判断是否辅流播放
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    return;
                }
                TRTCMeetingDef.UserInfo info = mUserInfoMap.get(userId);
                if (info != null) {
                    info.isMuteAudio = mute;
                }
                TXTRTCMeeting.getInstance().muteRemoteAudio(userId, mute);
            }
        });
    }

    @Override
    public void muteRemoteVideoStream(final String userId, final boolean mute) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                // 判断是否辅流播放
                String realUserId = mSubStreamMap.get(userId);
                if (realUserId != null) {
                    return;
                }
                TRTCMeetingDef.UserInfo info = mUserInfoMap.get(userId);
                if (info != null) {
                    info.isMuteVideo = mute;
                }
                TXTRTCMeeting.getInstance().muteRemoteVideoStream(userId, mute);
            }
        });
    }

    @Override
    public void startCameraPreview(final boolean isFront, final TXCloudVideoView view) {
        Log.d(TAG, "startCameraPreview() called with: isFront = [" + isFront + "], view = [" + view + "]");
        mIsUseFrontCamera = isFront;
        if (mIsFuEffect) {
            mTRTCCloud.setLocalVideoProcessListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D,
                    TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE, new TRTCCloudListener.TRTCVideoFrameListener() {
                        @Override
                        public void onGLContextCreated() {
                            Log.i(TAG, "tex onGLContextCreated: " + EGL14.eglGetCurrentContext());
                            initCsvUtil(mContext);
                            mFURenderer.prepareRenderer(new FURendererListener() {
                                @Override
                                public void onPrepare() {

                                }

                                @Override
                                public void onTrackStatusChanged(FUAIProcessorEnum type, int status) {
                                    if (mTrackStatusListener != null) {
                                        mTrackStatusListener.traceStatusChange(type,status);
                                    }
                                }

                                @Override
                                public void onFpsChanged(double fps, double callTime) {

                                }

                                @Override
                                public void onRelease() {
                                    if (mCameraStatusListener != null) {
                                        mCameraStatusListener.closeCameraFinish();
                                    }
                                }
                            });
                        }

                        @Override
                        public int onProcessVideoFrame(TRTCCloudDef.TRTCVideoFrame src, TRTCCloudDef.TRTCVideoFrame dest) {
                            Log.v(TAG, String.format("process video frame, w %d, h %d, tex %d, rotation %d, pixel format %d",
                                    src.width, src.height, src.texture.textureId, src.rotation, src.pixelFormat));
                            mFURenderer.setCameraFacing(mIsUseFrontCamera?CameraFacingEnum.CAMERA_FRONT:CameraFacingEnum.CAMERA_BACK);
                            long start =  System.nanoTime();
                            dest.texture.textureId = mFURenderer.onDrawFrameSingleInput(src.texture.textureId, src.width, src.height);
                            if (mCSVUtils != null) {
                                long renderTime = System.nanoTime() - start;
                                mCSVUtils.writeCsv(null, renderTime);
                            }
                            return 0;
                        }

                        @Override
                        public void onGLContextDestory() {
                            Log.i(TAG, "tex onGLContextDestory: " + EGL14.eglGetCurrentContext());
                            mFURenderer.release();
                        }
                    });

            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            Sensor sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().startCameraPreview(isFront, view, null);
            }
        });
    }

    @Override
    public void stopCameraPreview() {
        Log.d(TAG, "stopCameraPreview() called");
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().stopCameraPreview();
            }
        });
    }

    @Override
    public void switchCamera(final boolean isFront) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isFront != mIsUseFrontCamera) {
                    mIsUseFrontCamera = isFront;
                    TXTRTCMeeting.getInstance().switchCamera();
//                    setVideoEncoderMirror(isFront);
                    if (mIsFuEffect) {
                        mFURenderer.setCameraFacing(mIsUseFrontCamera ? CameraFacingEnum.CAMERA_FRONT : CameraFacingEnum.CAMERA_BACK);
                    }
                }
            }
        });
    }

    @Override
    public void setLocalVideoRenderListener(TRTCCloudListener.TRTCVideoFrameListener listener) {
        if (mTRTCCloud != null) {
            mTRTCCloud.setLocalVideoProcessListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D, TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE, listener);
            mTRTCCloud.setLocalVideoProcessListener(TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_NV21, TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY, listener);
        }
    }

    @Override
    public void setVideoResolution(final int resolution) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setVideoResolution(resolution);
            }
        });
    }

    @Override
    public void setVideoFps(final int fps) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setVideoFps(fps);
            }
        });
    }

    @Override
    public void setVideoBitrate(final int bitrate) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setVideoBitrate(bitrate);
            }
        });
    }

    @Override
    public void setLocalViewMirror(final int type) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setLocalViewMirror(type);
            }
        });
    }

    @Override
    public void setVideoEncoderMirror(final boolean mirror) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setVideoEncoderMirror(mirror);
            }
        });
    }

    @Override
    public void setNetworkQosParam(final TRTCCloudDef.TRTCNetworkQosParam qosParam) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setNetworkQosParam(qosParam);
            }
        });
    }

    @Override
    public void startMicrophone() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().startMicrophone();
            }
        });
    }

    @Override
    public void stopMicrophone() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().stopMicrophone();
            }
        });
    }

    @Override
    public void setAudioQuality(final int quality) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setAudioQuality(quality);
            }
        });
    }

    @Override
    public void setSpeaker(final boolean useSpeaker) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setSpeaker(useSpeaker);
            }
        });
    }

    @Override
    public void muteLocalAudio(final boolean mute) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().muteLocalAudio(mute);
            }
        });
    }

    @Override
    public void setAudioCaptureVolume(final int volume) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setAudioCaptureVolume(volume);
            }
        });
    }

    @Override
    public void setAudioPlayoutVolume(final int volume) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().setAudioPlayoutVolume(volume);
            }
        });
    }

    @Override
    public void startFileDumping(final TRTCCloudDef.TRTCAudioRecordingParams trtcAudioRecordingParams) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().startFileDumping(trtcAudioRecordingParams);
            }
        });
    }

    @Override
    public void stopFileDumping() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().stopFileDumping();
            }
        });
    }

    @Override
    public void enableAudioEvaluation(final boolean enable) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().enableAudioEvaluation(enable);
            }
        });
    }

    @Override
    public TXBeautyManager getBeautyManager() {
        return TXTRTCMeeting.getInstance().getTXBeautyManager();
    }

    @Override
    public void startScreenCapture(final TRTCCloudDef.TRTCVideoEncParam param, final TRTCCloudDef.TRTCScreenShareParams screenShareParams) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().startScreenCapture(param, screenShareParams);
            }
        });
    }

    @Override
    public void stopScreenCapture() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().stopScreenCapture();
            }
        });
    }

    @Override
    public void pauseScreenCapture() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().pauseScreenCapture();
            }
        });
    }

    @Override
    public void resumeScreenCapture() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().resumeScreenCapture();
            }
        });
    }

    @Override
    public String getLiveBroadcastingURL() {
        mIsGetLiveUrl = true;
        updateMixConfig();
        String streamId = TXTRTCMeeting.getInstance().getStreamId();
        TRTCLogger.i(TAG, "getLiveBroadcastingURL:" + streamId);
        return CDN_DOMAIN + (CDN_DOMAIN.endsWith("/") ? "" : "/") + streamId + ".flv";
    }

    @Override
    public void sendRoomTextMsg(final String message, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().sendRoomTextMsg(message, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void sendRoomCustomMsg(final String cmd, final String message, final TRTCMeetingCallback.ActionCallback callback) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TXRoomService.getInstance().sendRoomCustomMsg(cmd, message, new TXCallback() {
                    @Override
                    public void onCallback(int code, String msg) {
                        if (callback != null) {
                            callback.onCallback(code, msg);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onTRTCAnchorEnter(final String userId) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mUserIdList.add(userId);
                if (!mUserInfoMap.containsKey(userId)) {
                    TRTCMeetingDef.UserInfo info = new TRTCMeetingDef.UserInfo();
                    info.userId = userId;
                    mUserInfoMap.put(userId, info);
                }
                if (mIsGetLiveUrl) {
                    updateMixConfig();
                }
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTRTCMeetingDelegate != null) {
                            mTRTCMeetingDelegate.onUserEnterRoom(userId);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onTRTCAnchorExit(final String userId) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mUserIdList.remove(userId);
                mUserInfoMap.remove(userId);
                if (mIsGetLiveUrl) {
                    updateMixConfig();
                }
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTRTCMeetingDelegate != null) {
                            mTRTCMeetingDelegate.onUserLeaveRoom(userId);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onTRTCVideoAvailable(final String userId, final boolean available) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCMeetingDef.UserInfo userInfo = mUserInfoMap.get(userId);
                if (userInfo == null) {
                    return;
                }
                userInfo.isVideoAvailable = available;
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTRTCMeetingDelegate != null) {
                            mTRTCMeetingDelegate.onUserVideoAvailable(userId, available);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onTRTCAudioAvailable(final String userId, final boolean available) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCMeetingDef.UserInfo userInfo = mUserInfoMap.get(userId);
                if (userInfo == null) {
                    return;
                }
                userInfo.isAudioAvailable = available;
                runOnDelegateThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mTRTCMeetingDelegate != null) {
                            mTRTCMeetingDelegate.onUserAudioAvailable(userId, available);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void onTRTCSubStreamAvailable(final String userId, final boolean available) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                final String subStreamId = userId + "_sub";
                if (available) {
                    mSubStreamMap.put(subStreamId, userId);
                    // 有一条新的辅流进来
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mTRTCMeetingDelegate != null) {
                                mTRTCMeetingDelegate.onUserEnterRoom(subStreamId);
                                mTRTCMeetingDelegate.onUserVideoAvailable(subStreamId, available);
                            }
                        }
                    });
                } else {
                    // 有一条新的辅流离开了
                    mSubStreamMap.remove(subStreamId);
                    runOnDelegateThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mTRTCMeetingDelegate != null) {
                                mTRTCMeetingDelegate.onUserVideoAvailable(subStreamId, available);
                                mTRTCMeetingDelegate.onUserLeaveRoom(subStreamId);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onScreenCaptureStarted() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onScreenCaptureStarted();
                }
            }
        });
    }

    @Override
    public void onScreenCapturePaused() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onScreenCapturePaused();
                }
            }
        });
    }

    @Override
    public void onScreenCaptureResumed() {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onScreenCaptureResumed();
                }
            }
        });
    }

    @Override
    public void onScreenCaptureStopped(final int reason) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onScreenCaptureStopped(reason);
                }
            }
        });
    }

    @Override
    public void onError(final int errorCode, final String errorMsg) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onError(errorCode, errorMsg);
                }
            }
        });
    }

    @Override
    public void onNetworkQuality(final TRTCCloudDef.TRTCQuality trtcQuality, final ArrayList<TRTCCloudDef.TRTCQuality> arrayList) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onNetworkQuality(trtcQuality, arrayList);
                }
            }
        });
    }

    @Override
    public void onUserVoiceVolume(final ArrayList<TRTCCloudDef.TRTCVolumeInfo> userVolumes, int totalVolume) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null && userVolumes != null) {
                    for (TRTCCloudDef.TRTCVolumeInfo info : userVolumes) {
                        mTRTCMeetingDelegate.onUserVolumeUpdate(info.userId, info.volume);
                    }
                }
            }
        });
    }

    @Override
    public void onRoomDestroy(final String roomId) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                TXTRTCMeeting.getInstance().exitRoom(null);
                if (mTRTCMeetingDelegate != null) {
                    mTRTCMeetingDelegate.onRoomDestroy(roomId);
                }
            }
        });
    }

    @Override
    public void onRoomRecvRoomTextMsg(final String roomId, final String message, final TXUserInfo userInfo) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    TRTCMeetingDef.UserInfo trtcUserInfo = mUserInfoMap.get(userInfo.userId);
                    if (trtcUserInfo == null) {
                        trtcUserInfo = new TRTCMeetingDef.UserInfo();
                        trtcUserInfo.userId = userInfo.userId;
                        trtcUserInfo.userName = userInfo.userName;
                        trtcUserInfo.userAvatar = userInfo.avatarURL;
                    }
                    mTRTCMeetingDelegate.onRecvRoomTextMsg(message, trtcUserInfo);
                }
            }
        });
    }

    @Override
    public void onRoomRecvRoomCustomMsg(final String roomId, final String cmd, final String message, final TXUserInfo userInfo) {
        runOnDelegateThread(new Runnable() {
            @Override
            public void run() {
                if (mTRTCMeetingDelegate != null) {
                    TRTCMeetingDef.UserInfo trtcUserInfo = mUserInfoMap.get(userInfo.userId);
                    if (trtcUserInfo == null) {
                        trtcUserInfo = new TRTCMeetingDef.UserInfo();
                        trtcUserInfo.userId = userInfo.userId;
                        trtcUserInfo.userName = userInfo.userName;
                        trtcUserInfo.userAvatar = userInfo.avatarURL;
                    }
                    mTRTCMeetingDelegate.onRecvRoomCustomMsg(cmd, message, trtcUserInfo);
                }
            }
        });
    }


    private void updateMixConfig() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                TRTCLogger.i(TAG, "start mix stream:" + mUserIdList.size());
                //                if (TXRoomService.getInstance().isOwner()) {
                if (mUserIdList.size() > 0) {
                    // 等待被混的主播列表（不包括自己本身）
                    List<TXTRTCMixUser> needToMixUserList = new ArrayList<>();
                    for (String userId : mUserIdList) {
                        //把自己去除掉
                        if (userId.equals(mUserId)) {
                            continue;
                        }
                        TXTRTCMixUser user = new TXTRTCMixUser();
                        user.roomId = null;
                        user.userId = userId;
                        needToMixUserList.add(user);
                    }
                    if (needToMixUserList.size() > 0) {
                        // 混流人数大于 0，需要混流
                        TXTRTCMeeting.getInstance().setMixConfig(needToMixUserList);
                    } else {
                        // 没有需要混流的，取消混流
                        TXTRTCMeeting.getInstance().setMixConfig(null);
                    }
                } else {
                    // 没有需要混流的取消混流
                    TXTRTCMeeting.getInstance().setMixConfig(null);
                }
            }
            //            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mIsFuEffect && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            if (Math.abs(x) > 3 || Math.abs(y) > 3) {
                if (Math.abs(x) > Math.abs(y))
                    mFURenderer.setDeviceOrientation(x > 0 ? 0 : 180);
                else
                    mFURenderer.setDeviceOrientation(y > 0 ? 90 : 270);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    CameraStatusListener mCameraStatusListener;

    public interface CameraStatusListener{
        void closeCameraFinish();
    }

    public void setCameraStatusListener (CameraStatusListener cameraStatusListener){
        mCameraStatusListener = cameraStatusListener;
    }

    TrackStatusListener mTrackStatusListener;

    public interface TrackStatusListener{
        void traceStatusChange(FUAIProcessorEnum type, int status);
    }

    public void setTrackStatusListener (TrackStatusListener trackStatusListener){
        mTrackStatusListener = trackStatusListener;
    }

    private CSVUtils mCSVUtils;
    //性能测试部分
    private void initCsvUtil(Context context) {
        mCSVUtils = new CSVUtils(context);
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
}
