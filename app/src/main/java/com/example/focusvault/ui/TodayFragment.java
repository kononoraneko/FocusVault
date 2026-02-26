package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.focusvault.R;
import com.example.focusvault.data.DatabaseHelper;
import com.example.focusvault.ui.adapter.TaskAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class TodayFragment extends Fragment {

    private DatabaseHelper databaseHelper;
    private TaskAdapter taskAdapter;
    private TextView pomodoroCountText;
    private TextView completedTasksText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_today, container, false);

        databaseHelper = new DatabaseHelper(requireContext());
        RecyclerView recyclerView = view.findViewById(R.id.recycler_tasks);
        FloatingActionButton fab = view.findViewById(R.id.fab_add_task);
        pomodoroCountText = view.findViewById(R.id.text_pomodoro_count);
        completedTasksText = view.findViewById(R.id.text_completed_count);

        taskAdapter = new TaskAdapter((task, isChecked) -> {
            databaseHelper.updateTaskStatus(task.getId(), isChecked ? 1 : 0);
            loadData();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(taskAdapter);

        fab.setOnClickListener(v -> showAddTaskDialog());

        loadData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void showAddTaskDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.hint_task_title);
        container.addView(titleInput);

        EditText priorityInput = new EditText(requireContext());
        priorityInput.setHint(R.string.hint_task_priority);
        priorityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        container.addView(priorityInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_task)
                .setView(container)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String priorityText = priorityInput.getText().toString().trim();
                    int priority = priorityText.isEmpty() ? 1 : Integer.parseInt(priorityText);
                    if (!title.isEmpty()) {
                        databaseHelper.insertTask(title, databaseHelper.getTodayDate(), priority);
                        loadData();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadData() {
        String date = databaseHelper.getTodayDate();
        taskAdapter.setTasks(databaseHelper.getTasksByDate(date));
        int pomodoroCount = databaseHelper.getTodayPomodoroCount(date);
        int completedCount = databaseHelper.getTodayCompletedTasksCount(date);
        pomodoroCountText.setText(getString(R.string.today_pomodoro_count, pomodoroCount));
        completedTasksText.setText(getString(R.string.today_completed_count, completedCount));
    }
}
