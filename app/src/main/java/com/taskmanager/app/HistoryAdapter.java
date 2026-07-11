package com.taskmanager.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryVH> {

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US);

    private final List<TaskHistory> items = new ArrayList<>();

    public void setData(List<TaskHistory> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public HistoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryVH holder, int position) {
        TaskHistory h = items.get(position);
        holder.text.setText(describe(h));
        holder.time.setText(TIME_FMT.format(new Date(h.timestamp)));
    }

    private String describe(TaskHistory h) {
        String name = h.taskName != null ? "“" + h.taskName + "”" : "Task";
        if (h.action == null) return name;
        switch (h.action) {
            case TaskHistory.CREATED:
                return name + " created"
                        + (h.newValue != null ? " for " + h.newValue : "");
            case TaskHistory.RENAMED:
                return "Renamed “" + h.oldValue + "” → “"
                        + h.newValue + "”";
            case TaskHistory.DATE_CHANGED:
                return name + " moved " + h.oldValue + " → " + h.newValue;
            case TaskHistory.STATUS_CHANGED:
                return name + " marked "
                        + ("done".equals(h.newValue) ? "done" : "not done");
            case TaskHistory.DELETED:
                return name + " deleted";
            default:
                return name + " " + h.action;
        }
    }

    static class HistoryVH extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView time;
        HistoryVH(View v) {
            super(v);
            text = v.findViewById(R.id.historyText);
            time = v.findViewById(R.id.historyTime);
        }
    }
}
