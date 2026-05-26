package com.taskmanager.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int    VERSION = 1;
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
            "id      INTEGER PRIMARY KEY AUTOINCREMENT," +
            "date    TEXT    NOT NULL," +
            "name    TEXT    NOT NULL," +
            "is_done INTEGER NOT NULL DEFAULT 0)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public List<Task> getAllTasks() {
        List<Task> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase()
                .query(TABLE, null, null, null, null, null, "date ASC, id ASC")) {
            while (c.moveToNext()) {
                Task t  = new Task();
                t.id    = c.getInt(c.getColumnIndexOrThrow("id"));
                t.date  = c.getString(c.getColumnIndexOrThrow("date"));
                t.name  = c.getString(c.getColumnIndexOrThrow("name"));
                t.isDone = c.getInt(c.getColumnIndexOrThrow("is_done")) == 1;
                list.add(t);
            }
        }
        return list;
    }

    public void insertTask(Task task) {
        ContentValues v = new ContentValues();
        v.put("date",    task.date);
        v.put("name",    task.name);
        v.put("is_done", task.isDone ? 1 : 0);
        getWritableDatabase().insert(TABLE, null, v);
    }

    public void updateTask(Task task) {
        ContentValues v = new ContentValues();
        v.put("is_done", task.isDone ? 1 : 0);
        getWritableDatabase().update(TABLE, v, "id=?",
                new String[]{String.valueOf(task.id)});
    }
}
