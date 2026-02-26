package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.focusvault.model.Note;
import com.example.focusvault.ui.adapter.NoteAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotesFragment extends Fragment {

    private static final String PREFS = "focusvault_prefs";
    private static final String KEY_NOTES_HELP_HIDDEN = "notes_help_hidden";

    private DatabaseHelper databaseHelper;
    private NoteAdapter noteAdapter;
    private TextView emptyText;
    private TextInputEditText searchInput;
    private final List<Note> allNotes = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notes, container, false);

        databaseHelper = new DatabaseHelper(requireContext());
        RecyclerView recyclerView = view.findViewById(R.id.recycler_notes);
        FloatingActionButton fab = view.findViewById(R.id.fab_add_note);
        searchInput = view.findViewById(R.id.input_search_notes);
        emptyText = view.findViewById(R.id.text_notes_empty);
        View helpCard = view.findViewById(R.id.card_notes_help);
        TextView hideHelpButton = view.findViewById(R.id.button_hide_notes_help);

        setupHelpCard(helpCard, hideHelpButton);

        noteAdapter = new NoteAdapter(new NoteAdapter.NoteActionListener() {
            @Override
            public void onEdit(Note note) {
                showNoteDialog(note);
            }

            @Override
            public void onDelete(Note note) {
                new AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.delete_note_confirm, note.getTitle()))
                        .setPositiveButton(R.string.delete_note, (dialog, which) -> {
                            databaseHelper.deleteNote(note.getId());
                            loadNotes();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(noteAdapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterNotes(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        fab.setOnClickListener(v -> showNoteDialog(null));
        loadNotes();

        return view;
    }

    private void setupHelpCard(View helpCard, TextView hideHelpButton) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean hidden = prefs.getBoolean(KEY_NOTES_HELP_HIDDEN, false);
        helpCard.setVisibility(hidden ? View.GONE : View.VISIBLE);

        hideHelpButton.setOnClickListener(v -> {
            helpCard.setVisibility(View.GONE);
            prefs.edit().putBoolean(KEY_NOTES_HELP_HIDDEN, true).apply();
        });
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
        allNotes.clear();
        allNotes.addAll(databaseHelper.getAllNotes());
        filterNotes(searchInput == null || searchInput.getText() == null ? "" : searchInput.getText().toString());
    }

    private void filterNotes(String query) {
        String q = query.toLowerCase(Locale.getDefault()).trim();
        List<Note> filtered = new ArrayList<>();
        for (Note note : allNotes) {
            String title = note.getTitle() == null ? "" : note.getTitle().toLowerCase(Locale.getDefault());
            String content = note.getContent() == null ? "" : note.getContent().toLowerCase(Locale.getDefault());
            if (q.isEmpty() || title.contains(q) || content.contains(q)) {
                filtered.add(note);
            }
        }

        noteAdapter.setNotes(filtered);
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
