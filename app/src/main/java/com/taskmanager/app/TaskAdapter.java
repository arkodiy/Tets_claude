package com.taskmanager.app;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_TASK   = 1;

    private static final SimpleDateFormat DB_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat DISPLAY_FMT =
            new SimpleDateFormat("EEEE, MMMM d", Locale.US);

    interface OnTaskToggle { void onToggle(Task task); }

    private final OnTaskToggle toggleListener;
    private final List<Object> items = new ArrayList<>(); // String header | Task item

    public TaskAdapter(OnTaskToggle toggleListener) {
        this.toggleListener = toggleListener;
    }

    public void setData(Map<String, List<Task>> grouped) {
        items.clear();
        for (Map.Entry<String, List<Task>> entry : grouped.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return items.size(); }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_day_header, parent, false));
        }
        return new TaskVH(inf.inflate(R.layout.item_task, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH) {
            String raw = (String) items.get(position);
            String display = raw;
            try {
                Date d = DB_FMT.parse(raw);
                if (d != null) display = DISPLAY_FMT.format(d);
            } catch (ParseException ignored) {}
            ((HeaderVH) holder).date.setText(display);
        } else {
            Task task = (Task) items.get(position);
            TaskVH vh = (TaskVH) holder;

            vh.checkbox.setOnCheckedChangeListener(null);
            vh.checkbox.setChecked(task.isDone);
            vh.name.setText(task.name);

            if (task.isDone) {
                vh.name.setPaintFlags(vh.name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                vh.name.setAlpha(0.45f);
            } else {
                vh.name.setPaintFlags(vh.name.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                vh.name.setAlpha(1f);
            }

            vh.checkbox.setOnCheckedChangeListener((btn, checked) -> toggleListener.onToggle(task));
        }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView date;
        HeaderVH(View v) { super(v); date = v.findViewById(R.id.headerDate); }
    }

    static class TaskVH extends RecyclerView.ViewHolder {
        final CheckBox checkbox;
        final TextView name;
        TaskVH(View v) {
            super(v);
            checkbox = v.findViewById(R.id.taskCheckbox);
            name     = v.findViewById(R.id.taskName);
        }
    }
}
