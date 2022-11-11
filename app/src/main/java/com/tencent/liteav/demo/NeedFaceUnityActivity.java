package com.tencent.liteav.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Button;

import com.faceunity.core.utils.ThreadHelper;
import com.faceunity.nama.FURenderer;
import com.faceunity.nama.utils.PreferenceUtil;

/**
 * 是否使用 FaceUnity Nama
 */
public class NeedFaceUnityActivity extends Activity {
    private boolean mIsFaceUnityOn = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faceunity);

        final Button button = findViewById(R.id.btn_set);
        String isOnStr = PreferenceUtil.getString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON);
        mIsFaceUnityOn = TextUtils.equals(isOnStr, PreferenceUtil.VALUE_ON);
        button.setText(mIsFaceUnityOn ? "On" : "Off");
        button.setOnClickListener(v -> {
            mIsFaceUnityOn = !mIsFaceUnityOn;
            button.setText(mIsFaceUnityOn ? "On" : "Off");
        });
        Button btnToMain = findViewById(R.id.btn_to_main);
        btnToMain.setOnClickListener(v -> {
            Intent intent = new Intent(NeedFaceUnityActivity.this, SplashActivity.class);
            PreferenceUtil.persistString(NeedFaceUnityActivity.this, PreferenceUtil.KEY_FACEUNITY_IS_ON,
                    mIsFaceUnityOn ? PreferenceUtil.VALUE_ON : PreferenceUtil.VALUE_OFF);
            startActivity(intent);
            finish();

            if (mIsFaceUnityOn) {
                ThreadHelper.getInstance().execute(() -> FURenderer.getInstance().setup(getApplicationContext()));
            }
        });
    }
}
