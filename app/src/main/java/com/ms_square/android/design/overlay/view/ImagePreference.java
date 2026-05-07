package com.ms_square.android.design.overlay.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.ms_square.android.design.overlay.R;

public class ImagePreference extends Preference {

    private ImageView mImageView;

    private Bitmap mBitmap;

    // this is the one used when inflating preference from XML
    public ImagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mImageView = (ImageView) holder.findViewById(R.id.image_view);
        mImageView.setImageBitmap(mBitmap);
    }

    public void updateImage(Bitmap bitmap) {
        // onBindViewHolder might not have been called
        if (mImageView != null) {
            mImageView.setImageBitmap(bitmap);
        }
        mBitmap = bitmap;
    }
}