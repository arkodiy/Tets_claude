package com.taskmanager.app;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
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

    interface Callbacks {
        void onToggle(Task task);
        void onEdit(Task task);
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private final Callbacks callbacks;
    final List<Object> items = new ArrayList<>();

    public TaskAdapter(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setData(Map<String, List<Task>> grouped) {
        items.clear();
        for (Map.Entry<String, List<Task>> entry : grouped.entrySet()) {
            items.add(entry.getKey());
            items.addAll(entry.getValue());
        }
        notifyDataSetChanged();
    }

    public Object getItem(int pos) {
        return (pos >= 0 && pos < items.size()) ? items.get(pos) : null;
    }

    public boolean canDropOver(int dragPos, int targetPos) {
        Object drag   = getItem(dragPos);
        Object target = getItem(targetPos);
        if (!(drag instanceof Task) || !(target instanceof Task)) return false;
        return ((Task) drag).date.equals(((Task) target).date);
    }

    public void moveItem(int from, int to) {
        Object item = items.remove(from);
        items.add(to, item);
        notifyItemMoved(from, to);
    }

    public void removeItem(int pos) {
        if (pos >= 0 && pos < items.size() && items.get(pos) instanceof Task) {
            items.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public List<Task> getGroupTasks(int pos) {
        Object obj = getItem(pos);
        if (!(obj instanceof Task)) return new ArrayList<>();
        String date = ((Task) obj).date;
        List<Task> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Task && ((Task) item).date.equals(date)) {
                result.add((Task) item);
            }
        }
        return result;
    }

    @Override public int getItemCount() { return items.size(); }

    @Override
    public int getItemViewType(int pos) {
        return items.get(pos) instanceof String ? TYPE_HEADER : TYPE_TASK;
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

            vh.checkbox.setOnCheckedChangeListener((btn, checked) -> callbacks.onToggle(task));

            vh.itemView.setOnLongClickListener(v -> {
                callbacks.onEdit(task);
                return true;
            });

            vh.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    callbacks.onStartDrag(vh);
                }
                return false;
            });
        }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView date;
        HeaderVH(View v) { super(v); date = v.findViewById(R.id.headerDate); }
    }

    static class TaskVH extends RecyclerView.ViewHolder {
        final CheckBox  checkbox;
        final TextView  name;
        final ImageView dragHandle;
        TaskVH(View v) {
            super(v);
            checkbox   = v.findViewById(R.id.taskCheckbox);
            name       = v.findViewById(R.id.taskName);
            dragHandle = v.findViewById(R.id.dragHandle);
        }
    }
}
