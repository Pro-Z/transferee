package com.hitomi.tilibrary.transfer;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.gyf.immersionbar.ImmersionBar;
import com.hitomi.tilibrary.style.index.CircleIndexIndicator;
import com.hitomi.tilibrary.style.progress.ProgressBarIndicator;
import com.hitomi.tilibrary.utils.AppManager;
import com.hitomi.tilibrary.utils.FileUtils;
import com.hitomi.tilibrary.view.video.ExoVideoView;
import com.hitomi.tilibrary.view.video.source.ExoSourceManager;

import java.io.File;

/**
 * Main workflow: <br/>
 * 1、点击缩略图展示缩略图到 transferee 过渡动画 <br/>
 * 2、显示下载高清图片进度 <br/>
 * 3、加载完成显示高清图片 <br/>
 * 4、高清图支持手势缩放 <br/>
 * 5、关闭 transferee 展示 transferee 到原缩略图的过渡动画 <br/>
 * Created by Vans Z on 2017/1/19.
 * <p>
 * email: 196425254@qq.com
 */
public class Transferee implements DialogInterface.OnShowListener,
        DialogInterface.OnKeyListener,
        TransferLayout.OnLayoutResetListener,
        AppManager.OnAppStateChangeListener {

    private Context context;
    private Dialog transDialog;

    private TransferLayout transLayout;
    private TransferConfig transConfig;
    private OnTransfereeStateChangeListener transListener;

    // 因为Dialog的关闭有动画延迟，固不能使用 dialog.isShowing, 去判断 transferee 的显示逻辑
    private boolean shown;

    /**
     * 构造方法私有化，通过{@link #getDefault(Context)} 创建 transferee
     *
     * @param context 上下文环境
     */
    private Transferee(Context context) {
        this.context = context;
        createLayout();
        createDialog();
        AppManager.getInstance().init((Application) context.getApplicationContext());
    }

    /**
     * @param context
     * @return {@link Transferee}
     */
    public static Transferee getDefault(Context context) {
        return new Transferee(context);
    }

    private void createLayout() {
        transLayout = new TransferLayout(context);
        transLayout.setOnLayoutResetListener(this);
    }

    private void createDialog() {
        transDialog = new AlertDialog.Builder(context,
                android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
                .setView(transLayout)
                .create();
        transDialog.setOnShowListener(this);
        transDialog.setOnKeyListener(this);
    }

    /**
     * 检查参数，如果必须参数缺少，就使用缺省参数或者抛出异常
     */
    private void checkConfig() {
        if (transConfig == null)
            throw new IllegalArgumentException("The parameter TransferConfig can't be null");
        if (transConfig.isSourceEmpty())
            throw new IllegalArgumentException("The parameter sourceUrlList or sourceUriList  can't be empty");
        if (transConfig.getImageLoader() == null)
            throw new IllegalArgumentException("Need to specify an ImageLoader");

        transConfig.setNowThumbnailIndex(Math.max(transConfig.getNowThumbnailIndex(), 0));
        transConfig.setOffscreenPageLimit(transConfig.getOffscreenPageLimit() <= 0
                ? 1 : transConfig.getOffscreenPageLimit());
        transConfig.setDuration(transConfig.getDuration() <= 0
                ? 300 : transConfig.getDuration());
        transConfig.setProgressIndicator(transConfig.getProgressIndicator() == null
                ? new ProgressBarIndicator() : transConfig.getProgressIndicator());
        transConfig.setIndexIndicator(transConfig.getIndexIndicator() == null
                ? new CircleIndexIndicator() : transConfig.getIndexIndicator());
    }

    /**
     * 配置 transferee 参数对象
     *
     * @param config 参数对象
     * @return transferee
     */
    public Transferee apply(TransferConfig config) {
        if (!shown) {
            transConfig = config;
            OriginalViewHelper.getInstance().fillOriginImages(config);
            checkConfig();
            transLayout.apply(config);
        }
        return this;
    }

    /**
     * transferee 是否显示
     *
     * @return true ：显示, false ：关闭
     */
    public boolean isShown() {
        return shown;
    }

    /**
     * 显示 transferee
     */
    public void show() {
        if (shown) return;
        if (context instanceof Activity) {
            ImmersionBar.with(((Activity) context), transDialog).init();
        }
        transDialog.show();
        if (transListener != null) {
            transListener.onShow();
        }
        shown = true;
    }

    /**
     * 显示 transferee, 并设置 OnTransfereeChangeListener
     *
     * @param listener {@link OnTransfereeStateChangeListener}
     */
    public void show(OnTransfereeStateChangeListener listener) {
        if (shown) return;
        transDialog.show();
        transListener = listener;
        transListener.onShow();
        shown = true;
    }

    /**
     * 关闭 transferee
     */
    public void dismiss() {
        if (shown && transLayout.dismiss(transConfig.getNowThumbnailIndex())) {
            shown = false;
        }
    }

    /**
     * 获取图片文件
     */
    public File getImageFile(String imageUri) {
        return transConfig.getImageLoader().getCache(imageUri);
    }

    /**
     * 清除 transferee 缓存,包括图片和视频文件缓存，注意清除视频缓存必须保证 transferee 是关闭状态
     */
    public void clear() {
        if (transConfig != null && transConfig.getImageLoader() != null) {
            transConfig.getImageLoader().clearCache();
        }
        File cacheFile = new File(context.getCacheDir(), ExoVideoView.CACHE_DIR);
        if (cacheFile.exists() && !shown) {
            FileUtils.deleteDir(new File(cacheFile, VideoThumbState.FRAME_DIR));
            ExoSourceManager.clearCache(context, cacheFile, null);
        }
    }

    @Override
    public void onShow(DialogInterface dialog) {
        AppManager.getInstance().register(this);
        transLayout.show();
    }

    @Override
    public void onReset() {
        AppManager.getInstance().unregister(this);
        transDialog.dismiss();
        if (transListener != null)
            transListener.onDismiss();
        shown = false;
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
                event.getAction() == KeyEvent.ACTION_UP &&
                !event.isCanceled()) {
            dismiss();
            return true;
        }
        return false;
    }

    @Override
    public void onForeground() {
        transLayout.pauseOrPlayVideo(false);
    }

    @Override
    public void onBackground() {
        transLayout.pauseOrPlayVideo(true);
    }

    /**
     * 设置 Transferee 显示和关闭的监听器
     *
     * @param listener {@link OnTransfereeStateChangeListener}
     */
    public void setOnTransfereeStateChangeListener(OnTransfereeStateChangeListener listener) {
        transListener = listener;
    }

    /**
     * Transferee 显示的时候调用 {@link OnTransfereeStateChangeListener#onShow()}
     * <p>
     * Transferee 关闭的时候调用 {@link OnTransfereeStateChangeListener#onDismiss()}
     */
    public interface OnTransfereeStateChangeListener {
        void onShow();

        void onDismiss();
    }

    public interface OnTransfereeLongClickListener {
        void onLongClick(ImageView imageView, String imageUri, int pos);
    }

}
