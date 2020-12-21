/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.faceunity.nama.utils;

import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Camera-related utility functions.
 */
public class CameraUtils {
    private static final String TAG = "CameraUtils";
    private static final boolean DEBUG = false;

    /**
     * 获取相机方向
     *
     * @param cameraFacing
     * @return
     */
    public static int getCameraOrientation(int cameraFacing) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int cameraId = -1;
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == cameraFacing) {
                cameraId = i;
                break;
            }
        }
        if (cameraId < 0) {
            // no front camera, regard it as back camera
            return 90;
        } else {
            return info.orientation;
        }
    }

    public static void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /**
     * 设置对焦模式，优先支持自动对焦
     *
     * @param parameters
     */
    public static void setFocusModes(Camera.Parameters parameters) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        if (DEBUG) {
            Log.i(TAG, "setFocusModes: " + parameters.getFocusMode());
        }
    }

    /**
     * 设置相机 FPS，选择尽可能大的范围
     *
     * @param parameters
     */
    public static void chooseFrameRate(Camera.Parameters parameters) {
        List<int[]> supportedPreviewFpsRanges = parameters.getSupportedPreviewFpsRange();
        if (DEBUG) {
            StringBuilder buffer = new StringBuilder();
            buffer.append('[');
            for (Iterator<int[]> it = supportedPreviewFpsRanges.iterator(); it.hasNext(); ) {
                buffer.append(Arrays.toString(it.next()));
                if (it.hasNext()) {
                    buffer.append(", ");
                }
            }
            buffer.append(']');
            Log.d(TAG, "chooseFrameRate: Supported FPS ranges " + buffer.toString());
        }
        // FPS下限小于 7，弱光时能保证足够曝光时间，提高亮度。
        // range 范围跨度越大越好，光源足够时FPS较高，预览更流畅，光源不够时FPS较低，亮度更好。
        int[] bestFrameRate = supportedPreviewFpsRanges.get(0);
        for (int[] fpsRange : supportedPreviewFpsRanges) {
            int thisMin = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX];
            int thisMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
            if (thisMin < 7000) {
                continue;
            }
            if (thisMin <= 15000 && thisMax - thisMin > bestFrameRate[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                    - bestFrameRate[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]) {
                bestFrameRate = fpsRange;
            }
        }
        if (DEBUG) {
            Log.i(TAG, "setPreviewFpsRange: [" + bestFrameRate[Camera.Parameters.PREVIEW_FPS_MIN_INDEX] + ", " + bestFrameRate[Camera.Parameters.PREVIEW_FPS_MAX_INDEX] + "]");
        }
        parameters.setPreviewFpsRange(bestFrameRate[Camera.Parameters.PREVIEW_FPS_MIN_INDEX], bestFrameRate[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
    }

    /**
     * Attempts to find a preview size that matches the provided width and height (which
     * specify the dimensions of the encoded video).  If it fails to find a match it just
     * uses the default preview size for video.
     * <p>
     * https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
     */
    public static int[] choosePreviewSize(Camera.Parameters parameters, int width, int height) {
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        if (DEBUG) {
            StringBuilder sb = new StringBuilder("[");
            for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
                sb.append("[").append(supportedPreviewSize.width).append(", ")
                        .append(supportedPreviewSize.height).append("]").append(", ");
            }
            sb.append("]");
            Log.d(TAG, "choosePreviewSize: Supported preview size " + sb.toString());
        }

        for (Camera.Size size : supportedPreviewSizes) {
            if (size.width == width && size.height == height) {
                parameters.setPreviewSize(width, height);
                return new int[]{width, height};
            }
        }

        if (DEBUG) {
            Log.e(TAG, "Unable to set preview size to " + width + "x" + height);
        }
        Camera.Size ppsfv = parameters.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            parameters.setPreviewSize(ppsfv.width, ppsfv.height);
            return new int[]{ppsfv.width, ppsfv.height};
        }
        // else use whatever the default size is
        return new int[]{0, 0};
    }

}
