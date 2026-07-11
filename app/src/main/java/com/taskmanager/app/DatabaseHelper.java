package com.taskmanager.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int    VERSION      = 3;
    private static final String DB_NAME      = "tasks.db";
    private static final String TABLE        = "tasks";
    private static final String TABLE_HISTORY = "task_history";

    private static volatile DatabaseHelper instance;

    public static DatabaseHelper getInstance(Context ctx) {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) {
                    instance = new DatabaseHelper(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private DatabaseHelper(Context ctx) {
        super(ctx, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE + " (" +
            "id         INTEGER PRIMARY KEY AUTOINCREMENT," +
            "date       TEXT    NOT NULL," +
            "name       TEXT    NOT NULL," +
            "is_done    INTEGER NOT NULL DEFAULT 0," +
            "sort_order INTEGER NOT NULL DEFAULT 0)"
        );
        createHistoryTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE " + TABLE + " SET sort_order = id");
        }
        if (oldV < 3) {
            createHistoryTable(db);
        }
    }

    private void createHistoryTable(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE " + TABLE_HISTORY + " (" +
            "id        INTEGER PRIMARY KEY AUTOINCREMENT," +
            "task_id   INTEGER NOT NULL," +
            "task_name TEXT," +
            "action    TEXT    NOT NULL," +
            "old_value TEXT," +
            "new_value TEXT," +
            "timestamp INTEGER NOT NULL)"
        );
    }

    public List<Task> getAllTasks() {
        List<Task> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase()
                .query(TABLE, null, null, null, null, null, "date ASC, sort_order ASC, id ASC")) {
            while (c.moveToNext()) {
                Task t      = new Task();
                t.id        = c.getInt(c.getColumnIndexOrThrow("id"));
                t.date      = c.getString(c.getColumnIndexOrThrow("date"));
                t.name      = c.getString(c.getColumnIndexOrThrow("name"));
                t.isDone    = c.getInt(c.getColumnIndexOrThrow("is_done")) == 1;
                t.sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order"));
                list.add(t);
            }
        }
        return list;
    }

    private Task getTask(SQLiteDatabase db, int id) {
        try (Cursor c = db.query(TABLE, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToNext()) {
                Task t   = new Task();
                t.id     = c.getInt(c.getColumnIndexOrThrow("id"));
                t.date   = c.getString(c.getColumnIndexOrThrow("date"));
                t.name   = c.getString(c.getColumnIndexOrThrow("name"));
                t.isDone = c.getInt(c.getColumnIndexOrThrow("is_done")) == 1;
                return t;
            }
        }
        return null;
    }

    public void insertTask(Task task) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("date",    task.date);
        v.put("name",    task.name);
        v.put("is_done", task.isDone ? 1 : 0);
        long newId = db.insert(TABLE, null, v);
        if (newId > 0) {
            ContentValues order = new ContentValues();
            order.put("sort_order", newId);
            db.update(TABLE, order, "id=?", new String[]{String.valueOf(newId)});
            logHistory(db, (int) newId, task.name,
                    TaskHistory.CREATED, null, task.date);
        }
    }

    public void updateTask(Task task) {
        SQLiteDatabase db = getWritableDatabase();
        Task old = getTask(db, task.id);

        ContentValues v = new ContentValues();
        v.put("is_done",    task.isDone ? 1 : 0);
        v.put("name",       task.name);
        v.put("date",       task.date);
        v.put("sort_order", task.sortOrder);
        db.update(TABLE, v, "id=?", new String[]{String.valueOf(task.id)});

        if (old != null) {
            if (!old.name.equals(task.name)) {
                logHistory(db, task.id, task.name,
                        TaskHistory.RENAMED, old.name, task.name);
            }
            if (!old.date.equals(task.date)) {
                logHistory(db, task.id, task.name,
                        TaskHistory.DATE_CHANGED, old.date, task.date);
            }
            if (old.isDone != task.isDone) {
                logHistory(db, task.id, task.name, TaskHistory.STATUS_CHANGED,
                        old.isDone ? "done" : "pending",
                        task.isDone ? "done" : "pending");
            }
        }
    }

    public void deleteTask(int id) {
        SQLiteDatabase db = getWritableDatabase();
        Task old = getTask(db, id);
        db.delete(TABLE, "id=?", new String[]{String.valueOf(id)});
        if (old != null) {
            logHistory(db, id, old.name, TaskHistory.DELETED, null, null);
        }
    }

    public void replaceAllTasks(List<Task> tasks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, null, null);
            for (Task t : tasks) {
                ContentValues v = new ContentValues();
                v.put("date",       t.date);
                v.put("name",       t.name);
                v.put("is_done",    t.isDone ? 1 : 0);
                v.put("sort_order", t.sortOrder);
                db.insert(TABLE, null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void updateSortOrders(List<Task> tasks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < tasks.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("sort_order", i);
                db.update(TABLE, v, "id=?", new String[]{String.valueOf(tasks.get(i).id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void logHistory(SQLiteDatabase db, int taskId, String taskName,
                            String action, String oldValue, String newValue) {
        ContentValues v = new ContentValues();
        v.put("task_id",   taskId);
        v.put("task_name", taskName);
        v.put("action",    action);
        v.put("old_value", oldValue);
        v.put("new_value", newValue);
        v.put("timestamp", System.currentTimeMillis());
        db.insert(TABLE_HISTORY, null, v);
    }

    public List<TaskHistory> getAllHistory() {
        List<TaskHistory> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase()
                .query(TABLE_HISTORY, null, null, null, null, null,
                        "timestamp DESC, id DESC")) {
            while (c.moveToNext()) {
                TaskHistory h = new TaskHistory();
                h.id        = c.getLong(c.getColumnIndexOrThrow("id"));
                h.taskId    = c.getInt(c.getColumnIndexOrThrow("task_id"));
                h.taskName  = c.getString(c.getColumnIndexOrThrow("task_name"));
                h.action    = c.getString(c.getColumnIndexOrThrow("action"));
                h.oldValue  = c.getString(c.getColumnIndexOrThrow("old_value"));
                h.newValue  = c.getString(c.getColumnIndexOrThrow("new_value"));
                h.timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"));
                list.add(h);
            }
        }
        return list;
    }

    public void clearHistory() {
        getWritableDatabase().delete(TABLE_HISTORY, null, null);
    }
}
