package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.Conversation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Seller inbox adapter: lists conversations handed off to a human so the
 * seller/admin can tap in and reply within the same thread.
 */
public class ChatStaffAdapter extends RecyclerView.Adapter<ChatStaffAdapter.ViewHolder> {

    public interface Listener {
        void onConversationClick(@NonNull Conversation conversation);
    }

    private final List<Conversation> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatStaffAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Conversation> conversations) {
        items.clear();
        if (conversations != null) {
            items.addAll(conversations);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation c = items.get(position);

        String name = c.getBuyerName();
        if (name == null || name.trim().isEmpty()) {
            name = holder.itemView.getContext().getString(R.string.conversation_buyer_fallback);
        }
        holder.name.setText(name);

        String last = c.getLastMessage();
        holder.lastMessage.setText(last != null ? last : "");

        Date updated = c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getLastMessageAt();
        holder.time.setText(updated != null ? timeFormat.format(updated) : "");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConversationClick(c);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView lastMessage;
        final TextView time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.convName);
            lastMessage = itemView.findViewById(R.id.convLastMessage);
            time = itemView.findViewById(R.id.convTime);
        }
    }
}
