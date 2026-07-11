package com.example.healthup;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.ChatStaffAdapter;
import com.example.healthup.chat.SellerChatListViewModel;
import com.example.models.Conversation;
import com.google.android.material.tabs.TabLayout;

/**
 * Seller/admin inbox: active vs closed session tabs, search and unread badges.
 */
public class ChatStaffFragment extends Fragment implements ChatStaffAdapter.Listener {

    private SellerChatListViewModel viewModel;
    private ChatStaffAdapter adapter;
    private View emptyView;
    private View loadingView;
    private TextView emptyViewText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_staff, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SellerChatListViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.sellerInboxRecyclerView);
        emptyView = view.findViewById(R.id.sellerInboxEmpty);
        loadingView = view.findViewById(R.id.sellerInboxLoading);
        if (emptyView instanceof TextView) {
            emptyViewText = (TextView) emptyView;
        }

        TabLayout tabs = view.findViewById(R.id.sellerInboxTabs);
        tabs.addTab(tabs.newTab().setText(R.string.seller_inbox_tab_active));
        tabs.addTab(tabs.newTab().setText(R.string.seller_inbox_tab_closed));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewModel.setTab(tab.getPosition() == 1
                        ? SellerChatListViewModel.InboxTab.CLOSED
                        : SellerChatListViewModel.InboxTab.ACTIVE);
                updateEmptyCopy();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        EditText search = view.findViewById(R.id.sellerInboxSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatStaffAdapter(this);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.sellerInboxBack).setOnClickListener(v -> requireActivity().finish());

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loadingView != null) {
                loadingView.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getConversations().observe(getViewLifecycleOwner(), conversations -> {
            adapter.submit(conversations);
            boolean empty = conversations == null || conversations.isEmpty();
            if (emptyView != null && !Boolean.TRUE.equals(viewModel.getLoading().getValue())) {
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getCurrentTab().observe(getViewLifecycleOwner(), tab -> updateEmptyCopy());

        viewModel.getLoadError().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getAccessDenied().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                requireActivity().finish();
            }
        });

        viewModel.start();
        viewModel.resume();
    }

    private void updateEmptyCopy() {
        if (emptyViewText == null) {
            return;
        }
        SellerChatListViewModel.InboxTab tab = viewModel.getCurrentTab().getValue();
        emptyViewText.setText(tab == SellerChatListViewModel.InboxTab.CLOSED
                ? R.string.seller_inbox_empty_closed
                : R.string.seller_inbox_empty);
    }

    @Override
    public void onConversationClick(@NonNull Conversation conversation) {
        startActivity(ChatActivity.sellerIntent(
                requireContext(), conversation.getId(), conversation.getBuyerId()));
    }

    @Override
    public void onDestroyView() {
        if (viewModel != null) {
            viewModel.detach();
        }
        super.onDestroyView();
    }
}
