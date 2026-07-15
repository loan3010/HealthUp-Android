package com.example.healthup.admin;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.healthup.BaseAppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminNotificationsActivity extends BaseAppCompatActivity {

    private enum NotificationFilter {
        ALL,
        UNREAD,
        ORDERS,
        PROMO
    }

    private final List<AdminNotification> allItems = new ArrayList<>();
    private final List<AdminNotification> visibleItems = new ArrayList<>();
    private Adapter adapter;
    private TextView tvEmpty;
    private TextView tvMarkAllRead;
    private ChipGroup chipGroupFilters;
    private ListenerRegistration registration;
    private NotificationFilter currentFilter = NotificationFilter.ALL;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_notifications);

        MaterialToolbar toolbar = findViewById(R.id.toolbarAdminNotifications);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tvEmptyAdminNotifications);
        tvMarkAllRead = findViewById(R.id.tvMarkAllRead);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        RecyclerView recyclerView = findViewById(R.id.rvAdminNotifications);
        adapter = new Adapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        tvMarkAllRead.setOnClickListener(v -> markAllAsRead());
        setupFilterChips();
    }

    private void setupFilterChips() {
        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipFilterUnread) {
                currentFilter = NotificationFilter.UNREAD;
            } else if (checkedId == R.id.chipFilterOrders) {
                currentFilter = NotificationFilter.ORDERS;
            } else if (checkedId == R.id.chipFilterPromo) {
                currentFilter = NotificationFilter.PROMO;
            } else {
                currentFilter = NotificationFilter.ALL;
            }
            refreshDisplay();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registration = FirebaseFirestore.getInstance()
                .collection("admin_notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        // Fallback when composite index / orderBy is unavailable
                        FirebaseFirestore.getInstance()
                                .collection("admin_notifications")
                                .limit(100)
                                .get()
                                .addOnSuccessListener(fallbackSnap -> {
                                    allItems.clear();
                                    for (DocumentSnapshot doc : fallbackSnap.getDocuments()) {
                                        AdminNotification item = AdminNotification.from(doc);
                                        if (item != null) {
                                            allItems.add(item);
                                        }
                                    }
                                    allItems.sort((a, b) -> {
                                        long ta = a.createdAt != null ? a.createdAt.getTime() : 0L;
                                        long tb = b.createdAt != null ? b.createdAt.getTime() : 0L;
                                        return Long.compare(tb, ta);
                                    });
                                    refreshDisplay();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
                        return;
                    }
                    allItems.clear();
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            AdminNotification item = AdminNotification.from(doc);
                            if (item != null) {
                                allItems.add(item);
                            }
                        }
                    }
                    refreshDisplay();
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void refreshDisplay() {
        visibleItems.clear();
        for (AdminNotification item : allItems) {
            if (matchesFilter(item)) {
                visibleItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        if (visibleItems.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(allItems.isEmpty()
                    ? getString(R.string.admin_notifications_empty)
                    : getString(R.string.notifications_empty_filter));
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private boolean matchesFilter(AdminNotification item) {
        switch (currentFilter) {
            case UNREAD:
                return !item.read;
            case ORDERS:
                return isOrderNotification(item);
            case PROMO:
                return isPromoNotification(item);
            default:
                return true;
        }
    }

    private static boolean isOrderNotification(AdminNotification item) {
        String type = item.type != null ? item.type.toUpperCase(Locale.US) : "";
        if (type.contains("ORDER")
                || "PAYMENT".equals(type)
                || "REVIEW_REMINDER".equals(type)
                || "ORDER_SHIPPING".equals(type)
                || "ORDER_CANCELLED".equals(type)) {
            return true;
        }
        return !TextUtils.isEmpty(item.orderId) && !isPromoNotification(item);
    }

    private static boolean isPromoNotification(AdminNotification item) {
        String type = item.type != null ? item.type.toUpperCase(Locale.US) : "";
        return type.contains("PROMO") || "WISHLIST_SALE".equals(type);
    }

    private void markAsRead(@NonNull AdminNotification item) {
        if (item.read || TextUtils.isEmpty(item.id)) {
            return;
        }
        item.read = true;
        adapter.notifyDataSetChanged();
        Map<String, Object> update = new HashMap<>();
        update.put("read", true);
        FirebaseFirestore.getInstance()
                .collection("admin_notifications")
                .document(item.id)
                .update(update);
    }

    private void markAllAsRead() {
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        boolean changed = false;
        for (AdminNotification item : allItems) {
            if (!item.read && !TextUtils.isEmpty(item.id)) {
                item.read = true;
                changed = true;
                batch.update(
                        FirebaseFirestore.getInstance()
                                .collection("admin_notifications")
                                .document(item.id),
                        "read", true);
            }
        }
        if (!changed) {
            Toast.makeText(this, R.string.notifications_all_read_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        batch.commit()
                .addOnSuccessListener(v -> {
                    refreshDisplay();
                    Toast.makeText(this, R.string.notifications_all_read_toast, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void openNotification(@NonNull AdminNotification item) {
        markAsRead(item);
        if (isReturnNotification(item)) {
            String orderId = !TextUtils.isEmpty(item.orderId) ? item.orderId : item.returnId;
            if (TextUtils.isEmpty(orderId)) {
                Toast.makeText(this, R.string.admin_notifications_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, com.example.healthup.ReturnRefundHistoryDetailActivity.class);
            intent.putExtra("extra_order_id", orderId);
            if (!TextUtils.isEmpty(item.returnId)) {
                intent.putExtra("returnId", item.returnId);
            }
            startActivity(intent);
            return;
        }
        if (TextUtils.isEmpty(item.orderId)) return;
        Intent intent = new Intent(this, AdminOrderDetailActivity.class);
        intent.putExtra(AdminOrderDetailActivity.EXTRA_ORDER_ID, item.orderId);
        startActivity(intent);
    }

    private static boolean isReturnNotification(@NonNull AdminNotification item) {
        String type = item.type != null ? item.type.toUpperCase(Locale.US) : "";
        return type.contains("RETURN")
                || !TextUtils.isEmpty(item.returnId);
    }

    private void openOrder(@NonNull AdminNotification item) {
        openNotification(item);
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_notification, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            AdminNotification item = visibleItems.get(position);
            boolean isUnread = !item.read;
            holder.tvTitle.setText(item.title);
            holder.tvBody.setText(item.body);
            holder.tvTime.setText(item.createdAt != null ? dateFormat.format(item.createdAt) : "");
            holder.tvTitle.setTypeface(null, isUnread ? Typeface.BOLD : Typeface.NORMAL);
            holder.tvTitle.setTextColor(ContextCompat.getColor(
                    AdminNotificationsActivity.this,
                    isUnread ? R.color.text_dark : R.color.text_secondary));
            holder.tvBody.setAlpha(isUnread ? 1f : 0.75f);
            holder.layoutContent.setBackgroundResource(isUnread
                    ? R.drawable.bg_notification_item_unread
                    : R.drawable.bg_notification_item_read);
            holder.unreadDot.setVisibility(isUnread ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> openNotification(item));
        }

        @Override
        public int getItemCount() {
            return visibleItems.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final View layoutContent;
            final TextView tvTitle;
            final TextView tvBody;
            final TextView tvTime;
            final View unreadDot;

            Holder(@NonNull View itemView) {
                super(itemView);
                layoutContent = itemView.findViewById(R.id.layoutAdminNotifContent);
                tvTitle = itemView.findViewById(R.id.tvAdminNotifTitle);
                tvBody = itemView.findViewById(R.id.tvAdminNotifBody);
                tvTime = itemView.findViewById(R.id.tvAdminNotifTime);
                unreadDot = itemView.findViewById(R.id.viewAdminUnreadDot);
            }
        }
    }

    private static final class AdminNotification {
        String id;
        String orderId;
        String returnId;
        String refId;
        String title;
        String body;
        String type;
        boolean read;
        Date createdAt;

        @Nullable
        static AdminNotification from(@NonNull DocumentSnapshot doc) {
            AdminNotification item = new AdminNotification();
            item.id = doc.getId();
            item.orderId = doc.getString("orderId");
            item.returnId = doc.getString("returnId");
            if (TextUtils.isEmpty(item.returnId)) {
                item.returnId = doc.getString("returnRequestId");
            }
            item.refId = doc.getString("refId");
            if (TextUtils.isEmpty(item.orderId) && !TextUtils.isEmpty(item.refId)) {
                item.orderId = item.refId;
            }
            item.title = doc.getString("title");
            item.body = doc.getString("body");
            if (TextUtils.isEmpty(item.body)) {
                item.body = doc.getString("message");
            }
            item.type = doc.getString("type");
            Boolean readFlag = doc.getBoolean("read");
            item.read = readFlag != null && readFlag;
            if (TextUtils.isEmpty(item.title)) {
                item.title = "Thông báo";
            }
            if (TextUtils.isEmpty(item.body)) {
                item.body = "";
            }
            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts != null) {
                item.createdAt = ts.toDate();
            }
            return item;
        }
    }
}
