package com.shuiyes.video.ui.tvlive;

import android.graphics.Point;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.shuiyes.video.R;
import com.shuiyes.video.bean.ListVideo;
import com.shuiyes.video.util.HttpUtils;
import com.shuiyes.video.widget.NumberView;
import com.shuiyes.video.widget.Tips;
import com.zhy.view.flowlayout.TagView;

public class HuyaListActivity extends TVListActivity implements View.OnClickListener {

    private int mRealWidth, mRealHeight, mRedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        mRealWidth = size.x;
        mRealHeight = size.y;
        mRedColor = getResources().getColor(android.R.color.holo_red_dark);
    }

    @Override
    public int getContentViewId() {
        return R.layout.activity_huyalist;
    }

    @Override
    protected void addListVideo(String text) {
        if (!text.contains(",")) return;
        String[] tmp = text.split(",");
        String title = tmp[0];
        String url = tmp[1];

        // 虎牙电影
        if(!mVideos.isEmpty()){
            mVideos.add(new ListVideo("", null, null));
        }
        mVideos.add(new ListVideo(title, null, null));
        mVideos.add(new ListVideo("测试", title, url.replace("http", "test")));

        // http://aldirect.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1.m3u8
        mVideos.add(new ListVideo("源.aldirect", title, url));

        // http://aldirect.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1_1200.m3u8
        mVideos.add(new ListVideo("源.aldirect_1200", title, url.replace(".m3u8", "_1200.m3u8")));

        // http://tx.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1.m3u8
        String tx_url = url.replace("aldirect", "tx");
        mVideos.add(new ListVideo("源.tx", title, tx_url));

        // http://tx.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1_1200.m3u8
        tx_url = tx_url.replace(".m3u8", "_1200.m3u8");
        mVideos.add(new ListVideo("源.tx_1200", title, tx_url));

        // http://js.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1.m3u8
        String js_url = url.replace("aldirect", "js");
        mVideos.add(new ListVideo("源.js", title, js_url));

        // http://js.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1_1200.m3u8
        js_url = js_url.replace(".m3u8", "_1200.m3u8");
        mVideos.add(new ListVideo("源.js_1200", title, js_url));

        // http://js.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1.m3u8
        String al_url = url.replace("aldirect", "js");
        mVideos.add(new ListVideo("源.al", title, al_url));

        // http://js.hls.huya.com/huyalive/94525224-2460685313-10568562945082523648-2789274524-10057-A-0-1_1200.m3u8
        al_url = al_url.replace(".m3u8", "_1200.m3u8");
        mVideos.add(new ListVideo("源.al_1200", title, al_url));
    }

    @Override
    protected TagView getTagView(int position, ListVideo o) {
        if (o.getUrl() == null) {
            // 标题
            TagView view = new TagView(mContext);
            int width = TextUtils.isEmpty(o.getText()) ? WindowManager.LayoutParams.MATCH_PARENT : mRealWidth - 400;
            view.setSize(width, WindowManager.LayoutParams.WRAP_CONTENT);
            view.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
            view.setText(o.getText());
            view.setTextSize(30);
            return view;
        } else if (o.getUrl().startsWith("test://")) {
            NumberView view = new NumberView(getApplicationContext(), o);
            view.setTextColor(mRedColor);
            view.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    test((NumberView) v);
                }
            });
            view.setSize(view.measureWidth(), NumberView.WH);
            return view;
        }

        return super.getTagView(position, o);
    }

    private Thread mThread;

    public void test(NumberView v) {
        if (mThread != null && mThread.isAlive()) {
            mThread.interrupt();
            mThread = null;
        }
        mThread = new Thread() {
            @Override
            public void run() {

                int id = v.getId();
                int flag = 0;
                for (int i = 1; i <= 8; i++) {
                    final NumberView view = mResultView.findViewById(id + i);
                    String html = HttpUtils.get(view.getUrl());

                    if(html.startsWith("Exception: thread interrupted")){
                        return;
                    }

                    final boolean enable = html.startsWith("#EXTM3U");
                    Log.e(TAG, view.getTitle() + view.getText() + ", " + (enable ? "有效" : "无效"));
                    if (enable) {
                        flag++;
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setEnabled(enable);
                        }
                    });
                }

                if (flag == 8) {
                    tips(v.getTitle() + " 测试结束, 全部有效");
                } else {
                    tips(v.getTitle() + " 测试结束, 有效: " + flag + ", 无效: " + (8 - flag));
                }
            }
        };
        mThread.start();
    }

    private void tips(String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Tips.show(getApplicationContext(), text);
            }
        });
    }

}