package com.taskmanager.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Serializes the task list to/from the JSON backup format. */
public final class TaskBackup {

    private static final int FORMAT_VERSION = 1;

    private TaskBackup() {}

    public static String toJson(List<Task> tasks) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Task t : tasks) {
            JSONObject o = new JSONObject();
            o.put("date",       t.date);
            o.put("name",       t.name);
            o.put("is_done",    t.isDone);
            o.put("sort_order", t.sortOrder);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app",     "com.taskmanager.app");
        root.put("version", FORMAT_VERSION);
        root.put("tasks",   arr);
        return root.toString(2);
    }

    public static List<Task> fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("tasks");
        List<Task> tasks = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Task t      = new Task();
            t.date      = o.getString("date");
            t.name      = o.getString("name");
            t.isDone    = o.optBoolean("is_done", false);
            t.sortOrder = o.optInt("sort_order", i);
            tasks.add(t);
        }
        return tasks;
    }
}
