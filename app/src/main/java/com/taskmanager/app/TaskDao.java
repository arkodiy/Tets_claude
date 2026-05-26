package com.taskmanager.app;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY date ASC, id ASC")
    List<Task> getAllTasks();

    @Insert
    void insert(Task task);

    @Update
    void update(Task task);
}
