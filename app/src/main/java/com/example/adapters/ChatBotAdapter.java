package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.healthup.chat.SuggestionProvider;
import com.example.models.ChatMessage;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable single-thread chat adapter with multiple view types:
 * user (right green), bot/seller (left white), system (centered grey),
 * order card (tappable) and the suggested-questions card.
 */
public class ChatBotAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_BOT = 2;
    private static final int TYPE_SYSTEM = 3;
    private static final int TYPE_ORDER_CARD = 4;
    private static final int TYPE_SUGGESTION = 5;

    public interface Listener {
        void onOrderCardClick(@NonNull ChatMessage message);

        void onSuggestionQuestionClick(@NonNull String question);

        void onSuggestionChange();
    }

    private final List<ChatMessage> items = new ArrayList<>();
    private final Listener listener;
    private List<SuggestionProvider.Item> suggestionItems = SuggestionProvider.getSet(0);

    public ChatBotAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setSuggestionItems(@Nullable List<SuggestionProvider.Item> items) {
        suggestionItems = items != null ? items : SuggestionProvider.getSet(0);
        for (int i = 0; i < this.items.size(); i++) {
            if (ChatMessage.TYPE_SUGGESTION.equals(this.items.get(i).getType())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void submit(List<ChatMessage> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = items.get(position);
        if (ChatMessage.TYPE_SUGGESTION.equals(m.getType())) {
            return TYPE_SUGGESTION;
        }
        if (ChatMessage.TYPE_ORDER_CARD.equals(m.getType())) {
            return TYPE_ORDER_CARD;
        }
        if (ChatMessage.TYPE_SYSTEM.equals(m.getType())
                || ChatMessage.SENDER_SYSTEM.equals(m.getSenderType())) {
            return TYPE_SYSTEM;
        }
        if (ChatMessage.SENDER_USER.equals(m.getSenderType())) {
            return TYPE_USER;
        }
        return TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_USER:
                return new UserVH(inflater.inflate(R.layout.item_chat_message_user, parent, false));
            case TYPE_SYSTEM:
                return new SystemVH(inflater.inflate(R.layout.item_chat_message_system, parent, false));
            case TYPE_ORDER_CARD:
                return new OrderCardVH(inflater.inflate(R.layout.item_chat_order_card, parent, false));
            case TYPE_SUGGESTION:
                return new SuggestionVH(inflater.inflate(R.layout.item_chat_suggestion, parent, false));
            case TYPE_BOT:
            default:
                return new BotVH(inflater.inflate(R.layout.item_chat_message, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = items.get(position);
        if (holder instanceof UserVH) {
            ((UserVH) holder).text.setText(m.getText());
        } else if (holder instanceof SystemVH) {
            ((SystemVH) holder).text.setText(m.getText());
        } else if (holder instanceof BotVH) {
            ((BotVH) holder).bind(m);
        } else if (holder instanceof OrderCardVH) {
            ((OrderCardVH) holder).bind(m, listener);
        } else if (holder instanceof SuggestionVH) {
            ((SuggestionVH) holder).bind(suggestionItems, listener);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ---- ViewHolders -----------------------------------------------------

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView text;

        UserVH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chatUserText);
        }
    }

    static class SystemVH extends RecyclerView.ViewHolder {
        final TextView text;

        SystemVH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chatSystemText);
        }
    }

    static class BotVH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView text;

        BotVH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.chatSenderName);
            text = v.findViewById(R.id.chatBotText);
        }

        void bind(ChatMessage m) {
            text.setText(m.getText());
            if (ChatMessage.SENDER_SELLER.equals(m.getSenderType())) {
                name.setVisibility(View.VISIBLE);
                name.setText(m.getSenderName() != null ? m.getSenderName() : "Người bán");
            } else {
                name.setVisibility(View.GONE);
            }
        }
    }

    static class OrderCardVH extends RecyclerView.ViewHolder {
        final View root;
        final TextView code;
        final TextView status;
        final TextView items;
        final TextView total;

        OrderCardVH(@NonNull View v) {
            super(v);
            root = v.findViewById(R.id.orderCardRoot);
            code = v.findViewById(R.id.orderCardCode);
            status = v.findViewById(R.id.orderCardStatus);
            items = v.findViewById(R.id.orderCardItems);
            total = v.findViewById(R.id.orderCardTotal);
        }

        void bind(ChatMessage m, Listener listener) {
            String orderCode = m.getOrderCode() != null ? m.getOrderCode() : "";
            code.setText("Đơn hàng #" + orderCode);
            status.setText(statusLabel(status, m.getOrderStatus()));
            items.setText(itemView.getContext()
                    .getString(R.string.chat_order_card_items, m.getOrderItemCount()));
            total.setText("Tổng: " + formatCurrency(m.getOrderTotal()));
            root.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOrderCardClick(m);
                }
            });
        }

        private String statusLabel(View v, String status) {
            int resId;
            if (status == null) {
                resId = R.string.chat_order_status_unknown;
            } else {
                switch (status) {
                    case "pending":
                        resId = R.string.chat_order_status_pending;
                        break;
                    case "confirmed":
                        resId = R.string.chat_order_status_confirmed;
                        break;
                    case "shipping":
                        resId = R.string.chat_order_status_shipping;
                        break;
                    case "delivered":
                        resId = R.string.chat_order_status_delivered;
                        break;
                    case "cancelled":
                        resId = R.string.chat_order_status_cancelled;
                        break;
                    default:
                        resId = R.string.chat_order_status_unknown;
                        break;
                }
            }
            return v.getContext().getString(resId);
        }

        private String formatCurrency(double value) {
            String formatted = new DecimalFormat("#,###").format(value);
            // Vietnamese uses "." as the thousands separator.
            return formatted.replace(',', '.') + "đ";
        }
    }

    static class SuggestionVH extends RecyclerView.ViewHolder {
        private final SuggestionRow[] rows = new SuggestionRow[4];
        final TextView change;

        SuggestionVH(@NonNull View v) {
            super(v);
            rows[0] = new SuggestionRow(v.findViewById(R.id.suggestionRow1));
            rows[1] = new SuggestionRow(v.findViewById(R.id.suggestionRow2));
            rows[2] = new SuggestionRow(v.findViewById(R.id.suggestionRow3));
            rows[3] = new SuggestionRow(v.findViewById(R.id.suggestionRow4));
            change = v.findViewById(R.id.suggestionChange);
        }

        void bind(@NonNull List<SuggestionProvider.Item> items, Listener listener) {
            for (int i = 0; i < rows.length; i++) {
                if (i < items.size()) {
                    rows[i].bind(items.get(i), listener);
                    rows[i].root.setVisibility(View.VISIBLE);
                } else {
                    rows[i].root.setVisibility(View.GONE);
                }
            }
            change.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSuggestionChange();
                }
            });
        }

        private static class SuggestionRow {
            final View root;
            final ImageView icon;
            final TextView text;

            SuggestionRow(View root) {
                this.root = root;
                this.icon = root.findViewById(R.id.suggestionRowIcon);
                this.text = root.findViewById(R.id.suggestionRowText);
            }

            void bind(@NonNull SuggestionProvider.Item item, Listener listener) {
                icon.setImageResource(item.iconRes);
                text.setText(item.text);
                root.setClickable(true);
                root.setFocusable(true);
                root.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onSuggestionQuestionClick(item.text);
                    }
                });
            }
        }
    }
}
