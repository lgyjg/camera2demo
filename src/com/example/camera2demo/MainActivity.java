
package com.example.camera2demo;

import java.util.Arrays;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Builder;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {
    private Button captureButton;// 拍照按钮
    private Button recordButton;// 录像按钮
    private TextureView mTextureView;// 预览界面
    private SurfaceTexture mSurfaceTexturer;// 从图像流里获取一帧的缓存
    private CameraManager mCameraManager;
    private String[] mCameraIdList;
    private String mCameraId;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
//    private CameraOpenCloseLock mCameraOpenCloseLock;
    private Handler mHandler;
    private ImageReader mImageReader;
    private MediaRecorder mediaRecorder;
    private Builder mPreviewBuilder;
    private static final String TAG = "com.example.camera2demo.MainActivity";
    private Surface mTextureSurface;
    
    private static String mState = null;
    private static final String STATE_PREVIEW = "preview";
    private static final String STATE_WAITING_CAPTURE = "waiting_capture";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //绑定控件并设置监听器
        captureButton = (Button) findViewById(R.id.captrue_button);
        recordButton = (Button) findViewById(R.id.record_button);
        captureButton.setOnClickListener(this);
        recordButton.setOnClickListener(this);
        mTextureView = (TextureView) findViewById(R.id.m_textureview);
      
        mSurfaceTexturer = mTextureView.getSurfaceTexture();
        //fixbug : NPE
        if (mTextureView.getSurfaceTexture() == null) {
            Log.e(TAG, "mTextureView=null");
            
        }
        mSurfaceTexturer.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
        mSurfaceTexturer.setOnFrameAvailableListener(new OnFrameAvailableListener() {
            // 当帧数据读取时操作
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                // 开始初始化相机并且读取数据
                initCamera();// 初始化相机
            }
        });
        mTextureSurface = new Surface(mSurfaceTexturer);
        
    }

    /**
     * 初始化相机：
     * <p>
     * Title: initCamera<／p>
     * <p>
     * Description: 初始化相机需要以下几步：<／p> *1.通过服务取得CameraManager对象；
     * *2.通过CameraManager对象取得CameraCharacteristics对象,此对象的实例化需要CameraId； *3.取得CameraId的值
     */
    private void initCamera() {
        Log.d(TAG, "initCamera");
        
        HandlerThread mHandlerThread = new HandlerThread("camera");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        try {
            // 步骤1，实例化
            mCameraManager = (CameraManager) this.getSystemService(CAMERA_SERVICE);
            // 得到设备支持的相机列表，实例化
            mCameraIdList = mCameraManager.getCameraIdList();
            Log.d(TAG, mCameraIdList.toString());
            mCameraId = mCameraIdList[0];
            // 得到id为0的相机的属性,这些属性是不可变的，通过getKeys()\get()查询属性的值
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            // 存储一个有效的流配置用来创建会话，这些配置是可被相机支持的，可以作为输出格式，同时也包含了在连拍中所需要的每帧的最小持续时间
            StreamConfigurationMap configs = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            mImageReader = ImageReader.newInstance(mTextureView.getWidth(), mTextureView.getHeight(),
                    ImageFormat.JPEG, /* maxImages */7);
            mImageReader.setOnImageAvailableListener(new OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {

                }
            }, mHandler);
            //打开相机
            mCameraManager.openCamera(mCameraId, mDeviceStateCallback, mHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), R.string.camera_access_permision,
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // 创建拍照会话
    private void createCameraCaptureSession() throws CameraAccessException {
        Log.d(TAG, "createCameraCaptureSession");
        // 初始化预览界面
        mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        mPreviewBuilder.addTarget(mTextureSurface);
        mState = STATE_PREVIEW;
        // 要想通过设备照相或者录像就要取得CameraCaptureSession对象；
        mCameraDevice.createCaptureSession(
                Arrays.asList(mTextureSurface, mImageReader.getSurface()),
                mSessionStateCallback, mHandler);
    }

    // 通过DeviceStateCallback回调函数处理相机被打开后的事件
    private CameraDevice.StateCallback mDeviceStateCallback = new StateCallback() {
        // 设备被打开
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "DeviceStateCallback:camera was opend");
//            mCameraOpenCloseLock.release();
            mCameraDevice = camera;
            try {
                createCameraCaptureSession();
            } catch (CameraAccessException e) {
                Log.e(TAG, "mDeviceStateCallback onOpened");
                e.printStackTrace();
            }

        }

        // 设备错误
        @Override
        public void onError(CameraDevice camera, int error) {
            
        }

        // 设备失去连接
        @Override
        public void onDisconnected(CameraDevice camera) {

        }
    };
    // 状态改变时的回调函数
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            Log.d(TAG, "mSessionStateCallback onConfigured");
            //实例化session
            mCameraCaptureSession = session;
            try {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                session.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mHandler);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG,"set preview builder failed."+e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };
    // 拍照的回调函数，在这里完成相关的拍照操作
    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        // 开始照相
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            // 为mCameraCaptureSession对象实例化；
            mCameraCaptureSession = session;
            
            // 检查状态
            
        }

        // 照相过程中
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            // 为mCameraCaptureSession对象实例化；
            mCameraCaptureSession = session;
            checkState(partialResult);
        }

        // 照相完成
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            // 为mCameraCaptureSession对象实例化；
            mCameraCaptureSession = session;
            checkState(result);
        }

        // 照相失败
        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        // 照相对列完成
        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId,
                long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        // 照相队列中途打断
        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }
        //检查状态
        private void checkState(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW:
                    // NOTHING
                    break;
                case STATE_WAITING_CAPTURE:
                    int afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                            || CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState
                            || CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState) {
                        //do something like save picture
                    }
                    break;
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.captrue_button:
                // 照相
                try {
                    Log.i("linc", "take picture");
                    mState = STATE_WAITING_CAPTURE;
                    mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.record_button:
                // 录像

                break;
            default:
                break;
        }
    }

}
