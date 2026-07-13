package com.taskmanager.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private TaskAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private DatabaseHelper db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Paint deletePaint = new Paint();
    private Drawable deleteIcon;
    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;
    private ActivityResultLauncher<Intent> editLauncher;
    // The task currently open in the editor, so an edit/delete result can be
    // applied to the real object (preserving isDone/sortOrder). Null when adding.
    private Task editingTask;
    // Tasks swiped/deleted but still undoable — hidden from the list until the
    // undo window closes, then really removed from the database.
    private final Set<Integer> pendingDeletes = new HashSet<>();

    // The onCreate() load already refreshes the list once; skip the redundant
    // reload the immediately-following onResume() would otherwise trigger.
    private boolean skipNextResumeReload = true;

    // Fires when the calendar day rolls over (or the clock/time zone changes)
    // while the app is in the foreground, so the "Today" label follows the new
    // day instead of sticking to yesterday's header until the next reload.
    private final BroadcastReceiver dateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadOnUiThread(null);
        }
    };

    private static final SimpleDateFormat DB_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deletePaint.setColor(ContextCompat.getColor(this, R.color.color_delete));
        deletePaint.setAntiAlias(true);
        deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete_white);
        if (deleteIcon != null) deleteIcon = deleteIcon.mutate();

        db           = DatabaseHelper.getInstance(this);
        recyclerView = findViewById(R.id.recyclerView);
        emptyView    = findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TaskAdapter(new TaskAdapter.Callbacks() {
            @Override public void onToggle(Task task) {
                executor.execute(() -> {
                    task.isDone = !task.isDone;
                    db.updateTask(task);
                    reloadOnUiThread(null);
                });
            }
            @Override public void onEdit(Task task) { openTaskEditor(task); }
            @Override public void onStartDrag(RecyclerView.ViewHolder vh) {
                itemTouchHelper.startDrag(vh);
            }
        });
        recyclerView.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {

            @Override
            public int getMovementFlags(@NonNull RecyclerView rv,
                                        @NonNull RecyclerView.ViewHolder vh) {
                if (!(vh instanceof TaskAdapter.TaskVH)) return 0;
                return makeMovementFlags(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                if (adapter.canDropOver(from.getAdapterPosition(), to.getAdapterPosition())) {
                    adapter.moveItem(from.getAdapterPosition(), to.getAdapterPosition());
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                Object item = adapter.getItem(pos);
                if (item instanceof Task) {
                    deleteTaskWithUndo((Task) item);
                }
            }

            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View v = vh.itemView;
                    // Rounded backdrop matching the card shape; the card slides
                    // over it, revealing the red area on the right.
                    float radius = 16 * getResources().getDisplayMetrics().density;
                    c.drawRoundRect(new RectF(v.getLeft(), v.getTop(),
                            v.getRight(), v.getBottom()), radius, radius, deletePaint);
                    if (deleteIcon != null) {
                        float density = getResources().getDisplayMetrics().density;
                        int iconSize  = (int) (24 * density);
                        int margin    = (int) (16 * density);
                        int itemHeight = v.getBottom() - v.getTop();
                        int iconTop   = v.getTop() + (itemHeight - iconSize) / 2;
                        int iconRight = v.getRight() - margin;
                        deleteIcon.setBounds(iconRight - iconSize, iconTop,
                                             iconRight, iconTop + iconSize);
                        deleteIcon.draw(c);
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                int pos = vh.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    List<Task> group = adapter.getGroupTasks(pos);
                    if (!group.isEmpty()) {
                        executor.execute(() -> db.updateSortOrders(group));
                    }
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);

        findViewById(R.id.fab).setOnClickListener(v -> openTaskEditor(null));

        editLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        onTaskEditorResult(result.getData());
                    }
                });

        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        writeBackup(result.getData().getData());
                    }
                });
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null) {
                        readBackup(result.getData().getData());
                    }
                });

        findViewById(R.id.menuButton).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_history) { openHistory(-1, null); return true; }
                if (id == R.id.action_backup)  { startBackup();  return true; }
                if (id == R.id.action_restore) { startRestore(); return true; }
                return false;
            });
            popup.show();
        });

        reloadOnUiThread(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Listen for the day rolling over while we're in the foreground.
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(dateChangeReceiver, filter);

        // Returning to the app after midnight (e.g. backgrounded overnight)
        // must rebind headers so a stale "Today" badge moves to the real today.
        if (skipNextResumeReload) {
            skipNextResumeReload = false;
        } else {
            reloadOnUiThread(null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(dateChangeReceiver);
    }

    /** Opens the change history. Pass taskId >= 0 to scope it to a single task. */
    private void openHistory(int taskId, String taskName) {
        Intent intent = new Intent(this, HistoryActivity.class);
        if (taskId >= 0) {
            intent.putExtra(HistoryActivity.EXTRA_TASK_ID, taskId);
            intent.putExtra(HistoryActivity.EXTRA_TASK_NAME, taskName);
        }
        startActivity(intent);
    }

    /**
     * Removes a task from view immediately but defers the actual database
     * delete until the undo window elapses, so an accidental delete can be
     * taken back from the snackbar.
     */
    private void deleteTaskWithUndo(Task task) {
        pendingDeletes.add(task.id);
        reloadOnUiThread(null);

        Snackbar sb = Snackbar.make(recyclerView, R.string.task_deleted, Snackbar.LENGTH_LONG);
        sb.setAction(R.string.undo, v -> {
            pendingDeletes.remove(task.id);
            reloadOnUiThread(null);
        });
        sb.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar s, int event) {
                if (event != DISMISS_EVENT_ACTION && pendingDeletes.contains(task.id)) {
                    executor.execute(() -> {
                        db.deleteTask(task.id);
                        pendingDeletes.remove(task.id);
                    });
                }
            }
        });
        sb.show();
    }

    private void startBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE,
                        "tasks-backup-" + DB_FMT.format(new Date()) + ".json");
        backupLauncher.launch(intent);
    }

    private void startRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "application/octet-stream", "text/plain"});
        restoreLauncher.launch(intent);
    }

    private void writeBackup(Uri uri) {
        executor.execute(() -> {
            try {
                String json = TaskBackup.toJson(db.getAllTasks());
                OutputStream out;
                try {
                    // "wt" truncates an existing file the user chose to overwrite;
                    // some providers only support the default "w" mode.
                    out = getContentResolver().openOutputStream(uri, "wt");
                } catch (Exception e) {
                    out = getContentResolver().openOutputStream(uri);
                }
                if (out == null) throw new IOException("Cannot open output stream");
                try (OutputStream os = out) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> toast("Backup saved"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Backup failed: " + e.getMessage()));
            }
        });
    }

    private void readBackup(Uri uri) {
        executor.execute(() -> {
            try {
                byte[] data;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Cannot open input stream");
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
                    data = buf.toByteArray();
                }
                List<Task> restored =
                        TaskBackup.fromJson(new String(data, StandardCharsets.UTF_8));
                int currentCount = db.getAllTasks().size();
                runOnUiThread(() -> confirmRestore(restored, currentCount));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Restore failed: not a valid backup file"));
            }
        });
    }

    private void confirmRestore(List<Task> restored, int currentCount) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore from backup")
                .setMessage("This will replace your current " + currentCount
                        + " task(s) with " + restored.size() + " task(s) from the backup.")
                .setPositiveButton("Restore", (d, w) -> executor.execute(() -> {
                    db.replaceAllTasks(restored);
                    reloadOnUiThread(() -> {
                        recyclerView.scrollToPosition(adapter.getItemCount() - 1);
                        toast("Restored " + restored.size() + " task(s)");
                    });
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void reloadOnUiThread(Runnable afterLoad) {
        executor.execute(() -> {
            List<Task> tasks = db.getAllTasks();
            if (!pendingDeletes.isEmpty()) {
                Iterator<Task> it = tasks.iterator();
                while (it.hasNext()) {
                    if (pendingDeletes.contains(it.next().id)) it.remove();
                }
            }
            LinkedHashMap<String, List<Task>> grouped = group(tasks);
            runOnUiThread(() -> {
                adapter.setData(grouped);
                boolean empty = tasks.isEmpty();
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (afterLoad != null) recyclerView.post(afterLoad);
            });
        });
    }

    private static LinkedHashMap<String, List<Task>> group(List<Task> tasks) {
        LinkedHashMap<String, List<Task>> map = new LinkedHashMap<>();
        for (Task t : tasks) {
            if (!map.containsKey(t.date)) map.put(t.date, new ArrayList<>());
            map.get(t.date).add(t);
        }
        return map;
    }

    /** Opens the full-screen add (existing == null) or edit form. */
    private void openTaskEditor(Task existing) {
        editingTask = existing;
        Intent intent = new Intent(this, TaskEditActivity.class);
        if (existing != null) {
            intent.putExtra(TaskEditActivity.EXTRA_TASK_ID, existing.id);
            intent.putExtra(TaskEditActivity.EXTRA_TASK_NAME, existing.name);
            intent.putExtra(TaskEditActivity.EXTRA_TASK_DATE, existing.date);
        }
        editLauncher.launch(intent);
    }

    /** Applies whatever the editor reported back: a save, or a delete. */
    private void onTaskEditorResult(Intent data) {
        String action = data.getStringExtra(TaskEditActivity.EXTRA_RESULT_ACTION);
        if (TaskEditActivity.ACTION_DELETE.equals(action)) {
            if (editingTask != null) deleteTaskWithUndo(editingTask);
            return;
        }
        if (!TaskEditActivity.ACTION_SAVE.equals(action)) return;

        String name = data.getStringExtra(TaskEditActivity.EXTRA_TASK_NAME);
        String date = data.getStringExtra(TaskEditActivity.EXTRA_TASK_DATE);
        if (name == null || date == null) return;

        if (editingTask != null) {
            // Reuse the loaded task so isDone/sortOrder survive the edit.
            editingTask.name = name;
            editingTask.date = date;
            Task edited = editingTask;
            executor.execute(() -> {
                db.updateTask(edited);
                reloadOnUiThread(null);
            });
        } else {
            Task task   = new Task();
            task.date   = date;
            task.name   = name;
            task.isDone = false;
            executor.execute(() -> {
                db.insertTask(task);
                reloadOnUiThread(() ->
                        recyclerView.scrollToPosition(adapter.getItemCount() - 1));
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
