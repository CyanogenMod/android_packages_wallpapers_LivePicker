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

import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.service.wallpaper.IWallpaperConnection;
import android.service.wallpaper.IWallpaperEngine;
import android.service.wallpaper.IWallpaperService;
import android.service.wallpaper.WallpaperService;
import android.service.wallpaper.WallpaperSettingsActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Displays a list of live wallpapers, allowing the user to select one
 * and make it the system global wallpaper.
 */
public class LiveWallpaperPickActivity extends Activity implements
        AdapterView.OnItemSelectedListener, View.OnClickListener {

    private static final String TAG = "LiveWallpaperPickActivity";

    private PackageManager mPackageManager;
    private WallpaperManager mWallpaperManager;
    
    Intent mSelectedIntent;
    WallpaperInfo mSelectedInfo;
    WallpaperConnection mWallpaperConnection;

    private Button mConfigureButton;
    private TextView mWallpaperTitle;
    
    private ArrayList<Intent> mWallpaperIntents;
    private ArrayList<WallpaperInfo> mWallpaperInfos;
 
    private ArrayList<Drawable> mThumbnails;

    class WallpaperConnection extends IWallpaperConnection.Stub implements ServiceConnection {
        final Intent mIntent;
        IWallpaperService mService;
        IWallpaperEngine mEngine;
        boolean mConnected;

        WallpaperConnection(Intent intent) {
            mIntent = intent;
        }

        public boolean connect() {
            synchronized (this) {
                if (!bindService(mIntent, this, Context.BIND_AUTO_CREATE)) {
                    return false;
                }
                
                mConnected = true;
                return true;
            }
        }
        
        public void disconnect() {
            synchronized (this) {
                mConnected = false;
                if (mEngine != null) {
                    try {
                        mEngine.destroy();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                    mEngine = null;
                }
                unbindService(this);
                mService = null;
            }
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mWallpaperConnection == this) {
                mService = IWallpaperService.Stub.asInterface(service);
                try {
                    View button = findViewById(R.id.set);
                    mService.attach(this, button.getWindowToken(),
                            WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA,
                            true,
                            button.getRootView().getWidth(),
                            button.getRootView().getHeight());
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed attaching wallpaper; clearing", e);
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mEngine = null;
            if (mWallpaperConnection == this) {
                Log.w(TAG, "Wallpaper service gone: " + name);
            }
        }
        
        public void attachEngine(IWallpaperEngine engine) {
            synchronized (this) {
                if (mConnected) {
                    mEngine = engine;
                    try {
                        engine.setVisibility(true);
                    } catch (RemoteException e) {
                        // Ignore
                    }
                } else {
                    try {
                        engine.destroy();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }
            }
        }
        
        public ParcelFileDescriptor setWallpaper(String name) {
            return null;
        }
    }

    private class ImageAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        ImageAdapter(LiveWallpaperPickActivity context) {
            mLayoutInflater = context.getLayoutInflater();
        }

        public int getCount() {
            return mThumbnails.size();
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView image;
           
            if (convertView == null) {
                image = (ImageView) mLayoutInflater.inflate(R.layout.wallpaper_item, parent, false);
                image.setLayoutParams(new Gallery.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.FILL_PARENT));
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                image = (ImageView) convertView;
            }
          
            image.setImageDrawable(mThumbnails.get(position));

            return image;
        }
    }


    private void findLiveWallpapers() {
        mThumbnails = new ArrayList<Drawable>(24);
        List<ResolveInfo> list = mPackageManager.queryIntentServices(getTargetIntent(),
                PackageManager.GET_META_DATA);
        
        mWallpaperIntents = new ArrayList<Intent>(list.size());
        mWallpaperInfos = new ArrayList<WallpaperInfo>(list.size());
        
        int listSize = list.size();
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        Drawable galleryIcon = getResources().getDrawable(R.drawable.livewallpaper_placeholder);
        
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
        pt.setTextAlign(Paint.Align.CENTER);

        Canvas canvas = new Canvas();

        for (int i = 0; i < listSize; i++) {
            ResolveInfo resolveInfo = list.get(i);
            ComponentInfo ci = resolveInfo.serviceInfo;
            WallpaperInfo winfo;
            try {
                winfo = new WallpaperInfo(this, resolveInfo);
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Skipping wallpaper " + ci, e);
                continue;
            } catch (IOException e) {
                Log.w(TAG, "Skipping wallpaper " + ci, e);
                continue;
            }

            String packageName = winfo.getPackageName();
            String className = winfo.getServiceName();
            Intent intent = new Intent(getTargetIntent());
            intent.setClassName(packageName, className);
            mWallpaperIntents.add(intent);
            mWallpaperInfos.add(winfo);

            Drawable thumb = winfo.loadThumbnail(mPackageManager);
            if (thumb == null) {
                final int thumbWidth = (int) (180 * metrics.density);
                final int thumbHeight = (int) (160 * metrics.density);
                Bitmap thumbBit = Bitmap.createBitmap(thumbWidth, thumbHeight,
                        Bitmap.Config.ARGB_8888);
                pt.setARGB(204,102,102,102);
                canvas.setBitmap(thumbBit);
                canvas.drawPaint(pt);

                galleryIcon.setBounds(0, 0, thumbWidth, thumbHeight);
                ((BitmapDrawable) galleryIcon).setGravity(Gravity.CENTER);
                galleryIcon.draw(canvas);

                pt.setARGB(255, 255, 255, 255);
                pt.setTextSize(20 * metrics.density);
                canvas.drawText(className.substring(className.lastIndexOf('.') + 1),
                        (int) (thumbWidth * 0.5), (int) (thumbHeight - 22 * metrics.density), pt);
                thumb = new BitmapDrawable(getResources(), thumbBit);
            }

            thumb.setDither(true);
            mThumbnails.add(thumb);
        }
    }
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mPackageManager = getPackageManager();
        mWallpaperManager = WallpaperManager.getInstance(this);
        
        findLiveWallpapers();

        setContentView(R.layout.live_wallpaper_content);

        Gallery gallery = (Gallery) findViewById(R.id.gallery);
        gallery.setAdapter(new ImageAdapter(this));
        gallery.setOnItemSelectedListener(this);
        gallery.setCallbackDuringFling(false);

        View button = findViewById(R.id.set);
        button.setOnClickListener(this);
        
        mConfigureButton = (Button)findViewById(R.id.configure);
        mConfigureButton.setVisibility(View.GONE);
        mConfigureButton.setOnClickListener(this);

        mWallpaperTitle = (TextView)findViewById(R.id.title);
        mWallpaperTitle.setVisibility(View.GONE);
      
        // Set default return data
        setResult(RESULT_CANCELED);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            try {
                mWallpaperConnection.mEngine.setVisibility(true);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            try {
                mWallpaperConnection.mEngine.setVisibility(false);
            } catch (RemoteException e) {
                // Ignore
            }
        }
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }
        mWallpaperConnection = null;
    }
    
    protected Intent getTargetIntent() {
        return new Intent(WallpaperService.SERVICE_INTERFACE);
    }
    
    public void onItemSelected(AdapterView parent, View v, int position, long id) {
        mSelectedIntent = mWallpaperIntents.get(position);
        mSelectedInfo = mWallpaperInfos.get(position);
        mConfigureButton.setVisibility(
            (mSelectedInfo != null && 
                mSelectedInfo.getSettingsActivity() != null)
            ? View.VISIBLE
            : View.GONE);
        findViewById(R.id.set).setEnabled(true);

        findViewById(R.id.title).setVisibility(View.VISIBLE);
        mWallpaperTitle.setText(mSelectedInfo.loadLabel(mPackageManager).toString());
        
        WallpaperConnection conn = new WallpaperConnection(mSelectedIntent);
        if (conn.connect()) {
            if (mWallpaperConnection != null) {
                mWallpaperConnection.disconnect();
            }
            mWallpaperConnection = conn;
        }
    }
    
    public void onClick(View v) { // "Set" button
        if (v.getId() == R.id.set) {
            if (mSelectedIntent != null) {
                try {
                    mWallpaperManager.getIWallpaperManager().setWallpaperComponent(
                            mSelectedIntent.getComponent());
                    mWallpaperManager.setWallpaperOffsets(
                        v.getRootView().getWindowToken(), 0.5f, 0.0f);
                    this.setResult(RESULT_OK);
                } catch (RemoteException e) {
                    // do nothing
                } catch (RuntimeException e) {
                    Log.w(TAG, "Failure setting wallpaper", e);
                }
                finish();
            }
        } else if (v.getId() == R.id.configure) {
            if (mSelectedInfo != null && mSelectedInfo.getSettingsActivity() != null) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(mSelectedInfo.getPackageName(),
                        mSelectedInfo.getSettingsActivity()));
                intent.putExtra(WallpaperSettingsActivity.EXTRA_PREVIEW_MODE, true);
                startActivity(intent);
            }
        }
    }

    public void onNothingSelected(AdapterView parent) {
    }
}
