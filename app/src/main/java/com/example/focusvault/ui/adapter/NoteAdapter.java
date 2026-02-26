package com.example.focusvault.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.focusvault.R;
import com.example.focusvault.model.Note;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface NoteActionListener {
        void onEdit(Note note);

        void onDelete(Note note);
    }

    private final List<Note> notes = new ArrayList<>();
    private final NoteActionListener listener;

    public NoteAdapter(NoteActionListener listener) {
        this.listener = listener;
    }

    public void setNotes(List<Note> noteList) {
        notes.clear();
        notes.addAll(noteList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.title.setText(note.getTitle());
        holder.content.setText(note.getContent());
        holder.date.setText(formatDate(note.getCreatedAt()));
        holder.date.setText(note.getCreatedAt());
        holder.editButton.setOnClickListener(v -> listener.onEdit(note));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(note));
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    private String formatDate(String source) {
        try {
            LocalDateTime dt = LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        } catch (Exception ignored) {
            return source;
        }
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView content;
        TextView date;
        ImageButton editButton;
        ImageButton deleteButton;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_note_title);
            content = itemView.findViewById(R.id.text_note_content);
            date = itemView.findViewById(R.id.text_note_date);
            editButton = itemView.findViewById(R.id.button_edit_note);
            deleteButton = itemView.findViewById(R.id.button_delete_note);
        }
    }
}
