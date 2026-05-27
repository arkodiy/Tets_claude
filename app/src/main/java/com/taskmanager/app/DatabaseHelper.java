package com.taskmanager.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int    VERSION = 2;
    private static final String DB_NAME = "tasks.db";
    private static final String TABLE   = "tasks";

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE " + TABLE + " SET sort_order = id");
        }
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
        }
    }

    public void updateTask(Task task) {
        ContentValues v = new ContentValues();
        v.put("is_done",    task.isDone ? 1 : 0);
        v.put("name",       task.name);
        v.put("date",       task.date);
        v.put("sort_order", task.sortOrder);
        getWritableDatabase().update(TABLE, v, "id=?", new String[]{String.valueOf(task.id)});
    }

    public void deleteTask(int id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
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
}
