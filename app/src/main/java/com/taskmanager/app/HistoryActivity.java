package com.taskmanager.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only audit log of everything that has happened to tasks. Opened from
 * the overflow menu (whole log) or from a task's edit dialog (that task only).
 */
public class HistoryActivity extends AppCompatActivity {

    /** Optional intent extras to scope the log to a single task. */
    public static final String EXTRA_TASK_ID   = "task_id";
    public static final String EXTRA_TASK_NAME = "task_name";

    private RecyclerView recyclerView;
    private TextView emptyView;
    private HistoryAdapter adapter;
    private DatabaseHelper db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int taskId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db           = DatabaseHelper.getInstance(this);
        recyclerView = findViewById(R.id.historyRecyclerView);
        emptyView    = findViewById(R.id.historyEmptyView);

        taskId = getIntent().getIntExtra(EXTRA_TASK_ID, -1);
        String taskName = getIntent().getStringExtra(EXTRA_TASK_NAME);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        // The title always reads "History"; a single-task view names the task
        // in the subtitle. Clearing is global, so only the whole-log view
        // gets the Clear action.
        if (taskId >= 0) {
            if (taskName != null) toolbar.setSubtitle(taskName);
        } else {
            toolbar.inflateMenu(R.menu.history_menu);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_clear) {
                    confirmClear();
                    return true;
                }
                return false;
            });
        }

        adapter = new HistoryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        reload();
    }

    private void reload() {
        executor.execute(() -> {
            List<TaskHistory> history = taskId >= 0
                    ? db.getHistoryForTask(taskId)
                    : db.getAllHistory();
            runOnUiThread(() -> {
                adapter.setData(history);
                boolean empty = history.isEmpty();
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history_clear)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.history_clear, (d, w) ->
                        executor.execute(() -> {
                            db.clearHistory();
                            reload();
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
