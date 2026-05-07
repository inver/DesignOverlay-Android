package com.ms_square.android.design.overlay.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.ms_square.android.design.overlay.R;
import com.ms_square.android.design.overlay.activity.SettingsActivity;
import com.ms_square.android.design.overlay.app.AppEnvironment;
import com.ms_square.android.design.overlay.util.PrefUtil;
import com.ms_square.android.design.overlay.view.GridView;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

public class DesignOverlayService extends Service {

    private static final int NOTIFICATION_ID = 10000;

    private static final String NOTIFICATION_CHANNEL_ID = "design_overlay_service";

    private static final String ACTION_DISMISS = "com.ms_square.android.design.overlay.ACTION_DISMISS";

    private WindowManager mWindowManager;

    private NotificationManager mNotificationManager;

    private View mRootView;

    private ImageView mDesignImgView;

    private GridView mGridView;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public static Intent createIntent(Context context) {
        return new Intent(context, DesignOverlayService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppEnvironment.INSTANCE.setOverlayServiceRunning(true);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        showOverlay();

        ContextCompat.registerReceiver(this, mReceiver, new IntentFilter(ACTION_DISMISS), ContextCompat.RECEIVER_NOT_EXPORTED);

        PrefUtil.registerOnSharedPreferenceChangeListener(this, mPrefListener);

        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        AppEnvironment.INSTANCE.setOverlayServiceRunning(false);
        PrefUtil.unregisterOnSharedPreferenceChangeListener(this, mPrefListener);
        unregisterReceiver(mReceiver);
        dismissOverlay();
        cancelNotification();
        mExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("InflateParams")
    private void showOverlay() {
        mRootView = LayoutInflater.from(this).inflate(R.layout.service_design_overlay, null, false);
        mDesignImgView = mRootView.findViewById(R.id.design_image_view);
        mGridView = mRootView.findViewById(R.id.grid_view);
        updateImageVisibility();
        updateImageAlpha();
        updateImage();
        updateGridSize();
        updateGridColor();
        updateGridVisibility();
        mWindowManager.addView(mRootView, createDefaultSystemWindowParams(PrefUtil.isFullScreen(this)));
    }

    private void dismissOverlay() {
        mWindowManager.removeView(mRootView);
        mRootView = null;
        mDesignImgView = null;
        mGridView = null;
    }

    private void updateImage() {
        if (mDesignImgView != null) {
            final Uri uri = PrefUtil.getDesignImageUri(this);
            if (uri != null) {
                mExecutor.execute(() -> {
                    final Bitmap finalBitmap = loadBitmap(uri);
                    if (mRootView != null) {
                        mRootView.post(() -> {
                            if (mDesignImgView != null) {
                                mDesignImgView.setImageBitmap(finalBitmap);
                            }
                        });
                    }
                });
            }
        }
    }

    @Nullable
    private Bitmap loadBitmap(Uri uri) {
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(stream);
        } catch (SecurityException se) {
            Timber.w("No longer have access permission to the uri: %s", se.toString());
            // clear stored image Uri
            PrefUtil.setDesignImageUri(getApplicationContext(), null);
        } catch (IOException e) {
            Timber.w("Failed to load image: %s", e.toString());
        }
        return null;
    }

    private void updateImageAlpha() {
        if (mDesignImgView != null) {
            final int alpha = PrefUtil.getDesignImageAlpha(this); // 0 - 255
            mDesignImgView.setImageAlpha(alpha);
        }
    }

    private void updateImageVisibility() {
        if (mDesignImgView != null) {
            mDesignImgView.setVisibility(PrefUtil.isDesignImageEnabled(DesignOverlayService.this) ?
                    View.VISIBLE : View.INVISIBLE);
        }
    }

    private void updateGridSize() {
        if (mGridView != null) {
            final int gridSize = PrefUtil.getGridSize(DesignOverlayService.this);
            final boolean alignRight = PrefUtil.isAlignRight(DesignOverlayService.this);
            final boolean alignBottom = PrefUtil.isAlignBottom(DesignOverlayService.this);
            mGridView.updateGridSize(gridSize, alignRight, alignBottom);
        }
    }

    private void updateGridColor() {
        if (mGridView != null) {
            mGridView.updateGridColor(PrefUtil.getGridColor(DesignOverlayService.this));
        }
    }

    private void updateGridVisibility() {
        if (mGridView != null) {
            mGridView.setVisibility(PrefUtil.isGridEnabled(DesignOverlayService.this) ?
                    View.VISIBLE : View.INVISIBLE);
        }
    }

    private void showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            mNotificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_big_text)))
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_small_text))
                .setContentIntent(getNotificationActivityIntent());

        mBuilder.addAction(R.drawable.ic_action_clear, getString(R.string.notification_action_dismiss),
                getNotificationBroadcastIntent());

        // show the notification
        startForeground(NOTIFICATION_ID, mBuilder.build());
    }

    private void cancelNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

    private PendingIntent getNotificationActivityIntent() {
        int flags = PendingIntent.FLAG_CANCEL_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent intent = SettingsActivity.createIntent(this);
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private PendingIntent getNotificationBroadcastIntent() {
        int flags = PendingIntent.FLAG_CANCEL_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent intent = new Intent(ACTION_DISMISS);
        return PendingIntent.getBroadcast(this, 0, intent, flags);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_DISMISS.equals(action)) {
                stopSelf();
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = (sharedPreferences, key) -> {
        if (key == null) return;
        switch (key) {
            case PrefUtil.PREF_FULLSCREEN -> {
                dismissOverlay();
                showOverlay();
            }
            case PrefUtil.PREF_DESIGN_IMAGE_ENABLED -> updateImageVisibility();
            case PrefUtil.PREF_DESIGN_IMAGE_URI -> updateImage();
            case PrefUtil.PREF_DESIGN_IMAGE_ALPHA -> updateImageAlpha();
            case PrefUtil.PREF_GRID_ENABLED -> updateGridVisibility();
            case PrefUtil.PREF_GRID_SIZE, PrefUtil.PREF_ALIGN_RIGHT, PrefUtil.PREF_ALIGN_BOTTOM -> updateGridSize();
            case PrefUtil.PREF_GRID_COLOR -> updateGridColor();
        }
    };

    private static WindowManager.LayoutParams createDefaultSystemWindowParams(boolean isFullScreen) {
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | (isFullScreen ? WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN : 0);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT);
        params.format = PixelFormat.RGBA_8888;
        return params;
    }
}