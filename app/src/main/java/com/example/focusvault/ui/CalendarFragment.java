package com.example.focusvault.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
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

        adapter = new NoteAdapter(new NoteAdapter.NoteActionListener() {
            @Override
            public void onEdit(Note note) {
            }

            @Override
            public void onDelete(Note note) {
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

        return view;
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
