package com.taskmanager.app;

/**
 * A single audit-log entry describing something that happened to a task:
 * its creation, a rename, a date change, a status toggle or its deletion.
 * Kept separate from {@link Task} so entries survive after the task is gone.
 */
public class TaskHistory {
    // Action identifiers stored in the "action" column.
    public static final String CREATED        = "CREATED";
    public static final String RENAMED        = "RENAMED";
    public static final String DATE_CHANGED   = "DATE_CHANGED";
    public static final String STATUS_CHANGED = "STATUS_CHANGED";
    public static final String DELETED        = "DELETED";

    public long   id;
    public int    taskId;
    public String taskName;   // snapshot of the name at the time of the event
    public String action;
    public String oldValue;
    public String newValue;
    public long   timestamp;  // System.currentTimeMillis()
}
