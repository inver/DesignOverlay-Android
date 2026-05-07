package com.ms_square.android.design.overlay.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.ms_square.android.design.overlay.BuildConfig;
import com.ms_square.android.design.overlay.R;
import com.ms_square.android.design.overlay.app.AppEnvironment;
import com.ms_square.android.design.overlay.event.OverlayServiceEvent;
import com.ms_square.android.design.overlay.service.DesignOverlayService;
import com.ms_square.android.design.overlay.util.ImageUtil;
import com.ms_square.android.design.overlay.util.PrefUtil;
import com.ms_square.android.design.overlay.view.ImagePreference;
import com.ms_square.android.util.AppUtil;
import com.ms_square.android.util.ToastMaster;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    private Context mAppContext;

    private ImagePreference mImagePreference;

    private TwoStatePreference mOverlayEnabledPref;

    private int mImageSize;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> mImagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    final Uri uri = result.getData().getData();
                    if (uri != null) {
                    // needs to take the persistable permission
                    mAppContext.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    loadDesignImage(uri);
                }
                }
            }
    );

    private final ActivityResultLauncher<Intent> mOverlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(requireActivity())) {
                    if (mOverlayEnabledPref != null) {
                        mOverlayEnabledPref.setChecked(true);
                    }
                    ContextCompat.startForegroundService(requireActivity(), DesignOverlayService.createIntent(requireActivity()));
                } else {
                    if (mOverlayEnabledPref != null) {
                        mOverlayEnabledPref.setChecked(false);
                    }
                }
            }
    );

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences
        // to their values. When their values change, their summaries are
        // updated to reflect the new value, per the Android Design
        // guidelines.
        bindPreferenceSummaryToValue(findPreference(PrefUtil.PREF_GRID_SIZE));
        bindPreferenceSummaryToValue(findPreference(PrefUtil.PREF_GRID_COLOR));

        mOverlayEnabledPref = findPreference(PrefUtil.PREF_OVERLAY_ENABLED);
        if (mOverlayEnabledPref != null) {
            mOverlayEnabledPref.setOnPreferenceChangeListener(this);
        }

        mAppContext = requireContext().getApplicationContext();

        // get listPreferredItemHeight value in pixel and set it to mImageSize
        TypedValue value = new TypedValue();
        requireActivity().getTheme().resolveAttribute(android.R.attr.listPreferredItemHeight, value, true);
        mImageSize = (int) value.getDimension(getResources().getDisplayMetrics());

        mImagePreference = findPreference(PrefUtil.PREF_DESIGN_IMAGE_URI);
        if (mImagePreference != null) {
            mImagePreference.setOnPreferenceClickListener(this);
        }
        // load image if already set
        Uri imageUri = PrefUtil.getDesignImageUri(mAppContext);
        if (imageUri != null) {
            loadDesignImage(imageUri);
        }

        // Set application version
        Preference appVer = findPreference("pref_app_version");
        if (appVer != null) {
            appVer.setSummary(AppUtil.getVersion(mAppContext) + " - " + BuildConfig.BUILD_NUMBER);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutor.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mOverlayEnabledPref != null) {
            mOverlayEnabledPref.setChecked(AppEnvironment.INSTANCE.isOverlayServiceRunning());
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(OverlayServiceEvent event) {
        if (mOverlayEnabledPref != null) {
            mOverlayEnabledPref.setChecked(event.isRunning);
        }
    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
        String key = preference.getKey();
        if (PrefUtil.PREF_OVERLAY_ENABLED.equals(key)) {
            boolean isChecked = (boolean) newValue;
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireActivity())) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + requireActivity().getPackageName()));
                    mOverlayPermissionLauncher.launch(intent);
                    return false;
                }
                ContextCompat.startForegroundService(requireActivity(), DesignOverlayService.createIntent(requireActivity()));
            } else {
                requireActivity().stopService(DesignOverlayService.createIntent(requireActivity()));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        if (PrefUtil.PREF_DESIGN_IMAGE_URI.equals(preference.getKey())) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setType("image/*");
            mImagePickerLauncher.launch(intent);
            return true;
        }
        return false;
    }

    private void loadDesignImage(final Uri uri) {
        mExecutor.execute(() -> {
            Bitmap decodedBitmap = null;
            try (InputStream stream = mAppContext.getContentResolver().openInputStream(uri)) {
                if (stream != null) {
                    decodedBitmap = ImageUtil.decodeSampledBitmapFromStream(stream, mImageSize, mImageSize);
                }
            } catch (FileNotFoundException fe) {
                Timber.w("File was not found: %s", fe.toString());
            } catch (SecurityException se) {
                Timber.w("Probably no longer have access permission to the uri: %s", se.toString());
                // clear stored image Uri
                PrefUtil.setDesignImageUri(mAppContext, null);
            } catch (IOException ignore) {
            }

            final Bitmap finalBitmap = decodedBitmap;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (finalBitmap != null) {
                        PrefUtil.setDesignImageUri(mAppContext, uri);
                        mImagePreference.updateImage(finalBitmap);
                    } else {
                        ToastMaster.showToast(mAppContext, getString(R.string.toast_bitmap_not_found), Toast.LENGTH_LONG);
                    }
                });
            }
        });
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        if (preference == null) return;
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getString(preference.getKey(), ""));
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
        String stringValue = value.toString();

        if (preference instanceof ListPreference listPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            int index = listPreference.findIndexOfValue(stringValue);

            // Set the summary to reflect the new value.
            preference.setSummary(index >= 0
                    ? listPreference.getEntries()[index]
                    : null);
        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }

        return true;
    };
}