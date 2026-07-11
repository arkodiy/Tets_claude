package com.taskmanager.app;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only audit log of everything that has happened to tasks. Reached
 * rarely, via a long-press on the "add" button on the main screen, so it
 * stays out of the way during normal use.
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private HistoryAdapter adapter;
    private DatabaseHelper db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db           = DatabaseHelper.getInstance(this);
        recyclerView = findViewById(R.id.historyRecyclerView);
        emptyView    = findViewById(R.id.historyEmptyView);

        adapter = new HistoryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        MaterialButton clear = findViewById(R.id.btnClearHistory);
        clear.setOnClickListener(v -> confirmClear());

        reload();
    }

    private void reload() {
        executor.execute(() -> {
            List<TaskHistory> history = db.getAllHistory();
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
