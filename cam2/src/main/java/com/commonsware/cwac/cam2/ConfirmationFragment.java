/***
 * Copyright (c) 2015 CommonsWare, LLC
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class ConfirmationFragment extends Fragment {
    private static final String ARG_NORMALIZE_ORIENTATION =
            "normalizeOrientation";
    private Float quality;
    private TextView recordTimeText;

    public interface BaseContract<T> {
        void completeRequest(T context, boolean isOK);

        void retakePicture();
    }

    public interface VideoContract extends BaseContract<CameraEngine.VideoTakenEvent> {
    }

    public interface Contract extends BaseContract<ImageContext> {
    }

    private ImageView iv;
    private ImageContext imageContext;
    private CameraEngine.VideoTakenEvent videoContext;

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

    public void setRecordingDuration(long duration) {
        if (getView() == null) {
            return;
        }

        if (recordTimeText == null) {
            return;
        }

        if (duration == 0) {
            recordTimeText.setVisibility(View.INVISIBLE);
            return;
        }

        recordTimeText.setVisibility(View.VISIBLE);

        long diff = duration / 1000;
        long mins = diff / 60;
        long secs = diff % 60;
        String minsText = mins < 10 ? "0" + mins : Long.toString(mins);
        String secsText = secs < 10 ? "0" + secs : Long.toString(secs);
        recordTimeText.setText(minsText + ":" + secsText);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.cwac_cam2_confirm_fragment, container, false);

        iv = (ImageView) v.findViewById(R.id.confirmImageView);

        if (imageContext != null) {
            loadImage(quality);
        }

        v.findViewById(R.id.cwac_cam2_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeRequest(false);
            }
        });

        v.findViewById(R.id.cwac_cam2_retry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageContext = null;
                videoContext = null;
                getContract().retakePicture();
            }
        });
        recordTimeText = (TextView) v.findViewById(R.id.cwac_cam2_timestamp);
        v.findViewById(R.id.cwac_cam2_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeRequest(true);
            }
        });

        return v;
    }

    private void completeRequest(boolean isOK) {
        if (getContract() instanceof VideoContract) {
            getContract().completeRequest(videoContext, isOK);
        } else {
            getContract().completeRequest(imageContext, isOK);
        }
    }

    public void setImage(ImageContext imageContext, Float quality) {
        this.imageContext = imageContext;
        this.quality = quality;

        if (iv != null) {
            loadImage(quality);
        }
    }

    public void setVideoThumbnail(CameraEngine.VideoTakenEvent event, Bitmap bitmap) {
        this.videoContext = event;
        iv.setImageBitmap(bitmap);
    }

    public boolean hasConfirmation() {
        return imageContext != null || videoContext != null;
    }

    private BaseContract getContract() {
        return ((BaseContract) getActivity());
    }

    private void loadImage(Float quality) {
        iv.setImageBitmap(imageContext.buildPreviewThumbnail(getActivity(),
                quality, getArguments().getBoolean(ARG_NORMALIZE_ORIENTATION)));
    }
}
