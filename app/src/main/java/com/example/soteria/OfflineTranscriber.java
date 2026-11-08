package com.example.soteria;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline, on-phone call transcription (no Twilio).
 *
 * <p>Android 13+: we record the microphone ourselves and hand the audio to the on-device speech
 * recognizer through a pipe ({@link RecognizerIntent#EXTRA_AUDIO_SOURCE}). This matters because the
 * recognizer runs in another (Google) app, which Android would give silence during a call; Soteria's
 * own recording is allowed because its {@link SoteriaAccessibilityService} is switched on.
 * Older Android: the recognizer records by itself (may be silent during calls on some phones).
 *
 * <p>The recognizer stops after each sentence, so we restart it in a loop. Main thread only.
 */
final class OfflineTranscriber {

    interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        /** Progress / warnings, e.g. "Downloading speech model…". */
        void onStatus(String status);
        /** Transcription can't continue. */
        void onFatal(String reason);
    }

    private static final String TAG = "SoteriaOffline";
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHUNK_BYTES = 1_280;           // 40 ms of 16-bit mono: < PIPE_BUF, so writes are atomic
    private static final long RESTART_DELAY_MS = 150;
    private static final long MAX_SEGMENT_MS = 20_000;      // force a final result at least this often
    private static final long PARTIAL_SETTLE_MS = 2_000;    // partial unchanged this long -> finalize it
    private static final long SILENCED_AFTER_MS = 6_000;    // only digital zeros this long -> OS is blocking us
    private static final long MODEL_POLL_MS = 3_000;
    private static final int MODEL_POLL_ATTEMPTS = 60;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private SpeechRecognizer recognizer;
    private Intent baseIntent;
    private boolean running;
    private boolean onDevice;
    private boolean feedOwnAudio;
    private boolean sessionActive;
    private int consecutiveErrors;
    private boolean gotAnyText;
    private String lastPartial = "";
    /** We asked the recognizer to wrap up; with piped audio it answers with ERROR_CLIENT. */
    private boolean finalizing;
    /** We told the listener we're live (first onReadyForSpeech after start). */
    private boolean announcedReady;

    // Our own microphone capture (Android 13+).
    @Nullable private AudioRecord record;
    @Nullable private Thread captureThread;
    private volatile boolean capturing;
    @Nullable private ParcelFileDescriptor pipeRead;
    @Nullable private ParcelFileDescriptor pipeWrite;
    @Nullable private volatile FileDescriptor writeFd;
    /** Guards writeFd/pipeWrite so the mic thread never writes to a closed (reused) descriptor. */
    private final Object pipeLock = new Object();
    private volatile long firstAudioMs;
    private volatile long lastNonZeroMs;
    private boolean warnedSilenced;

    private final Runnable forceFinal = () -> {
        if (sessionActive && recognizer != null) {
            Log.d(TAG, "Finalizing segment");
            finalizing = true;
            recognizer.stopListening();
        }
    };

    private final Runnable silenceCheck = new Runnable() {
        @Override
        public void run() {
            if (!running || !capturing) return;
            long now = System.currentTimeMillis();
            if (!warnedSilenced && firstAudioMs > 0 && now - firstAudioMs > SILENCED_AFTER_MS
                    && now - lastNonZeroMs > SILENCED_AFTER_MS) {
                warnedSilenced = true;
                Log.w(TAG, "Microphone delivers only silence");
                listener.onStatus(context.getString(R.string.offline_silenced));
            }
            main.postDelayed(this, 2_000);
        }
    };

    OfflineTranscriber(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    boolean isRunning() {
        return running;
    }

    static boolean hasMicPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (running) return;
        if (!hasMicPermission(context)) {
            listener.onFatal(context.getString(R.string.offline_mic_required));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            onDevice = true;
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            onDevice = false;
        } else {
            listener.onFatal(context.getString(R.string.offline_unavailable));
            return;
        }
        recognizer.setRecognitionListener(recognitionListener);

        baseIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName())
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());

        running = true;
        announcedReady = false;
        consecutiveErrors = 0;
        gotAnyText = false;
        warnedSilenced = false;
        feedOwnAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && startCapture();
        Log.i(TAG, "Starting: onDevice=" + onDevice + " feedOwnAudio=" + feedOwnAudio);

        if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkModelThenBegin(MODEL_POLL_ATTEMPTS, false);
        } else {
            beginSession();
        }
    }

    void stop() {
        if (!running && recognizer == null) return;
        Log.i(TAG, "Stopping");
        running = false;
        sessionActive = false;
        main.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (RuntimeException ignored) {
            }
            recognizer = null;
        }
        stopCapture();
        closePipe();
    }

    // ---- Recognizer sessions ---------------------------------------------------------------

    private void beginSession() {
        if (!running || recognizer == null) return;
        Intent intent = new Intent(baseIntent);
        if (feedOwnAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!openPipe(intent)) {
                Log.w(TAG, "Pipe failed; letting the recognizer use the mic itself");
                feedOwnAudio = false;
                stopCapture();
            }
        }
        lastPartial = "";
        finalizing = false;
        sessionActive = true;
        try {
            recognizer.startListening(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "startListening failed", e);
            scheduleRestart(1_000);
            return;
        }
        main.removeCallbacks(forceFinal);
        main.postDelayed(forceFinal, MAX_SEGMENT_MS);
    }

    private void endSessionAndRestart(long delayMs) {
        sessionActive = false;
        main.removeCallbacks(forceFinal);
        closePipe();
        scheduleRestart(delayMs);
    }

    private void scheduleRestart(long delayMs) {
        if (!running) return;
        main.postDelayed(this::beginSession, delayMs);
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {
            if (running && !announcedReady) {
                announcedReady = true; // model loaded, mic flowing: "Starting…" -> live
                listener.onStatus(context.getString(R.string.protection_offline_active));
            }
        }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (!running) return;
            String text = firstResult(partialResults);
            if (text.isEmpty() || text.equals(lastPartial)) return;
            lastPartial = text;
            listener.onPartial(text);
            // Some recognizers keep going while audio flows: finalize once the text settles.
            main.removeCallbacks(forceFinal);
            main.postDelayed(forceFinal, PARTIAL_SETTLE_MS);
        }

        @Override
        public void onResults(Bundle results) {
            if (!running) return;
            String text = firstResult(results);
            if (text.isEmpty()) text = lastPartial; // stopListening() can return an empty final
            consecutiveErrors = 0;
            if (!text.isEmpty()) {
                gotAnyText = true;
                listener.onFinal(text);
            } else {
                listener.onPartial("");
            }
            endSessionAndRestart(RESTART_DELAY_MS);
        }

        @Override
        public void onError(int error) {
            if (!running) return;
            Log.d(TAG, "Recognizer error " + error);
            boolean expected = finalizing; // our own stopListening(), not a real failure
            if (!lastPartial.isEmpty()) {
                // Keep what we heard before the error.
                gotAnyText = true;
                listener.onFinal(lastPartial);
                lastPartial = "";
            }
            if (expected && error == SpeechRecognizer.ERROR_CLIENT) {
                consecutiveErrors = 0;
                endSessionAndRestart(RESTART_DELAY_MS);
                return;
            }
            switch (error) {
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    listener.onFatal(context.getString(R.string.offline_mic_required));
                    stop();
                    return;
                case SpeechRecognizer.ERROR_NO_MATCH:
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    consecutiveErrors = 0;
                    endSessionAndRestart(RESTART_DELAY_MS);
                    return;
                case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
                case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                    if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        sessionActive = false;
                        closePipe();
                        checkModelThenBegin(MODEL_POLL_ATTEMPTS, false);
                    } else {
                        listener.onFatal(context.getString(R.string.offline_unavailable));
                        stop();
                    }
                    return;
                default:
                    consecutiveErrors++;
                    // Our audio pipe isn't accepted by this recognizer: let it record by itself.
                    if (feedOwnAudio && !gotAnyText && consecutiveErrors >= 3) {
                        Log.w(TAG, "Recognizer rejects piped audio; falling back to its own mic");
                        feedOwnAudio = false;
                        stopCapture();
                    }
                    endSessionAndRestart(Math.min(2_000, 250L * consecutiveErrors));
            }
        }
    };

    private static String firstResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list == null || list.isEmpty() || list.get(0) == null ? "" : list.get(0).trim();
    }

    // ---- On-device model -------------------------------------------------------------------

    /** Makes sure the on-device language model is installed (downloads it if needed), then listens. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void checkModelThenBegin(int attemptsLeft, boolean downloading) {
        if (!running || recognizer == null) return;
        String lang = Locale.getDefault().toLanguageTag();
        recognizer.checkRecognitionSupport(baseIntent, ContextCompat.getMainExecutor(context),
                new RecognitionSupportCallback() {
                    @Override
                    public void onSupportResult(@NonNull RecognitionSupport support) {
                        if (!running || recognizer == null) return;
                        if (containsLang(support.getInstalledOnDeviceLanguages(), lang)) {
                            beginSession();
                        } else if (attemptsLeft <= 0) {
                            listener.onFatal(context.getString(R.string.offline_unavailable));
                            stop();
                        } else {
                            if (!downloading) {
                                Log.i(TAG, "Downloading on-device model for " + lang);
                                listener.onStatus(context.getString(R.string.offline_model_downloading));
                                recognizer.triggerModelDownload(baseIntent);
                            }
                            main.postDelayed(() -> checkModelThenBegin(attemptsLeft - 1, true), MODEL_POLL_MS);
                        }
                    }

                    @Override
                    public void onError(int error) {
                        // Not every service implements the check: just try.
                        beginSession();
                    }
                });
    }

    private static boolean containsLang(@Nullable List<String> langs, String lang) {
        if (langs == null) return false;
        String base = lang.split("-")[0];
        for (String l : langs) {
            if (l.equalsIgnoreCase(lang) || l.split("-")[0].equalsIgnoreCase(base)) return true;
        }
        return false;
    }

    // ---- Microphone capture -> pipe ----------------------------------------------------------

    @SuppressWarnings("MissingPermission") // checked in start()
    private boolean startCapture() {
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) return false;
        try {
            // VOICE_RECOGNITION: tuned for speech, without the call's echo cancellation where possible.
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, SAMPLE_RATE * 2));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                record = null;
                return false;
            }
            record.startRecording();
        } catch (RuntimeException e) {
            Log.w(TAG, "AudioRecord failed", e);
            if (record != null) record.release();
            record = null;
            return false;
        }
        capturing = true;
        firstAudioMs = 0;
        lastNonZeroMs = 0;
        captureThread = new Thread(this::captureLoop, "soteria-offline-mic");
        captureThread.start();
        main.postDelayed(silenceCheck, 2_000);
        return true;
    }

    private void captureLoop() {
        AudioRecord rec = record;
        if (rec == null) return;
        byte[] buffer = new byte[CHUNK_BYTES];
        while (capturing) {
            int n = rec.read(buffer, 0, buffer.length);
            if (n < 0) {
                Log.w(TAG, "AudioRecord.read error " + n);
                break;
            }
            if (n == 0) continue;
            long now = System.currentTimeMillis();
            if (firstAudioMs == 0) firstAudioMs = now;
            for (int i = 0; i < n; i++) {
                if (buffer[i] != 0) {
                    lastNonZeroMs = now;
                    break;
                }
            }
            synchronized (pipeLock) {
                FileDescriptor fd = writeFd;
                if (fd == null) continue; // between sentences: drop audio
                try {
                    Os.write(fd, buffer, 0, n); // non-blocking, so holding the lock is cheap
                } catch (ErrnoException e) {
                    if (e.errno != OsConstants.EAGAIN) writeFd = null; // recognizer closed its end
                    // EAGAIN: recognizer is behind; drop this chunk instead of blocking the mic.
                } catch (IOException e) {
                    writeFd = null;
                }
            }
        }
    }

    private void stopCapture() {
        capturing = false;
        main.removeCallbacks(silenceCheck);
        Thread t = captureThread;
        captureThread = null;
        AudioRecord rec = record;
        record = null;
        if (rec != null) {
            try {
                rec.stop();
            } catch (RuntimeException ignored) {
            }
        }
        if (t != null) {
            try {
                t.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (rec != null) rec.release();
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private boolean openPipe(Intent intent) {
        closePipe();
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            pipeRead = pipe[0];
            pipeWrite = pipe[1];
            FileDescriptor fd = pipeWrite.getFileDescriptor();
            int flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0);
            Os.fcntlInt(fd, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipeRead);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE);
            synchronized (pipeLock) {
                writeFd = fd;
            }
            return true;
        } catch (IOException | ErrnoException e) {
            Log.w(TAG, "Couldn't open audio pipe", e);
            closePipe();
            return false;
        }
    }

    private void closePipe() {
        synchronized (pipeLock) {
            writeFd = null;
            closeQuietly(pipeWrite);
            pipeWrite = null;
        }
        closeQuietly(pipeRead);
        pipeRead = null;
    }

    private static void closeQuietly(@Nullable ParcelFileDescriptor pfd) {
        if (pfd == null) return;
        try {
            pfd.close();
        } catch (IOException ignored) {
        }
    }
}
