/*
 * Copyright 2016 Continuous Emotion Detection
 *
 */

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
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
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
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
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
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

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
            Log.i(TAG, "onSurfaceTextureSizeChanged(...), Width, Height: " + Integer.toString(width) + ", " + Integer.toString(height));
            configureTransform(width, height);
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

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * An {@link TextView} for emotion notification.
     */
    private TextView mTextView;
    private TextView mPermText;


    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onClosed(CameraDevice camera) {
            Log.i(TAG, "CameraDevice onClosed()");
            // Called when the camera successfully closed
            mCameraDevice = null;
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.i(TAG, "CameraDevice onOpened()");
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            mCameraOpenCloseLock.release(); // release after preview initialized
            mUIHandler.postDelayed(takePictureTask, 2000);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.i(TAG, "CameraDevice onDisconnected()");
            // can be called
            // when a foreground running higher priority process takes over the camera -> onPause called first on this app
            // when initialization is unsuccessful
            if (mCameraOpenCloseLock.availablePermits() > 0) {
                mCameraOpenCloseLock.release(); // camera open may end up here
            }
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "CameraDevice OnError(), error code: " + Integer.toString(error));
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            Activity activity = getActivity();
            if (null != activity) {
                Log.i(TAG, "activity.finish() to be called");
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundPreviewThread;
    private HandlerThread mBackgroundCaptureThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundPreviewHandler;
    private Handler mBackgroundCaptureHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * the UI thread Handler
     */
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
            new EmotionApiCallAsyncTask(mTextView, mConnMgr, false).execute(reader.acquireNextImage());
        }

    };


    /**
     * A {@link Semaphore} to ensure camera open/ camera close execution is separated
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;


    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
            int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                    option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static EmotionDetectionFragment newInstance() {
        return new EmotionDetectionFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        mTextView = (TextView)view.findViewById(R.id.emoResult);
        mPermText = (TextView)view.findViewById(R.id.permisson);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mUIHandler = new Handler(Looper.getMainLooper());
        mConnMgr = (ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    Runnable takePictureTask = new Runnable() {
        @Override
        public void run() {
            takePicture();
        }};

    @Override
    public void onResume() {
        Log.i(TAG, "onResume() called");
        super.onResume();
        mAppIsResumed = true;
        if (!mCanUseCamera && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED &&
                !FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            mPermText.setVisibility(View.VISIBLE);
        } else {
            mCanUseCamera = true;
            mPermText.setVisibility(View.INVISIBLE);
            startBackgroundThreads();

            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
            // a camera and start preview from here (otherwise, we wait until the surface is ready in
            // the SurfaceTextureListener).
            if (mTextureView.isAvailable()) {
                Log.i(TAG, "onResume() mTextureView.isAvailable() - TRUE");
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                Log.i(TAG, "onResume() mTextureView.isAvailable() - FALSE");
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        }
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause() called");
        super.onPause();
        mAppIsResumed = false;
        mUIHandler.removeCallbacks(takePictureTask);
        stopBackgroundThreads();
        closeCamera();
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

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                Integer hwLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                Log.i(TAG, "Hardware level: " + Integer.toString(hwLevel));

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                Log.i(TAG, "Lens facing: " + Integer.toString(facing));

                int maxProc = characteristics.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC);
                Log.i(TAG, "REQUEST_MAX_NUM_OUTPUT_PROC: " + Integer.toString(maxProc));

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                // Log.i(TAG, "Scaler Stream Configuration Map got");

                int[] outputFormats = map.getOutputFormats();
                Log.i(TAG, "Output Formats: " + Arrays.toString(outputFormats));

                // For still image captures, we use the smallest available size.
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG); // ImageFormat.YUV_420_888, ImageFormat.JPEG
                Size largest = Collections.max(
                        Arrays.asList(sizes), new CompareSizesByArea());
                Collections.sort(Arrays.asList(sizes), new CompareSizesByArea());
                Log.i(TAG, "Number of image sizes: " + Integer.toString(sizes.length));
                // Size smallest = /*(sizes.length > 1)? sizes[1]:*/ Collections.min(Arrays.asList(sizes), new CompareSizesByArea()); // bigger than the smallest
                Log.i(TAG, "Smallest image size: " + Integer.toString(sizes[0].getWidth()) + "x" + Integer.toString(sizes[0].getHeight()) );
                Size smallest = findGreaterOrEqualTo640x480(sizes);
                Log.i(TAG, "Selected image size: " + Integer.toString(smallest.getWidth()) + "x" + Integer.toString(smallest.getHeight()) );

                if (mImageReader != null) {
                    mImageReader.close();
                }
                mImageReader = ImageReader.newInstance(smallest.getWidth(), smallest.getHeight(),
                        ImageFormat.JPEG, 2); // ImageFormat.YUV_420_888, ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mUIHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;
                Log.i(TAG, "Flash available:" + Boolean.toString(available));

                int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
                Log.i(TAG, "AutoFocus modes:" + Arrays.toString(afModes));

                int[] aeModes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
                Log.i(TAG, "AutoExplosure modes:" + Arrays.toString(aeModes));

                int[] scenes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
                Log.i(TAG, "Scene modes:" + Arrays.toString(scenes));

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when setUpCameraOutputs(...)");
            e.printStackTrace();
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
            if (ss.getWidth()*ss.getHeight() >= 640*480) {
                return ss;
            }
        }
        return sizes[0]; // smallest
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
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                // throw new RuntimeException("Time out waiting to lock camera opening.");
                ErrorDialog.newInstance(getString(R.string.camera_lockerror))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
            setUpCameraOutputs(width, height);
            configureTransform(width, height);
            manager.openCamera(mCameraId, mStateCallback, mBackgroundPreviewHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when openCamera(...)");
            e.printStackTrace();
        } catch (InterruptedException e) {
            // throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
            ErrorDialog.newInstance(getString(R.string.camera_lockinterruped))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);

        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
        } catch (InterruptedException e) {
            // throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
            ErrorDialog.newInstance(getString(R.string.camera_lockinterrupedclose))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
        if (null != mCaptureSession) {
            try {
                mCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                Log.i(TAG, "CameraAccessException when onPause()");
                e.printStackTrace();
            }
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mCameraOpenCloseLock.release();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThreads() {
        mBackgroundPreviewThread = new HandlerThread("CameraBackground");
        mBackgroundPreviewThread.start();
        mBackgroundPreviewHandler = new Handler(mBackgroundPreviewThread.getLooper());
        mBackgroundCaptureThread = new HandlerThread("CameraBackground");
        mBackgroundCaptureThread.start();
        mBackgroundCaptureHandler = new Handler(mBackgroundCaptureThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThreads() {
        if (mBackgroundPreviewThread != null) {
            mBackgroundPreviewThread.quitSafely();
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
            mBackgroundCaptureThread.quitSafely();
            try {
                mBackgroundCaptureThread.join();
                mBackgroundCaptureThread = null;
                mBackgroundCaptureHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "InterruptedException when stopBackgroundThreads(...)");
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
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
                            Log.i(TAG, "CameraCaptureSession onConfigured()");
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                Log.i(TAG, "The camera is already closed");
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            if (mCaptureSession != null) {
                                mCaptureSession.close();
                            }
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                // previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

                                // Finally, we start displaying the camera preview.
                                CaptureRequest previewRequest = previewRequestBuilder.build();
                                Log.i(TAG, "CameraCaptureSession onConfigured() setRepeatingRequest called");
                                mCaptureSession.setRepeatingRequest(previewRequest,
                                        null, mBackgroundPreviewHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "CameraAccessException when CameraCaptureSession.onConfigured(...)");
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.i(TAG, "CameraCaptureSession.StateCallback onConfigureFailed(..)");
                        }
                    }, null
            );
        }
        catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when createCameraPreviewSession(...)");
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        // lockFocus();
        mBackgroundCaptureHandler.post(captureStillPictureTask);
        mUIHandler.postDelayed(takePictureTask, 2000); // TODO: take picture ramdomly in a few sec
    }


    Runnable captureStillPictureTask = new Runnable() {
        @Override
        public void run() {
            captureStillPicture();
        }};

    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            // captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback captureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.i(TAG, "captureStillPicture() onCaptureCompleted()");
                }
            };

            mCaptureSession.capture(captureBuilder.build(), captureCallback, mBackgroundCaptureHandler); // TZs it was null
            Log.i(TAG, "captureStillPicture() capture(...) called");
        } catch (CameraAccessException e) {
            Log.e(TAG, "CameraAccessException when captureStillPicture(...)");
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
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

    /**
     * Shows an error message dialog.
     */
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

    /**
     * Shows OK confirmation dialog about camera permission.
     */
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

}
