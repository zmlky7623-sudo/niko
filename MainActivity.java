package com.niko.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 10;
    private static final int REQ_CONTACTS = 11;
    private static final int REQ_CALL = 12;
    private static final int REQ_SPEECH = 20;
    private TextView status, transcript;
    private TextToSpeech tts;
    private String pendingNumber = null;
    private String pendingName = null;
    private int bg = Color.rgb(16,16,20);
    private int red = Color.rgb(240,68,68);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("fa", "IR"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    status.setText("صدای فارسی دستگاه در دسترس نیست؛ از تنظیمات تبدیل متن به گفتار، صدای فارسی را نصب کن.");
                } else {
                    speak("سلام! من نیکو هستم. آماده‌ام.");
                }
            }
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 42, 28, 28);
        root.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText("NIKO");
        title.setTextColor(red);
        title.setTextSize(38);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("دستیار صوتی شخصی");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        TextView orb = new TextView(this);
        orb.setText("◉");
        orb.setTextColor(red);
        orb.setTextSize(82);
        orb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, 0, 1);
        root.addView(orb, op);

        status = new TextView(this);
        status.setText("برای صحبت، دکمه میکروفون را بزن.");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        transcript = new TextView(this);
        transcript.setTextColor(Color.LTGRAY);
        transcript.setTextSize(14);
        transcript.setGravity(Gravity.CENTER);
        transcript.setPadding(0, 18, 0, 18);
        root.addView(transcript, new LinearLayout.LayoutParams(-1, -2));

        Button talk = new Button(this);
        talk.setText("🎙  صحبت با نیکو");
        talk.setTextSize(17);
        talk.setTextColor(Color.WHITE);
        talk.setBackgroundTintList(android.content.res.ColorStateList.valueOf(red));
        talk.setOnClickListener(v -> startListening());
        root.addView(talk, new LinearLayout.LayoutParams(-1, 58));

        Button wake = new Button(this);
        wake.setText("🔴  فعال‌کردن شنود پس‌زمینه");
        wake.setTextColor(Color.WHITE);
        wake.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(95,28,32)));
        wake.setOnClickListener(v -> startWakeService());
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, 54);
        wp.topMargin = 12;
        root.addView(wake, wp);

        Button stopWake = new Button(this);
        stopWake.setText("توقف شنود پس‌زمینه");
        stopWake.setOnClickListener(v -> {
            stopService(new Intent(this, WakeWordService.class));
            sayStatus("شنود پس‌زمینه متوقف شد.");
        });
        root.addView(stopWake, new LinearLayout.LayoutParams(-1, 48));

        TextView note = new TextView(this);
        note.setText("نسخه آزمایشی ۰٫۱  •  تماس با تأیید شما");
        note.setTextColor(Color.GRAY);
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 22, 0, 0);
        root.addView(note);
        setContentView(root);
    }

    private void startWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 31);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 30);
            return;
        }
        try {
            Intent service = new Intent(this, WakeWordService.class).setAction(WakeWordService.ACTION_START);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            sayStatus("درخواست فعال‌سازی شنود پس‌زمینه ارسال شد. اعلان NIKO را می‌بینی.");
        } catch (Exception e) {
            sayStatus("اندروید اجازه شروع شنود را نداد. برنامه را باز نگه دار و مجوز میکروفون را بررسی کن.");
        }
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sayStatus("تشخیص گفتار روی این گوشی در دسترس نیست.");
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "با نیکو صحبت کن");
        try { startActivityForResult(i, REQ_SPEECH); }
        catch (Exception e) { sayStatus("برنامه تشخیص گفتار گوشی قابل اجرا نیست."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String heard = results.get(0);
                transcript.setText("تو گفتی: " + heard);
                handleCommand(heard);
            }
        }
    }

    private void handleCommand(String raw) {
        String s = raw.toLowerCase(new Locale("fa", "IR")).replace("‌", " ").trim();
        if (s.contains("ساعت") || s.contains("وقت چنده")) {
            String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            speak("الان ساعت " + t است.");
        } else if (s.contains("تاریخ") || s.contains("امروز چندمه")) {
            String d = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("fa", "IR")).format(new Date());
            speak("امروز " + d + " است.");
        } else if (s.contains("تماس بگیر") || s.contains("زنگ بزن") || s.contains("تماس بگیر")) {
            String name = extractName(raw);
            if (name.isEmpty()) { speak("اسم مخاطب رو متوجه نشدم. دوباره بگو."); return; }
            findContact(name);
        } else if (s.contains("هوا") || s.contains("آب و هوا")) {
            speak("برای اعلام وضعیت زنده هوا باید اینترنت و دسترسی موقعیت مکانی داشته باشم. این قابلیت در مرحله بعد اضافه می‌شود.");
        } else if (s.contains("سلام")) {
            speak("سلام! بگو چه کاری برات انجام بدم؟");
        } else if (s.contains("نیکو")) {
            speak("جانم، گوش می‌کنم.");
        } else {
            speak("این دستور هنوز به نسخه آزمایشی من اضافه نشده. می‌تونی ساعت، تاریخ یا تماس با مخاطب رو امتحان کنی.");
        }
    }

    private String extractName(String raw) {
        String n = raw.replace("نیکو", "").replace("با ", "")
                .replace("تماس بگیر", "").replace("زنگ بزن", "")
                .replace("تماس", "").replace("لطفا", "").replace("لطفاً", "")
                .replace("بگیر", "").trim();
        return n;
    }

    private void findContact(String name) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingName = name;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }
        String number = null;
        Cursor c = null;
        try {
            c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null);
            if (c != null) while (c.moveToNext()) {
                String contact = c.getString(0);
                if (contact != null && contact.toLowerCase().contains(name.toLowerCase())) {
                    number = c.getString(1); break;
                }
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.close(); }

        if (number == null) { speak("مخاطبی با این اسم پیدا نکردم."); return; }
        pendingNumber = number;
        pendingName = name;
        new AlertDialog.Builder(this)
                .setTitle("تأیید تماس")
                .setMessage("با " + name + " تماس بگیرم؟")
                .setNegativeButton("لغو", (d,w) -> speak("باشه، تماس لغو شد."))
                .setPositiveButton("تماس", (d,w) -> makeCall())
                .show();
        speak("برای تماس با " + name + " تأیید روی صفحه رو بزن.");
    }

    private void makeCall() {
        if (pendingNumber == null) return;
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL);
            return;
        }
        try {
            Intent call = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + pendingNumber));
            startActivity(call);
        } catch (Exception e) {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + pendingNumber));
            startActivity(dial);
        }
    }

    private void speak(String text) {
        sayStatus(text);
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "niko_reply");
    }

    private void sayStatus(String text) { status.setText(text); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
        else if (requestCode == REQ_CONTACTS && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && pendingName != null) findContact(pendingName);
        else if (requestCode == REQ_CALL && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) makeCall();
        else if (requestCode == 30 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startWakeService();
        else if (requestCode == 31 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startWakeService();
        else speak("برای انجام این کار به مجوز لازم نیاز دارم.");
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
