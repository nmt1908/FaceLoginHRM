package com.example.facelogin2;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.graphics.Matrix;
import android.media.ExifInterface;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.*;

import com.example.facelogin2.User;
import com.example.facelogin2.ui.ComposeBridge;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;
import com.google.mlkit.vision.facemesh.FaceMeshPoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity implements FaceAnalyzer.FaceDetectionListener {
    private PreviewView previewView;
    private FaceGraphicOverlay graphicOverlay;
    private ImageCapture imageCapture;
    private Runnable hidePreviewRunnable;
    private ProcessCameraProvider cameraProvider;
    private TextView tvSsidValue;
    private LinearLayout endpointStatusContainer;

    private final Handler netHandler = new Handler(Looper.getMainLooper());
    private final long NET_REFRESH_MS = 5000; // 5 giây
    private boolean isTakingPhoto = false;
    private final OkHttpClient httpClient = new OkHttpClient();
    private long faceStraightStartTime = 0;
    private static final long STRAIGHT_FACE_DURATION = 1000;
    // UI States (từ VG-CAMERA)
    private static final int STATE_IDLE = 0;
    private static final int STATE_DETECTING = 1;
    private static final int STATE_PROCESSING = 2;
    private static final int STATE_COUNTDOWN = 3;
    private static final int STATE_SUCCESS = 4;
    private static final int STATE_ERROR = 5;
    private int currentUIState = STATE_IDLE;

    private androidx.compose.ui.platform.ComposeView composeUserInfo;
    private User activeUserForCompose = null;
    private boolean isUserInfoVisible = false;

    private FaceMeshDetector staticDetector;
    private long countdownStartTime = 0;

    private android.graphics.Rect lastFaceRect = null;
    private float[] lastMeshPoints = null;
    private int lastImageWidth = 0;
    private int lastImageHeight = 0;
    private int lastImageRotation = 0;
    private Bitmap frozenBitmap = null;

    private Handler idleHandler = new Handler(Looper.getMainLooper());

    private long IDLE_DELAY_MS;
    private boolean isCountingDownToIdle = false;
    private boolean isPreviewVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        staticDetector = FaceMeshDetection.getClient(
                new FaceMeshDetectorOptions.Builder().build()
        );

        if (!isInternetAvailable()) {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_LONG).show();
        }
        IDLE_DELAY_MS = getSharedPreferences("settings", MODE_PRIVATE).getInt("time_waiting", 1000);
        Log.e("IDLE_DELAY_MS", String.valueOf(IDLE_DELAY_MS));

        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        tvSsidValue = findViewById(R.id.tvSsidValue);
        endpointStatusContainer = findViewById(R.id.endpointStatusContainer);

        composeUserInfo = findViewById(R.id.composeUserInfo);
        updateComposeUI();

        // Safe View Area handling for Android 15/16 and devices with System Bars / Taskbars
        View mainRoot = findViewById(R.id.mainRoot);
        if (mainRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                );

                // Safe Area: Bù lề trên cho titleBar (tránh đè Status Bar / Camera Notch)
                LinearLayout titleBar = findViewById(R.id.titleBar);
                if (titleBar != null) {
                    titleBar.setPadding(
                            titleBar.getPaddingLeft(),
                            insets.top + dp(12),
                            titleBar.getPaddingRight(),
                            dp(12)
                    );
                }

                // Safe Area: Bù lề dưới và phải cho networkStatusBar (nổi an toàn trên Navigation Bar / Taskbar)
                View netBar = findViewById(R.id.networkStatusBar);
                if (netBar != null && netBar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) netBar.getLayoutParams();
                    lp.bottomMargin = insets.bottom + dp(10);
                    lp.rightMargin = insets.right + dp(10);
                    netBar.setLayoutParams(lp);
                }

                return windowInsets;
            });
        }
        // Kiosk Immersive Mode (tương tự như VG-FaceDetection)
        applyImmersiveMode();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 102);
        }
        startNetworkStatusLoop();

        graphicOverlay.setCameraFacing(true); // camera trước

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 101);
        }
        hidePreviewRunnable = () -> {
            runOnUiThread(() -> {
                previewView.setVisibility(View.GONE);
                isPreviewVisible = false;
                // Nếu cần, có thể gọi stopCamera() để giảm tải
                // stopCamera();
            });
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    // ===== Immersive Fullscreen (tương tự như VG-FaceDetection) =====
    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            final View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }
    private void startNetworkStatusLoop() {
        // Lập UI item cho từng endpoint 1 lần
        buildEndpointRowsIfNeeded();

        // Cập nhật ngay và lặp lại
        netHandler.post(networkRefreshRunnable);
    }

    private final Runnable networkRefreshRunnable = new Runnable() {
        @Override public void run() {
            updateSsidUI();
            checkAllEndpoints();
            netHandler.postDelayed(this, NET_REFRESH_MS);
        }
    };
    private void buildEndpointRowsIfNeeded() {
        if (endpointStatusContainer.getChildCount() > 0) return;

        for (int i = 0; i < endpoints.length; i++) {
            Endpoint ep = endpoints[i];

            // mỗi item: [Label][space][dot]
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(0, dp(2), 0, dp(2));

            TextView label = new TextView(this);
            label.setText(ep.label);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(12);

            View spacer = space(6);

            View dot = new View(this);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(10), dp(10));
            dot.setLayoutParams(dotLp);
            dot.setBackground(ContextCompat.getDrawable(this, R.drawable.dot_red)); // default

            item.addView(label);
            item.addView(spacer);
            item.addView(dot);

            // tag để update nhanh dot
            item.setTag(ep.label + "_row");
            dot.setTag(ep.label + "_dot");

            endpointStatusContainer.addView(item);

            // chèn gạch dọc giữa các item (trừ item cuối)
            if (i < endpoints.length - 1) {
                endpointStatusContainer.addView(makeDivider());
            }
        }
    }

    private View makeDivider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(8), 0, dp(8), 0);   // << spacing hai bên gạch
        v.setLayoutParams(lp);
        v.setBackgroundColor(0x66FFFFFF);    // trắng mờ
        return v;
    }

    private void updateSsidUI() {
        String ssid = getCurrentSsid();
        tvSsidValue.setText(ssid != null ? ssid : "—");
    }
    private void checkAllEndpoints() {
        for (Endpoint ep : endpoints) {
            checkSingleEndpoint(ep, ok -> runOnUiThread(() -> updateEndpointDot(ep.label, ok)));
        }
    }

    private void updateEndpointDot(String label, boolean ok) {
        View dot = null;
        // tìm dot theo tag
        for (int i = 0; i < endpointStatusContainer.getChildCount(); i++) {
            View row = endpointStatusContainer.getChildAt(i);
            Object tag = row.getTag();
            if (tag != null && tag.toString().equals(label + "_row")) {
                // dot là con thứ 2 (label, space, dot), nhưng để chắc chắn, tìm theo tag riêng
                dot = row.findViewWithTag(label + "_dot");
                break;
            }
        }
        if (dot != null) {
            dot.setBackground(ContextCompat.getDrawable(this, ok ? R.drawable.dot_green : R.drawable.dot_red));
        }
    }
    private void checkSingleEndpoint(Endpoint ep, java.util.function.Consumer<Boolean> cb) {
        new Thread(() -> {
            boolean ok = false;
            java.net.Socket socket = null;
            try {
                java.net.URI u = java.net.URI.create(ep.url);
                String host = (u.getHost() != null) ? u.getHost() : u.getAuthority();
                if (host == null) host = ep.url; // phòng hờ
                int port = u.getPort();
                if (port == -1) {
                    port = "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
                }

                socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 1200); // timeout connect
                ok = true; // chỉ cần connect thành công là coi như reachable
            } catch (Exception ignore) {
                ok = false;
            } finally {
                try { if (socket != null) socket.close(); } catch (Exception ignore) {}
                final boolean result = ok;
                runOnUiThread(() -> cb.accept(result));
            }
        }).start();
    }


    private String getCurrentSsid() {
        try {
            // 1) Thử WifiManager trước (nhiều máy đáng tin hơn)
            android.net.wifi.WifiManager wm =
                    (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) {
                    String ssid = normalizeSsid(wi.getSSID());
                    // Nếu có SSID hợp lệ -> trả luôn
                    if (ssid != null && !ssid.equals("<unknown>") && !ssid.equals("0x")) {
                        return ssid;
                    }
                }
            }

            // 2) Fallback qua ConnectivityManager + NetworkCapabilities
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                android.net.Network active = cm.getActiveNetwork();
                if (active != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Object ti = caps.getTransportInfo();
                            if (ti instanceof android.net.wifi.WifiInfo) {
                                String ssid = normalizeSsid(((android.net.wifi.WifiInfo) ti).getSSID());
                                if (ssid != null) return ssid;
                            }
                        }
                    }
                }
            }

            // 3) Nếu đến đây vẫn không có, kiểm tra quyền & trạng thái Location
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return "<permission required>";
            }
            if (!isLocationEnabled()) {
                return "<location off>";
            }
        } catch (SecurityException se) {
            return "<permission required>";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "—";
    }

    private String normalizeSsid(String raw) {
        if (raw == null) return null;
        if (raw.equals("<unknown ssid>")) return "<unknown>";
        // Xóa dấu ngoặc kép nếu có
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private View space(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(1)));
        return v;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    private static class Endpoint {
        final String label;
        final String url;
        Endpoint(String label, String url) { this.label = label; this.url = url; }
    }

    // Khai báo các endpoint bạn muốn giám sát
    private final Endpoint[] endpoints = new Endpoint[] {
            new Endpoint("GMO040", "http://10.13.34.166:5001/recognize-anti-spoofing"),
            new Endpoint("PAPAGO", "http://10.1.16.23:8001/api/x/fr/env/face_search"),
            new Endpoint("GMO021", "https://gmo021.cansportsvg.com/hr/hrm/")
    };

    private void updateComposeUI() {
        if (composeUserInfo == null) return;
        ComposeBridge.setMainOverlayContent(
                composeUserInfo,
                activeUserForCompose,
                isUserInfoVisible,
                currentUIState,
                frozenBitmap,
                lastFaceRect,
                lastMeshPoints,
                lastImageWidth,
                lastImageHeight,
                lastImageRotation
        );
    }

    private void showModernUserInfo(User user) {
        runOnUiThread(() -> {
            activeUserForCompose = user;
            isUserInfoVisible = true;
            updateComposeUI();
        });
    }

    private void hideModernUserInfo() {
        runOnUiThread(() -> {
            isUserInfoVisible = false;
            currentUIState = STATE_IDLE;
            frozenBitmap = null;
            updateComposeUI();
        });
    }

    private void handleRecognitionFail() {
        runOnUiThread(() -> {
            currentUIState = STATE_ERROR;
            User failUser = new User("Recognition Failed", "------", "0%");
            activeUserForCompose = failUser;
            isUserInfoVisible = true;
            frozenBitmap = previewView.getBitmap();
            updateComposeUI();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                hideModernUserInfo();
                currentUIState = STATE_IDLE;
                isTakingPhoto = false;
                updateComposeUI();
                startCamera();
            }, 2500);
        });
    }

    private void showToastOnMainThread(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void startCamera() {
        isTakingPhoto = false;
        faceStraightStartTime = 0;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(
                        ContextCompat.getMainExecutor(this),
                        new FaceAnalyzer(this, graphicOverlay, this)
                );

                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isTakingPhoto = false;
        faceStraightStartTime = 0;
    }

    @Override
    public void onFaceMeshDetected(FaceMesh mesh, int width, int height, int rotation, boolean isLookingStraight) {
        if (currentUIState == STATE_PROCESSING || currentUIState == STATE_SUCCESS) return;

        runOnUiThread(() -> {
            lastFaceRect = mesh.getBoundingBox();
            java.util.List<FaceMeshPoint> points = mesh.getAllPoints();
            lastMeshPoints = new float[points.size() * 2];
            for (int i = 0; i < points.size(); i++) {
                lastMeshPoints[i * 2] = points.get(i).getPosition().getX();
                lastMeshPoints[i * 2 + 1] = points.get(i).getPosition().getY();
            }

            lastImageWidth = width;
            lastImageHeight = height;
            lastImageRotation = rotation;

            if (currentUIState == STATE_COUNTDOWN) {
                if (!isLookingStraight) {
                    currentUIState = STATE_DETECTING;
                    countdownStartTime = 0;
                } else {
                    long elapsed = System.currentTimeMillis() - countdownStartTime;
                    if (elapsed >= 1500) {
                        lockAndProcess();
                    }
                }
            } else if (currentUIState != STATE_PROCESSING && currentUIState != STATE_SUCCESS) {
                currentUIState = STATE_DETECTING;
            }

            if (currentUIState != STATE_PROCESSING && currentUIState != STATE_SUCCESS) {
                updateComposeUI();
            }
        });
    }

    @Override
    public void onNoFaceDetected() {
        if (currentUIState == STATE_PROCESSING || currentUIState == STATE_SUCCESS) return;
        runOnUiThread(() -> {
            if (currentUIState != STATE_IDLE) {
                currentUIState = STATE_IDLE;
                countdownStartTime = 0;
                lastFaceRect = null;
                lastMeshPoints = null;
                updateComposeUI();
            }
        });
    }

    @Override
    public void onFaceInTarget(FaceMesh mesh, boolean isLookingStraight) {
        if (isTakingPhoto || currentUIState == STATE_PROCESSING || currentUIState == STATE_SUCCESS) return;
        runOnUiThread(() -> {
            if (isLookingStraight && currentUIState != STATE_COUNTDOWN) {
                currentUIState = STATE_COUNTDOWN;
                countdownStartTime = System.currentTimeMillis();
                updateComposeUI();
            }
        });
    }

    @Override
    public void onFaceNotInTarget() {
    }

    @Override
    public void onAverageLuminance(double luminance) {
    }

    private void lockAndProcess() {
        if (currentUIState == STATE_PROCESSING) return;

        runOnUiThread(() -> {
            frozenBitmap = previewView.getBitmap();
            if (frozenBitmap == null) return;

            currentUIState = STATE_PROCESSING;
            countdownStartTime = 0;
            updateComposeUI();

            // Haptic Feedback
            try {
                android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(60);
                    }
                }
            } catch (Exception ignored) {}

            InputImage inputImage = InputImage.fromBitmap(frozenBitmap, 0);

            staticDetector.process(inputImage)
                    .addOnSuccessListener(meshes -> {
                        if (!meshes.isEmpty()) {
                            FaceMesh mesh = meshes.get(0);
                            java.util.List<FaceMeshPoint> points = mesh.getAllPoints();
                            lastMeshPoints = new float[points.size() * 2];
                            for (int i = 0; i < points.size(); i++) {
                                lastMeshPoints[i * 2] = points.get(i).getPosition().getX();
                                lastMeshPoints[i * 2 + 1] = points.get(i).getPosition().getY();
                            }

                            lastFaceRect = mesh.getBoundingBox();
                            lastImageWidth = frozenBitmap.getWidth();
                            lastImageHeight = frozenBitmap.getHeight();
                            lastImageRotation = 0;

                            updateComposeUI();
                        }
                        uploadBitmapToApi(frozenBitmap);
                    })
                    .addOnFailureListener(e -> uploadBitmapToApi(frozenBitmap));
        });
    }

    private void uploadBitmapToApi(Bitmap bitmap) {
        if (bitmap == null) return;
        new Thread(() -> {
            try {
                File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File file = new File(dir, "frozen_capture_" + System.currentTimeMillis() + ".jpg");
                java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.flush();
                out.close();
                uploadImageFile(file);
            } catch (Exception e) {
                Log.e("Upload", "Failed to save frozen bitmap", e);
            }
        }).start();
    }

    private void uploadImageFile(File file) {
        new Thread(() -> {
            Response response = null;
            String responseBody = "";
            boolean isFallback = false;

            try {
                byte[] fileBytes;
                try (FileInputStream fis = new FileInputStream(file)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = fis.read(buf)) != -1) bos.write(buf, 0, r);
                    fileBytes = bos.toByteArray();
                }

                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                // 1. API chính (Port 5001)
                RequestBody bodyPrimary = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image_file", file.getName(),
                                RequestBody.create(fileBytes, MediaType.parse("image/jpeg")))
                        .build();

                Request reqPrimary = new Request.Builder()
                        .url("http://10.13.34.166:5001/recognize-anti-spoofing")
                        .addHeader("X-API-Key", "vg_login_app")
                        .addHeader("X-Time", currentTime)
                        .post(bodyPrimary)
                        .build();

                OkHttpClient clientWithTimeout = httpClient.newBuilder()
                        .callTimeout(10, TimeUnit.SECONDS)
                        .build();

                try {
                    Log.d("TIMECALL", "Calling primary API: http://10.13.34.166:5001/recognize-anti-spoofing");
                    response = clientWithTimeout.newCall(reqPrimary).execute();
                    responseBody = response.body() != null ? response.body().string() : "";
                    Log.d("TIMECALL", "Primary API response: " + responseBody);
                } catch (Exception ex) {
                    Log.e("TIMECALL", "Primary API failed: " + ex.getMessage());
                    isFallback = true;

                    RequestBody bodyFallback = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("env_token", "8d59d8d588f84fc0a24291b8c36b6206")
                            .addFormDataPart("image_file", file.getName(),
                                    RequestBody.create(fileBytes, MediaType.parse("image/jpeg")))
                            .build();

                    Request reqFallback = new Request.Builder()
                            .url("http://10.1.16.23:8001/api/x/fr/env/face_search")
                            .post(bodyFallback)
                            .build();

                    response = clientWithTimeout.newCall(reqFallback).execute();
                    responseBody = response.body() != null ? response.body().string() : "";
                    Log.d("TIMECALL", "Fallback API response: " + responseBody);
                }

                if (response != null && response.isSuccessful()) {
                    JSONObject json = new JSONObject(responseBody);
                    if (json.optBoolean("is_fake", false)) {
                        handleRecognitionFail();
                        return;
                    }
                    if (json.optInt("is_recognized", 0) == 1) {
                        String name = json.optString("name");
                        String cardId = json.optString("id_string");
                        double similarityVal = json.optDouble("similarity", 0) * 100.0;
                        if (similarityVal > 55.0) {
                            String similarity = String.format(Locale.getDefault(), "%.2f%%", similarityVal);
                            User activeUser = new User(name, cardId, similarity);

                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                            String now = sdf.format(new Date());
                            String combined = cardId + " " + now;
                            String hashed = CryptoHelper.encrypt(combined);

                            runOnUiThread(() -> {
                                currentUIState = STATE_SUCCESS;
                                showModernUserInfo(activeUser);

                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        String finalUrl = "https://gmo021.cansportsvg.com/hr/hrm/?hash=" +
                                                URLEncoder.encode(hashed, "UTF-8");
                                        Log.d("OpenURL", "Opening browser with URL: " + finalUrl);
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl));
                                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(browserIntent);
                                    } catch (UnsupportedEncodingException e) {
                                        Log.e("URL Encode", "Encoding failed: " + e.getMessage());
                                        showToastOnMainThread("Failed to open browser");
                                    }
                                    hideModernUserInfo();
                                    startCamera();
                                }, 2200);
                            });
                            return;
                        }
                    }
                }
                handleRecognitionFail();
            } catch (Exception e) {
                Log.e("API", "Upload error: " + e.getMessage());
                handleRecognitionFail();
            }
        }).start();
    }


    private void showImageDialog(String imagePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap == null) {
            Toast.makeText(this, "Không thể tải ảnh để hiển thị", Toast.LENGTH_SHORT).show();
            isTakingPhoto = false;
            return;
        }

        // Xoay bitmap nếu cần thiết
        bitmap = rotateBitmapIfRequired(imagePath, bitmap);

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ảnh đã chụp")
                .setView(imageView)
                .setPositiveButton("Đóng", (d, which) -> {
                    d.dismiss();
                    startCamera();
                })
                .setCancelable(false)
                .create();

        dialog.show();
    }
    private Bitmap rotateBitmapIfRequired(String imagePath, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            int rotationDegrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationDegrees = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationDegrees = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationDegrees = 270;
                    break;
                default:
                    return bitmap; // không cần xoay
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);

            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle(); // giải phóng bitmap cũ
            return rotatedBitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return bitmap;
        }
    }
    private boolean isLocationEnabled() {
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
        if (requestCode == 102) {
            // Quyền location cho SSID
            updateSsidUI();
        }
    }
    public boolean isInternetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                return capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        netHandler.removeCallbacksAndMessages(null);
    }

}
