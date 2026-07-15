package com.example.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

        String username = c.getBuyerUsername();
        String displayName;
        if (!TextUtils.isEmpty(username)) {
            displayName = username.startsWith("@") ? username : "@" + username;
        } else {
            displayName = c.getBuyerName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = holder.itemView.getContext().getString(R.string.conversation_buyer_fallback);
            }
        }
        holder.name.setText(displayName);

        // Messenger-style: preview is always the last message (not phone).
        String preview = c.getLastMessage();
        if (TextUtils.isEmpty(preview)) {
            preview = !TextUtils.isEmpty(c.getBuyerPhone()) ? c.getBuyerPhone() : "";
        }
        holder.lastMessage.setText(preview);

        Date updated = c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getLastMessageAt();
        holder.time.setText(updated != null ? timeFormat.format(updated) : "");

        boolean unread = c.isStaffUnread();
        if (holder.unreadDot != null) {
            holder.unreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);
        }
        holder.name.setTypeface(null, unread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        holder.lastMessage.setTypeface(null, unread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        int primaryColor = androidx.core.content.ContextCompat.getColor(
                holder.itemView.getContext(), R.color.text_primary);
        int secondaryColor = androidx.core.content.ContextCompat.getColor(
                holder.itemView.getContext(), R.color.text_secondary);
        holder.name.setTextColor(primaryColor);
        holder.lastMessage.setTextColor(unread ? primaryColor : secondaryColor);
        if (holder.time != null) {
            holder.time.setTypeface(null, unread ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

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
        @Nullable
        final View unreadDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.convName);
            lastMessage = itemView.findViewById(R.id.convLastMessage);
            time = itemView.findViewById(R.id.convTime);
            unreadDot = itemView.findViewById(R.id.convUnreadDot);
        }
    }
}
