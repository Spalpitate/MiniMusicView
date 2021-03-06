package com.hrb.library;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import static android.R.attr.filter;

public class MiniMusicView extends FrameLayout implements MediaService.IMediaStateListener, ServiceConnection {
    private final String TAG = "MiniMusicView";
    private Context mContext;
    private ViewStub mViewStub;
    private RelativeLayout mLayout;
    private ImageView mIcon;
    private ImageView mControlBtn;
    private ProgressBar mLoadMusic;
    private TextView mMusicTitle;
    private TextView mMusicAuthor;
    private ProgressBar mProgressBar;
    private boolean mIsAddView;
    private boolean mIsPlay;
    private OnMusicStateListener mMusicStateListener;
    private OnNextButtonClickListener mNextButtonClickListener;
    private HeadsetPlugReceiver mHeadsetPlugReceiver;
    private int mMusicDuration;
    private boolean mIsPlayComplete;
    private String mCurPlayUrl;

    private MediaService mediaService;

    public MiniMusicView(Context context) {
        this(context, null);
    }

    public MiniMusicView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MiniMusicView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        mIsAddView = false;
        mIsPlay = true;
        mIsPlayComplete = false;
        initView();
        initAttributeSet(attrs);
    }

    private void initAttributeSet(AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray arr = mContext.obtainStyledAttributes(attrs, R.styleable.MiniMusicView);
        final boolean isLoadLayout = arr.getBoolean(R.styleable.MiniMusicView_isLoadLayout, false);
        if (isLoadLayout) {
            initDefaultView();
        }
        final int titleColor = arr.getColor(R.styleable.MiniMusicView_titleColor, Color.parseColor("#000000"));
        setTitleColor(titleColor);
        final int titleSize = arr.getDimensionPixelOffset(R.styleable.MiniMusicView_titleTextSize, -1);
        if (titleSize != -1) {
            setTitleTextSize(titleSize);
        }

        final int bgColor = arr.getColor(R.styleable.MiniMusicView_musicBackgroundColor, Color.parseColor("#eeeeee"));
        setMusicBackgroundColor(bgColor);

        final Drawable progressDrawable = arr.getDrawable(R.styleable.MiniMusicView_progressDrawable);
        if (progressDrawable != null) {
            setProgressDrawable(progressDrawable);
        }
        final Drawable iconDrawable = arr.getDrawable(R.styleable.MiniMusicView_musicIcon);
        if (iconDrawable != null) {
            setIconDrawable(iconDrawable);
        }
        arr.recycle();
    }

    private void initView() {
        View.inflate(mContext, R.layout.layout_default_viewstup, this);
        mViewStub = (ViewStub) findViewById(R.id.vs_mini_view);
        initReceiver();
    }

    public void initDefaultView() {
        if (mViewStub != null) {
            View view = mViewStub.inflate();
            mLayout = (RelativeLayout) view.findViewById(R.id.ll_layout);
            mIcon = (ImageView) view.findViewById(R.id.iv_music_icon);
            mControlBtn = (ImageView) view.findViewById(R.id.iv_control_btn);
            ImageView mNextBtn = (ImageView) view.findViewById(R.id.iv_next_btn);
            mLoadMusic = (ProgressBar) view.findViewById(R.id.pb_loading);
            mMusicTitle = (TextView) view.findViewById(R.id.tv_music_title);
            mMusicAuthor = (TextView) view.findViewById(R.id.tv_music_author);
            mProgressBar = (ProgressBar) view.findViewById(R.id.pb_progress);

            mControlBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    controlBtnClick();
                }
            });

            mNextBtn.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mNextButtonClickListener != null) {
                        mNextButtonClickListener.OnClick();
                    }
                }
            });
            mViewStub = null;
        }
    }

    private void initReceiver() {
        if (mHeadsetPlugReceiver == null) {
            mHeadsetPlugReceiver = new HeadsetPlugReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        mContext.registerReceiver(mHeadsetPlugReceiver, intentFilter);
    }

    private void controlBtnClick() {
        if (mIsPlay) {
            pausePlayMusic();
            changeControlBtnState(false);
        } else {
            if (!mIsPlayComplete) {
                resumePlayMusic();
            } else {
                startPlayMusic(mCurPlayUrl);
                mProgressBar.setProgress(0);
                mIsPlayComplete = false;
            }
            changeControlBtnState(true);
        }
        Log.i(TAG, "controlBtnClick: isPlay=" + mIsPlay);
    }

    @Override
    public void addView(View child) {
        removeAllViews();
        super.addView(child);
        mIsAddView = true;
        Log.d(TAG, "addView: [ " + this.hashCode() + " ]");
    }

    private void changeLoadingMusicState(boolean isLoading) {
        if (!mIsAddView) {
            if (isLoading) {
                mLoadMusic.setVisibility(View.VISIBLE);
                mControlBtn.setVisibility(View.GONE);
            } else {
                mLoadMusic.setVisibility(View.GONE);
                mControlBtn.setVisibility(View.VISIBLE);
            }
        }
    }

    public void changeControlBtnState(boolean isPlay) {
        if (!mIsAddView && mControlBtn != null) {
            if (isPlay) {
                mControlBtn.setImageResource(R.drawable.mini_btn_pause);
                mIsPlay = true;
            } else {
                mControlBtn.setImageResource(R.drawable.mini_btn_play);
                mIsPlay = false;
            }
        }
    }

    public void setOnMusicStateListener(OnMusicStateListener listener) {
        mMusicStateListener = listener;
    }

    public void setOnNextBtnClickListener(OnNextButtonClickListener listener) {
        mNextButtonClickListener = listener;
    }

    public void startPlayMusic(String path) {
        mCurPlayUrl = path;
        changeLoadingMusicState(true);
        changeControlBtnState(true);
        if (mediaService == null) {
            // bind service
            Intent intent = new Intent(mContext, MediaService.class);
            mContext.bindService(intent, MiniMusicView.this, Context.BIND_AUTO_CREATE);
        } else {
            mediaService.playMusic(mCurPlayUrl);
        }
        Log.d(TAG, "startPlayMusic: [ " + this.hashCode() + " ]");
    }

    public void resumePlayMusic() {
        if (mediaService != null) {
            mediaService.resumeMusic(mCurPlayUrl);
        }
        Log.d(TAG, "resumePlayMusic: [ " + this.hashCode() + " ]");
    }

    public void pausePlayMusic() {
        if (mediaService != null) {
            mediaService.pauseMusic();
        }
        Log.d(TAG, "pausePlayMusic: [ " + this.hashCode() + " ]");
    }

    public void seekToMusic(int pos) {
        if (mediaService != null) {
            mediaService.seekToMusic(pos);
        }
        Log.d(TAG, "seekToMusic: pos = " + pos);
    }

    public void stopPlayMusic() {
        mContext.unbindService(MiniMusicView.this);

        if (mHeadsetPlugReceiver != null) {
            mContext.unregisterReceiver(mHeadsetPlugReceiver);
        }
        mContext = null;
        Log.d(TAG, "stopPlayMusic: [ " + hashCode() + " ]");
    }

    public void setTitleColor(int color) {
        if (!mIsAddView && mMusicTitle != null) {
            mMusicTitle.setTextColor(color);
        }
    }

    public void setTitleTextSize(int dimen) {
        if (!mIsAddView && mMusicTitle != null) {
            mMusicTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen);
        }
    }

    public void setMusicBackgroundColor(int color) {
        if (!mIsAddView && mLayout != null) {
            mLayout.setBackgroundColor(color);
        }
    }

    public void setIconDrawable(Drawable background) {
        if (!mIsAddView && mIcon != null) {
            mIcon.setImageDrawable(background);
        }
    }

    public void setProgressDrawable(Drawable drawable) {
        if (!mIsAddView && mProgressBar != null) {
            mProgressBar.setProgressDrawable(drawable);
        }
    }

    public void setProgressMax(int max) {
        if (!mIsAddView && mProgressBar != null) {
            mProgressBar.setMax(max);
        }
    }

    public void setProgress(int progress) {
        if (!mIsAddView && mProgressBar != null) {
            mProgressBar.setProgress(progress);
        }
    }

    public void setTitleText(String text) {
        if (!mIsAddView && mMusicTitle != null) {
            mMusicTitle.setText(text);
        }
    }

    public void setAuthor(String text) {
        if (!mIsAddView && mMusicAuthor != null) {
            mMusicAuthor.setText(text);
        }
    }

    public boolean isPlaying() {
        return mIsPlay;
    }

    public int getMusicDuration() {
        return mMusicDuration;
    }

    @Override
    public void onPrepared(int duration) {
        mMusicDuration = duration;
        if (mMusicStateListener != null) {
            mMusicStateListener.onPrepared(mMusicDuration);
        }
        changeLoadingMusicState(false);
        setProgressMax(mMusicDuration);
        Log.d(TAG, "onPrepared: STATE_MUSIC_PREPARE");
    }

    @Override
    public void onProgressUpdate(int currentPos, int duration) {
        if (mMusicStateListener != null) {
            mMusicStateListener.onProgressUpdate(duration, currentPos);
        }
        setProgress(currentPos);
    }

    @Override
    public void onSeekComplete() {
        if (mMusicStateListener != null) {
            mMusicStateListener.onSeekComplete();
        }
        Log.d(TAG, "onSeekComplete: STATE_SEEK_COMPLETE");
    }

    @Override
    public void onCompletion() {
        mIsPlayComplete = true;
        changeControlBtnState(false);
        if (mMusicStateListener != null) {
            mMusicStateListener.onMusicPlayComplete();
        }
        Log.d(TAG, "onCompletion: STATE_PLAY_COMPLETE");
    }

    @Override
    public boolean onInfo(int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            changeLoadingMusicState(true);
            Log.i(TAG, "MEDIA_INFO_BUFFERING_START");
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            changeLoadingMusicState(false);
            Log.i(TAG, "MEDIA_INFO_BUFFERING_END");
        }
        if (mMusicStateListener != null) {
            mMusicStateListener.onInfo(what, extra);
        }
        return false;
    }

    @Override
    public boolean onError(int what, int extra) {
        if (mMusicStateListener != null) {
            mMusicStateListener.onError(what, extra);
        }
        if (!mIsAddView) {
            Toast.makeText(mContext, getResources().getString(R.string.load_error),
                    Toast.LENGTH_SHORT).show();
            mLoadMusic.setVisibility(View.GONE);
            if (mControlBtn.getVisibility() != View.VISIBLE) {
                mControlBtn.setVisibility(View.VISIBLE);
            }
            changeControlBtnState(false);
        }
        Log.d(TAG, "onError: STATE_PLAY_ERROR");
        return false;
    }

    public interface OnMusicStateListener {
        void onPrepared(int duration);
        void onError(int what, int extra);
        void onInfo(int what, int extra);
        void onMusicPlayComplete();
        void onSeekComplete();
        void onProgressUpdate(int duration, int currentPos);
        void onHeadsetPullOut();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(TAG, "onServiceConnected: [ " + this.hashCode() + " ]");
        MediaService.MediaBinder mediaBinder = (MediaService.MediaBinder) service;
        mediaService = mediaBinder.getService();
        mediaService.setMediaStateListener(MiniMusicView.this);
        if (!TextUtils.isEmpty(mCurPlayUrl) && mediaService != null) {
            mediaService.playMusic(mCurPlayUrl);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    public interface OnNextButtonClickListener {
        void OnClick();
    }

    private class HeadsetPlugReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                if (mIsPlay) {
                    if (mMusicStateListener != null) {
                        mMusicStateListener.onHeadsetPullOut();
                    }
                    pausePlayMusic();
                    if (!mIsAddView) {
                        changeControlBtnState(false);
                    }
                }
            }
            Log.d(TAG, "onReceive: ===HeadsetPullout===");
        }
    }
}
