package com.ms_square.android.design.overlay.activity;

import android.content.Context;
import android.content.Intent;

import com.ms_square.android.design.overlay.R;
import com.ms_square.android.design.overlay.activity.base.BaseActivity;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;

@EActivity(R.layout.activity_settings)
public class SettingsActivity extends BaseActivity {

    public static Intent createIntent(Context context) {
        Intent intent = new Intent(context, SettingsActivity_.class);
        return intent;
    }

    @AfterViews
    void afterViews() {
    }

    @Override
    protected boolean shouldRegisterToEventBus() {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
