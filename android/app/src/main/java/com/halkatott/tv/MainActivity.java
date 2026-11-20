package com.halkatott.tv;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hosts the HalkatOTT terminal UI: a Termux terminal view running the native armv7 binary
 * (shipped as libhalkatott.so) on a pseudo-terminal, with Android TV remote key handling.
 */
public final class MainActivity extends Activity implements TerminalViewClient, TerminalSessionClient {
    private static final String TAG = "HalkatOTT";
    private static final String PREFS = "halkatott";
    private static final String PREF_TEXT_PX = "text_px";

    private TerminalView terminalView;
    private TerminalSession session;
    private PlayerBridge bridge;
    private boolean imeShown;
    private boolean exited;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow file:// URIs in player intents (local subtitles / downloads).
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        terminalView = findViewById(R.id.terminal_view);
        terminalView.setTerminalViewClient(this);
        terminalView.setTextSize(loadTextSize());
        terminalView.requestFocus();

        File exe = new File(getApplicationInfo().nativeLibraryDir, "libhalkatott.so");
        if (!exe.isFile()) {
            Toast.makeText(this, "The HalkatOTT binary is missing from this build (" + exe + ")", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            File bin = new File(getFilesDir(), "bin");
            bridge = new PlayerBridge(this, new File(getFilesDir(), "bridge"));
            File shim = bridge.install(bin);
            bridge.start();
            session = new TerminalSession(
                    exe.getAbsolutePath(),
                    getFilesDir().getAbsolutePath(),
                    new String[]{"halkatott"},
                    buildEnvironment(bin, shim),
                    2000,
                    this);
            terminalView.attachSession(session);
        } catch (IOException e) {
            Log.e(TAG, "startup failed", e);
            Toast.makeText(this, "Startup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Toast.makeText(this, "Press MENU on the remote for the on-screen keyboard", Toast.LENGTH_LONG).show();
    }

    private String[] buildEnvironment(File binDir, File shim) {
        File files = getFilesDir();
        File cache = getCacheDir();
        Map<String, String> env = new java.util.HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("LANG", "en_US.UTF-8");
        env.put("HOME", files.getAbsolutePath());
        env.put("TMPDIR", cache.getAbsolutePath());
        env.put("XDG_CONFIG_HOME", new File(files, ".config").getAbsolutePath());
        env.put("XDG_DATA_HOME", new File(files, ".local/share").getAbsolutePath());
        env.put("XDG_STATE_HOME", new File(files, ".local/state").getAbsolutePath());
        env.put("XDG_CACHE_HOME", cache.getAbsolutePath());
        env.put("PATH", "/system/bin:/system/xbin:" + binDir.getAbsolutePath());
        // Makes the native app use our termux-am shim for launching external video players.
        env.put("HALKATOTT_ANDROID_PLAYER_PATH", shim.getAbsolutePath());
        env.put("RUST_BACKTRACE", "0");
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : env.entrySet()) out.add(e.getKey() + "=" + e.getValue());
        return out.toArray(new String[0]);
    }

    // ---- text size ---------------------------------------------------------------------------

    private int defaultTextSize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.max(14, Math.min(dm.widthPixels, dm.heightPixels) / 30);
    }

    private int loadTextSize() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getInt(PREF_TEXT_PX, defaultTextSize());
    }

    private void changeTextSize(int delta) {
        int px = Math.max(10, Math.min(120, loadTextSize() + delta));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_TEXT_PX, px).apply();
        terminalView.setTextSize(px);
    }

    // ---- keyboard ----------------------------------------------------------------------------

    private void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (imeShown) {
            imm.hideSoftInputFromWindow(terminalView.getWindowToken(), 0);
        } else {
            terminalView.requestFocus();
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_FORCED);
        }
        imeShown = !imeShown;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && terminalView != null) terminalView.requestFocus();
    }

    @Override
    public void onBackPressed() {
        // BACK is delivered to the terminal as ESC by the key handler; only leave when the process is gone.
        if (exited) finish();
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) bridge.stop();
        if (session != null) session.finishIfRunning();
        super.onDestroy();
    }

    // ---- TerminalViewClient --------------------------------------------------------------------

    @Override
    public float onScale(float scale) {
        return 1.0f;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        toggleKeyboard();
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return true;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession s) {
        if (exited) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                finish();
            }
            return true;
        }
        if (e.isCtrlPressed()) {
            if (keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_PLUS
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ADD) {
                changeTextSize(2);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MINUS || keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT) {
                changeTextSize(-2);
                return true;
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_BUTTON_Y:
                if (e.getRepeatCount() == 0) toggleKeyboard();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_B:
                if (e.getRepeatCount() == 0) s.write("\u001b");
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (e.getRepeatCount() == 0) s.write("\r");
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        // Swallow BACK so the system does not close the activity on key-up.
        return keyCode == KeyEvent.KEYCODE_BACK && !exited;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return true; // no text-selection mode on TV
    }

    @Override
    public boolean readControlKey() {
        return false;
    }

    @Override
    public boolean readAltKey() {
        return false;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession s) {
        return false;
    }

    @Override
    public void onEmulatorSet() {
    }

    // ---- TerminalSessionClient -----------------------------------------------------------------

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        terminalView.onScreenUpdated();
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {
        int code = finishedSession.getExitStatus();
        if (code == 0 || isFinishing()) {
            finish();
            return;
        }
        exited = true;
        String msg = "\r\n[HalkatOTT exited with code " + code + ". Press BACK or OK to close.]\r\n";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        if (finishedSession.getEmulator() != null) {
            finishedSession.getEmulator().append(bytes, bytes.length);
            terminalView.onScreenUpdated();
        }
    }

    @Override
    public void onCopyTextToClipboard(TerminalSession s, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("HalkatOTT", text));
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) return;
        CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text != null && s != null && s.getEmulator() != null) s.getEmulator().paste(text.toString());
    }

    @Override
    public void onBell(TerminalSession s) {
    }

    @Override
    public void onColorsChanged(TerminalSession s) {
        terminalView.invalidate();
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null;
    }

    // ---- logging (shared by both client interfaces) --------------------------------------------

    @Override
    public void logError(String tag, String message) {
        Log.e(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        Log.w(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        Log.i(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        Log.v(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        Log.e(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        Log.e(tag, "exception", e);
    }
}
