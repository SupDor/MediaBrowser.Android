package com.mb.android.activities;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.media.MediaRouter;
import android.support.v7.widget.SearchView;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import com.google.android.gms.cast.ApplicationMetadata;
import com.mb.android.MainApplication;
import com.mb.android.Playlist;
import com.mb.android.PlaylistItem;
import com.mb.android.activities.mobile.BookDetailsActivity;
import com.mb.android.player.AudioService;
import com.mb.android.ui.mobile.homescreen.HomescreenActivity;
import com.mb.android.activities.mobile.MediaDetailsActivity;
import com.mb.android.activities.mobile.PhotoDetailsActivity;
import mediabrowser.apiinteraction.ConnectionResult;
import mediabrowser.apiinteraction.Response;
import mediabrowser.apiinteraction.android.AndroidApiClient;
import com.mb.android.interfaces.IWebsocketEventListener;
import com.mb.android.playbackmediator.cast.VideoCastManager;
import com.mb.android.playbackmediator.cast.callbacks.IVideoCastConsumer;
import com.mb.android.playbackmediator.cast.callbacks.VideoCastConsumerImpl;
import com.mb.android.playbackmediator.widgets.MiniController;
import com.mb.android.R;
import com.mb.android.ui.main.ConnectionActivity;
import com.mb.android.ui.main.SettingsActivity;
import com.mb.android.ui.mobile.album.MusicAlbumActivity;
import com.mb.android.ui.mobile.musicartist.ArtistActivity;
import com.mb.android.ui.mobile.playback.AudioPlaybackActivity;
import com.mb.android.ui.mobile.playback.PlaybackActivity;
import mediabrowser.model.apiclient.ConnectionState;
import mediabrowser.model.dto.BaseItemDto;
import com.mb.android.logging.AppLogger;
import mediabrowser.model.dto.UserDto;
import mediabrowser.model.session.PlayRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;


/**
 * Created by Mark on 12/12/13.
 *
 * This Activity extends the BaseMB3Activity by adding user profile specific info
 */
public abstract class BaseMbMobileActivity extends ActionBarActivity implements IWebsocketEventListener {

    private static final String TAG = "BaseMB3ProfileActivity";
    private float mDensity;
    private int mHeight;
    private int mWidth;
    private DisplayMetrics mMetrics;
    private Intent mIntent;
    protected ActionBar mActionBar;
    protected MenuItem mSearchMenuItem;
    protected MenuItem mMediaRouteMenuItem;
    protected VideoCastManager mCastManager;
    protected IVideoCastConsumer mCastConsumer;
    protected boolean mSearchEnabled = true;
    protected MiniController mMini;
    protected DrawerLayout drawer;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If we've recovered from a crash, we don't want to crash again. Just restart the app normally
        if (MainApplication.getInstance().API == null) {
            AppLogger.getLogger().Info("Recovering from crash. Trying to re-acquiring server");
            Thread thread = new Thread() {
                @Override
                public void run() {
                    MainApplication.getInstance().getConnectionManager().Connect(connectionResult);
                }
            };
            thread.start();
        }

        mActionBar = getSupportActionBar();

        if (mActionBar != null) {
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            mActionBar.setDisplayShowTitleEnabled(true);
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
        }

        /**
         * Should mean this is a session that's been relaunched from the notification tray.
         * ChromeCast. Or a recovering crash.
         */

//        if (MB3Application.getInstance().Payload == null) {
//            MB3Application.getInstance().Payload = LoginHelper.LoadPayload(this);
//        }

        mCastManager = MainApplication.getCastManager(this);
        mCastConsumer = new VideoCastConsumerImpl() {

            @Override
            public void onApplicationConnected(ApplicationMetadata appMetadata,
                                               String sessionId, boolean wasLaunched) {

                mCastManager.sendIdentifyMessage();
            }

            @Override
            public void onApplicationDisconnected(int errorCode) {

            }

            @Override
            public void onFailed(int resourceId, int statusCode) {

            }

            @Override
            public void onConnectionSuspended(int cause) {
                AppLogger.getLogger().Debug(TAG, "onConnectionSuspended() was called with cause: " + cause);

            }

            @Override
            public void onConnectivityRecovered() {

            }

            @Override
            public void onCastDeviceDetected(final MediaRouter.RouteInfo info) {

            }

            @Override
            public void onDataMessageSendFailed(int errorCode) {
                AppLogger.getLogger().Debug(TAG, "onDataMessageSendFailed. Error Code: " + String.valueOf(errorCode));
                AppLogger.getLogger().Error("Error sending data message.");
            }

            @Override
            public void onDataMessageReceived(String message) {

            }
        };

        mCastManager.reconnectSessionIfPossible(this, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        MainApplication.getAudioService().incrementUiCounter();
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApplication.getInstance().setCurrentActivity(this);
        mCastManager = MainApplication.getCastManager(this);
        if (null != mCastManager) {
            mCastManager.addVideoCastConsumer(mCastConsumer);
            mCastManager.incrementUiCounter();
            if (mMini != null)
                mMini.setOnMiniControllerChangedListener(mCastManager);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        clearReferences();
        if (null == mCastManager) return;
        mCastManager.decrementUiCounter();
        mCastManager.removeVideoCastConsumer(mCastConsumer);
        if (mMini != null)
            mMini.removeOnMiniControllerChangedListener(mCastManager);
    }

    @Override
    public void onStop() {
        super.onStop();
        mActionBar = null;
        MainApplication.getAudioService().decrementUiCounter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearReferences();
        mMetrics = null;
        if (mCastManager != null) {
            mCastManager.removeMiniController(mMini);
        }
    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        AppLogger.getLogger().Warn("Application is running low on memory");
    }

    private Response<ConnectionResult> connectionResult = new Response<ConnectionResult>() {
        @Override
        public void onResponse(ConnectionResult result) {

            if (ConnectionState.SignedIn.equals(result.getState())) {
                // A server was found and the user has been signed in using previously saved credentials.
                // Ready to browse using result.ApiClient
                AppLogger.getLogger().Info("**** SIGNED IN ****");
                MainApplication.getInstance().API = (AndroidApiClient)result.getApiClient();
                MainApplication.getInstance().user = new UserDto();
                MainApplication.getInstance().user.setId(MainApplication.getInstance().API.getCurrentUserId());
                onConnectionRestored();
            } else {
                returnToConnectionActivity();
            }
        }

        @Override
        public void onError(Exception ex) {
            returnToConnectionActivity();
        }
    };


    private void returnToConnectionActivity() {
        AppLogger.getLogger().Info("Failed to recover session after crash");
        Intent intent = new Intent(MainApplication.getInstance(), ConnectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        this.finish();
    }


    /*
    If an Activity crashes, then this method is called after ConnectionManager has rebuilt the ApiClient
     */
    protected abstract void onConnectionRestored();

    public Intent getMb3Intent() {
        if (mIntent == null) {
            mIntent = getIntent();
        }
        return mIntent;
    }

    public int getScreenWidth() {
        if (mWidth == 0) {
            measureScreen();
        }
        return mWidth;
    }

    public int getScreenHeight() {
        if (mHeight == 0) {
            measureScreen();
        }
        return mHeight;
    }

    public float getScreenDensity() {
        if (mDensity == 0) {
            measureScreen();
        }
        return mDensity;
    }

    private void measureScreen() {

        WindowManager w = getWindowManager();
        Display d = w.getDefaultDisplay();
        mMetrics = new DisplayMetrics();
        d.getMetrics(mMetrics);
        mDensity = mMetrics.density;

        // since SDK_INT = 1;
        mWidth = mMetrics.widthPixels;
        mHeight = mMetrics.heightPixels;
        // includes window decorations (statusbar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17)
            try {
                mWidth = (Integer) Display.class.getMethod("getRawWidth").invoke(d);
                mHeight = (Integer) Display.class.getMethod("getRawHeight").invoke(d);
            } catch (Exception ignored) {
            }
        // includes window decorations (statusbar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 17)
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(d, realSize);
                mWidth = realSize.x;
                mHeight = realSize.y;
            } catch (Exception ignored) {
            }
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(Gravity.START)) {
            drawer.closeDrawer(Gravity.START);
        } else {
            super.onBackPressed();
        }
    }

//    @Override
//    public boolean onKeyDown(int keycode, KeyEvent e) {
//
//        switch (keycode) {
//            case KeyEvent.KEYCODE_MENU:
//                if (drawer != null) {
//                    if (drawer.isDrawerOpen(Gravity.START)) {
//                        drawer.closeDrawer(Gravity.START);
//                    } else {
//                        drawer.openDrawer(Gravity.START);
//                        drawer.requestFocus();
//                    }
//
//                    return true;
//                }
//            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
//                Toast.makeText(this, "PLAY/PAUSE pressed", Toast.LENGTH_LONG).show();
//                return true;
//        }
//
//        return super.onKeyDown(keycode, e);
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main, menu);
        mMediaRouteMenuItem = mCastManager.addMediaRouterButton(menu, R.id.media_route_menu_item);

        if (mSearchEnabled) {
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            SearchView searchView = new SearchView(this);
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            mSearchMenuItem = menu.add("Search");
            mSearchMenuItem.setIcon(android.R.drawable.ic_menu_search);
            mSearchMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            mSearchMenuItem.setActionView(searchView);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onTakeScreenshotRequest() {
        // image naming and path  to include sd card  appending name you choose for file
        String mPath = Environment.getExternalStorageDirectory().toString() + "/Pictures/" + String.valueOf(new Date().getTime() + ".jpg");

        // create bitmap screen capture
        Bitmap bitmap;
        View v1 = getWindow().getDecorView().getRootView();
        v1.setDrawingCacheEnabled(true);
        bitmap = Bitmap.createBitmap(v1.getDrawingCache());
        v1.setDrawingCacheEnabled(false);

        OutputStream fout;
        File imageFile = new File(mPath);

        try {
            fout = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fout);
            fout.flush();
            fout.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        playShutterSound();
    }

    @Override
    public void onRemotePlayRequest(PlayRequest request, String mediaType) {

        if ("audio".equalsIgnoreCase(mediaType)) {
            MainApplication.getInstance().PlayerQueue = new Playlist();
            addItemsToPlaylist(request.getItemIds());
            Intent intent = new Intent(this, AudioPlaybackActivity.class);
            startActivity(intent);
        } else if ("video".equalsIgnoreCase(mediaType)) {
            MainApplication.getInstance().PlayerQueue = new Playlist();
            addItemsToPlaylist(request.getItemIds());
            Intent intent = new Intent(this, PlaybackActivity.class);
            startActivity(intent);
        }
    }

    protected void addItemsToPlaylist(String[] itemIds) {
        for (String id : itemIds) {
            PlaylistItem item = new PlaylistItem();
            item.Id = id;
            MainApplication.getInstance().PlayerQueue.PlaylistItems.add(item);
        }
    }

    @Override
    public void onSeekCommand(Long seekPositionTicks) {
        if (seekPositionTicks == null) return;
        if (AudioService.PlayerState.PLAYING.equals(MainApplication.getAudioService().getPlayerState())) {
            MainApplication.getAudioService().playerSeekTo((int)(seekPositionTicks / 10000));
        }
    }

    @Override
    public void onRemoteBrowseRequest(BaseItemDto item) {
        if ("video".equalsIgnoreCase(item.getMediaType())) {
            browseToVideoDetails(item);
        } else if ("audio".equalsIgnoreCase(item.getMediaType())) {
            if (!tangible.DotNetToJavaStringHelper.isNullOrEmpty(item.getAlbumId()))
            MainApplication.getInstance().API.GetItemAsync(item.getAlbumId(), MainApplication.getInstance().API.getCurrentUserId(), getAlbumResponse);
        } else if ("book".equalsIgnoreCase(item.getMediaType())) {
            browseToBookDetails(item);
        } else if ("game".equalsIgnoreCase(item.getMediaType())) {
            browseToVideoDetails(item);
        } else if ("photo".equalsIgnoreCase(item.getMediaType())) {
            browseToPhotoDetails(item);
        } else if ("musicalbum".equalsIgnoreCase(item.getType())) {
            browseToAlbumDetails(item);
        } else if ("musicartist".equalsIgnoreCase(item.getType())) {
            browseToArtistDetails(item);
        }
    }

    @Override
    public void onUserDataUpdated() {

    }

    @Override
    public void onGoHomeRequest() {
        Intent intent = new Intent(this, HomescreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onGoToSettingsRequest() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void clearReferences(){
        IWebsocketEventListener currActivity = MainApplication.getInstance().getCurrentActivity();
        if (currActivity != null && currActivity.equals(this))
            MainApplication.getInstance().setCurrentActivity(null);
    }

    private void browseToVideoDetails(BaseItemDto item) {
        String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(item);
        Intent intent = new Intent(this, MediaDetailsActivity.class);
        intent.putExtra("Item", jsonData);
        startActivity(intent);
    }

    private void browseToBookDetails(BaseItemDto item) {
        String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(item);
        Intent intent = new Intent(this, BookDetailsActivity.class);
        intent.putExtra("Item", jsonData);
        startActivity(intent);
    }

    private Response<BaseItemDto> getAlbumResponse = new Response<BaseItemDto>() {
        @Override
        public void onResponse(BaseItemDto item) {
            if (item == null) return;
            browseToAlbumDetails(item);
        }
    };

    private void browseToAlbumDetails(BaseItemDto item) {
        Intent intent = new Intent(BaseMbMobileActivity.this, MusicAlbumActivity.class);
        intent.putExtra("AlbumId", item.getId());
        startActivity(intent);
    }

    private void browseToPhotoDetails(BaseItemDto item) {
        String jsonData = MainApplication.getInstance().getJsonSerializer().SerializeToString(item);
        Intent intent = new Intent(this, PhotoDetailsActivity.class);
        intent.putExtra("Item", jsonData);
        startActivity(intent);
    }

    private void browseToArtistDetails(BaseItemDto item) {
        Intent intent = new Intent(this, ArtistActivity.class);
        intent.putExtra("ArtistId", item.getId());
        startActivity(intent);
    }


    private MediaPlayer mShutterMediaPlayer = null;

    private void playShutterSound() {
        AudioManager audioManager = (AudioManager) MainApplication.getInstance().getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);

        if (volume != 0)
        {
            if (mShutterMediaPlayer == null)
                mShutterMediaPlayer = MediaPlayer.create(MainApplication.getInstance(), Uri.parse("file:///system/media/audio/ui/camera_click.ogg"));
            if (mShutterMediaPlayer != null)
                mShutterMediaPlayer.start();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (MainApplication.getAudioService().getPlayerState() != AudioService.PlayerState.IDLE) {
            if (KeyEvent.KEYCODE_MEDIA_PLAY == event.getKeyCode() ||
                    KeyEvent.KEYCODE_MEDIA_PAUSE == event.getKeyCode() ||
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE == event.getKeyCode()) {
                MainApplication.getAudioService().togglePause();
                return true;
            } else if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == event.getKeyCode()) {
                MainApplication.getAudioService().previous();
                return true;
            } else if (KeyEvent.KEYCODE_MEDIA_NEXT == event.getKeyCode()) {
                MainApplication.getAudioService().next();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
