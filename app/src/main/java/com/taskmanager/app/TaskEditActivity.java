package com.taskmanager.app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Full-screen add/edit form for a single task. Replaces the old modal dialog:
 * Material guidance puts text-entry forms on a full screen (they need the
 * keyboard) with the dismiss/back affordance top-left and the confirmation
 * action top-right.
 *
 * <p>The screen is a pure form — it never touches the database. It reports the
 * outcome back to {@link MainActivity} through the activity result so that the
 * caller keeps ownership of persistence (undo snackbar, list scrolling,
 * preserving {@code isDone}/{@code sortOrder}).
 */
public class TaskEditActivity extends AppCompatActivity {

    /** Input extras. Presence of a non-negative {@link #EXTRA_TASK_ID} means edit mode. */
    public static final String EXTRA_TASK_ID   = "task_id";
    public static final String EXTRA_TASK_NAME = "task_name";
    public static final String EXTRA_TASK_DATE = "task_date";

    /** Result extras. {@link #EXTRA_RESULT_ACTION} is one of the ACTION_* values. */
    public static final String EXTRA_RESULT_ACTION = "result_action";
    public static final String ACTION_SAVE   = "save";
    public static final String ACTION_DELETE = "delete";

    private static final SimpleDateFormat DB_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private boolean isEdit;
    private int taskId = -1;

    private TextInputLayout   nameLayout;
    private TextInputEditText editName;
    private TextInputEditText editDate;

    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);

        taskId = getIntent().getIntExtra(EXTRA_TASK_ID, -1);
        isEdit = taskId >= 0;
        String initialName = getIntent().getStringExtra(EXTRA_TASK_NAME);
        String initialDate = getIntent().getStringExtra(EXTRA_TASK_DATE);

        nameLayout = findViewById(R.id.nameLayout);
        editName   = findViewById(R.id.editName);
        editDate   = findViewById(R.id.editDate);
        MaterialButton btnToday    = findViewById(R.id.btnToday);
        MaterialButton btnTomorrow = findViewById(R.id.btnTomorrow);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(isEdit ? R.string.edit_task_title : R.string.new_task_title);
        // Back/up closes the screen and discards changes (no result set).
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.task_edit_menu);
        Menu menu = toolbar.getMenu();
        menu.findItem(R.id.action_save)
                .setTitle(isEdit ? R.string.action_save : R.string.action_add);
        menu.findItem(R.id.action_task_history).setVisible(isEdit);
        menu.findItem(R.id.action_delete).setVisible(isEdit);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        // Reject newlines so a task name stays on a single logical line.
        editName.setFilters(new InputFilter[]{ (src, s, e, dst, ds, de) -> {
            for (int i = s; i < e; i++) if (src.charAt(i) == '\n') return "";
            return null;
        }});

        selectedDate = isEdit && initialDate != null ? initialDate : DB_FMT.format(new Date());
        editDate.setText(selectedDate);
        editDate.setKeyListener(null);

        if (isEdit && initialName != null) {
            editName.setText(initialName);
            editName.setSelection(initialName.length());
        }

        editDate.setOnClickListener(v -> openDatePicker());
        editDate.setOnFocusChangeListener((v, has) -> { if (has) openDatePicker(); });

        btnToday.setOnClickListener(v -> setDate(new Date()));
        btnTomorrow.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            setDate(cal.getTime());
        });

        // Focus the name field and raise the keyboard immediately — the whole
        // point of a full-screen form is to start typing right away.
        editName.requestFocus();
        editName.postDelayed(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editName, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            save();
            return true;
        }
        if (id == R.id.action_task_history) {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra(HistoryActivity.EXTRA_TASK_ID, taskId);
            intent.putExtra(HistoryActivity.EXTRA_TASK_NAME,
                    getIntent().getStringExtra(EXTRA_TASK_NAME));
            startActivity(intent);
            return true;
        }
        if (id == R.id.action_delete) {
            Intent result = new Intent()
                    .putExtra(EXTRA_RESULT_ACTION, ACTION_DELETE)
                    .putExtra(EXTRA_TASK_ID, taskId);
            setResult(RESULT_OK, result);
            finish();
            return true;
        }
        return false;
    }

    private void save() {
        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.name_required));
            return;
        }
        nameLayout.setError(null);

        Intent result = new Intent()
                .putExtra(EXTRA_RESULT_ACTION, ACTION_SAVE)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TASK_NAME, name)
                .putExtra(EXTRA_TASK_DATE, selectedDate);
        setResult(RESULT_OK, result);
        finish();
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        try {
            Date d = DB_FMT.parse(selectedDate);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (picker, y, m, d) ->
                setDate(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setDate(Date date) {
        setDate(DB_FMT.format(date));
    }

    private void setDate(String date) {
        selectedDate = date;
        editDate.setText(date);
    }
}
