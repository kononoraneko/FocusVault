package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
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
import com.example.focusvault.model.Note;
import com.example.focusvault.ui.adapter.NoteAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CalendarFragment extends Fragment {

    private DatabaseHelper databaseHelper;
    private NoteAdapter adapter;
    private TextView dateText;
    private TextView tasksText;
    private TextView pomodoroText;
    private TextView notesText;
    private String selectedDate;

    public CalendarFragment() {
        super(R.layout.fragment_calendar);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        databaseHelper = new DatabaseHelper(requireContext());

        CalendarView calendarView = view.findViewById(R.id.calendar_stats);
        RecyclerView recycler = view.findViewById(R.id.recycler_calendar_notes);
        dateText = view.findViewById(R.id.text_stats_date);
        tasksText = view.findViewById(R.id.text_stats_tasks);
        pomodoroText = view.findViewById(R.id.text_stats_pomodoro);
        notesText = view.findViewById(R.id.text_stats_notes);
        Button toggleButton = view.findViewById(R.id.button_toggle_calendar);
        LinearLayout calendarContainer = view.findViewById(R.id.calendar_container);

        adapter = new NoteAdapter(new NoteAdapter.NoteActionListener() {
            @Override
            public void onEdit(Note note) {
                showEditNoteDialog(note);
            }

            @Override
            public void onDelete(Note note) {
                showDeleteNoteDialog(note);
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        selectedDate = databaseHelper.getTodayDate();
        loadForDate(selectedDate);

        calendarView.setOnDateChangeListener((view1, year, month, dayOfMonth) -> {
            LocalDate date = LocalDate.of(year, month + 1, dayOfMonth);
            selectedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            loadForDate(selectedDate);
        });

        toggleButton.setOnClickListener(v -> {
            if (calendarContainer.getVisibility() == View.VISIBLE) {
                calendarContainer.setVisibility(View.GONE);
                toggleButton.setText(R.string.show_calendar);
            } else {
                calendarContainer.setVisibility(View.VISIBLE);
                toggleButton.setText(R.string.hide_calendar);
            }
        });

        return view;
    }

    private void showEditNoteDialog(Note note) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.hint_note_title);
        titleInput.setText(note.getTitle());
        container.addView(titleInput);

        EditText contentInput = new EditText(requireContext());
        contentInput.setHint(R.string.hint_note_content);
        contentInput.setText(note.getContent());
        container.addView(contentInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_note)
                .setView(container)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();
                    if (!title.isEmpty()) {
                        databaseHelper.updateNote(note.getId(), title, content, note.getIsFavorite());
                        loadForDate(selectedDate);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteNoteDialog(Note note) {
        new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.delete_note_confirm, note.getTitle()))
                .setPositiveButton(R.string.delete_note, (dialog, which) -> {
                    databaseHelper.deleteNote(note.getId());
                    loadForDate(selectedDate);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadForDate(String date) {
        List<Note> notes = databaseHelper.getNotesByDate(date);
        int tasks = databaseHelper.getTasksByDate(date).size();
        int completed = databaseHelper.getTodayCompletedTasksCount(date);
        int pomodoro = databaseHelper.getPomodoroCountByDate(date);
        int notesCount = databaseHelper.getNotesCountByDate(date);

        adapter.setNotes(notes);
        dateText.setText(getString(R.string.stats_date, date));
        tasksText.setText(getString(R.string.stats_tasks, completed, tasks));
        pomodoroText.setText(getString(R.string.stats_pomodoro, pomodoro));
        notesText.setText(getString(R.string.stats_notes, notesCount));
    }
}
