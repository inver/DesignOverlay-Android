package com.ms_square.android.design.overlay.view;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

import com.ms_square.android.design.overlay.R;

public class ColorPreference extends ListPreference {

    private View mColorPreview;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.pref_widget_layout_color);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mColorPreview = holder.findViewById(R.id.color_preview);
        updatePreview();
    }

    private void updatePreview() {
        if (mColorPreview != null) {
            String value = getValue();
            if (value != null) {
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setColor(Color.parseColor(value));
                shape.setStroke(2, Color.GRAY);
                mColorPreview.setBackground(shape);
            }
        }
    }

    @Override
    protected void onClick() {
        // Show custom grid dialog instead of the default list
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getTitle());

        GridView gridView = new GridView(getContext());
        gridView.setNumColumns(4);
        gridView.setPadding(16, 16, 16, 16);

        final AlertDialog dialog = builder.create();
        gridView.setAdapter(new ColorAdapter(dialog));

        dialog.setView(gridView);
        dialog.show();
    }

    private class ColorAdapter extends BaseAdapter {

        private final AlertDialog mDialog;

        public ColorAdapter(AlertDialog dialog) {
            mDialog = dialog;
        }

        @Override
        public int getCount() {
            return getEntryValues().length;
        }

        @Override
        public Object getItem(int position) {
            return getEntryValues()[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View view = (convertView != null) ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.grid_item_color, parent, false);

            View colorView = view.findViewById(R.id.color_view);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setColor(Color.parseColor(getEntryValues()[position].toString()));
            shape.setStroke(2, Color.GRAY);
            colorView.setBackground(shape);

            view.setOnClickListener(v -> {
                String value = getEntryValues()[position].toString();
                if (callChangeListener(value)) {
                    setValue(value);
                    updatePreview();
                }
                if (mDialog != null) {
                    mDialog.dismiss();
                }
            });

            return view;
        }
    }
}
