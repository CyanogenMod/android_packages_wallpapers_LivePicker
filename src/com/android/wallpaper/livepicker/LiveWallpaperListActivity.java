/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.wallpaper.livepicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.app.WallpaperInfo;
import android.os.Bundle;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

public class LiveWallpaperListActivity extends Activity {
    private static final String LOG_TAG = "LiveWallpapersPicker";
    private static final int REQUEST_PREVIEW = 100;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.live_wallpaper_base);
        ViewGroup baseView = (ViewGroup) findViewById(R.id.live_wallpaper_base_view);

        boolean isXLarge = (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE;

        DialogFragment fragment = new WallpaperDialogFragment(this, baseView, !isXLarge);
        if (isXLarge) {
            // When the screen is XLarge
            fragment.show(getFragmentManager(), "dialog");
        } else {
            // When the screen is normal. i.e: a phone
            FragmentTransaction ft = getFragmentManager().openTransaction();
            ft.add(R.id.live_wallpaper_base_view, fragment);
            ft.commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PREVIEW) {
            if (resultCode == RESULT_OK) finish();
        }
    }

    private class WallpaperDialogFragment extends DialogFragment implements
            AdapterView.OnItemClickListener{
        private Activity mActivity;
        private LiveWallpaperListAdapter mAdapter;
        private ViewGroup mBaseView;
        private boolean mEmbedded;

        public WallpaperDialogFragment(Activity activity, ViewGroup baseView, boolean embedded) {
            mActivity = activity;
            mEmbedded = embedded;
            mBaseView = baseView;
            setCancelable(true);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            mActivity.finish();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final int contentInset = getResources().getDimensionPixelSize(
                    R.dimen.dialog_content_inset);
            View view = generateView(getLayoutInflater(), mBaseView);

            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setNegativeButton(R.string.wallpaper_cancel, null);
            builder.setTitle(R.string.live_wallpaper_picker_title);
            builder.setView(view, contentInset, contentInset, contentInset, contentInset);
            return builder.create();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            if (mEmbedded) {
                return generateView(inflater, container);
            }
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @SuppressWarnings("unchecked")
        private View generateView(LayoutInflater inflater, ViewGroup container) {
            View layout = inflater.inflate(R.layout.live_wallpaper_list, container, false);

            mAdapter = new LiveWallpaperListAdapter(mActivity);
            AdapterView<BaseAdapter> adapterView =
                    (AdapterView<BaseAdapter>) layout.findViewById(android.R.id.list);
            adapterView.setAdapter(mAdapter);
            adapterView.setOnItemClickListener(this);
            adapterView.setEmptyView(layout.findViewById(android.R.id.empty));
            return layout;
        }

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            LiveWallpaperListAdapter.LiveWallpaperInfo wallpaperInfo =
                    (LiveWallpaperListAdapter.LiveWallpaperInfo) mAdapter.getItem(position);
            final Intent intent = wallpaperInfo.intent;
            final WallpaperInfo info = wallpaperInfo.info;
            LiveWallpaperPreview.showPreview(mActivity, REQUEST_PREVIEW, intent, info);
        }
    }
}
