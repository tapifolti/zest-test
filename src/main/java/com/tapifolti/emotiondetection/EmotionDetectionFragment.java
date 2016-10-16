package com.tapifolti.emotiondetection;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.XmlResourceParser;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class EmotionDetectionFragment extends Fragment
        implements FragmentCompat.OnRequestPermissionsResultCallback {

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_APP_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    /**
     * onResume() sets it to true
     * onPause() sets it to false
     */
    boolean mAppIsResumed = false;

    /**
     * Tag for the {@link Log}.
     */
    public static final String TAG = "EmotionDetection";

    /**
     * Max preview size that is guaranteed by Camera2 API
     */
    private static int MAX_PREVIEW_WIDTH = 1920;
    private static int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureAvailable(...) called");
            if (mAppIsResumed) {
                openCamera(width, height);
            } // else onPause was just called after onResume which triggered this callback
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged(...), Width, Height: " + width + ", " + height);
            Log.i(TAG, "mTextureView width, height: " + mTextureView.getWidth() + "," + mTextureView.getHeight());
            setUpCameraOrientation(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            Log.i(TAG, "onSurfaceTextureDestroyed(...) called");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            // Log.i(TAG, "onSurfaceTextureUpdated(...) called");
        }

    };

    private String mCameraId;
    private TextureView mTextureView;
    private TextView mTextView;
    private TextView mPermText;
    private String mKeyCSEmotion;


    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onClosed(CameraDevice camera) {
            Log.i(TAG, "CameraDevice onClosed()");
            mCameraDevice = null;
            if (mCameraOpenCloseLock.availablePermits() < 1) {
                mCameraOpenCloseLock.release(); // camera open may end up here
            }
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.i(TAG, "CameraDevice onOpened()");
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            mUIHandler.postDelayed(takePictureTask, 4000);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.i(TAG, "CameraDevice onDisconnected()");
            // can be called
            // when a foreground running higher priority process takes over the camera -> onPause called first on this app
            // when initialization is unsuccessful
            if (mCameraOpenCloseLock.availablePermits() < 1) {
                mCameraOpenCloseLock.release(); // camera open may end up here
            }
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "CameraDevice OnError(), error code: " + error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            Activity activity = getActivity();
            if (null != activity) {
                Log.i(TAG, "activity.finish() to be called");
                activity.finish();
            }
        }

    };

    private HandlerThread mBackgroundPreviewThread;
    private HandlerThread mBackgroundCaptureThread;

    private Handler mBackgroundPreviewHandler;
    private Handler mBackgroundCaptureHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    private Handler mUIHandler;

    private boolean mCanUseCamera = true;

    private ConnectivityManager mConnMgr;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            new CSEmotionCallAsyncTask(mTextView, mConnMgr, false, mKeyCSEmotion).execute(reader.acquireNextImage());
        }

    };


    /**
     * A {@link Semaphore} to ensure camera open/ camera close execution is separated
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

     /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;
    private Size mPreviewSize; // W > H always should be swapped as needed


    public static EmotionDetectionFragment newInstance() {
        return new EmotionDetectionFragment();
    }


    Runnable takePictureTask = new Runnable() {
        @Override
        public void run() {
            takePicture();
        }};

    private void setTextureViewDims() {
        {   // TODO remove
            int displayRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            Log.i(TAG, "getActivity().getWindowManager().getDefaultDisplay().getRotation() is: " + displayRotation);
            Point size = new Point();
            getActivity().getWindowManager().getDefaultDisplay().getSize(size);
            Log.i(TAG, "getActivity().getWindowManager().getDefaultDisplay().getSize(size) is (X,Y): " + size.x + ", " + size.y);
            int orient = getActivity().getResources().getConfiguration().orientation;
            Log.i(TAG, "getActivity().getResources().getConfiguration().orientation is: " + orient);
            Log.i(TAG, "mTextureView width, height: " + mTextureView.getWidth() + "," + mTextureView.getHeight());
        }

        // it gives back orientation dependent dimension
        Point size = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(size);

        if (size.x >= size.y) {
            mPreviewSize = new Size(Math.max(mPreviewSize.getWidth(), mPreviewSize.getHeight()), Math.min(mPreviewSize.getWidth(), mPreviewSize.getHeight()));
        } else {
            mPreviewSize = new Size(Math.min(mPreviewSize.getWidth(), mPreviewSize.getHeight()), Math.max(mPreviewSize.getWidth(), mPreviewSize.getHeight()));
        }
        Log.i(TAG, "setTextureViewDims() mTextureView size set: " + mPreviewSize.getWidth() + " x " + mPreviewSize.getHeight() + " Rotation: " + mLastRotation);
        RelativeLayout.LayoutParams relLayo = new RelativeLayout.LayoutParams(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        relLayo.addRule(RelativeLayout.CENTER_IN_PARENT);
        mTextureView.setLayoutParams(relLayo);
        // TODO arrange text and image as needed
        // ViewGroup.LayoutParams textRelLayo = mTextView.getLayoutParams();
        if (!isInLayout()) {
            mTextureView.requestLayout();
        }
    }


    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_APP_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_APP_PERMISSION) {
            if (grantResults == null || grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "onRequestPermissionsResult() permission denied");
                mCanUseCamera = false;
            } else {
                Log.i(TAG, "onRequestPermissionsResult() permission granted");
                mCanUseCamera = true;
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void setUpCameraOutput() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                Integer hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                Log.i(TAG, "Hardware level: " + hwLevel);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                Log.i(TAG, "Lens facing: " + facing);

                int maxProc = characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC);
                Log.i(TAG, "REQUEST_MAX_NUM_OUTPUT_PROC: " + maxProc);

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Log.i(TAG, "SENSOR_ORIENTATION: " + mSensorOrientation);

                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Log.i(TAG, "Flash available:" + Boolean.toString(available));

                int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                Log.i(TAG, "AutoFocus modes:" + Arrays.toString(afModes));

                int[] aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                Log.i(TAG, "AutoExplosure modes:" + Arrays.toString(aeModes));

                int[] scenes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
                Log.i(TAG, "Scene modes:" + Arrays.toString(scenes));

                int[] noiseRed =  characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
                Log.i(TAG, "Noise reduction modes:" + Arrays.toString(noiseRed));

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                int[] outputFormats = map.getOutputFormats();
                Log.i(TAG, "Output Formats: " + Arrays.toString(outputFormats));

                Size[] sizesJpeg = map.getOutputSizes(ImageFormat.JPEG);
                Size largest = Collections.max(
                        Arrays.asList(sizesJpeg), new CompareSizesByArea());
                Collections.sort(Arrays.asList(sizesJpeg), new CompareSizesByArea());
                Log.i(TAG, "Number of image sizes: " + sizesJpeg.length);
                Log.i(TAG, "Smallest image size: " + sizesJpeg[0].getWidth() + "x" + sizesJpeg[0].getHeight());
                Size smallest = findGreaterOrEqualTo640x480(sizesJpeg);
                Log.i(TAG, "Selected image size: " + smallest.getWidth() + "x" + smallest.getHeight());

                if (mImageReader != null) {
                    mImageReader.close();
                }
                mImageReader = ImageReader.newInstance(smallest.getWidth(), smallest.getHeight(),
                        ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mUIHandler);

                Size[] sizesSurface = map.getOutputSizes(SurfaceTexture.class);
                mPreviewSize = chooseOptimalPreviewSize(sizesSurface);
                Log.i(TAG, "Selected preview size: " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight());
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when setUpCameraOutputs(...)");
            ErrorDialog.newInstance(getString(R.string.camera_cameraaccess))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when Camera2API not supported on the device
            Log.e(TAG, "NPE Camera2 API doesn't supported on this device");
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private static Size findGreaterOrEqualTo640x480(Size [] sizes) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        Collections.sort(Arrays.asList(sizes), new CompareSizesByArea());
        for (Size ss: sizes) {
            if (ss.getWidth() == 640 || ss.getWidth() == 480) {
                return ss;
            }
            if ((long)ss.getWidth()*ss.getHeight() >= (long)640*480) {
                return ss;
            }
        }
        return sizes[0]; // smallest
    }

    private Size chooseOptimalPreviewSize(Size [] sizes) {
        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        // mTextureView not yet available
        // chosen the biggest one and both dim <= MAX
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        Collections.sort(Arrays.asList(sizes), new CompareSizesByArea());
        int maxW = Math.max(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
        int maxH = Math.min(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);

        Size ret = null;
        for (Size s : sizes) {
            // Log.i(TAG, "Preview size: " + s.getWidth() + "x" + s.getHeight());
            int w = Math.max(s.getWidth(), s.getHeight());
            int h = Math.min(s.getWidth(), s.getHeight());

            if (w <= maxW && h <= maxH) {
                ret = s;
            }
        }
        return ret;
    }

    /**
     * Opens the camera specified by {@link EmotionDetectionFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return; // onResume will be called if permission granted
        }
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(4000, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "openCamera() mCameraOpenCloseLock.tryAcquire(...) failed");
                ErrorDialog.newInstance(getString(R.string.camera_lockerror))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
            setUpCameraOrientation(width, height);
            manager.openCamera(mCameraId, mStateCallback, mBackgroundPreviewHandler);
            Log.i(TAG, "manager.openCamera(...) called");
        } catch (CameraAccessException e) {
            mCameraOpenCloseLock.release();
            Log.e(TAG, "CameraAccessException when openCamera(...)");
            ErrorDialog.newInstance(getString(R.string.camera_cameraaccess))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            Log.e(TAG, "openCamera() InterruptedException");
            ErrorDialog.newInstance(getString(R.string.camera_lockinterruped))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);

        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        Log.i(TAG, "closeCamera() called");
        try {
            mCameraOpenCloseLock.acquire();
        } catch (InterruptedException e) {
            Log.e(TAG, "closeCamera() InterruptedException");
            ErrorDialog.newInstance(getString(R.string.camera_lockinterrupedclose))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
        if (null != mCaptureSession) {
            try {
                mCaptureSession.stopRepeating();
                mCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                Log.e(TAG, "closeCamera() CameraAccessException");
                ErrorDialog.newInstance(getString(R.string.camera_cameraaccess))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        } else {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThreads() {
        mBackgroundPreviewThread = new HandlerThread("PreviewBackground");
        mBackgroundPreviewThread.start();
        mBackgroundPreviewHandler = new Handler(mBackgroundPreviewThread.getLooper());
        mBackgroundCaptureThread = new HandlerThread("CaptureBackground");
        mBackgroundCaptureThread.start();
        mBackgroundCaptureHandler = new Handler(mBackgroundCaptureThread.getLooper());
    }

    /**
     * Stops the background threads and its {@link Handler}.
     */
    private void stopBackgroundThreads() {
        if (mBackgroundPreviewThread != null) {
            mBackgroundPreviewThread.quit();
            try {
                mBackgroundPreviewThread.join();
                mBackgroundPreviewThread = null;
                mBackgroundPreviewHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "InterruptedException when stopBackgroundThreads(...)");
                e.printStackTrace();
            }
        }
        if (mBackgroundCaptureThread != null) {
            mBackgroundCaptureThread.quit();
            try {
                mBackgroundCaptureThread.join();
                mBackgroundCaptureThread = null;
                mBackgroundCaptureHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "InterruptedException when stopBackgroundThreads(...)");
                e.printStackTrace();
            }
        }
        if (mCameraOpenCloseLock.availablePermits() < 1) {
            mCameraOpenCloseLock.release(); // the thread may died early
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Log.i(TAG, "setDefaultBufferSize: " + mPreviewSize.getWidth() + " x " + mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onActive(CameraCaptureSession session) {
                            Log.i(TAG, "CameraCaptureSession onActive()");
                        }

                        @Override
                        public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {
                            Log.i(TAG, "CameraCaptureSession onSurfacePrepared()");
                        }

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.i(TAG, "CameraCaptureSession onConfigured() called");
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                Log.i(TAG, "The camera is already closed");
                                return;
                            }

                            if (mCaptureSession != null) {
                                mCaptureSession.close();
                            }
                            mCaptureSession = cameraCaptureSession;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                previewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST);
                                previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);

                                CaptureRequest previewRequest = previewRequestBuilder.build();

                                // TODO only for testing purpose
                                CameraCaptureSession.CaptureCallback repeatingCallback =
                                        new CameraCaptureSession.CaptureCallback() {
                                            private boolean notify = true;
                                            @Override
                                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                                if (notify) {
                                                    Log.i(TAG, "First Repeating Capture completed");
                                                    notify = false;
                                                }
                                    }
                                };
                                Log.i(TAG, "CameraCaptureSession onConfigured() setRepeatingRequest called");
                                mCaptureSession.setRepeatingRequest(previewRequest,
                                        repeatingCallback, mBackgroundPreviewHandler);
                                mCameraOpenCloseLock.release(); // release after preview initialized
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "CameraAccessException when CameraCaptureSession.onConfigured(...)");
                                mCameraOpenCloseLock.release();
                                ErrorDialog.newInstance(getString(R.string.camera_cameraaccess))
                                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.i(TAG, "CameraCaptureSession.StateCallback onConfigureFailed(..)");
                            mCameraOpenCloseLock.release();
                        }
                    }, null
            );
        }
        catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when createCameraPreviewSession(...)");
            ErrorDialog.newInstance(getString(R.string.camera_cameraaccess))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    private int mLastRotation = 0;

    private void setUpCameraOrientation(int width, int height) {

        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        mLastRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        Log.i(TAG,  "setUpCameraOrientation() params: " + width + "x" + height +
                    "  mTextureView: " + mTextureView.getWidth() + "x" + mTextureView.getHeight() +
                    "  Preview dim: " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight() +
                    "  Rotation: " + mLastRotation);

        RectF textureRect = new RectF(0, 0, width, height);
        float centerX = textureRect.centerX();
        float centerY = textureRect.centerY();
        if (Surface.ROTATION_90 == mLastRotation || Surface.ROTATION_270 == mLastRotation) {
             float scaleX = (float) height / mPreviewSize.getWidth();
             float scaleY = (float) width / mPreviewSize.getHeight();
             matrix.postScale(scaleX, scaleY, centerX, centerY);
             matrix.postRotate(90 * (mLastRotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == mLastRotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void takePicture() {
        // it can hit any time on the UI thread, even between onStart and onStop
        if (mBackgroundCaptureHandler != null) {
            mUIHandler.postDelayed(takePictureTask, 4000); // TODO: take picture ramdomly in a few sec
            mBackgroundCaptureHandler.post(captureStillPictureTask);
        }
    }


    Runnable captureStillPictureTask = new Runnable() {
        @Override
        public void run() {
            captureStillPicture();
        }};

    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice || mCaptureSession == null) {
                return;
            }
            CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);

            // JPEG Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getImageOrientation(ORIENTATIONS.get(rotation)));

            CameraCaptureSession.CaptureCallback captureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.i(TAG, "captureStillPicture() onCaptureCompleted()");
                }
            };

            if (mCaptureSession != null) {
                mCaptureSession.capture(captureBuilder.build(), captureCallback, mBackgroundCaptureHandler);
                Log.i(TAG, "captureStillPicture() capture(...) called");
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when captureStillPicture(...)");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException when captureStillPicture(...)");
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage() + "Exception when captureStillPicture(...)");
            e.printStackTrace();
        }
    }

    private int getImageOrientation(int displayRotation) {
        int ret = mSensorOrientation - ((360 - displayRotation) % 360);
//        Log.i(TAG, "mSensorOrientation:" + mSensorOrientation+
//        " displayRotation:" + displayRotation + " ImageOrientation:" + ret);
        return ret;
    }
    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "Error";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_APP_PERMISSION);
                        }
                    })
                    .create();
        }
    }

    private String getApiKey() {
        XmlResourceParser parser = getActivity().getResources().getXml(R.xml.mscsvalue);
        try {
            parser.next();
            parser.next();
            return parser.nextText();
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            Log.e(TAG, "API key not accessible on this device");
            ErrorDialog.newInstance(getString(R.string.apikey_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);

        }
        return "";
    }

    // TODO make 180 degree flip fix simpler and faster
    Runnable setUpCameraOrientationTask = new Runnable() {
        @Override
        public void run() {
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            if (rotation != mLastRotation) {
                setUpCameraOrientation(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }};
    // fast 180 degree flip cannot be detected with onSurfaceTextureSizeChanged() or
    private OrientationEventListener mOrientationListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOrientationListener = new OrientationEventListener(getActivity().getBaseContext(),
                SensorManager.SENSOR_DELAY_NORMAL) {

            @Override
            public void onOrientationChanged(int orientation) {
                int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                // fix fast 180 degree flip
                if (rotation != mLastRotation) {
                    Log.i(TAG, "Orientation changed to: " + orientation + " degrees, lastRotation: " + mLastRotation + " currentRotation: " +rotation);
                    // to avoid calling it if onSurfaceTextureSizeChanged() will be called too
                    mUIHandler.postDelayed(setUpCameraOrientationTask, 100);
                }
            }
        };

        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        } else {
            mOrientationListener.disable();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mOrientationListener.disable();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView(...) called");
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        Log.i(TAG, "onViewCreated(...) called");
        mTextureView = (TextureView) view.findViewById(R.id.texture);
        mTextView = (TextView)view.findViewById(R.id.emoResult);
        mPermText = (TextView)view.findViewById(R.id.permisson);
        mKeyCSEmotion = getApiKey();

        Point size = new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(size);
        MAX_PREVIEW_WIDTH = Math.min(1920, Math.max(size.x, size.y));
        MAX_PREVIEW_HEIGHT = Math.min(1080, Math.min(size.x, size.y));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG, "onActivityCreated(...) called");
        mUIHandler = new Handler(Looper.getMainLooper());
        mConnMgr = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart() called");
        setUpCameraOutput();
        startBackgroundThreads();
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop() called");
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
        stopBackgroundThreads();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called");
        mAppIsResumed = true;
        if (!mCanUseCamera && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED &&
                !FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            mPermText.setVisibility(View.VISIBLE);
        } else {
            mCanUseCamera = true;
            mPermText.setVisibility(View.INVISIBLE);

            setTextureViewDims();

            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
            // a camera and start preview from here (otherwise, we wait until the surface is ready in
            // the SurfaceTextureListener).
            if (mTextureView.isAvailable()) {
                Log.i(TAG, "onResume() mTextureView.isAvailable() - TRUE - mTextureView.width:" +
                        mTextureView.getWidth() + ", mTextureView.height:" + mTextureView.getHeight());
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                Log.i(TAG, "onResume() mTextureView.isAvailable() - FALSE");
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause() called");
        mAppIsResumed = false;
        mUIHandler.removeCallbacks(takePictureTask);
        closeCamera();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged (...) called");
        setTextureViewDims();
    }
}
