package com.shuiyes.video.iqiyi;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.shuiyes.video.R;
import com.shuiyes.video.base.PlayActivity;
import com.shuiyes.video.bean.ListVideo;
import com.shuiyes.video.bean.PlayVideo;
import com.shuiyes.video.dialog.AlbumDialog;
import com.shuiyes.video.dialog.MiscDialog;
import com.shuiyes.video.letv.LetvSource;
import com.shuiyes.video.letv.LetvStream;
import com.shuiyes.video.util.HttpUtils;
import com.shuiyes.video.util.Utils;
import com.shuiyes.video.widget.MiscView;
import com.shuiyes.video.widget.NumberView;
import com.shuiyes.video.widget.Tips;
import com.shuiyes.video.youku.YoukuVideo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class IQiyiVActivity extends PlayActivity implements View.OnClickListener {

    private final String TAG = this.getClass().getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSourceView.setOnClickListener(this);
        mClarityView.setOnClickListener(this);
        mClarityView.setEnabled(false);
        mSelectView.setOnClickListener(this);
        mNextView.setOnClickListener(this);

        playVideo();
    }

    private List<ListVideo> mVideoList = new ArrayList<ListVideo>();
    private List<IQiyiVideo> mUrlList = new ArrayList<IQiyiVideo>();

    @Override
    protected void playVideo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /**
                     https://cache.video.iqiyi.com/jp/vi/1360228700/ec8be83b8d77aef79e25a983ebee3c6c/

                     视频详情：http://mixer.video.iqiyi.com/jp/mixin/videos/tvid
                     Exeample：http://mixer.video.iqiyi.com/jp/mixin/videos/1342023800
                     */

                    String tvid = null;
                    String vid = null;

                    String key = "?tvId=";
                    if(mUrl.contains(key)){
                        int len = mUrl.indexOf(key);
                        String tmp = mUrl.substring(len + key.length());
                        len = tmp.indexOf("&");
                        tvid = tmp.substring(0, len);

                        key = "&vid=";
                        len = tmp.indexOf(key);
                        vid = tmp.substring(len + key.length());

                        Log.e(TAG, ":url 'tvId="+tvid+", vid="+vid+"'");
                    }

                    if(tvid == null && vid == null){
                        mHandler.sendEmptyMessage(MSG_FETCH_VIDEOINFO);
                        String html = HttpUtils.open(mUrl);


                        // page-info 见附录1
                        key = ":page-info='";
                        if(html.contains(key)){
                            int len = html.indexOf(key);
                            String tmp = html.substring(len + key.length());
                            len = tmp.indexOf("'");
                            String pageInfo = tmp.substring(0, len);

                            try{
                                JSONObject obj = new JSONObject(pageInfo);

                                tvid = obj.getString("tvId");
                                vid = obj.getString("vid");

                                Log.e(TAG, ":page-info 'tvId="+tvid+", vid="+vid+"'");
                                mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TITLE, obj.getString("tvName")));
                            }catch (Exception e){
                            }
                        }

                        key = "param['tvid'] = \"";
                        if(tvid == null && html.contains(key)){
                            int len = html.indexOf(key);
                            String tmp = html.substring(len + key.length());
                            len = tmp.indexOf("\"");
                            tvid = tmp.substring(0, len);
                            Log.e(TAG, "param['tvid'] = "+tvid);
                        }

                        key = "param['vid'] = \"";
                        if(vid == null && html.contains(key)){
                            int len = html.indexOf(key);
                            String tmp = html.substring(len + key.length());
                            len = tmp.indexOf("\"");
                            vid = tmp.substring(0, len);
                            Log.e(TAG, "param['vid'] = "+vid);
                        }
                    }

                    String video = IQiyiUtils.getVMS(tvid, vid);

//                    Log.e(TAG, "video ="+video);
                    Utils.setFile("/sdcard/iqiyi", video);

                    JSONObject obj = new JSONObject(video);
                    if (!"A00000".equals(obj.getString("code"))) {
//                        Log.e(TAG, info);

                        if(obj.has("msg")){
                            fault(obj.getString("msg"));
                        }else{
                            fault(obj.getJSONObject("ctl").getString("msg"));
                        }
                        return;
                    }

                    JSONArray vidl = obj.getJSONObject("data").getJSONArray("vidl");
                    int vidlLen = vidl.length();

                    mUrlList.clear();
                    for (int i = 0; i < vidlLen; i++) {
                        JSONObject stream = (JSONObject) vidl.get(i);

                        int vd = stream.getInt("vd");
                        String m3u8Url = stream.getString("m3u");

                        IQiyiVideo.VideoType type = IQiyiVideo.formateVideoType(vd);
                        if(type != null){
                            mUrlList.add(new IQiyiVideo(type, m3u8Url));
                        }
                    }
                    Log.e(TAG, "UrlList=" + mUrlList.size() + "/" + vidlLen);

                    if (mUrlList.isEmpty()) {
                        fault("无视频地址");
                    } else {
                        Collections.sort(mUrlList, new Comparator<IQiyiVideo>() {
                            @Override
                            public int compare(IQiyiVideo v1, IQiyiVideo v2) {
                                return v2.getType().getScreenSize() - v1.getType().getScreenSize();
                            }
                        });

                        IQiyiVideo playVideo = null;
                        for (IQiyiVideo v : mUrlList) {
                            Log.i(TAG, v.toStr(mContext));

                            // TODO 4K 较卡，要改成可配置
                            if(playVideo == null && v.getType().getScreenSize() < IQiyiVideo.UHD_SZ){
                                playVideo = v;
                            }
                        }

                        mCurrentPosition = 0;
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_CACHE_VIDEO, playVideo));
                    }
                } catch (Exception e) {
                    fault(e);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_clarity:
                if (mClarityDialog != null && mClarityDialog.isShowing()) {
                    mClarityDialog.dismiss();
                }
                mClarityDialog = new MiscDialog(this, mUrlList);
                mClarityDialog.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mClarityDialog != null && mClarityDialog.isShowing()) {
                            mClarityDialog.dismiss();
                        }

                        mStateView.setText("初始化...");
                        mHandler.sendMessage(mHandler.obtainMessage(MSG_CACHE_VIDEO, ((MiscView) view).getPlayVideo()));
                    }
                });
                mClarityDialog.show();
                break;
            case R.id.btn_select:
                if (mAlbumDialog != null && mAlbumDialog.isShowing()) {
                    mAlbumDialog.dismiss();
                }
                mAlbumDialog = new AlbumDialog(this, mVideoList);
                mAlbumDialog.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mAlbumDialog != null && mAlbumDialog.isShowing()) {
                            mAlbumDialog.dismiss();
                        }

                        NumberView v = (NumberView) view;
                        mTitleView.setText(v.getTitle());
                        mVid = v.getUrl();

                        mVideoView.stopPlayback();

                        playVideo();
                    }
                });
                mAlbumDialog.show();
                break;
            case R.id.btn_next:
                playVideo();
                break;
        }
    }

    @Override
    protected void cacheVideo(PlayVideo video) {
        if (mUrlList.size() < 2) {
            mClarityView.setEnabled(false);
        }else{
            mClarityView.setEnabled(true);
        }

        mClarityView.setText(((IQiyiVideo)video).getType().getProfile());
    }

}