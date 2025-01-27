package com.xukj.kpframework.gallery;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.xukj.kpframework.gallery.R;

import java.io.File;
import java.io.IOException;

public class ViewPagerFragment extends Fragment {

    private static final String BUNDLE_PHOTOIMAGE = "PhotoImage";

    private PhotoImage image;
    private SubsamplingScaleImageView mImageView;
    private ImageView mGIF;
    private ProgressBar mProgress;
    private TextView mTextView;

    public void setImage(PhotoImage image) {
        this.image = image;
    }

    public PhotoImage getImage() {
        return image;
    }

    public ViewPagerFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.view_pager_page, container, false);

        if (savedInstanceState != null) {
            if (image == null && savedInstanceState.containsKey(BUNDLE_PHOTOIMAGE)) {
                image = savedInstanceState.getParcelable(BUNDLE_PHOTOIMAGE);
            }
        }

        mGIF = rootView.findViewById(R.id.ivGif);
        mImageView = rootView.findViewById(R.id.imageView);
        mProgress = rootView.findViewById(R.id.loading);
        mTextView = rootView.findViewById(R.id.info);

        loadPhotoImage();

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        View rootView = getView();
        if (rootView != null) {
            outState.putParcelable(BUNDLE_PHOTOIMAGE, image);
        }
    }

    private void loadPhotoImage() {
        if (image == null || image.getUri() == null) return;
        mProgress.setVisibility(View.VISIBLE);
        mTextView.setText("");
        mTextView.setVisibility(View.GONE);
        RequestOptions options = new RequestOptions().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
        Glide.with(this).asFile().load(image.getUri())
                .apply(options)
                .listener(new RequestListener<File>() {

                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<File> target, boolean isFirstResource) {
                        useErrorTextView();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(final File resource, Object model, Target<File> target, DataSource dataSource, boolean isFirstResource) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    boolean isGif = false;
                                    try {
                                        isGif = PhotoImage.isGif(resource.getPath());
                                    } catch (Exception e) {
                                        // do nothing
                                    }
                                    if (isGif) {
                                        useGifImageView(resource);
                                    } else {
                                        useNormalImageView(resource);
                                    }
                                }
                            });
                        }
                        return false;
                    }
                }).submit();

    }

    private void useErrorTextView() {
        mProgress.setVisibility(View.GONE);
        mTextView.setText("图片加载失败");
        mTextView.setVisibility(View.VISIBLE);
        mImageView.setVisibility(View.GONE);
        mGIF.setVisibility(View.GONE);
    }

    private void useGifImageView(File resource) {
        mTextView.setVisibility(View.GONE);
        mProgress.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        mGIF.setVisibility(View.VISIBLE);
        Glide.with(this).asGif().load(resource).into(mGIF);
    }

    private void useNormalImageView(File resource) {
        String mode = image.getMode() != null ? image.getMode() : "inside";
        switch (mode) {
            case "custom":
                setCustomMode(resource, image);
                break;
            case "crop":
                setCropMode(resource, image);
                break;
            default:
                setInsideMode(resource, image);
                break;
        }

        mTextView.setVisibility(View.GONE);
        mProgress.setVisibility(View.GONE);
        mGIF.setVisibility(View.GONE);
        mImageView.setVisibility(View.VISIBLE);
    }

    /**
     * 高宽都在视图范围内
     */
    private void setInsideMode(final File resource, final PhotoImage image) {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ExifInterface exifInterface = new ExifInterface(activity.getContentResolver().openInputStream(Uri.fromFile(resource)));
                        String originalOrientation = exifInterface.getAttribute(ExifInterface.TAG_ORIENTATION);
                        int orientation = SubsamplingScaleImageView.ORIENTATION_0;
                        if (originalOrientation != null) {
                            switch (originalOrientation) {
                                case "6":
                                    orientation = SubsamplingScaleImageView.ORIENTATION_90;
                                    break;
                                case "3":
                                    orientation = SubsamplingScaleImageView.ORIENTATION_180;
                                    break;
                                case "8":
                                    orientation = SubsamplingScaleImageView.ORIENTATION_270;
                                    break;
                                default:
                                    break;
                            }
                        }
                        mImageView.setOrientation(orientation);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE);
                    mImageView.setDebug(image.isDebug());
                    mImageView.setImage(ImageSource.uri(Uri.fromFile(resource)));
                }
            });
        }
    }

    /**
     * 图片显示宽度等于视图宽度(如果图片原始宽度小于视图宽度则默认inside)
     *
     * @param resource
     */
    private void setCropMode(File resource, PhotoImage image) {
        DisplayMetrics displayMetrics = ScreenUtils.getDisplayMetrics(getContext());
        BitmapFactory.Options options = ScreenUtils.getImageFileOptions(resource);
        float scale = displayMetrics.widthPixels * 1f / options.outWidth;
        ImageViewState defaultState = null;
        if (options.outHeight > options.outWidth && options.outHeight * scale > displayMetrics.heightPixels) {
            // 大于屏幕的长图，需要把图片移到最上方
            defaultState = new ImageViewState(0, new PointF(0, 0), 0);
        }

        mImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);
        mImageView.setMinScale(scale);
        mImageView.setDebug(image.isDebug());
        mImageView.setImage(ImageSource.uri(Uri.fromFile(resource)), defaultState);
    }

    /**
     * 自定义模式
     */
    private void setCustomMode(File resource, PhotoImage image) {
        mImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);
        mImageView.setMinScale(image.getMinScale());
        mImageView.setMaxScale(image.getMaxScale());
        mImageView.setDebug(image.isDebug());
        mImageView.setImage(ImageSource.uri(Uri.fromFile(resource)));
    }
}
