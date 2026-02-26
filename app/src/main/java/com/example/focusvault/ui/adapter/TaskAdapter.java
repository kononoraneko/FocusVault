package com.example.focusvault.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.focusvault.R;
import com.example.focusvault.model.Task;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    public interface OnTaskStatusChangeListener {
        void onStatusChanged(Task task, boolean isChecked);
    }

    private final List<Task> tasks = new ArrayList<>();
    private final OnTaskStatusChangeListener listener;

    public TaskAdapter(OnTaskStatusChangeListener listener) {
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
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public List<Task> getTasks() {
        return tasks;
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        TextView priority;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox_done);
            title = itemView.findViewById(R.id.text_task_title);
            priority = itemView.findViewById(R.id.text_task_priority);
        }
    }
}
