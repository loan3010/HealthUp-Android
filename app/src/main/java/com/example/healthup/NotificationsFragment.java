package com.example.healthup;

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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.NotificationAdapter;
import com.example.models.NotificationItem;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private static final String COLLECTION_USERS = "users";
    private static final String SUBCOLLECTION_NOTIFICATIONS = "notifications";
    private static final String TOP_LEVEL_NOTIFICATIONS = "notifications";

    private FirebaseFirestore db;
    private String userId;

    private RecyclerView rvNotifications;
    private View emptyState;
    private ProgressBar progressBar;
    private TextView tvEmptyMessage;

    private final List<NotificationItem> items = new ArrayList<>();
    private NotificationAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        rvNotifications = view.findViewById(R.id.rvNotifications);
        emptyState = view.findViewById(R.id.emptyState);
        progressBar = view.findViewById(R.id.progressBar);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);

        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(items, this::formatNotificationTime);
        rvNotifications.setAdapter(adapter);

        loadNotifications();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        userId = FirebaseAuth.getInstance().getUid();
        loadNotifications();
    }

    private void loadNotifications() {
        items.clear();
        adapter.notifyDataSetChanged();
        showLoading();

        if (userId == null) {
            showEmpty(getString(R.string.notifications_login_required));
            return;
        }

        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        bindNotifications(snapshot);
                        return;
                    }
                    loadTopLevelNotifications();
                })
                .addOnFailureListener(e -> loadNotificationsWithoutOrdering());
    }

    private void loadNotificationsWithoutOrdering() {
        db.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_NOTIFICATIONS)
                .get()
                .addOnSuccessListener(snapshot -> {
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
                                    showEmpty(getString(R.string.notifications_empty));
                                    android.content.Context context = getContext();
                                    if (context != null) {
                                        Toast.makeText(context,
                                                getString(R.string.notifications_load_error),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }));
    }

    private void bindNotifications(com.google.firebase.firestore.QuerySnapshot snapshot) {
        if (!isAdded()) {
            return;
        }
        items.clear();
        for (QueryDocumentSnapshot doc : snapshot) {
            NotificationItem item = parseNotification(doc);
            if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                items.add(item);
            }
        }

        if (items.size() > 1) {
            Collections.sort(items, Comparator.comparing(
                    (NotificationItem item) -> item.getCreatedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())
            ).reversed());
        }

        if (items.isEmpty()) {
            showEmpty(getString(R.string.notifications_empty));
        } else {
            showList();
            adapter.notifyDataSetChanged();
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
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(doc.getTimestamp("createdAt"));
            if (item.getCreatedAt() == null && doc.getDate("createdAt") != null) {
                item.setCreatedAt(new Timestamp(doc.getDate("createdAt")));
            }
        }
        return item;
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
