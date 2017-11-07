package com.EFPlayer.example;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Thread.sleep;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;

public class EFPlayerDemo extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private final static String TAG = "EFPlayerDemo";

    private final static int UPDATE_VIEW_PLAYED_TIME = 0;
    private final static int UPDATE_VIEW_DURATION = 1;

    private Context mContext = null;

    private SurfaceView player_view = null;
    private EditText link_input = null;
    private Button start_btn = null, pause_btn = null, stop_btn = null, restart_btn = null, file_select_btn = null, url_select_btn = null;
    private ListView urlListView = null;

    private Thread played_time_thread = null;
    private SeekBar seekBar = null;
    private long linkDuration = 0;
    private TextView played_view = null, duration_view = null;
    private Handler mHandler = null;

    private IjkMediaPlayer nioMediaPlayer = null;
    private boolean playerStarted = false;
    private boolean stopThread = false;

    private Uri linkUri = null;
    private List<String> urlList = new LinkedList<String>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;

        setContentView(R.layout.efplayer_demo);
//        player_view = (SurfaceView) findViewById(R.id.video_view);
        link_input = (EditText) findViewById(R.id.play_link);
        start_btn = (Button) findViewById(R.id.start_play);
        pause_btn = (Button) findViewById(R.id.pause_play);
        stop_btn = (Button) findViewById(R.id.stop_play);
        restart_btn = (Button) findViewById(R.id.restart_play);
        file_select_btn = (Button) findViewById(R.id.file_select);
        url_select_btn = (Button) findViewById(R.id.url_select);

        urlListView = (ListView) findViewById(R.id.uri_text);
        urlListView.setOnItemClickListener(this);

        start_btn.setOnClickListener(this);
        pause_btn.setOnClickListener(this);
        stop_btn.setOnClickListener(this);
        restart_btn.setOnClickListener(this);

        played_view = (TextView) findViewById(R.id.played_view);
        duration_view = (TextView) findViewById(R.id.duration_view);

        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (null != nioMediaPlayer)
                    nioMediaPlayer.seekTo(seekBar.getProgress());
            }
        });

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case UPDATE_VIEW_PLAYED_TIME:
                        long position = (long)msg.obj;

                        played_view.setText(timestamp2String(position));
                        duration_view.setText(timestamp2String(linkDuration - position));

                        seekBar.setProgress((int)position);

                        break;
                    case UPDATE_VIEW_DURATION:

                        duration_view.setText(timestamp2String(linkDuration));
                        seekBar.setMax((int)linkDuration);
                        break;
                    default:
                        break;
                }
            }

        };


        file_select_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
//                intent.setType("audio/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent,1);
            }
        });

        url_select_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File("/sdcard/url.txt");
                try{
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line = "";
                    while((line = br.readLine())!=null){
                        urlList.add(line);
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_expandable_list_item_1, urlList);
                    urlListView.setAdapter(adapter);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 1:
                if (resultCode == RESULT_OK) {
                    linkUri = data.getData();
                    Log.d(TAG, "File Uri: " + linkUri.toString());
                    Log.d(TAG, "File Path: " + getPath(this, linkUri));
                    stop();
                    start();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Toast.makeText(mContext,"Select url : " + urlList.get(position),Toast.LENGTH_SHORT).show();
        linkUri = Uri.parse(urlList.get(position));
        stop();
        start();
    }

    @Override
    public void onClick(View view) {
        String link = null;
        switch (view.getId()) {
            case R.id.start_play:
                link = link_input.getText().toString();
//                link = "https://ws-db2.pajk.qz123.com/66756a18-8b44-49ce-8968-7d67909a5a88_mp4_800k.mp4";
//                link = "http://ws-db2.pajk.qz123.com/d5031998-d227-455f-8a6f-7c70712c5e7b_mp4_800k_540p.mp4";
                linkUri = Uri.parse(link);
                start();
                break;
            case R.id.pause_play:
                pause();
                break;
            case R.id.stop_play:
                stop();
                break;
            case R.id.restart_play:
                restart();
                break;
        }
    }

    private void start() {
        if (null == nioMediaPlayer) {
            nioMediaPlayer = new IjkMediaPlayer();
            nioMediaPlayer.setScreenOnWhilePlaying(true);

            setPlayerOptions();

            try {
                nioMediaPlayer.setDataSource(mContext, linkUri);
            } catch(Exception e) {
                e.printStackTrace();
            }

            // No need setDisplay during play audio only.
//            nioMediaPlayer.setDisplay(player_view.getHolder());

            nioMediaPlayer.prepareAsync();

        } else if (false == playerStarted)
            nioMediaPlayer.start();

        playerStarted = true;
        stopThread = false;
    }

    private void pause() {
        if (null != nioMediaPlayer) {
            if (true == playerStarted) {
                nioMediaPlayer.pause();
                playerStarted = false;
                pause_btn.setText("Play");
            } else {
                nioMediaPlayer.start();
                playerStarted = true;
                pause_btn.setText("Pause");
            }
        }
    }

    private void stop() {
        if (null != nioMediaPlayer){
            nioMediaPlayer.setScreenOnWhilePlaying(false);
            nioMediaPlayer.stop();
            nioMediaPlayer.setDisplay(null);
            nioMediaPlayer.release();
        }

        nioMediaPlayer = null;
        playerStarted = false;
        stopThread = true;
    }

    private void restart() {
        stop();
        start();
    }

    private void setPlayerOptions() {

        if (null == nioMediaPlayer)
            return;

        nioMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_ERROR);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1);
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);

        // skip_loop_filter, this is for VIDEO software decoder output quality.
        nioMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", -16);

        nioMediaPlayer.setOnPreparedListener(mPreparedListener);
        nioMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
        nioMediaPlayer.setOnCompletionListener(mCompletionListener);
        nioMediaPlayer.setOnErrorListener(mErrorListener);
        nioMediaPlayer.setOnInfoListener(mInfoListener);
        nioMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
        nioMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
        nioMediaPlayer.setOnTimedTextListener(mOnTimedTextListener);
    }

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener = new IMediaPlayer.OnVideoSizeChangedListener() {
        public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
            int mVideoWidth = mp.getVideoWidth();
            int mVideoHeight = mp.getVideoHeight();
            int mVideoSarNum = mp.getVideoSarNum();
            int mVideoSarDen = mp.getVideoSarDen();
        }
    };

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            long mPrepareEndTime = System.currentTimeMillis();

            if (null != nioMediaPlayer)
                linkDuration = nioMediaPlayer.getDuration();

            Message msg = new Message();
            msg.what = UPDATE_VIEW_DURATION;
            msg.obj = linkDuration;
            mHandler.sendMessage(msg);

            played_time_thread = new Thread(new Runnable(){
                @Override
                public void run(){
                    long position = 0;
                    do {

                        if (true == playerStarted && null != nioMediaPlayer) {

                            position = nioMediaPlayer.getCurrentPosition();

                            Message msg = new Message();
                            msg.what = UPDATE_VIEW_PLAYED_TIME;
                            msg.obj = position;
                            mHandler.sendMessage(msg);
                        }

                        try {
                            sleep(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } while (false == stopThread);
                }
            });

            played_time_thread.start();
        }
    };

    private IMediaPlayer.OnCompletionListener mCompletionListener = new IMediaPlayer.OnCompletionListener() {
        public void onCompletion(IMediaPlayer mp) {
            playerStarted = false;
        }
    };

    private IMediaPlayer.OnInfoListener mInfoListener = new IMediaPlayer.OnInfoListener() {
        public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
            switch (arg1) {
                case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                    Log.d(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:");
                    break;
                case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    Log.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:");
                    break;
                case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.d(TAG, "MEDIA_INFO_BUFFERING_START:");
                    break;
                case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.d(TAG, "MEDIA_INFO_BUFFERING_END:");
                    break;
                case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                    Log.d(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: " + arg2);
                    break;
                case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    Log.d(TAG, "MEDIA_INFO_BAD_INTERLEAVING:");
                    break;
                case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    Log.d(TAG, "MEDIA_INFO_NOT_SEEKABLE:");
                    break;
                case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    Log.d(TAG, "MEDIA_INFO_METADATA_UPDATE:");
                    break;
                case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                    Log.d(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:");
                    break;
                case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                    Log.d(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:");
                    break;
                case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
//                            mVideoRotationDegree = arg2;
//                            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: " + arg2);
//                            if (mRenderView != null)
//                                mRenderView.setVideoRotation(arg2);
                    break;
                case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                    Log.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:");
                    break;
            }
            return true;
        }
    };

    private IMediaPlayer.OnErrorListener mErrorListener = new IMediaPlayer.OnErrorListener() {
        public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
            Log.d(TAG, "Error: " + framework_err + "," + impl_err);
            return true;
        }
    };

    private IMediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new IMediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(IMediaPlayer mp, int percent) {
            int mCurrentBufferPercentage = percent;
        }
    };

    private IMediaPlayer.OnSeekCompleteListener mSeekCompleteListener = new IMediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(IMediaPlayer mp) {
            long position = mp.getCurrentPosition();
            played_view.setText(timestamp2String(position));
        }
    };

    private IMediaPlayer.OnTimedTextListener mOnTimedTextListener = new IMediaPlayer.OnTimedTextListener() {
        @Override
        public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
            if (text != null) {
//                subtitleDisplay.setText(text.getText());
            }
        }
    };

    private String timestamp2String(long timestamp) {
        int hour, min, sec;
        String sHour, sMin, sSec;

        String output = null;

        hour = (int)timestamp / 3600000;
        min = (int)(timestamp % 3600000) / 60000;
        sec = (int)(timestamp % 60000) / 1000;

        if (hour <= 0)
            sHour = "00";
        else if (hour < 10)
            sHour = "0" + hour;
        else
            sHour = String.valueOf(hour);

        if (min <= 0)
            sMin = "00";
        else if (min < 10)
            sMin = "0" + min;
        else
            sMin = String.valueOf(min);

        if (sec <= 0)
            sSec = "00";
        else if (sec < 10)
            sSec = "0" + sec;
        else
            sSec = String.valueOf(sec);

        if (0 == hour && 0 == min)
            output = sSec;
        else if (0 == hour && 0 != min)
            output = sMin + ":" + sSec;
        else if (0 != hour)
            output = sHour + ":" + sMin + ":" + sSec;

        return output;
    }


    private String getPath(Context context, Uri uri){
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }
}