package com.pipixia;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.pipixia.service.ClaudeWebSocketService;
import com.pipixia.speech.SpeechRecognizerManager;

import io.noties.markwon.Markwon;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;

    private ClaudeWebSocketService service;
    private boolean isServiceBound = false;
    private SpeechRecognizerManager speechRecognizerManager;
    private Markwon markwon;

    private TextView statusText;
    private TextView outputText;
    private ScrollView outputScrollView;
    private EditText textInput;
    private ImageButton sendButton;
    private ImageButton micButton;
    private ImageButton newSessionButton;
    private LinearLayout statusBarLayout;
    private ViewSwitcher statusViewSwitcher;
    private TextView lobsterAnimationText;
    private Handler animationHandler;
    private Runnable animationRunnable;
    private int lobsterPosition = 0;
    private boolean isAnimationRunning = false;

    private boolean isRecording = false;
    private boolean isActivityVisible = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            ClaudeWebSocketService.LocalBinder localBinder = (ClaudeWebSocketService.LocalBinder) binder;
            service = localBinder.getService();
            isServiceBound = true;
            service.setMainActivityVisible(isActivityVisible);
            observeService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bottom_sheet);

        initViews();
        initSpeechRecognizer();
        requestAllPermissions();
        bindToService();

        handleIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActivityVisible = true;
        if (isServiceBound && service != null) {
            service.setMainActivityVisible(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActivityVisible = false;
        if (isServiceBound && service != null) {
            service.setMainActivityVisible(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check permissions again when app resumes
        checkAndRequestManageStorage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();

        if (Intent.ACTION_ASSIST.equals(action) ||
            "android.intent.action.VOICE_ASSIST".equals(action) ||
            Intent.ACTION_SEARCH.equals(action) ||
            "android.speech.action.WEB_SEARCH".equals(action)) {

            if (isRecording) {
                speechRecognizerManager.stopListening();
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                speechRecognizerManager.startListening();
            }
        }
    }

    private void initViews() {
        statusViewSwitcher = findViewById(R.id.statusViewSwitcher);
        statusText = findViewById(R.id.statusText);
        lobsterAnimationText = findViewById(R.id.lobsterAnimationText);
        outputText = findViewById(R.id.outputText);
        outputScrollView = findViewById(R.id.outputScrollView);
        statusBarLayout = findViewById(R.id.statusBarLayout);

        textInput = findViewById(R.id.textInput);
        sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        newSessionButton = findViewById(R.id.newSessionButton);

        // 初始化动画Handler
        animationHandler = new Handler(Looper.getMainLooper());

        // 初始化 Markwon
        markwon = Markwon.create(this);

        // 设置 TextView 的 LinkMovementMethod 使链接可点击
        outputText.setMovementMethod(LinkMovementMethod.getInstance());

        setupClickListeners();
    }

    /**
     * 文件路径点击处理工具类
     */
    private static class FileSpanUtils {

        // 匹配常见文件路径的正则表达式
        // 匹配: /sdcard/, /storage/emulated/0/, /storage/self/primary/, /Android/data/ 等开头的路径
        private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
                "/(?:sdcard|storage/(?:emulated/0|self/primary)|Android/data)[^\\s\\n\\r\\|<>\\?\\*\"\\\\]+"
        );

        /**
         * 检测是否有默认应用可以处理这个 Intent
         */
        private static boolean hasDefaultApp(Context context, Intent intent) {
            try {
                ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                );
                if (resolveInfo == null || resolveInfo.activityInfo == null) {
                    return false;
                }
                String packageName = resolveInfo.activityInfo.packageName;
                // 检查是否是系统的应用选择器（ResolverActivity）
                // 如果是系统选择器，说明没有默认应用
                return !"android".equals(packageName)
                        && !packageName.contains("resolver")
                        && !packageName.contains("Resolver");
            } catch (Exception e) {
                Log.w("FileSpanUtils", "Error checking default app", e);
                return false;
            }
        }

        /**
         * 在文本中查找文件路径并添加可点击的 Span
         */
        static Spannable addFileClickSpans(Context context, CharSequence text) {
            if (text == null) {
                return null;
            }

            String content = text.toString();
            SpannableString spannable;

            if (text instanceof Spanned) {
                spannable = new SpannableString(text);
            } else {
                spannable = new SpannableString(content);
            }

            Matcher matcher = FILE_PATH_PATTERN.matcher(content);
            while (matcher.find()) {
                final String filePath = matcher.group();
                int start = matcher.start();
                int end = matcher.end();

                // 检查是否已经有 URLSpan 或其他 ClickableSpan，避免重复
                ClickableSpan[] existingSpans = spannable.getSpans(start, end, ClickableSpan.class);
                if (existingSpans.length > 0) {
                    continue;
                }

                // 添加自定义的 ClickableSpan
                ClickableSpan span = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openFile(context, filePath);
                    }
                };
                spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            return spannable;
        }

        /**
         * 打开文件
         */
        static void openFile(Context context, String filePath) {
            File file = new File(filePath);

            if (!file.exists()) {
                Toast.makeText(context, "文件不存在: " + filePath, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!file.canRead()) {
                Toast.makeText(context, "无法读取文件: " + filePath, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Uri uri;

                // Android 7.0+ 需要使用 FileProvider
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uri = FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".fileprovider",
                            file
                    );
                } else {
                    uri = Uri.fromFile(file);
                }

                // 获取可能的 MIME 类型列表（按优先级排序）
                List<String> mimeTypes = getMimeTypes(filePath);

                // 尝试用不同的 MIME 类型打开文件
                boolean started = false;
                for (String mimeType : mimeTypes) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, mimeType);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }

                        // 检测是否有默认应用
                        if (hasDefaultApp(context, intent)) {
                            // 有默认应用，直接打开
                            Log.d("FileSpanUtils", "Using default app for MIME: " + mimeType);
                            context.startActivity(intent);
                        } else {
                            // 没有默认应用，显示选择器
                            Log.d("FileSpanUtils", "No default app, showing chooser for MIME: " + mimeType);
                            Intent chooser = Intent.createChooser(intent, "选择打开方式");
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(chooser);
                        }
                        started = true;
                        break;
                    } catch (Exception e) {
                        Log.w("FileSpanUtils", "Failed with MIME type " + mimeType + ": " + e.getMessage());
                        // 继续尝试下一个 MIME 类型
                    }
                }

                if (!started) {
                    // 如果所有特定 MIME 类型都失败了，使用 */* 最后尝试一次
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "*/*");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }

                    // 对于 */* 也检测默认应用
                    if (hasDefaultApp(context, intent)) {
                        context.startActivity(intent);
                    } else {
                        Intent chooser = Intent.createChooser(intent, "选择打开方式");
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(chooser);
                    }
                }
            } catch (Exception e) {
                Log.e("FileSpanUtils", "Failed to open file: " + filePath, e);
                Toast.makeText(context, "打开文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        /**
         * 根据文件扩展名获取可能的 MIME 类型列表（按优先级排序）
         */
        private static List<String> getMimeTypes(String filePath) {
            List<String> result = new ArrayList<>();
            String extension = getFileExtension(filePath);
            if (extension == null) {
                result.add("*/*");
                return result;
            }

            extension = extension.toLowerCase();

            // 先添加具体的 MIME 类型
            switch (extension) {
                // 文本文件
                case "txt":
                    result.add("text/plain");
                    break;
                case "html": case "htm":
                    result.add("text/html");
                    break;
                case "xml":
                    result.add("text/xml");
                    result.add("application/xml");
                    break;
                case "json":
                    result.add("application/json");
                    result.add("text/plain");
                    break;
                case "md":
                    result.add("text/markdown");
                    result.add("text/plain");
                    break;
                case "csv":
                    result.add("text/csv");
                    result.add("text/plain");
                    break;
                case "log":
                    result.add("text/plain");
                    break;
                case "properties":
                case "ini":
                case "cfg":
                case "conf":
                    result.add("text/plain");
                    break;

                // 图片文件
                case "jpg": case "jpeg":
                    result.add("image/jpeg");
                    break;
                case "png":
                    result.add("image/png");
                    break;
                case "gif":
                    result.add("image/gif");
                    break;
                case "bmp":
                    result.add("image/bmp");
                    break;
                case "webp":
                    result.add("image/webp");
                    result.add("image/*");
                    break;

                // 音频文件
                case "mp3":
                    result.add("audio/mpeg");
                    result.add("audio/*");
                    break;
                case "wav":
                    result.add("audio/wav");
                    result.add("audio/*");
                    break;
                case "ogg":
                    result.add("audio/ogg");
                    result.add("audio/*");
                    break;
                case "m4a":
                    result.add("audio/mp4");
                    result.add("audio/*");
                    break;
                case "aac":
                    result.add("audio/aac");
                    result.add("audio/*");
                    break;
                case "flac":
                    result.add("audio/flac");
                    result.add("audio/*");
                    break;

                // 视频文件
                case "mp4":
                    result.add("video/mp4");
                    result.add("video/*");
                    break;
                case "avi":
                    result.add("video/x-msvideo");
                    result.add("video/*");
                    break;
                case "mov":
                    result.add("video/quicktime");
                    result.add("video/*");
                    break;
                case "mkv":
                    result.add("video/x-matroska");
                    result.add("video/*");
                    break;
                case "webm":
                    result.add("video/webm");
                    result.add("video/*");
                    break;

                // 文档文件
                case "pdf":
                    result.add("application/pdf");
                    break;
                case "doc":
                    result.add("application/msword");
                    result.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                    result.add("application/*");
                    break;
                case "docx":
                    result.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                    result.add("application/msword");
                    result.add("application/*");
                    break;
                case "xls":
                    result.add("application/vnd.ms-excel");
                    result.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    result.add("application/*");
                    break;
                case "xlsx":
                    result.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    result.add("application/vnd.ms-excel");
                    result.add("application/*");
                    break;
                case "ppt":
                    result.add("application/vnd.ms-powerpoint");
                    result.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
                    result.add("application/*");
                    break;
                case "pptx":
                    result.add("application/vnd.openxmlformats-officedocument.presentationml.presentation");
                    result.add("application/vnd.ms-powerpoint");
                    result.add("application/*");
                    break;

                // 压缩文件
                case "zip":
                    result.add("application/zip");
                    result.add("application/x-zip");
                    result.add("application/*");
                    break;
                case "rar":
                    result.add("application/x-rar-compressed");
                    result.add("application/rar");
                    result.add("application/*");
                    break;
                case "7z":
                    result.add("application/x-7z-compressed");
                    result.add("application/*");
                    break;
                case "tar":
                    result.add("application/x-tar");
                    result.add("application/*");
                    break;
                case "gz":
                    result.add("application/gzip");
                    result.add("application/x-gzip");
                    result.add("application/*");
                    break;

                // APK 文件
                case "apk":
                    result.add("application/vnd.android.package-archive");
                    break;

                default:
                    // 未知扩展名，尝试根据文件类型猜测
                    break;
            }

            // 添加通用的 MIME 类型作为回退
            if (!result.isEmpty()) {
                String firstType = result.get(0);
                if (firstType.startsWith("text/")) {
                    result.add("text/*");
                } else if (firstType.startsWith("image/")) {
                    result.add("image/*");
                } else if (firstType.startsWith("audio/")) {
                    result.add("audio/*");
                } else if (firstType.startsWith("video/")) {
                    result.add("video/*");
                } else if (firstType.startsWith("application/")) {
                    result.add("application/*");
                }
            }

            // 最后添加 */* 作为终极回退
            result.add("*/*");

            return result;
        }

        /**
         * 获取文件扩展名
         */
        private static String getFileExtension(String filePath) {
            if (filePath == null) {
                return null;
            }
            int lastDot = filePath.lastIndexOf('.');
            int lastSlash = filePath.lastIndexOf('/');
            if (lastDot > lastSlash && lastDot < filePath.length() - 1) {
                return filePath.substring(lastDot + 1);
            }
            return null;
        }
    }

    private void setupClickListeners() {
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecording();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextInput();
            }
        });

        newSessionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isServiceBound) {
                    service.createSession();
                    service.clearOutput();
                }
            }
        });
    }

    private void initSpeechRecognizer() {
        speechRecognizerManager = new SpeechRecognizerManager(this);
        speechRecognizerManager.setListener(new SpeechRecognizerManager.Listener() {
            @Override
            public void onReadyForSpeech() {
                updateMicState(true);
                setStatusText("正在聆听...");
                if (isServiceBound) {
                    service.setListeningStatus();
                }
            }

            @Override
            public void onPartialResults(String text) {
                // 可以在输入框显示临时结果
            }

            @Override
            public void onResults(String text) {
                updateMicState(false);
                if (!text.isEmpty() && isServiceBound) {
                    service.sendInput(text);
                } else if (isServiceBound) {
                    service.setIdleStatus();
                }
            }

            @Override
            public void onError(int errorCode, String errorMessage) {
                updateMicState(false);
                setStatusText("语音识别错误: " + errorMessage);
                if (isServiceBound) {
                    service.setIdleStatus();
                }
            }
        });
    }

    private void requestAllPermissions() {
        Log.d(TAG, "Requesting all permissions...");

        // First check MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not granted, requesting...");
                checkAndRequestManageStorage();
            } else {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE already granted");
            }
        }

        // Request other permissions
        requestDangerousPermissions();
    }

    private void checkAndRequestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.d(TAG, "Opening MANAGE_EXTERNAL_STORAGE settings...");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open manage storage settings", e);
                    // Fallback to general settings
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                    } catch (Exception e2) {
                        Log.e(TAG, "Failed to open general settings too", e2);
                    }
                }
            }
        }
    }

    private void requestDangerousPermissions() {
        List<String> permissions = new ArrayList<>();

        // Always request RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        // POST_NOTIFICATIONS for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // READ_EXTERNAL_STORAGE for Android < 13
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // READ_MEDIA_AUDIO for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        }

        if (!permissions.isEmpty()) {
            Log.d(TAG, "Requesting dangerous permissions: " + permissions);
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "All dangerous permissions already granted");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE granted!");
                } else {
                    Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not granted");
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                Log.d(TAG, "Permission: " + permissions[i] + " = " +
                        (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            }
        }
    }

    private void bindToService() {
        Intent intent = new Intent(this, ClaudeWebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void observeService() {
        if (service == null) return;

        // 使用 Markwon 渲染初始内容
        markwon.setMarkdown(outputText, service.getOutputBuffer());

        // 在 Markdown 渲染后，再添加文件路径的可点击 Span
        CharSequence text = outputText.getText();
        Spannable spannableWithFiles = FileSpanUtils.addFileClickSpans(MainActivity.this, text);
        if (spannableWithFiles != null) {
            outputText.setText(spannableWithFiles);
        }

        scrollToBottom();

        service.getStatusLiveData().observe(this, new Observer<ClaudeWebSocketService.Status>() {
            @Override
            public void onChanged(ClaudeWebSocketService.Status status) {
                // 只有当没有过程信息时才更新状态文本
                Boolean statusLineVisible = service.getStatusLineVisibleLiveData().getValue();
                if (statusLineVisible == null || !statusLineVisible) {
                    updateStatusText(status);
                }
            }
        });

        service.getOutputLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String output) {
                // 使用 Markwon 渲染 Markdown 内容
                markwon.setMarkdown(outputText, output);

                // 在 Markdown 渲染后，再添加文件路径的可点击 Span
                CharSequence text = outputText.getText();
                Spannable spannableWithFiles = FileSpanUtils.addFileClickSpans(MainActivity.this, text);
                if (spannableWithFiles != null) {
                    outputText.setText(spannableWithFiles);
                }

                scrollToBottom();
                // 如果Activity可见，取消任何未读通知
                if (isActivityVisible && isServiceBound && service != null) {
                    service.cancelMessageNotification();
                }
            }
        });

        service.getStatusLineVisibleLiveData().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean visible) {
                if (visible) {
                    // 显示龙虾动画
                    startLobsterAnimation();
                } else {
                    // 停止动画，恢复显示当前状态
                    stopLobsterAnimation();
                    ClaudeWebSocketService.Status status = service.getCurrentStatus();
                    if (status != null) {
                        updateStatusText(status);
                    }
                }
            }
        });
    }

    private void startLobsterAnimation() {
        if (isAnimationRunning) return;

        isAnimationRunning = true;
        lobsterPosition = 0;

        // 切换到动画视图
        if (statusViewSwitcher.getDisplayedChild() != 1) {
            statusViewSwitcher.showNext();
        }

        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAnimationRunning) return;

                // 构建龙虾位置字符串 - 使用空格移动龙虾
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lobsterPosition; i++) {
                    sb.append(" ");
                }
                sb.append("🦞");

                lobsterAnimationText.setText(sb.toString());

                // 增加位置 - 调整速度
                lobsterPosition++;
                if (lobsterPosition > 30) {  // 假设30个空格到右边
                    lobsterPosition = 0;
                }

                // 继续动画
                animationHandler.postDelayed(this, 100);  // 100ms更新一次
            }
        };

        animationHandler.post(animationRunnable);
    }

    private void stopLobsterAnimation() {
        isAnimationRunning = false;
        if (animationHandler != null && animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
        }

        // 切换回状态文本视图
        if (statusViewSwitcher.getDisplayedChild() != 0) {
            statusViewSwitcher.showPrevious();
        }
    }

    private void updateStatusText(ClaudeWebSocketService.Status status) {
        switch (status) {
            case IDLE:
                setStatusText("准备就绪");
                break;
            case CONNECTING:
                setStatusText("正在连接...");
                break;
            case CONNECTED:
                setStatusText("已连接");
                break;
            case LISTENING:
                setStatusText("正在聆听...");
                break;
            case SENDING:
                setStatusText("正在发送...");
                break;
            case WAITING:
                setStatusText("正在等待回复...");
                break;
            case RECEIVING:
                setStatusText("正在接收...");
                break;
            case DISCONNECTED:
                setStatusText("已断开连接");
                break;
            case ERROR:
                setStatusText("出错了");
                break;
        }
    }

    private void setStatusText(String text) {
        statusText.setText(text);
    }

    private void scrollToBottom() {
        outputScrollView.post(new Runnable() {
            @Override
            public void run() {
                outputScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void toggleRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestAllPermissions();
            return;
        }

        if (isRecording) {
            speechRecognizerManager.stopListening();
        } else {
            speechRecognizerManager.startListening();
        }
    }

    private void updateMicState(boolean recording) {
        isRecording = recording;
        if (recording) {
            micButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light));
        } else {
            micButton.setColorFilter(null);
        }
    }

    private void sendTextInput() {
        String text = textInput.getText().toString().trim();
        if (!text.isEmpty() && isServiceBound) {
            service.sendInput(text);
        }
        textInput.setText("");
        hideKeyboard();
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(textInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLobsterAnimation();  // 停止动画
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (speechRecognizerManager != null) {
            speechRecognizerManager.destroy();
        }
    }
}
