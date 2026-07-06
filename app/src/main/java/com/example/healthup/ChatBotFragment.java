package com.example.healthup;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapters.ChatBotAdapter;
import com.example.healthup.chat.ChatViewModel;
import com.example.models.ChatMessage;

/**
 * The single chat screen (Shopee-style single thread). Starts with the local
 * bot; the buyer can hand off to a human seller in the SAME thread. The same
 * screen is reused by a seller (via {@link #ARG_SELLER_MODE}) to reply.
 *
 * <p>Firestore listeners are owned by {@link ChatViewModel} and detached in
 * {@link #onDestroyView()} to avoid leaks.</p>
 */
public class ChatBotFragment extends Fragment implements ChatBotAdapter.Listener {

    public static final String ARG_SELLER_MODE = "arg_seller_mode";
    public static final String ARG_CONVERSATION_ID = "arg_conversation_id";
    public static final String ARG_BUYER_ID = "arg_buyer_id";

    private ChatViewModel viewModel;
    private ChatBotAdapter adapter;

    private RecyclerView recyclerView;
    private EditText input;
    private TextView title;
    private TextView subtitle;
    private View quickChipsRow;

    public static ChatBotFragment newBuyerInstance() {
        return new ChatBotFragment();
    }

    public static ChatBotFragment newBuyerInstance(@Nullable Bundle extras) {
        ChatBotFragment fragment = new ChatBotFragment();
        if (extras != null) {
            fragment.setArguments(extras);
        }
        return fragment;
    }

    public static ChatBotFragment newSellerInstance(String conversationId, String buyerId) {
        ChatBotFragment fragment = new ChatBotFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SELLER_MODE, true);
        args.putString(ARG_CONVERSATION_ID, conversationId);
        args.putString(ARG_BUYER_ID, buyerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_bot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        recyclerView = view.findViewById(R.id.chatRecyclerView);
        input = view.findViewById(R.id.chatInput);
        title = view.findViewById(R.id.chatTitle);
        subtitle = view.findViewById(R.id.chatSubtitle);
        quickChipsRow = view.findViewById(R.id.chatQuickChipsRow);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ChatBotAdapter(this);
        recyclerView.setAdapter(adapter);

        boolean sellerMode = getArguments() != null && getArguments().getBoolean(ARG_SELLER_MODE, false);

        setupHeader(view, sellerMode);
        setupInput(view);
        setupQuickChips(view, sellerMode);

        observe();

        String conversationId = getArguments() != null
                ? getArguments().getString(ARG_CONVERSATION_ID) : null;
        String buyerId = getArguments() != null
                ? getArguments().getString(ARG_BUYER_ID) : null;

        if (!sellerMode && getArguments() != null
                && getArguments().getBoolean(ChatActivity.EXTRA_AUTO_SEND_INQUIRY, false)) {
            viewModel.setInquiryContext(
                    getArguments().getString(ChatActivity.EXTRA_ORDER_CODE),
                    getArguments().getString(ChatActivity.EXTRA_PRODUCT_NAME),
                    getArguments().getString(ChatActivity.EXTRA_PRODUCT_VARIANT));
        }

        viewModel.init(sellerMode, conversationId, buyerId);
        viewModel.resumeListening();
        view.post(() -> viewModel.dispatchPendingInquiry());
    }

    private void setupHeader(@NonNull View view, boolean sellerMode) {
        ImageView back = view.findViewById(R.id.chatBack);
        ImageView more = view.findViewById(R.id.chatMore);

        back.setOnClickListener(v -> requireActivity().finish());

        if (sellerMode) {
            subtitle.setText(R.string.chat_status_human);
            input.setHint(R.string.seller_reply_hint);
            more.setVisibility(View.GONE);
        } else {
            more.setOnClickListener(v -> onMoreClicked());
        }
    }

    private void onMoreClicked() {
        Boolean access = viewModel.getSellerAccess().getValue();
        if (access != null && access) {
            startActivity(new Intent(requireContext(), SellerChatListActivity.class));
        } else {
            Toast.makeText(requireContext(), R.string.seller_inbox_no_access, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupInput(@NonNull View view) {
        View send = view.findViewById(R.id.chatSend);
        ImageView attach = view.findViewById(R.id.chatAttach);

        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setRawInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        send.setOnClickListener(v -> submit());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        attach.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.chat_attach_unavailable, Toast.LENGTH_SHORT).show());
    }

    private void setupQuickChips(@NonNull View view, boolean sellerMode) {
        if (sellerMode) {
            quickChipsRow.setVisibility(View.GONE);
            return;
        }
        view.findViewById(R.id.chipOrderStatus).setOnClickListener(v ->
                viewModel.onSuggestedQuestionTapped(getString(R.string.chat_q_order_status)));
        view.findViewById(R.id.chipCancelOrder).setOnClickListener(v ->
                viewModel.onSuggestedQuestionTapped(getString(R.string.chat_q_cancel_order)));
        view.findViewById(R.id.chipSeller).setOnClickListener(v -> viewModel.requestHumanHandoff());
    }

    private void submit() {
        String text = input.getText() != null ? input.getText().toString() : "";
        if (text.trim().isEmpty()) {
            return;
        }
        viewModel.sendUserText(text);
        input.setText("");
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void observe() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submit(messages);
            if (messages != null && !messages.isEmpty()) {
                recyclerView.post(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));
            }
        });

        viewModel.getConversation().observe(getViewLifecycleOwner(), conversation -> {
            if (viewModel.isSellerMode()) {
                if (conversation != null) {
                    String buyerName = conversation.getBuyerName();
                    if (buyerName != null && !buyerName.trim().isEmpty()) {
                        title.setText(buyerName);
                    } else {
                        title.setText(R.string.conversation_buyer_fallback);
                    }
                }
                return;
            }
            if (conversation != null && conversation.isHumanMode()) {
                subtitle.setText(R.string.chat_status_human);
            } else {
                subtitle.setText(R.string.chat_status_online);
            }
        });

        viewModel.getToast().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSuggestionItems().observe(getViewLifecycleOwner(), items ->
                adapter.setSuggestionItems(items));
    }

    // ---- ChatBotAdapter.Listener ----------------------------------------

    @Override
    public void onOrderCardClick(@NonNull ChatMessage message) {
        Intent intent = new Intent(requireContext(), OrderDetailActivity.class);
        intent.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, message.getOrderId());
        startActivity(intent);
    }

    @Override
    public void onSuggestionQuestionClick(@NonNull String question) {
        viewModel.onSuggestedQuestionTapped(question);
    }

    @Override
    public void onSuggestionChange() {
        viewModel.rotateSuggestions();
    }

    @Override
    public void onDestroyView() {
        // Detach Firestore listeners to avoid leaks.
        if (viewModel != null) {
            viewModel.detachListeners();
        }
        super.onDestroyView();
    }
}
