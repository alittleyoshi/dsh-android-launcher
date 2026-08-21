package io.patrick.dshlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class TermuxBridge {
    static final String TERMUX_PACKAGE = "com.termux";
    static final String TERMUX_SERVICE = "com.termux.app.RunCommandService";
    static final String RUN_PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    static final String EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    static final String EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    static final String START_SCRIPT = TERMUX_HOME + "/dsh-android/start.sh";
    static final String STOP_SCRIPT = TERMUX_HOME + "/dsh-android/stop.sh";

    private TermuxBridge() {}

    static boolean isTermuxInstalled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.getPackageManager().getPackageInfo(
                        TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0));
            } else {
                context.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static void runScript(Context context, String path, String label) {
        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
        intent.setAction(ACTION_RUN_COMMAND);
        intent.putExtra(EXTRA_COMMAND_PATH, path);
        intent.putExtra(EXTRA_ARGUMENTS, new String[0]);
        intent.putExtra(EXTRA_WORKDIR, TERMUX_HOME);
        intent.putExtra(EXTRA_BACKGROUND, true);
        intent.putExtra(EXTRA_COMMAND_LABEL, label);
        context.startService(intent);
    }

    static void startBackend(Context context) {
        runScript(context, START_SCRIPT, "Start DeepSeek Harness");
    }

    static void stopBackend(Context context) {
        runScript(context, STOP_SCRIPT, "Stop DeepSeek Harness");
    }
}
