package com.example.healthup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.ChatStaffAdapter;
import com.example.healthup.chat.SellerChatListViewModel;
import com.example.models.Conversation;

/**
 * Seller/admin inbox screen: lists conversations handed off to a human and
 * opens the shared chat thread in seller mode when tapped.
 *
 * <p>The Firestore listener is owned by {@link SellerChatListViewModel} and
 * detached in {@link #onDestroyView()}.</p>
 */
public class ChatStaffFragment extends Fragment implements ChatStaffAdapter.Listener {

    private SellerChatListViewModel viewModel;
    private ChatStaffAdapter adapter;
    private View emptyView;

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

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ChatStaffAdapter(this);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.sellerInboxBack).setOnClickListener(v -> requireActivity().finish());

        viewModel.getConversations().observe(getViewLifecycleOwner(), conversations -> {
            adapter.submit(conversations);
            boolean empty = conversations == null || conversations.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        viewModel.start();
        viewModel.resume();
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
