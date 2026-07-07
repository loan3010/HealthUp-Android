package com.example.healthup;

import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.adapters.NotificationAdapter;
import com.example.models.NotificationItem;
import com.example.models.NotificationType;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NotificationsFragment extends Fragment {

    private enum NotificationFilter {
        ALL,
        UNREAD,
        ORDERS,
        PROMO
    }

    private static final String COLLECTION_USERS = "users";
    private static final String SUBCOLLECTION_NOTIFICATIONS = "notifications";
    private static final String TOP_LEVEL_NOTIFICATIONS = "notifications";

    private FirebaseFirestore db;
    private String userId;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvNotifications;
    private View emptyState;
    private ProgressBar progressBar;
    private TextView tvEmptyMessage;
    private TextView tvViewAll;
    private ChipGroup chipGroupFilters;

    private final List<NotificationItem> items = new ArrayList<>();
    private NotificationFilter currentFilter = NotificationFilter.ALL;
    private final Set<String> readMockIds = new HashSet<>();
    private final Set<String> deletedMockIds = new HashSet<>();
    private NotificationAdapter adapter;
    private boolean usingMockData;
    private final android.graphics.Paint swipeDeletePaint = new android.graphics.Paint();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        rvNotifications = view.findViewById(R.id.rvNotifications);
        emptyState = view.findViewById(R.id.emptyState);
        progressBar = view.findViewById(R.id.progressBar);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);
        tvViewAll = view.findViewById(R.id.tvViewAll);
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters);

        swipeRefresh.setColorSchemeColors(
                ContextCompat.getColor(requireContext(), R.color.primary),
                ContextCompat.getColor(requireContext(), R.color.green_button));
        swipeRefresh.setOnRefreshListener(this::loadNotifications);

        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(this::formatNotificationTime);
        adapter.setListener(new NotificationAdapter.NotificationListener() {
            @Override
            public void onNotificationClick(NotificationItem item) {
                markAsRead(item);
                navigateForNotification(item);
            }

            @Override
            public void onNotificationDelete(NotificationItem item) {
                deleteNotification(item);
            }
        });
        rvNotifications.setAdapter(adapter);
        setupSwipeToDelete();
        setupHeaderActions();
        setupFilterChips();

        loadNotifications();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        userId = FirebaseAuth.getInstance().getUid();
    }

    private void setupHeaderActions() {
        tvViewAll.setOnClickListener(v -> markAllAsRead());
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

    private void setupSwipeToDelete() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getItemViewType() == NotificationAdapter.VIEW_TYPE_HEADER) {
                    return 0;
                }
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                NotificationItem item = adapter.getNotificationAt(position);
                if (item != null) {
                    deleteNotification(item);
                } else {
                    adapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                View itemView = viewHolder.itemView;
                if (dX < 0) {
                    swipeDeletePaint.setColor(
                            ContextCompat.getColor(requireContext(), R.color.error));
                    c.drawRect(
                            itemView.getRight() + dX,
                            itemView.getTop(),
                            itemView.getRight(),
                            itemView.getBottom(),
                            swipeDeletePaint);
                }
            }
        });
        helper.attachToRecyclerView(rvNotifications);
    }

    private void loadNotifications() {
        if (!swipeRefresh.isRefreshing()) {
            showLoading();
        }

        userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            swipeRefresh.setRefreshing(false);
            bindMockNotifications();
            return;
        }

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) {
                        return;
                    }
                    swipeRefresh.setRefreshing(false);

                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        bindNotifications(task.getResult());
                        return;
                    }

                    loadNotificationsWithoutOrdering();
                });
    }

    private void loadNotificationsWithoutOrdering() {
        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (!snapshot.isEmpty()) {
                        bindNotifications(snapshot);
                        return;
                    }
                    loadTopLevelNotifications();
                })
                .addOnFailureListener(e -> loadTopLevelNotifications());
    }

    private void loadTopLevelNotifications() {
        db.collection(TOP_LEVEL_NOTIFICATIONS)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(this::bindNotifications)
                .addOnFailureListener(e ->
                        db.collection(TOP_LEVEL_NOTIFICATIONS)
                                .whereEqualTo("userId", userId)
                                .get()
                                .addOnSuccessListener(this::bindNotifications)
                                .addOnFailureListener(err -> {
                                    if (!isAdded()) {
                                        return;
                                    }
                                    bindMockNotifications();
                                }));
    }

    private void bindMockNotifications() {
        usingMockData = true;
        items.clear();
        for (NotificationItem sample : NotificationMockProvider.getSamples()) {
            if (deletedMockIds.contains(sample.getId())) {
                continue;
            }
            if (readMockIds.contains(sample.getId())) {
                sample.setRead(true);
            }
            items.add(sample);
        }
        refreshDisplay();
    }

    private void bindNotifications(com.google.firebase.firestore.QuerySnapshot snapshot) {
        if (!isAdded()) {
            return;
        }
        usingMockData = false;
        items.clear();
        for (QueryDocumentSnapshot doc : snapshot) {
            NotificationItem item = parseNotification(doc);
            if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                items.add(item);
            }
        }

        if (items.size() > 1) {
            Collections.sort(items, Comparator.comparing(
                    NotificationItem::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            ).reversed());
        }

        if (items.isEmpty()) {
            bindMockNotifications();
        } else {
            refreshDisplay();
        }
    }

    private NotificationItem parseNotification(DocumentSnapshot doc) {
        NotificationItem item = doc.toObject(NotificationItem.class);
        if (item == null) {
            item = new NotificationItem();
        }
        item.setId(doc.getId());

        if (item.getTitle() == null || item.getTitle().isEmpty()) {
            item.setTitle(doc.getString("title"));
        }
        if (item.getDisplayBody().isEmpty()) {
            String body = doc.getString("body");
            if (body == null) {
                body = doc.getString("message");
            }
            if (body == null) {
                body = doc.getString("content");
            }
            item.setBody(body);
        }
        if (item.getType() == null || item.getType().isEmpty()) {
            item.setType(doc.getString("type"));
        }
        if (item.getRefId() == null || item.getRefId().isEmpty()) {
            String refId = doc.getString("refId");
            if (refId == null) {
                refId = doc.getString("orderId");
            }
            item.setRefId(refId);
        }
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(doc.getTimestamp("createdAt"));
            if (item.getCreatedAt() == null && doc.getDate("createdAt") != null) {
                item.setCreatedAt(new Timestamp(doc.getDate("createdAt")));
            }
        }
        if (doc.contains("read")) {
            item.setRead(Boolean.TRUE.equals(doc.getBoolean("read")));
        }
        return item;
    }

    private void refreshDisplay() {
        List<NotificationItem> filteredItems = getFilteredItems();
        if (filteredItems.isEmpty()) {
            String message = items.isEmpty()
                    ? getString(R.string.notifications_empty)
                    : getString(R.string.notifications_empty_filter);
            showEmpty(message);
            return;
        }
        showList();
        adapter.setRows(buildGroupedRows(filteredItems));
    }

    private List<NotificationItem> getFilteredItems() {
        List<NotificationItem> filtered = new ArrayList<>();
        for (NotificationItem item : items) {
            if (matchesFilter(item)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean matchesFilter(NotificationItem item) {
        switch (currentFilter) {
            case UNREAD:
                return !item.isRead();
            case ORDERS:
                return isOrderNotification(item.getNotificationType());
            case PROMO:
                return isPromoNotification(item.getNotificationType());
            default:
                return true;
        }
    }

    private static boolean isOrderNotification(NotificationType type) {
        return type == NotificationType.ORDER_SHIPPING
                || type == NotificationType.PAYMENT
                || type == NotificationType.REVIEW_REMINDER;
    }

    private static boolean isPromoNotification(NotificationType type) {
        return type == NotificationType.PROMO
                || type == NotificationType.WISHLIST_SALE;
    }

    private List<NotificationAdapter.Row> buildGroupedRows() {
        return buildGroupedRows(getFilteredItems());
    }

    private List<NotificationAdapter.Row> buildGroupedRows(List<NotificationItem> sourceItems) {
        List<NotificationAdapter.Row> rows = new ArrayList<>();
        String lastGroup = null;
        for (NotificationItem item : sourceItems) {
            String group = getDateGroupLabel(item.getCreatedAt());
            if (!group.equals(lastGroup)) {
                rows.add(NotificationAdapter.Row.header(group));
                lastGroup = group;
            }
            rows.add(NotificationAdapter.Row.item(item));
        }
        return rows;
    }

    private String getDateGroupLabel(Timestamp createdAt) {
        if (createdAt == null) {
            return getString(R.string.notifications_group_earlier);
        }
        Calendar today = Calendar.getInstance();
        Calendar itemDay = Calendar.getInstance();
        itemDay.setTime(createdAt.toDate());
        if (isSameDay(today, itemDay)) {
            return getString(R.string.notifications_group_today);
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(yesterday, itemDay)) {
            return getString(R.string.notifications_group_yesterday);
        }
        return getString(R.string.notifications_group_earlier);
    }

    private static boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    String formatNotificationTime(NotificationItem item) {
        Timestamp createdAt = item.getCreatedAt();
        if (createdAt == null) {
            return "";
        }
        Date date = createdAt.toDate();
        long now = System.currentTimeMillis();
        CharSequence relative = DateUtils.getRelativeTimeSpanString(
                date.getTime(),
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
        return relative.toString();
    }

    private void markAsRead(NotificationItem item) {
        if (item.isRead()) {
            return;
        }
        item.setRead(true);
        adapter.setRows(buildGroupedRows());

        if (item.isMock() || usingMockData) {
            if (item.getId() != null) {
                readMockIds.add(item.getId());
            }
            return;
        }

        if (userId == null || item.getId() == null) {
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("read", true);

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)
                .document(item.getId())
                .update(update)
                .addOnFailureListener(e ->
                        db.collection(TOP_LEVEL_NOTIFICATIONS)
                                .document(item.getId())
                                .update(update));
    }

    private void markAllAsRead() {
        boolean changed = false;
        for (NotificationItem item : items) {
            if (!item.isRead()) {
                item.setRead(true);
                changed = true;
                if (item.isMock() || usingMockData) {
                    if (item.getId() != null) {
                        readMockIds.add(item.getId());
                    }
                } else if (userId != null && item.getId() != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("read", true);
                    db.collection(COLLECTION_USERS)
                            .document(userId)
                            .collection(SUBCOLLECTION_NOTIFICATIONS)
                            .document(item.getId())
                            .update(update)
                            .addOnFailureListener(e ->
                                    db.collection(TOP_LEVEL_NOTIFICATIONS)
                                            .document(item.getId())
                                            .update(update));
                }
            }
        }
        if (changed) {
            adapter.setRows(buildGroupedRows());
            Toast.makeText(requireContext(),
                    R.string.notifications_all_read_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteNotification(NotificationItem item) {
        items.remove(item);

        if (item.isMock() || usingMockData) {
            if (item.getId() != null) {
                deletedMockIds.add(item.getId());
            }
            refreshDisplay();
            Toast.makeText(requireContext(), R.string.notifications_deleted, Toast.LENGTH_SHORT).show();
            return;
        }

        if (userId != null && item.getId() != null) {
            db.collection(COLLECTION_USERS)
                    .document(userId)
                    .collection(SUBCOLLECTION_NOTIFICATIONS)
                    .document(item.getId())
                    .delete()
                    .addOnFailureListener(e ->
                            db.collection(TOP_LEVEL_NOTIFICATIONS)
                                    .document(item.getId())
                                    .delete());
        }
        refreshDisplay();
        Toast.makeText(requireContext(), R.string.notifications_deleted, Toast.LENGTH_SHORT).show();
    }

    private void navigateForNotification(NotificationItem item) {
        NotificationType type = item.getNotificationType();
        String refId = item.getRefId();

        switch (type) {
            case ORDER_SHIPPING:
            case PAYMENT:
                if (refId != null && !refId.isEmpty()) {
                    Intent orderIntent = new Intent(requireContext(), OrderDetailActivity.class);
                    orderIntent.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, refId);
                    startActivity(orderIntent);
                } else {
                    navigateToOrderTab("shipping_tab");
                }
                break;
            case PROMO:
                ProductListFragment promoFragment = new ProductListFragment();
                Bundle promoArgs = new Bundle();
                promoArgs.putString("category", "Granola");
                promoFragment.setArguments(promoArgs);
                loadMainFragment(promoFragment);
                break;
            case WISHLIST_SALE:
                loadMainFragment(new WishlistFragment());
                break;
            case REVIEW_REMINDER:
                if (refId != null && !refId.isEmpty()) {
                    Intent reviewOrderIntent = new Intent(requireContext(), OrderDetailActivity.class);
                    reviewOrderIntent.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, refId);
                    startActivity(reviewOrderIntent);
                } else {
                    navigateToOrderTab("delivered_tab");
                }
                break;
            default:
                break;
        }
    }

    private void navigateToOrderTab(String tabTarget) {
        OrderHistoryFragment fragment = new OrderHistoryFragment();
        Bundle args = new Bundle();
        int tabIndex = 0;
        if ("shipping_tab".equals(tabTarget)) {
            tabIndex = 3;
        } else if ("delivered_tab".equals(tabTarget)) {
            tabIndex = 4;
        }
        args.putInt("initial_tab", tabIndex);
        fragment.setArguments(args);
        loadMainFragment(fragment);
    }

    private void loadMainFragment(Fragment fragment) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
    }

    private void showList() {
        progressBar.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    private void showEmpty(String message) {
        if (!isAdded()) {
            return;
        }
        progressBar.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        tvEmptyMessage.setText(message);
    }
}
