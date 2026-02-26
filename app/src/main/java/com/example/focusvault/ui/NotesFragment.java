package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.focusvault.R;
import com.example.focusvault.data.DatabaseHelper;
import com.example.focusvault.model.Note;
import com.example.focusvault.ui.adapter.NoteAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class NotesFragment extends Fragment {

    private DatabaseHelper databaseHelper;
    private NoteAdapter noteAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        databaseHelper = new DatabaseHelper(requireContext());
        RecyclerView recyclerView = view.findViewById(R.id.recycler_notes);
        FloatingActionButton fab = view.findViewById(R.id.fab_add_note);

        noteAdapter = new NoteAdapter(new NoteAdapter.NoteActionListener() {
            @Override
            public void onEdit(Note note) {
                showNoteDialog(note);
            }

            @Override
            public void onDelete(Note note) {
                databaseHelper.deleteNote(note.getId());
                loadNotes();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(noteAdapter);

        fab.setOnClickListener(v -> showNoteDialog(null));
        loadNotes();

        return view;
    }

    private void showNoteDialog(@Nullable Note note) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        EditText titleInput = new EditText(requireContext());
        titleInput.setHint(R.string.hint_note_title);
        if (note != null) {
            titleInput.setText(note.getTitle());
        }
        container.addView(titleInput);

        EditText contentInput = new EditText(requireContext());
        contentInput.setHint(R.string.hint_note_content);
        if (note != null) {
            contentInput.setText(note.getContent());
        }
        container.addView(contentInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(note == null ? R.string.add_note : R.string.edit_note)
                .setView(container)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String content = contentInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        return;
                    }
                    if (note == null) {
                        databaseHelper.insertNote(title, content);
                    } else {
                        databaseHelper.updateNote(note.getId(), title, content, note.getIsFavorite());
                    }
                    loadNotes();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadNotes() {
        noteAdapter.setNotes(databaseHelper.getAllNotes());
    }
}
