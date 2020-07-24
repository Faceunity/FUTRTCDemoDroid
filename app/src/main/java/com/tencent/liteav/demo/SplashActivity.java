package com.tencent.liteav.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.faceunity.customdata.utils.PreferenceUtil;
import com.faceunity.nama.FURenderer;
import com.tencent.liteav.login.LoginActivity;
import com.tencent.liteav.login.ProfileManager;


public class SplashActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        findViewById(R.id.btn_yes).setOnClickListener(this);
        findViewById(R.id.btn_no).setOnClickListener(this);
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }
    }

    @Override
    public void onClick(View v) {
        if (!ProfileManager.getInstance().isLogin()) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, TRTCMainActivity.class);
            startActivity(intent);
        }
        finish();
        switch (v.getId()) {
            case R.id.btn_yes:
                PreferenceUtil.persistString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON, PreferenceUtil.VALUE_ON);
                FURenderer.setup(getApplicationContext());
                break;
            case R.id.btn_no:
                PreferenceUtil.persistString(this, PreferenceUtil.KEY_FACEUNITY_IS_ON, PreferenceUtil.VALUE_OFF);
                break;
            default:
        }
    }
}
