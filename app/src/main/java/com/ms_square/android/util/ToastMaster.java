package com.ms_square.android.util;

import android.content.Context;
import android.widget.Toast;

public class ToastMaster {
    public static void showToast(Context context, String text, int duration) {
        Toast.makeText(context, text, duration).show();
    }
}
