package com.niko.assistant;

import android.app.*;
import android.content.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;

import java.text.SimpleDateFormat;
import java.util.*;

public class WakeWordService extends Service implements RecognitionListener {
    public static final String ACTION_START = "com.niko.assistant.START_WAKE";
    public static final String ACTION_STOP = "com.niko.assistant.STOP_WAKE";
    private static final String CHANNEL = "niko_wake_channel";
    private static final int NOTIFICATION_ID = 4101;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean running = false;
    private boolean processing = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("fa","IR"));
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 31 && !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            updateNotification("تشخیص گفتار آفلاین روی این گوشی موجود نیست");
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, makeNotification("نیکو در پس‌زمینه گوش می‌دهد"));
        running = true;
        startRecognizer();
        return START_STICKY;
    }

    private void startRecognizer() {
        if (!running || processing) return;
        try {
            if (recognizer != null) { recognizer.destroy(); recognizer = null; }
            if (Build.VERSION.SDK_INT >= 31) recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            else recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizer.startListening(i);
        } catch (Exception e) {
            updateNotification("شنود فعال نشد؛ بسته گفتار فارسی آفلاین را بررسی کن");
            scheduleRestart();
        }
    }

    @Override public void onResults(Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String heard = (list != null && !list.isEmpty()) ? list.get(0) : "";
        String normalized = heard.replace("‌"," ").trim();
        if (normalized.contains("نیکو") || normalized.toLowerCase(Locale.ROOT).contains("niko")) {
            String command = normalized.replace("نیکو","").replace("Niko","").trim();
            if (command.isEmpty()) {
                updateNotification("نیکو صدات رو شنید؛ فرمانت رو بگو");
                say("جانم، گوش می‌کنم.");
            } else {
                handleOfflineCommand(command);
            }
        }
        scheduleRestart();
    }

    private void handleOfflineCommand(String command) {
        String s = command;
        if (s.contains("ساعت") || s.contains("وقت")) {
            String now = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            say("الان ساعت " + now + " است.");
            updateNotification("فرمان اجرا شد: اعلام ساعت");
        } else if (s.contains("تاریخ") || s.contains("امروز")) {
            String date = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("fa","IR")).format(new Date());
            say("امروز " + date + " است.");
            updateNotification("فرمان اجرا شد: اعلام تاریخ");
        } else if (s.contains("باتری")) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent battery = registerReceiver(null, filter);
            int level = battery == null ? -1 : battery.getIntExtra("level", -1);
            int scale = battery == null ? 100 : battery.getIntExtra("scale", 100);
            int percent = (level < 0) ? -1 : Math.round(level * 100f / scale);
            say(percent < 0 ? "درصد باتری در دسترس نیست." : "شارژ باتری " + percent + " درصد است.");
            updateNotification("فرمان اجرا شد: وضعیت باتری");
        } else {
            say("صدات رو شنیدم. برای این فرمان، برنامه رو باز کن.");
            updateNotification("نیکو صدات رو شنید؛ برای ادامه برنامه را باز کن");
        }
    }

    private void say(String message) {
        if (tts != null) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "niko_wake_reply");
    }

    private void scheduleRestart() {
        processing = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            processing = false;
            if (running) startRecognizer();
        }, 700);
    }

    @Override public void onError(int error) {
        scheduleRestart();
    }
    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) {
            String heard = list.get(0);
            if (heard.contains("نیکو") || heard.toLowerCase(Locale.ROOT).contains("niko")) {
                updateNotification("کلمه بیدارباش تشخیص داده شد");
            }
        }
    }
    @Override public void onEvent(int eventType, Bundle params) { }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "NIKO listening", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان دائمی هنگام فعال بودن شنود نیکو");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification makeNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, WakeWordService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = (Build.VERSION.SDK_INT >= 26)
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NIKO AI")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "توقف شنود", stopPi).build());
        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, makeNotification(text));
    }

    @Override public void onDestroy() {
        running = false;
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        stopForeground(true);
        super.onDestroy();
    }
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
