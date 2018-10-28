package com.shuiyes.video.youku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.shuiyes.video.base.BasePlayActivity;
import com.shuiyes.video.R;
import com.shuiyes.video.bean.ListVideo;
import com.shuiyes.video.bean.PlayVideo;
import com.shuiyes.video.dialog.MiscDialog;
import com.shuiyes.video.util.HttpUtils;
import com.shuiyes.video.util.Utils;
import com.shuiyes.video.widget.MiscView;

public class YoukuVActivity extends BasePlayActivity {

    private static String mToken;
    private List<YoukuVideo> mUrlList = new ArrayList<YoukuVideo>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBatName = "优酷视频";
        mVid = YoukuUtils.getPlayVid(mIntentUrl);
        playVideo();
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
                        YoukuVideo playVideo = (YoukuVideo) ((MiscView) view).getPlayVideo();
                        mStream = playVideo.getType().getType();

                        mHandler.sendMessage(mHandler.obtainMessage(MSG_CACHE_VIDEO, playVideo));
                    }
                });
                mClarityDialog.show();
                break;
            default:
                super.onClick(view);
                break;
        }
    }

    @Override
    protected void playVideo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    mHandler.sendEmptyMessage(MSG_FETCH_TOKEN);
                    if (mToken == null) {
                        mToken = YoukuUtils.fetchCna();
                        Log.e(TAG, "new mToken=" + mToken);
                    } else {
                        Log.e(TAG, "prev mToken=" + mToken);
                    }

                    if (mToken == null) {
                        fault("鉴权异常请重试");
                        return;
                    }

                    mHandler.sendEmptyMessage(MSG_FETCH_VIDEO);
                    String info = YoukuUtils.fetchVideo(mVid, mToken);

                    if (TextUtils.isEmpty(info)) {
                        mToken = null;
                        fault("解析异常请重试");
                        return;
                    }

                    Utils.setFile("youku", info);

                    JSONObject data = new JSONObject(info).getJSONObject("data");

                    if (data.has("error")) {
                        mToken = null;
                        fault(data.getJSONObject("error").getString("note"));
                        return;
                    }

                    JSONObject video = data.getJSONObject("video");

                    mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TITLE, video.getString("title")));

                    if (data.has("videos")) {
                        JSONObject videos = data.getJSONObject("videos");

                        if (videos.has("next")) {
                            String nid = videos.getJSONObject("next").getString("encodevid");
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_NEXT, nid));
                        }else{
                            Log.e(TAG, "No next video.");
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_NEXT, mVid));
                        }
//                        Log.e(TAG, "videos=" + videos);

                        if (videos.has("list")) {
                            mVideoList.clear();

                            JSONArray videoList = videos.getJSONArray("list");
                            for (int i = 0; i < videoList.length(); i++) {
                                JSONObject listVideo = (JSONObject) videoList.get(i);
                                String encodevid = listVideo.getString("encodevid");
                                String title = listVideo.getString("title");
                                mVideoList.add(new ListVideo(i + 1, title, encodevid));
                            }
                            Log.e(TAG, "VideoList=" + mVideoList.size());

                            mHandler.sendEmptyMessage(MSG_UPDATE_SELECT);
                        }
                    }

                    JSONArray streams = data.getJSONArray("stream");
                    int streamsLen = streams.length();

                    mUrlList.clear();
                    for (int i = 0; i < streamsLen; i++) {
                        JSONObject stream = (JSONObject) streams.get(i);

                        int size = stream.getInt("size");
                        String stream_type = stream.getString("stream_type");
                        String m3u8Url = stream.getString("m3u8_url");

                        mUrlList.add(new YoukuVideo(YoukuVideo.formateVideoType(stream_type), size, m3u8Url));
                    }

                    Log.e(TAG, "UrlList=" + mUrlList.size() + "/" + streamsLen);
                    if (mUrlList.isEmpty()) {
                        fault("无视频地址");
                    } else {
                        Collections.sort(mUrlList, new Comparator<YoukuVideo>() {
                            @Override
                            public int compare(YoukuVideo v1, YoukuVideo v2) {
                                return v2.getSize() - v1.getSize();
                            }
                        });

                        YoukuVideo playVideo = null;
                        for (YoukuVideo v : mUrlList) {
                            Log.i(TAG, v.toStr()+" mStream="+mStream);

                            if(playVideo == null || v.getType().getType().equals(mStream)){
                                playVideo = v;
                            }
                        }

                        mHandler.sendMessage(mHandler.obtainMessage(MSG_CACHE_VIDEO, playVideo));
                    }

                    //listHtmlAlbums();
                    listJsonAlbums();
                    Log.e(TAG, "VideoList=" + mVideoList.size());
                    mHandler.sendEmptyMessage(MSG_UPDATE_SELECT);
                } catch (Exception e) {
                    fault(e);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void listJsonAlbums(){
        try{
            String html = YoukuUtils.listAlbums(mVid);
            if(TextUtils.isEmpty(html)){
                Log.e(TAG, "listJsonAlbums is empty.");
                return;
            }

            Utils.setFile("album.youku", html);

            JSONObject obj = new JSONObject(html);
            html = obj.getString("html");

            String key = "item item-";
            if(html.contains(key)){
                mVideoList.clear();
                while(html.contains(key)){

                    html = html.substring(html.indexOf(key)+key.length());

                    String s = "seq=\"";
                    String tmp = html.substring(html.indexOf(s)+s.length());
                    s = "\"";
                    String seq = tmp.substring(0, tmp.indexOf(s));

                    s = "item-id=\"item_";
                    tmp = html.substring(html.indexOf(s)+s.length());
                    s = "\"";
                    String vid = tmp.substring(0, tmp.indexOf(s));


                    s = "title=\"";
                    tmp = html.substring(html.indexOf(s)+s.length());
                    s = "\"";
                    String title = tmp.substring(0, tmp.indexOf(s));

                    mVideoList.add(new ListVideo(seq, title, vid));
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 最多获取前后30集
     */
    private void listHtmlAlbums(){
        try{
            String html = HttpUtils.open(YoukuUtils.getPlayUrlByVid(mVid));
            if(TextUtils.isEmpty(html)){
                Log.e(TAG, "listHtmlAlbums is empty.");
                return;
            }

            Utils.setFile("youku.html", html);


            String key = "window.playerAnthology= ";
            if(html.contains(key)){
                String tmp = html.substring(html.indexOf(key)+key.length());
                key = "</script>";
                if(tmp.contains(key)){
                    tmp = tmp.substring(0, tmp.indexOf(key));
                }

                Utils.setFile("album.youku", tmp);

                JSONObject obj = new JSONObject(tmp);
                JSONArray arr = (JSONArray) obj.get("list");
                if(arr.length() > 0){
                    mVideoList.clear();
                    for (int i=0; i<arr.length(); i++){
                        JSONObject video = arr.getJSONObject(i);
                        String index = video.getString("seq");
                        String vid = video.getString("encodevid");
                        String title = video.getString("title");

                        mVideoList.add(new ListVideo(index, title, vid));
                    }
                }
            }else{
                Log.e(TAG, "listHtmlAlbums not album.");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    protected void cacheVideo(PlayVideo video) {
        if (mUrlList.size() < 2) {
            mClarityView.setEnabled(false);
        }else{
            mClarityView.setEnabled(true);
        }

        mClarityView.setText(((YoukuVideo)video).getType().getProfile());
    }

    @Override
    protected void playNextVideo(String title, String url) {
        mVideoView.stopPlayback();
        mTitleView.setText(title);
        mStateView.setText("初始化...");
        mLoadingProgress.setVisibility(View.VISIBLE);

        mVid = url;
        mPrepared = false;
        mCurrentPosition = 0;

        playVideo();
    }

    @Override
    protected int getPlayIndex(){
        int index = 0;
        for (int i = 0; i < mVideoList.size() - 1; i++) {
            if (mVid.equals(mVideoList.get(i).getUrl())) {
                index = i;
                break;
            }
        }
        return index;
    }

}
