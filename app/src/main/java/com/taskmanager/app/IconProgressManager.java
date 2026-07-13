package com.taskmanager.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reflects the current day's task completion on the launcher icon by switching
 * between pre-baked activity-alias icons (0 / 25 / 50 / 75 / 100 %).
 *
 * <p>Android has no API to redraw a launcher icon at runtime, so we ship one
 * static icon per bucket (declared as an {@code <activity-alias>} in the
 * manifest) and enable exactly one at a time. The switch uses
 * {@link PackageManager#DONT_KILL_APP} so toggling the alias that launched the
 * current task does not kill the process. Some launchers still briefly flicker
 * the icon or re-sort it in the app drawer when the active alias changes — an
 * unavoidable cost of this approach.
 */
final class IconProgressManager {

    /** Alias short-names declared in AndroidManifest.xml, ordered by fill. */
    private static final String[] ALIASES = {
            "Launcher0", "Launcher25", "Launcher50", "Launcher75", "Launcher100"
    };

    private static final String PREFS      = "icon_progress";
    private static final String KEY_BUCKET = "bucket";

    private static final SimpleDateFormat DB_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private IconProgressManager() {}

    /**
     * Maps today's done/total ratio to a bucket index into {@link #ALIASES}.
     * Returns bucket 0 (empty ring) when nothing is scheduled for today.
     */
    static int bucketFor(List<Task> tasks) {
        String today = DB_FMT.format(new Date());
        int total = 0, done = 0;
        for (Task t : tasks) {
            if (today.equals(t.date)) {
                total++;
                if (t.isDone) done++;
            }
        }
        if (total == 0) return 0;
        int idx = Math.round((float) done / total * (ALIASES.length - 1));
        if (idx < 0) idx = 0;
        if (idx > ALIASES.length - 1) idx = ALIASES.length - 1;
        return idx;
    }

    /** Recomputes today's progress and updates the launcher icon if it changed. */
    static void refresh(Context context, List<Task> tasks) {
        apply(context, bucketFor(tasks));
    }

    /** Enables the given bucket's alias (and disables the rest) if not current. */
    static void apply(Context context, int bucket) {
        if (bucket < 0) bucket = 0;
        if (bucket > ALIASES.length - 1) bucket = ALIASES.length - 1;

        SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Skip no-op changes so we don't trigger the launcher flicker needlessly.
        if (prefs.getInt(KEY_BUCKET, -1) == bucket) return;

        PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();

        // Enable the target FIRST so the app is never left without a launcher
        // entry, then disable the others.
        setAlias(pm, pkg, ALIASES[bucket], true);
        for (int i = 0; i < ALIASES.length; i++) {
            if (i != bucket) setAlias(pm, pkg, ALIASES[i], false);
        }
        prefs.edit().putInt(KEY_BUCKET, bucket).apply();
    }

    private static void setAlias(PackageManager pm, String pkg,
                                 String alias, boolean enabled) {
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(
                new ComponentName(pkg, pkg + "." + alias),
                state, PackageManager.DONT_KILL_APP);
    }
}
