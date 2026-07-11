package com.taskmanager.app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
            @Override public void onEdit(Task task) { showTaskDialog(task); }
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
                    adapter.removeItem(pos);
                    executor.execute(() -> db.deleteTask(((Task) item).id));
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

        findViewById(R.id.fab).setOnClickListener(v -> showTaskDialog(null));

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

    /** Opens the change history. Pass taskId >= 0 to scope it to a single task. */
    private void openHistory(int taskId, String taskName) {
        Intent intent = new Intent(this, HistoryActivity.class);
        if (taskId >= 0) {
            intent.putExtra(HistoryActivity.EXTRA_TASK_ID, taskId);
            intent.putExtra(HistoryActivity.EXTRA_TASK_NAME, taskName);
        }
        startActivity(intent);
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

    private void showTaskDialog(Task existing) {
        boolean isEdit = existing != null;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        TextInputLayout   nameLayout       = view.findViewById(R.id.nameLayout);
        TextInputEditText editDate         = view.findViewById(R.id.editDate);
        TextInputEditText editName         = view.findViewById(R.id.editName);
        View              quickDateButtons = view.findViewById(R.id.quickDateButtons);
        MaterialButton    btnToday         = view.findViewById(R.id.btnToday);
        MaterialButton    btnTomorrow      = view.findViewById(R.id.btnTomorrow);
        View              btnTaskHistory   = view.findViewById(R.id.btnTaskHistory);

        editName.setFilters(new InputFilter[]{ (src, s, e, dst, ds, de) -> {
            for (int i = s; i < e; i++) if (src.charAt(i) == '\n') return "";
            return null;
        }});

        final String[] selectedDate = {isEdit ? existing.date : DB_FMT.format(new Date())};
        editDate.setText(selectedDate[0]);
        editDate.setKeyListener(null);
        if (isEdit) {
            editName.setText(existing.name);
            editName.setSelection(existing.name.length());
            quickDateButtons.setVisibility(View.VISIBLE);
            btnTaskHistory.setVisibility(View.VISIBLE);
            btnTaskHistory.setOnClickListener(v -> openHistory(existing.id, existing.name));
        }

        View.OnClickListener openPicker = v -> {
            Calendar cal = Calendar.getInstance();
            try {
                Date d = DB_FMT.parse(selectedDate[0]);
                if (d != null) cal.setTime(d);
            } catch (Exception ignored) {}
            new DatePickerDialog(this, (picker, y, m, d) -> {
                selectedDate[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                editDate.setText(selectedDate[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                    .show();
        };
        editDate.setOnClickListener(openPicker);
        editDate.setOnFocusChangeListener((v, has) -> { if (has) openPicker.onClick(v); });

        btnToday.setOnClickListener(v -> {
            selectedDate[0] = DB_FMT.format(new Date());
            editDate.setText(selectedDate[0]);
        });
        btnTomorrow.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            selectedDate[0] = DB_FMT.format(cal.getTime());
            editDate.setText(selectedDate[0]);
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(isEdit ? "Edit Task" : "New Task")
                .setView(view)
                .setPositiveButton(isEdit ? "Save" : "Add", null)
                .setNegativeButton("Cancel", null);

        if (isEdit) {
            builder.setNeutralButton(" ", null);
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> editName.postDelayed(() -> {
            editName.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editName,
                    android.view.inputmethod.InputMethodManager.SHOW_FORCED);
        }, 100));
        dialog.show();

        // Pin the dialog width so it doesn't resize as the task name grows/shrinks.
        if (dialog.getWindow() != null) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int margin = (int) (32 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(screenWidth - 2 * margin,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        if (isEdit) {
            Button neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutralBtn instanceof MaterialButton) {
                MaterialButton mb = (MaterialButton) neutralBtn;
                mb.setText("");
                mb.setIconResource(R.drawable.ic_delete_white);
                mb.setIconTint(ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.color_text_secondary)));
                mb.setContentDescription("Delete task");
            }
            neutralBtn.setOnClickListener(v -> {
                dialog.dismiss();
                executor.execute(() -> {
                    db.deleteTask(existing.id);
                    reloadOnUiThread(null);
                });
            });
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = editName.getText() != null
                    ? editName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                nameLayout.setError("Task name is required");
                return;
            }
            nameLayout.setError(null);
            dialog.dismiss();

            if (isEdit) {
                existing.name = name;
                existing.date = selectedDate[0];
                executor.execute(() -> {
                    db.updateTask(existing);
                    reloadOnUiThread(null);
                });
            } else {
                Task task   = new Task();
                task.date   = selectedDate[0];
                task.name   = name;
                task.isDone = false;
                executor.execute(() -> {
                    db.insertTask(task);
                    reloadOnUiThread(() ->
                            recyclerView.scrollToPosition(adapter.getItemCount() - 1));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
