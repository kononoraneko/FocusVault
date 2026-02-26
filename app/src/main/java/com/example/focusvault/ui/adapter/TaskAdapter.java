package com.example.focusvault.ui.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.focusvault.R;
import com.example.focusvault.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface TaskActionListener {
        void onStatusChanged(Task task, boolean isChecked);
        void onEdit(Task task);
        void onDelete(Task task);
    }

    private final List<Task> tasks = new ArrayList<>();
    private final TaskActionListener listener;

    public TaskAdapter(TaskActionListener listener) {
        this.listener = listener;
    }

    public void setTasks(List<Task> taskList) {
        tasks.clear();
        tasks.addAll(taskList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.title.setText(task.getTitle());
        holder.priority.setText(holder.itemView.getContext().getString(R.string.priority_value, task.getPriority()));
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(task.getIsDone() == 1);
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onStatusChanged(task, isChecked));
        holder.editButton.setOnClickListener(v -> listener.onEdit(task));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(task));

        if (task.getIsDone() == 1) {
            holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.title.setPaintFlags(holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        int colorRes;
        if (task.getPriority() >= 7) {
            colorRes = R.color.priority_high;
        } else if (task.getPriority() >= 4) {
            colorRes = R.color.priority_mid;
        } else {
            colorRes = R.color.priority_low;
        }
        holder.priorityDot.setBackgroundTintList(ContextCompat.getColorStateList(holder.itemView.getContext(), colorRes));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        TextView priority;
        View priorityDot;
        ImageButton editButton;
        ImageButton deleteButton;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_done);
            title = itemView.findViewById(R.id.text_task_title);
            priority = itemView.findViewById(R.id.text_task_priority);
            priorityDot = itemView.findViewById(R.id.view_priority_dot);
            editButton = itemView.findViewById(R.id.button_edit_task);
            deleteButton = itemView.findViewById(R.id.button_delete_task);
        }
    }
}
