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

import android.app.ListActivity;
import android.app.WallpaperInfo;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

public class LiveWallpaperListActivity extends ListActivity implements
        AdapterView.OnItemClickListener {
    private static final String LOG_TAG = "LiveWallpapersPicker";
    private static final int REQUEST_PREVIEW = 100;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.live_wallpaper_list);

        setListAdapter(new LiveWallpaperListAdapter(this));
        getListView().setOnItemClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PREVIEW) {
            if (resultCode == RESULT_OK) finish();
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        LiveWallpaperListAdapter.LiveWallpaperInfo wallpaperInfo =
                (LiveWallpaperListAdapter.LiveWallpaperInfo) getListAdapter().getItem(position);
        final Intent intent = wallpaperInfo.intent;
        final WallpaperInfo info = wallpaperInfo.info;
        LiveWallpaperPreview.showPreview(this, REQUEST_PREVIEW, intent, info);
    }
}
