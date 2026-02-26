package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences;
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
import com.example.focusvault.model.Task;
import com.example.focusvault.ui.adapter.TaskAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

public class TodayFragment extends Fragment {

    private static final String PREFS = "focusvault_prefs";
    private static final String KEY_TODAY_HELP_HIDDEN = "today_help_hidden";

    private DatabaseHelper databaseHelper;
    private TaskAdapter taskAdapter;
    private TextView pomodoroCountText;
    private TextView completedTasksText;
    private TextView progressLabel;
    private TextView pomodoroProgressLabel;
    private LinearProgressIndicator tasksProgress;
    private LinearProgressIndicator pomodoroProgress;

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
        progressLabel = view.findViewById(R.id.text_progress_label);
        pomodoroProgressLabel = view.findViewById(R.id.text_pomodoro_progress_label);
        tasksProgress = view.findViewById(R.id.progress_tasks);
        pomodoroProgress = view.findViewById(R.id.progress_pomodoro);

        View helpCard = view.findViewById(R.id.card_today_help);
        TextView hideHelpButton = view.findViewById(R.id.button_hide_today_help);
        setupHelpCard(helpCard, hideHelpButton);

        taskAdapter = new TaskAdapter(new TaskAdapter.TaskActionListener() {
            @Override
            public void onStatusChanged(Task task, boolean isChecked) {
                databaseHelper.updateTaskStatus(task.getId(), isChecked ? 1 : 0);
                loadData();
            }

            @Override
            public void onEdit(Task task) {
                showEditTaskDialog(task);
            }

            @Override
            public void onDelete(Task task) {
                showDeleteTaskDialog(task);
            }
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

    private void setupHelpCard(View helpCard, TextView hideHelpButton) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean hidden = prefs.getBoolean(KEY_TODAY_HELP_HIDDEN, false);
        helpCard.setVisibility(hidden ? View.GONE : View.VISIBLE);

        hideHelpButton.setOnClickListener(v -> {
            helpCard.setVisibility(View.GONE);
            prefs.edit().putBoolean(KEY_TODAY_HELP_HIDDEN, true).apply();
        });
    }

    private void showAddTaskDialog() {
        showTaskEditDialog(null);
    }

    private void showEditTaskDialog(Task task) {
        showTaskEditDialog(task);
    }

    private void showTaskEditDialog(@Nullable Task task) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.hint_task_title);
        if (task != null) {
            titleInput.setText(task.getTitle());
        }
        container.addView(titleInput);

        EditText priorityInput = new EditText(requireContext());
        priorityInput.setHint(R.string.hint_task_priority);
        priorityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (task != null) {
            priorityInput.setText(String.valueOf(task.getPriority()));
        }
        container.addView(priorityInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(task == null ? R.string.add_task : R.string.edit_task)
                .setView(container)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String priorityText = priorityInput.getText().toString().trim();
                    int priority = parsePriority(priorityText);
                    if (!title.isEmpty()) {
                        if (task == null) {
                            databaseHelper.insertTask(title, databaseHelper.getTodayDate(), priority);
                        } else {
                            databaseHelper.updateTask(task.getId(), title, priority);
                        }
                        loadData();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteTaskDialog(Task task) {
        new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.delete_task_confirm, task.getTitle()))
                .setPositiveButton(R.string.delete_task, (dialog, which) -> {
                    databaseHelper.deleteTask(task.getId());
                    loadData();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int parsePriority(String priorityText) {
        if (priorityText == null || priorityText.isEmpty()) {
            return 1;
        }
        try {
            int value = Integer.parseInt(priorityText);
            if (value < 1) {
                return 1;
            }
            return Math.min(value, 10);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void loadData() {
        String date = databaseHelper.getTodayDate();
        List<Task> tasks = databaseHelper.getTasksByDate(date);
        taskAdapter.setTasks(tasks);

        int completedCount = 0;
        for (Task task : tasks) {
            if (task.getIsDone() == 1) {
                completedCount++;
            }
        }

        int total = tasks.size();
        int pomodoroCount = databaseHelper.getTodayPomodoroCount(date);

        pomodoroCountText.setText(getString(R.string.today_pomodoro_count, pomodoroCount));
        completedTasksText.setText(getString(R.string.today_completed_count, completedCount));

        int taskProgressPercent = total == 0 ? 0 : (completedCount * 100 / total);
        tasksProgress.setProgress(taskProgressPercent);
        progressLabel.setText(getString(R.string.task_progress_percent, taskProgressPercent, completedCount, total));

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int pomodoroTarget = prefs.getInt("daily_goal", 4);
        int pomodoroPercent = Math.min(100, pomodoroCount * 100 / pomodoroTarget);
        pomodoroProgress.setProgress(pomodoroPercent);
        pomodoroProgressLabel.setText(getString(R.string.pomodoro_progress_percent, pomodoroPercent, pomodoroCount, pomodoroTarget));

        if (total == 0) {
            progressLabel.setText(getString(R.string.no_tasks_yet));
            tasksProgress.setProgress(0);
        }
    }
}
