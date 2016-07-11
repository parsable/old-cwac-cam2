/***
 * Copyright (c) 2015 CommonsWare, LLC
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commonsware.cwac.cam2;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class ConfirmationFragment extends Fragment {
    private static final String ARG_NORMALIZE_ORIENTATION =
            "normalizeOrientation";
    private Float quality;
    private CameraEngine.VideoTakenEvent videoContext;
    private TextView tvVideoDuration;

    public interface BaseContract<T> {
        void completeRequest(T context, boolean isOK);

        void retakePicture();
    }

    public interface VideoContract extends BaseContract<CameraEngine.VideoTakenEvent> {
    }

    public interface Contract extends BaseContract<ImageContext> {
    }


    private ImageView ivConfirm;
    private ImageContext imageContext;

    public static ConfirmationFragment newInstance(boolean normalizeOrientation) {
        ConfirmationFragment result = new ConfirmationFragment();
        Bundle args = new Bundle();

        args.putBoolean(ARG_NORMALIZE_ORIENTATION, normalizeOrientation);
        result.setArguments(args);

        return (result);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        if (!(activity instanceof BaseContract)) {
            throw new IllegalStateException("Hosting activity must implement Contract interface");
        }

        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.cwac_cam2_confirm_fragment, container, false);
        ivConfirm = (ImageView) layout.findViewById(R.id.cwac_cam2_iv_confirm);
        //Look Pretty
        ivConfirm.setBackgroundColor(Color.BLACK);

        tvVideoDuration = (TextView) layout.findViewById(R.id.tv_timestamp);

        if (imageContext != null) {
            loadImage(quality);
        }

        return layout;
    }

    @Override
    public void onHiddenChanged(boolean isHidden) {
        super.onHiddenChanged(isHidden);

        if (!isHidden) {
            ActionBar ab = ((AppCompatActivity) getActivity()).getSupportActionBar();

            if (ab == null) {
                throw new IllegalStateException(
                        "CameraActivity confirmation requires an action bar!");
            } else {
                ab.show();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ab.setDisplayHomeAsUpEnabled(true);
                    ab.setHomeAsUpIndicator(R.drawable.cwac_cam2_ic_close_white);
                } else {
                    ab.setIcon(R.drawable.cwac_cam2_ic_close_white);
                    ab.setDisplayShowHomeEnabled(true);
                    ab.setHomeButtonEnabled(true);
                }
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.cwac_cam2_confirm, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getContract().completeRequest(getMediaContext(), false);
        } else if (item.getItemId() == R.id.cwac_cam2_ok) {
            getContract().completeRequest(getMediaContext(), true);
        } else if (item.getItemId() == R.id.cwac_cam2_retry) {
            getContract().retakePicture();
            imageContext = null;
            videoContext = null;
        } else {
            return (super.onOptionsItemSelected(item));
        }

        return (true);
    }

    private Object getMediaContext() {
        return imageContext == null ? videoContext : imageContext;
    }

    public void setImage(ImageContext imageContext, Float quality) {
        this.imageContext = imageContext;
        this.quality = quality;

        if (ivConfirm != null) {
            loadImage(quality);
        }
    }

    private BaseContract getContract() {
        return ((BaseContract) getActivity());
    }

    private void loadImage(Float quality) {
        ivConfirm.setImageBitmap(imageContext.buildPreviewThumbnail(getActivity(),
                quality, getArguments().getBoolean(ARG_NORMALIZE_ORIENTATION)));
    }

    public void setVideoThumbnail(CameraEngine.VideoTakenEvent event, Bitmap bitmap) {
        this.videoContext = event;
        ivConfirm.setImageBitmap(bitmap);
    }

    public boolean isWaitingForConfirmation() {
        return imageContext != null || videoContext != null;
    }

    public void setRecordingDuration(long duration) {
        if (getView() == null) {
            return;
        }
        if (tvVideoDuration == null) {
            return;
        }

        if (duration == 0) {
            tvVideoDuration.setVisibility(View.INVISIBLE);
            return;
        }

        tvVideoDuration.setVisibility(View.VISIBLE);

        long diff = duration / 1000;
        long mins = diff / 60;
        long secs = diff % 60;
        String minsText = mins < 10 ? "0" + mins : Long.toString(mins);
        String secsText = secs < 10 ? "0" + secs : Long.toString(secs);
        tvVideoDuration.setText(minsText + ":" + secsText);
    }
}
