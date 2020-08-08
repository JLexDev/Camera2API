package jlexdev.com.camera2api;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CameraApi extends AppCompatActivity {

    public static final String TAG = "CameraApi";

    CameraManager cameraManager;
    Integer cameraFacing;


    private FloatingActionButton fabTakePhoto;
    private TextureView textureView;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSession;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;

    private Size previewSize; // imageDimension
    private ImageReader imageReader;
    private File file;
    private boolean flashSupported;

    private static final int CAMERA_REQUEST_CODE = 200;

    private Handler backgroundHandler;
    private HandlerThread backgroundThread;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_api);


        cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;



        // Permiso de CAMARA
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_REQUEST_CODE);



        // Inicializar
        textureView = (TextureView)findViewById(R.id.texture_view);
        assert textureView != null;

        fabTakePhoto = (FloatingActionButton)findViewById(R.id.fab_take_photo);
        assert fabTakePhoto != null;

        // Eventos
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        /* Para llamarlo internamente
        TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
            // Methods
        };
        */

        fabTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });


    }



    /** CAMERA TextureView */
    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setUpCamera();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };



    /** CAMERA Callback */ // onCreate() ¿?
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.e(TAG, "onOpened");

            cameraDevice = camera;
            createPreviewSession(); // createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            cameraDevice.close();
            cameraDevice = null; // TODO: (Opcional aquí ¿?)
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

            cameraDevice.close();
            cameraDevice = null;
        }
    };


    /** CAMERA CaptureSession */
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            Toast.makeText(CameraApi.this, "Guardado: " + file, Toast.LENGTH_SHORT).show();
            createPreviewSession();
        }
    };



    /** Photo Session */ // createCameraPreview();
    private void createPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            assert surfaceTexture != null;

            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            Surface surface = new Surface(surfaceTexture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            // TODO: En el primer parámetro Arrays.asList(surface)
            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {

                            // Cuando la cámara ya está cerrada
                            if (cameraDevice == null){
                                return;
                            }

                            // // Cuando la sesión está lista, muestra la vista previa
                            try {
                                captureRequest = captureRequestBuilder.build();
                                cameraCaptureSession = session;
                                cameraCaptureSession.setRepeatingRequest(captureRequest,
                                        null, backgroundHandler);
                            } catch (CameraAccessException e){
                                e.printStackTrace();
                            }


                            /** TODO: O probar esto! en lugar del try ^^^^^

                            cameraCaptureSession = session;
                           updatePreview();
                            */
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }

                    }, backgroundHandler);

        } catch (CameraAccessException e){
            e.printStackTrace();
        }

    }



    /** PERMISO CAMARA */ // TODO: ver que sale de esto...
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQUEST_CODE){

            if (grantResults[0] == PackageManager.PERMISSION_DENIED){

                Toast.makeText(this, "Debes aceptar el permiso de acceso a CAMARA", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }




    /************ SETUP CAMERA ************/
    private void setUpCamera() {
        try {
            for (String cameraId: cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);

                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                        == cameraFacing){

                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];

                    this.cameraId = cameraId;
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    /************ OPEN/CLOSE CAMERA ************/
    private void openCamera() {
        Log.e(TAG, "is camera open");

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED){

                cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
            }

        } catch (CameraAccessException e){
            e.printStackTrace();
        }

        Log.e(TAG, "openCamera X");
    }


    private void closeCamera(){
        if (cameraCaptureSession != null){
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }

        if (cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
    }
    /******************************************/



    /************* Hilo 2do Plano *************/
    protected void startBackgroundThread(){
        backgroundThread = new HandlerThread("camera_background_thread");
        backgroundThread.start();

        backgroundHandler = new Handler(backgroundThread.getLooper());
    }



    protected void stopBackgroundThread(){
        if (backgroundHandler != null){
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }

        /* TODO: Probar de esta manera
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e){
            e.printStackTrace();
        }
        */
    }
    /******************************************/



    /** Take Picture */
    private void takePhoto() {
        if (cameraDevice == null){
            Log.e(TAG, "cameraDevice is null");
            return;
        }


        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());

            Size[] jpegSizes = null;

            if (characteristics != null){
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }

            int width = 640;
            int height = 480;

            if (jpegSizes != null && 0 < jpegSizes.length){
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);

            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            final File file = new File(Environment.getExternalStorageDirectory() + "/pic.jpg");

            ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;

                    try {
                        image = reader.acquireLatestImage();

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e){
                        e.printStackTrace();
                    } catch (IOException e){
                        e.printStackTrace();
                    } finally {
                        if (image != null){
                            image.close();
                        }
                    }
                }

                private void save(byte[] bytes) throws IOException{
                    OutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if (null != outputStream){
                            outputStream.close();
                        }
                    }
                }
            };

            reader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    Toast.makeText(CameraApi.this, "Guardado: " + file, Toast.LENGTH_SHORT).show();
                    createPreviewSession();
                }
            };

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, backgroundHandler);
                    } catch (CameraAccessException e){
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, backgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }











    @Override
    protected void onResume() {
        super.onResume();

        Log.e(TAG, "onResume");

        startBackgroundThread();
        if (textureView.isAvailable()){
            setUpCamera();
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        Log.e(TAG, "onStop");

        closeCamera();
        stopBackgroundThread();
    }



}
