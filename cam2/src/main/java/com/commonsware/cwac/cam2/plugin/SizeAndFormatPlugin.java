/**
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

package com.commonsware.cwac.cam2.plugin;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;

import com.commonsware.cwac.cam2.CameraConfigurator;
import com.commonsware.cwac.cam2.CameraPlugin;
import com.commonsware.cwac.cam2.CameraSession;
import com.commonsware.cwac.cam2.ClassicCameraConfigurator;
import com.commonsware.cwac.cam2.SimpleCameraTwoConfigurator;
import com.commonsware.cwac.cam2.SimpleClassicCameraConfigurator;
import com.commonsware.cwac.cam2.VideoTransaction;
import com.commonsware.cwac.cam2.util.Size;

import java.util.List;

/**
 * A plugin that configures the size and format of previews and
 * pictures to be taken by the camera. This, or a plugin like it,
 * needs to be in the plugin chain for the CameraSession.
 */
public class SizeAndFormatPlugin implements CameraPlugin {
    final private Size pictureSize;
    final private Size previewSize;
    private final int pictureFormat;

    private static int cachedHighResQuality = -1;
    private static int cachedLowResQuality = -1;

    private static final int VIDEO_MAX_WIDTH = 640;
    private static final int IMAGE_MAX_AREA = 960 * 720;

    private static Size cachedBestPictureSize = null;

    /**
     * Constructor.
     *
     * @param previewSize   the size of preview images
     * @param pictureSize   the size of pictures to be taken
     * @param pictureFormat the format of pictures to be taken, in
     *                      the form of an ImageFormat constant
     *                      (e.g., ImageFormat.JPEG)
     */
    public SizeAndFormatPlugin(Size previewSize, Size pictureSize, int pictureFormat) {
        this.previewSize = previewSize;
        this.pictureSize = pictureSize;
        this.pictureFormat = pictureFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends CameraConfigurator> T buildConfigurator(Class<T> type) {
        if (type == ClassicCameraConfigurator.class) {
            return (type.cast(new Classic()));
        }

        return (type.cast(new Two()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate(CameraSession session) {
        if (!session.getDescriptor().getPreviewSizes().contains(previewSize)) {
            throw new IllegalStateException(
                    "Requested preview size is not one that the camera supports");
        }

        if (!session.getDescriptor().getPictureSizes().contains(pictureSize)) {
            throw new IllegalStateException(
                    "Requested picture size is not one that the camera supports");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        // not required
    }

    class Classic extends SimpleClassicCameraConfigurator {
        /**
         * {@inheritDoc}
         */
        @Override
        public Camera.Parameters configureStillCamera(
                CameraSession session,
                Camera.CameraInfo info,
                Camera camera, Camera.Parameters params) {
            if (params != null) {
                Size best = choosePictureSize(params.getSupportedPictureSizes(), pictureSize);
                params.setPictureSize(best.getWidth(), best.getHeight());
                params.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
            }

            return (params);
        }

        @Override
        public void configureRecorder(CameraSession session,
                                      int cameraId,
                                      VideoTransaction xact,
                                      MediaRecorder recorder) {


            // HACKED TO SELECT A GOOD SIZE:
            CamcorderProfile profile =
                    CamcorderProfile.get(cameraId, chooseQualityFor(xact.getQuality() == 1));
            recorder.setProfile(profile);

            int highD = Math.max(profile.videoFrameWidth, profile.videoFrameHeight);
            int lowD = Math.min(profile.videoFrameWidth, profile.videoFrameHeight);
            float scale = 1.0f;

            /*
             * This was the reason the Nexus 5 was not working. For some reason scaling is not
             * working (even integer scales) for it. We have tested extensively against 4 : 3
             * and that seems to work fine (especially since it scales it 640x480 and the default
             * recording size is 320x240 which are also 4:3). So being super defensive about this.
             * Nexus 5 is 352x288 (11:9)
             */
            if (floatEquals((float) highD / lowD, 4.0f / 3.0f)) {
                if (highD <= VIDEO_MAX_WIDTH) {
                    scale = Math.round((float) VIDEO_MAX_WIDTH / highD);
                } else {
                    scale = (float) (1.0d / Math.round((double) highD / VIDEO_MAX_WIDTH));
                }
            }

            highD = (int) (scale * highD);
            lowD = (int) (scale * lowD);

            if (profile.videoFrameWidth >= profile.videoFrameHeight) {
                recorder.setVideoSize(highD, lowD);
            } else {
                recorder.setVideoSize(lowD, highD);
            }
        }
    }

    public static boolean floatEquals(float f1, float f2) {
        return Math.abs(f1 - f2) < 0.01f;
    }

    public static Size choosePictureSize(List<Camera.Size> sizes, Size originalSize) {
        if (cachedBestPictureSize != null) {
            return cachedBestPictureSize;
        }

        Camera.Size best = null;
        float originalAR = (float) originalSize.getWidth() / originalSize.getHeight();
        int bestAreaDiff = Integer.MAX_VALUE;

        for (Camera.Size size : sizes) {
            int currentAreaDiff = Math.abs(size.width * size.height - IMAGE_MAX_AREA);
            if (currentAreaDiff < bestAreaDiff &&
                    floatEquals((float) size.width / size.height, originalAR)) {
                bestAreaDiff = currentAreaDiff;
                best = size;
            }
        }

        cachedBestPictureSize = new Size(best.width, best.height);
        return cachedBestPictureSize;

    }

    public static int chooseQualityFor(boolean hiRes) {
        if (hiRes) {
            if (cachedHighResQuality < 0) {
                cachedHighResQuality = internalChooseQualityFor(true);
            }
            return cachedHighResQuality;
        } else {
            if (cachedLowResQuality < 0) {
                cachedLowResQuality = internalChooseLowQuality();
            }
            return cachedLowResQuality;
        }
    }

    private static int internalChooseLowQuality() {
        if (isCamcorderProfileSupported(CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA))) {
            return CamcorderProfile.QUALITY_QVGA;
        }

        if (isCamcorderProfileSupported(CamcorderProfile.get(CamcorderProfile.QUALITY_CIF))) {
            return CamcorderProfile.QUALITY_CIF;
        }

        if (isCamcorderProfileSupported(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))) {
            return CamcorderProfile.QUALITY_480P;
        }

        return CamcorderProfile.QUALITY_LOW;
    }

    private static int internalChooseQualityFor(boolean hiRes) {
        // https://parsable.atlassian.net/browse/SERA-3488
        int defaultQuality = hiRes ? CamcorderProfile.QUALITY_HIGH : CamcorderProfile.QUALITY_LOW;

        try {
            if (isCamcorderProfileSupported(CamcorderProfile.get(defaultQuality))) {
                return defaultQuality;
            }

            int bestRate = hiRes ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            int bestQuality = defaultQuality;

            int end = 8; // Can't use CamcorderProfile.QUALITY_2160P, only added in Android 5.0

            for (int i = CamcorderProfile.QUALITY_HIGH + 1; i < end; i++) {
                try {
                    CamcorderProfile profile = CamcorderProfile.get(i);
                    if (isCamcorderProfileSupported(profile)) {
                        if ((hiRes && profile.videoBitRate > bestRate) ||
                                (!hiRes && profile.videoBitRate < bestRate)) {
                            bestRate = profile.videoBitRate;
                            bestQuality = i;
                        }
                    }
                } catch (Throwable t) {
                    // Ignore
                }
            }

            return bestQuality;

        } catch (Throwable t) {
        }

        return defaultQuality;
    }

    public static boolean isCamcorderProfileSupported(CamcorderProfile profile) {
        return profile != null && (profile.videoCodec == MediaRecorder.VideoEncoder.H263 ||
                profile.videoCodec == MediaRecorder.VideoEncoder.H264) &&
                (profile.audioCodec == MediaRecorder.AudioEncoder.AAC ||
                        profile.audioCodec == MediaRecorder.AudioEncoder.HE_AAC ||
                        profile.audioCodec == MediaRecorder.AudioEncoder.AAC_ELD);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    class Two extends SimpleCameraTwoConfigurator {
        /**
         * {@inheritDoc}
         */
        @Override
        public ImageReader buildImageReader() {
            return (ImageReader.newInstance(pictureSize.getWidth(),
                    pictureSize.getHeight(), pictureFormat, 2));
        }
    }
}
