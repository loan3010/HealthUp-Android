package com.example.adapters;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.example.models.NotificationItem;
import com.example.models.NotificationType;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ITEM = 1;

    public interface TimeFormatter {
        String format(NotificationItem item);
    }

    public interface NotificationListener {
        void onNotificationClick(NotificationItem item);

        void onNotificationDelete(NotificationItem item);
    }

    public static final class Row {
        public static final int KIND_HEADER = 0;
        public static final int KIND_ITEM = 1;

        public final int kind;
        public final String headerLabel;
        public final NotificationItem item;

        private Row(int kind, String headerLabel, NotificationItem item) {
            this.kind = kind;
            this.headerLabel = headerLabel;
            this.item = item;
        }

        public static Row header(String label) {
            return new Row(KIND_HEADER, label, null);
        }

        public static Row item(NotificationItem notificationItem) {
            return new Row(KIND_ITEM, null, notificationItem);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final TimeFormatter timeFormatter;
    private NotificationListener listener;

    public NotificationAdapter(TimeFormatter timeFormatter) {
        this.timeFormatter = timeFormatter;
    }

    public void setListener(NotificationListener listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    public NotificationItem getNotificationAt(int adapterPosition) {
        if (adapterPosition < 0 || adapterPosition >= rows.size()) {
            return null;
        }
        Row row = rows.get(adapterPosition);
        return row.kind == Row.KIND_ITEM ? row.item : null;
    }

    public void removeNotification(NotificationItem item) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row row = rows.get(i);
            if (row.kind == Row.KIND_ITEM && row.item == item) {
                rows.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).kind == Row.KIND_HEADER ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_notification_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_notification, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(row.headerLabel);
            return;
        }
        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        NotificationItem item = row.item;
        itemHolder.bind(item, timeFormatter);
        itemHolder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvSectionHeader);
        }

        void bind(String label) {
            tvHeader.setText(label);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        private final View layoutContent;
        private final ImageView ivIcon;
        private final TextView tvTitle;
        private final TextView tvBody;
        private final TextView tvTime;
        private final View unreadDot;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutContent = itemView.findViewById(R.id.layoutNotificationContent);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvBody = itemView.findViewById(R.id.tvNotificationBody);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            unreadDot = itemView.findViewById(R.id.viewUnreadDot);
        }

        void bind(NotificationItem item, TimeFormatter timeFormatter) {
            tvTitle.setText(item.getTitle());
            tvBody.setText(item.getDisplayBody());
            tvTime.setText(timeFormatter != null ? timeFormatter.format(item) : "");

            ivIcon.setImageResource(iconForType(item.getNotificationType()));

            boolean isUnread = !item.isRead();
            unreadDot.setVisibility(isUnread ? View.VISIBLE : View.GONE);
            layoutContent.setBackgroundResource(isUnread
                    ? R.drawable.bg_notification_item_unread
                    : R.drawable.bg_notification_item_read);
            tvTitle.setTypeface(null, isUnread ? Typeface.BOLD : Typeface.NORMAL);
            tvBody.setAlpha(isUnread ? 1f : 0.75f);
        }

        private static int iconForType(NotificationType type) {
            if (type == null) {
                return R.drawable.ic_notification_bell;
            }
            switch (type) {
                case ORDER_SHIPPING:
                case ORDER_CONFIRMED:
                case ORDER_DELIVERY_CONFIRMED:
                case ORDER_DELIVERY_FAILED:
                case ORDER_REDELIVERY:
                case ORDER_DELIVERED:
                case ORDER_CANCELLED:
                case ORDER_RETURN_REQUESTED:
                case ORDER_RETURN_APPROVED:
                case ORDER_RETURN_REJECTED:
                case ORDER_UPDATE:
                    return R.drawable.ic_order_shipping;
                case PROMO:
                    return R.drawable.ic_gift;
                case PAYMENT:
                    return R.drawable.ic_payment_wallet;
                case WISHLIST_SALE:
                    return R.drawable.ic_heart_filled;
                case REVIEW_REMINDER:
                    return R.drawable.ic_star;
                default:
                    return R.drawable.ic_notification_bell;
            }
        }
    }
}
