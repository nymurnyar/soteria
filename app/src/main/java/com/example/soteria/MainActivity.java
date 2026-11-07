package com.example.soteria;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple screen for testing continuous live speech-to-text.
 * Uses the on-device SpeechRecognizer (API 31+) so audio never leaves the phone.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Transcriber";
    /** Small delay before restarting, avoids ERROR_RECOGNIZER_BUSY on many devices. */
    private static final long RESTART_DELAY_MS = 250;
    /** How often / how long to poll for a background model download (3 s x 100 = 5 min). */
    private static final long MODEL_POLL_INTERVAL_MS = 3000;
    private static final int MODEL_POLL_ATTEMPTS = 100;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder finalTranscript = new StringBuilder();

    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    /** True when using the dedicated on-device recognizer (API 31+). */
    private boolean onDevice = false;
    /** Ensures we only try to download the language model once per Start tap. */
    private boolean downloadAttempted = false;
    /** True while the user wants us listening (between Start and Stop taps). */
    private boolean listening = false;

    private Button toggleButton;
    private TextView statusText;
    private TextView transcriptText;
    private ScrollView transcriptScroll;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListening();
                } else {
                    setStatus("Error (microphone permission denied)");
                    Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = Math.round(16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        toggleButton = findViewById(R.id.toggleButton);
        statusText = findViewById(R.id.statusText);
        transcriptText = findViewById(R.id.transcriptText);
        transcriptScroll = findViewById(R.id.transcriptScroll);
        Button clearButton = findViewById(R.id.clearButton);

        toggleButton.setOnClickListener(v -> {
            if (listening) {
                stopListening();
            } else {
                checkPermissionAndStart();
            }
        });
        clearButton.setOnClickListener(v -> {
            finalTranscript.setLength(0);
            transcriptText.setText("");
        });

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        // Fallback hint for devices without a dedicated on-device recognizer.
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
    }

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startListening() {
        if (recognizer == null && !createRecognizer()) {
            return;
        }
        listening = true;
        downloadAttempted = false;
        toggleButton.setText("Stop");

        if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setStatus("Checking on-device model…");
            checkModelThenListen();
        } else {
            beginSession();
        }
    }

    /** Creates the on-device recognizer when available, otherwise the default one. */
    private boolean createRecognizer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            onDevice = true;
            Log.i(TAG, "Using on-device SpeechRecognizer");
        } else if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            onDevice = false;
            Log.w(TAG, "On-device recognition unavailable; using default recognizer (prefer offline)");
        } else {
            Log.e(TAG, "Speech recognition is not available on this device");
            setStatus("Error (recognition not available)");
            return false;
        }
        recognizer.setRecognitionListener(listener);
        return true;
    }

    private void beginSession() {
        if (!listening || recognizer == null) return;
        setStatus(onDevice ? "Listening (on-device)" : "Listening");
        recognizer.startListening(recognizerIntent);
    }

    /** API 33+: make sure the language model is installed before listening; download if needed. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void checkModelThenListen() {
        String lang = Locale.getDefault().toLanguageTag();
        recognizer.checkRecognitionSupport(recognizerIntent, ContextCompat.getMainExecutor(this),
                new RecognitionSupportCallback() {
                    @Override
                    public void onSupportResult(@NonNull RecognitionSupport support) {
                        Log.i(TAG, "On-device support: installed=" + support.getInstalledOnDeviceLanguages()
                                + " pending=" + support.getPendingOnDeviceLanguages()
                                + " supported=" + support.getSupportedOnDeviceLanguages());
                        if (!listening) return;
                        if (containsLang(support.getInstalledOnDeviceLanguages(), lang)) {
                            beginSession();
                        } else if (containsLang(support.getSupportedOnDeviceLanguages(), lang)
                                || containsLang(support.getPendingOnDeviceLanguages(), lang)) {
                            downloadModel();
                        } else {
                            Log.e(TAG, "Language " + lang + " not supported on-device");
                            stopListening();
                            setStatus("Error (" + lang + " not supported on-device)");
                        }
                    }

                    @Override
                    public void onError(int error) {
                        // Some services don't implement the check; just try to listen.
                        Log.w(TAG, "checkRecognitionSupport failed: " + errorName(error));
                        beginSession();
                    }
                });
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void downloadModel() {
        downloadAttempted = true;
        String lang = Locale.getDefault().toLanguageTag();
        Log.i(TAG, "Downloading on-device model for " + lang);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            downloadModelWithProgress();
        } else {
            // API 33: no progress callback; the system shows its own download UI.
            recognizer.triggerModelDownload(recognizerIntent);
            stopListening();
            setStatus("Downloading " + lang + " model… tap Start when done");
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void downloadModelWithProgress() {
        setStatus("Downloading model…");
        recognizer.triggerModelDownload(recognizerIntent, ContextCompat.getMainExecutor(this),
                new ModelDownloadListener() {
                    @Override
                    public void onProgress(int completedPercent) {
                        if (listening) setStatus("Downloading model… " + completedPercent + "%");
                    }

                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "Model download complete");
                        beginSession();
                    }

                    @Override
                    public void onScheduled() {
                        // The service stops reporting progress but keeps downloading in the
                        // background; poll until the model is installed, then start listening.
                        Log.i(TAG, "Model download continuing in background");
                        if (listening) setStatus("Downloading model in background…");
                        waitForModel(MODEL_POLL_ATTEMPTS);
                    }

                    @Override
                    public void onError(int error) {
                        Log.e(TAG, "Model download failed: " + errorName(error));
                        stopListening();
                        setStatus("Error (model download: " + errorName(error) + ")");
                    }
                });
    }

    /** Polls checkRecognitionSupport until the language model shows up as installed. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void waitForModel(int attemptsLeft) {
        if (!listening) return;
        if (attemptsLeft <= 0) {
            Log.e(TAG, "Timed out waiting for on-device model");
            stopListening();
            setStatus("Error (model download timed out)");
            return;
        }
        String lang = Locale.getDefault().toLanguageTag();
        handler.postDelayed(() -> {
            if (!listening || recognizer == null) return;
            recognizer.checkRecognitionSupport(recognizerIntent,
                    ContextCompat.getMainExecutor(this), new RecognitionSupportCallback() {
                        @Override
                        public void onSupportResult(@NonNull RecognitionSupport support) {
                            if (containsLang(support.getInstalledOnDeviceLanguages(), lang)) {
                                Log.i(TAG, "On-device model installed");
                                beginSession();
                            } else {
                                waitForModel(attemptsLeft - 1);
                            }
                        }

                        @Override
                        public void onError(int error) {
                            waitForModel(attemptsLeft - 1);
                        }
                    });
        }, MODEL_POLL_INTERVAL_MS);
    }

    private static boolean containsLang(List<String> langs, String lang) {
        if (langs == null) return false;
        String base = lang.split("-")[0];
        for (String l : langs) {
            if (l.equalsIgnoreCase(lang)) return true;
        }
        // Fall back to matching just the language (e.g. "en" for "en-GB").
        for (String l : langs) {
            if (l.split("-")[0].equalsIgnoreCase(base)) return true;
        }
        return false;
    }

    private void stopListening() {
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.cancel();
        }
        toggleButton.setText("Start Listening");
        setStatus("Stopped");
        renderTranscript(null); // drop any dangling partial text
    }

    /** Restarts the recognizer after a session ends, as long as the user hasn't tapped Stop. */
    private void scheduleRestart() {
        if (!listening) return;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (listening && recognizer != null) {
                recognizer.startListening(recognizerIntent);
            }
        }, RESTART_DELAY_MS);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            if (listening) setStatus(onDevice ? "Listening (on-device)" : "Listening");
        }

        @Override
        public void onBeginningOfSpeech() { }

        @Override
        public void onRmsChanged(float rmsdB) { }

        @Override
        public void onBufferReceived(byte[] buffer) { }

        @Override
        public void onEndOfSpeech() { }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (!listening) return;
            renderTranscript(firstResult(partialResults));
        }

        @Override
        public void onResults(Bundle results) {
            String text = firstResult(results);
            if (text != null && !text.isEmpty()) {
                if (finalTranscript.length() > 0) finalTranscript.append(' ');
                finalTranscript.append(text);
            }
            renderTranscript(null);
            scheduleRestart();
        }

        @Override
        public void onError(int error) {
            Log.e(TAG, "SpeechRecognizer error: " + errorName(error) + " (" + error + ")");
            if (!listening) return; // e.g. ERROR_CLIENT after we cancelled on Stop

            if (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                renderTranscript(null);
                scheduleRestart();
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                recognizer.cancel();
                scheduleRestart();
            } else if (onDevice && !downloadAttempted
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)) {
                // Model missing even though the support check passed (or was skipped).
                downloadModel();
            } else {
                stopListening();
                setStatus("Error (" + errorName(error) + ")");
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) { }
    };

    /** Shows committed text plus the current in-progress partial (if any). */
    private void renderTranscript(String partial) {
        StringBuilder sb = new StringBuilder(finalTranscript);
        if (partial != null && !partial.isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(partial);
        }
        transcriptText.setText(sb);
        transcriptScroll.post(() -> transcriptScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private static String firstResult(Bundle bundle) {
        if (bundle == null) return null;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return (matches == null || matches.isEmpty()) ? null : matches.get(0);
    }

    private void setStatus(String status) {
        statusText.setText("Status: " + status);
    }

    private static String errorName(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "ERROR_NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NETWORK: return "ERROR_NETWORK";
            case SpeechRecognizer.ERROR_AUDIO: return "ERROR_AUDIO";
            case SpeechRecognizer.ERROR_SERVER: return "ERROR_SERVER";
            case SpeechRecognizer.ERROR_CLIENT: return "ERROR_CLIENT";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "ERROR_SPEECH_TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH: return "ERROR_NO_MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "ERROR_RECOGNIZER_BUSY";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "ERROR_INSUFFICIENT_PERMISSIONS";
            // API 31+ codes, as literals so they compile/run on any minSdk.
            case 10: return "ERROR_TOO_MANY_REQUESTS";
            case 11: return "ERROR_SERVER_DISCONNECTED";
            case 12: return "ERROR_LANGUAGE_NOT_SUPPORTED";
            case 13: return "ERROR_LANGUAGE_UNAVAILABLE";
            case 14: return "ERROR_CANNOT_CHECK_SUPPORT";
            case 15: return "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS";
            default: return "UNKNOWN_ERROR";
        }
    }

    @Override
    protected void onStop() {
        // Android revokes mic access in the background (surfaces as ERROR_CLIENT); stop cleanly.
        if (listening) stopListening();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }
}
