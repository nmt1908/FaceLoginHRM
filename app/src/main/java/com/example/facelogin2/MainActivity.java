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
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
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
import com.google.common.util.concurrent.ListenableFuture;

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
    TextView alertTextView, labelName, nameTextView, labelCardId, cardIDTextView, labelSimilarity, similarityTextView;
    ProgressBar loadingSpinner;
    private LinearLayout loadingContainer,userInfoPanel;

    private Handler idleHandler = new Handler(Looper.getMainLooper());


    private long IDLE_DELAY_MS;
    private boolean isCountingDownToIdle = false;
    private boolean isPreviewVisible = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isInternetAvailable()) {
            Toast.makeText(this, "No Internet Connection", Toast.LENGTH_LONG).show();

        }
        IDLE_DELAY_MS = getSharedPreferences("settings", MODE_PRIVATE).getInt("time_waiting", 1000);
        Log.e("IDLE_DELAY_MS", String.valueOf(IDLE_DELAY_MS));
        loadingContainer = findViewById(R.id.loadingContainer);
        userInfoPanel = findViewById(R.id.userInfoPanel);
        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        alertTextView = findViewById(R.id.labelAlert);
        labelName = findViewById(R.id.labelUserName);
        nameTextView = findViewById(R.id.userName);
        labelCardId = findViewById(R.id.labelUserCardId);
        cardIDTextView = findViewById(R.id.userCardId);
        labelSimilarity = findViewById(R.id.labelUserSimilarity);
        similarityTextView = findViewById(R.id.userSimilarity);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        tvSsidValue = findViewById(R.id.tvSsidValue);
        endpointStatusContainer = findViewById(R.id.endpointStatusContainer);
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
    private void uploadImageToApi(File file) {
        runOnUiThread(() -> {
            loadingContainer.setVisibility(View.VISIBLE);
            loadingSpinner.setVisibility(View.VISIBLE);
        });

        new Thread(() -> {
            try {
                byte[] fileBytes;
                try (InputStream is = new FileInputStream(file)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    fileBytes = buffer.toByteArray();
                }

                String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date());

                // API chính: port 5001
                RequestBody requestBodyPrimary = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image_file", file.getName(),
                                RequestBody.create(fileBytes, MediaType.parse("image/jpeg")))
                        .build();

                Request requestPrimary = new Request.Builder()
                        .url("http://10.13.34.166:5001/recognize-anti-spoofing")
                        .addHeader("X-API-Key", "vg_login_app")
                        .addHeader("X-Time", currentTime)
                        .post(requestBodyPrimary)
                        .build();

                OkHttpClient clientWithTimeout = httpClient.newBuilder()
                        .callTimeout(5, TimeUnit.SECONDS)
                        .build();

                Response response;
                String responseBody;
                String apiUsed;

                try {
                    Log.d("API_CALL", "Calling PRIMARY API: port 5001");
                    response = clientWithTimeout.newCall(requestPrimary).execute();
                    responseBody = response.body().string();
                    Log.e("Response-Primary", responseBody);
                    apiUsed = "Primary";
                } catch (Exception ex) {
                    Log.e("PrimaryAPI", "Primary API failed, fallback to secondary: " + ex.getMessage());

                    // Fallback: API phụ port 8001
                    RequestBody requestBodyFallback = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("env_token", "8d59d8d588f84fc0a24291b8c36b6206")
                            .addFormDataPart("image_file", file.getName(),
                                    RequestBody.create(file, MediaType.parse("image/jpeg")))
                            .build();

                    Request requestFallback = new Request.Builder()
                            .url("http://10.1.16.23:8001/api/x/fr/env/face_search")
                            .post(requestBodyFallback)
                            .build();

                    Log.d("API_CALL", "Calling FALLBACK API: port 8001");
                    response = httpClient.newCall(requestFallback).execute();
                    responseBody = response.body().string();
                    Log.e("Response-Fallback", responseBody);
                    apiUsed = "Fallback";
                }

                runOnUiThread(() -> {
                    loadingSpinner.setVisibility(View.GONE);
                    loadingContainer.setVisibility(View.GONE);
                });

                if (!response.isSuccessful()) {
                    if (response.code() == 400) {
                        handleRecognitionFail();
                    } else {
                        showToastOnMainThread("API error: " + response.code());
                    }
                    return;
                }

                JSONObject jsonObject = new JSONObject(responseBody);

                if (jsonObject.optBoolean("is_fake", false)) {
                    Log.w("AntiSpoofing", "Detected fake face (from " + apiUsed + " API)");
                    handleRecognitionFail();
                    return;
                }

                if (jsonObject.optInt("is_recognized", 0) == 1) {
                    String name = jsonObject.optString("name");
                    String cardId = jsonObject.optString("id_string");
                    double similarityVal = jsonObject.optDouble("similarity", 0) * 100;

                    if (similarityVal <= 55) {
                        Log.w("Recognition", "Low similarity (" + similarityVal + "%) from " + apiUsed + " API");
                        handleRecognitionFail();
                        return;
                    }

                    String similarity = String.format("%.2f%%", similarityVal);
                    User activeUser = new User(name, cardId, similarity);

                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                    String now = sdf.format(new Date());
                    String combined = cardId + " " + now;
                    String hashed = CryptoHelper.encrypt(combined);

                    runOnUiThread(() -> {
                        alertTextView.setText("Facial recognition successful");

                        labelName.setText("Name:");
                        nameTextView.setText(activeUser.getName());

                        labelCardId.setText("Card ID:");
                        cardIDTextView.setText(activeUser.getCardId());

                        labelSimilarity.setText("Similarity:");
                        similarityTextView.setText(activeUser.getSimilarity());

                        userInfoPanel.setVisibility(View.VISIBLE);
                        userInfoPanel.postDelayed(() -> userInfoPanel.setVisibility(View.GONE), 3000);

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
                    });

                } else {
                    Log.w("Recognition", "Face not recognized (" + apiUsed + " API)");
                    runOnUiThread(() -> alertTextView.setText("Face not recognized"));
                    handleRecognitionFail();
                }

            } catch (Exception e) {
                e.printStackTrace();
                showToastOnMainThread("Error: " + e.getMessage());
                Log.e("FaceAPI_Error", e.getMessage(), e);
            } finally {
                runOnUiThread(() -> {
                    startCamera();
                    isTakingPhoto = false;
                });
            }
        }).start();
    }


    private void handleRecognitionFail() {
        runOnUiThread(() -> {
            // Hiện panel chứa thông báo
            userInfoPanel.setVisibility(View.VISIBLE);

            // Hiện alertTextView và set text báo lỗi
            alertTextView.setVisibility(View.VISIBLE);
            alertTextView.setText("Facial recognition failed");

            // Ẩn các thông tin user
            labelName.setVisibility(View.GONE);
            nameTextView.setVisibility(View.GONE);
            labelCardId.setVisibility(View.GONE);
            cardIDTextView.setVisibility(View.GONE);
            labelSimilarity.setVisibility(View.GONE);
            similarityTextView.setVisibility(View.GONE);

            // Ẩn loading spinner nếu còn hiện
            loadingContainer.setVisibility(View.GONE);

            // Tự ẩn sau 2 giây
            alertTextView.postDelayed(() -> alertTextView.setText(""), 2000);
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

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new FaceAnalyzer(this,graphicOverlay, this));

                // Set rotation đúng chiều của màn hình hiển thị
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
            cameraProvider.unbindAll();
        }
        isTakingPhoto = false;
        faceStraightStartTime = 0;
    }

    @Override
    public void onFaceLookingStraight() {
        runOnUiThread(() -> {
            if (faceStraightStartTime == 0) {
                faceStraightStartTime = System.currentTimeMillis();
            } else {
                long elapsed = System.currentTimeMillis() - faceStraightStartTime;
                if (elapsed >= 0 && !isTakingPhoto) {
                    isTakingPhoto = true;
                    takePhoto();
                }
            }
        });
    }

    @Override
    public void onFaceNotLookingStraight() {
        runOnUiThread(() -> {
            faceStraightStartTime = 0; // reset nếu mặt không nhìn thẳng hoặc không đủ gần
        });
    }

    private void takePhoto() {
        if (imageCapture == null) {
            isTakingPhoto = false;
            return;
        }

        String filename = "IMG_" + System.currentTimeMillis() + ".jpg";
        File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            isTakingPhoto = false;
            return;
        }
        File file = new File(dir, filename);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {

//                         Gửi ảnh lên API, không hiển thị dialog
                        uploadImageToApi(file);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        exception.printStackTrace();
                        isTakingPhoto = false;
                    }
                });
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
