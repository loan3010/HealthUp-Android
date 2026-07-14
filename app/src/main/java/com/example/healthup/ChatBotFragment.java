package com.example.healthup;



import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.text.InputType;

import android.text.TextUtils;

import android.view.LayoutInflater;

import android.view.View;

import android.view.ViewGroup;

import android.view.inputmethod.EditorInfo;

import android.widget.EditText;

import android.widget.ImageView;

import android.widget.TextView;

import android.widget.Toast;



import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import androidx.core.graphics.Insets;

import androidx.core.view.ViewCompat;

import androidx.core.view.WindowInsetsCompat;

import androidx.fragment.app.Fragment;

import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;

import androidx.recyclerview.widget.RecyclerView;



import com.example.adapters.ChatBotAdapter;

import com.example.healthup.chat.ChatViewModel;

import com.example.healthup.VariantBottomSheetFragment;

import com.example.healthup.util.CartHelper;

import com.example.healthup.util.CheckoutIntentHelper;

import com.example.healthup.util.PhoneVerifiedHelper;

import com.example.models.CartItem;

import com.example.models.ChatMessage;

import com.example.models.Conversation;

import com.example.models.Product;

import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;



import java.util.ArrayList;



/**

 * The single chat screen (Shopee-style single thread). Starts with the local

 * bot; the buyer can hand off to a human seller in the SAME thread. The same

 * screen is reused by a seller (via {@link #ARG_SELLER_MODE}) to reply.

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

    private View inputBar;

    private ImageView moreButton;

    private View sendButton;

    private boolean sendingImage;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickChatImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    uploadAndSendImage(uri);
                }
            });

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

        inputBar = view.findViewById(R.id.chatInputBar);

        moreButton = view.findViewById(R.id.chatMore);

        sendButton = view.findViewById(R.id.chatSend);



        boolean sellerMode = getArguments() != null && getArguments().getBoolean(ARG_SELLER_MODE, false);



        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());

        layoutManager.setStackFromEnd(true);

        recyclerView.setLayoutManager(layoutManager);

        adapter = new ChatBotAdapter(this);

        adapter.setStaffView(sellerMode);

        recyclerView.setAdapter(adapter);



        setupWindowInsets(view);

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

                    getArguments().getString(ChatActivity.EXTRA_PRODUCT_VARIANT),

                    getArguments().getString(ChatActivity.EXTRA_PRODUCT_ID));

        }



        viewModel.init(sellerMode, conversationId, buyerId);

        viewModel.resumeListening();

        view.post(() -> viewModel.dispatchPendingInquiry());

    }



    private void setupWindowInsets(@NonNull View root) {

        View header = root.findViewById(R.id.chatHeaderBar);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {

            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            int horizontal = header.getPaddingLeft();

            header.setPadding(horizontal, systemBars.top + dp(12), horizontal, header.getPaddingBottom());



            int baseBottom = dp(8);

            int bottomInset = Math.max(ime.bottom, systemBars.bottom);

            if (inputBar != null) {

                inputBar.setPadding(

                        inputBar.getPaddingLeft(),

                        inputBar.getPaddingTop(),

                        inputBar.getPaddingRight(),

                        baseBottom + bottomInset);

            }



            if (ime.bottom > 0 && adapter != null && adapter.getItemCount() > 0) {

                recyclerView.post(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));

            }

            return windowInsets;

        });

        ViewCompat.requestApplyInsets(root);

    }



    private int dp(int value) {

        return (int) (value * getResources().getDisplayMetrics().density);

    }



    private void setupHeader(@NonNull View view, boolean sellerMode) {

        ImageView back = view.findViewById(R.id.chatBack);

        back.setOnClickListener(v -> requireActivity().finish());



        if (sellerMode) {

            subtitle.setText(R.string.chat_status_human);

            input.setHint(R.string.seller_reply_hint);

            moreButton.setVisibility(View.VISIBLE);

            updateSellerHeaderActions();

        } else {

            moreButton.setOnClickListener(v -> onMoreClicked());

        }

    }



    private void updateSellerHeaderActions() {

        if (moreButton == null || !viewModel.isSellerMode()) {

            return;

        }

        if (viewModel.isClosedSessionView()) {

            moreButton.setOnClickListener(v -> confirmReopenSession());

        } else {

            moreButton.setOnClickListener(v -> confirmCloseSession());

        }

    }



    private void updateClosedSessionUi() {

        if (!viewModel.isSellerMode()) {

            return;

        }

        boolean closed = viewModel.isClosedSessionView();

        if (closed) {

            subtitle.setText(R.string.chat_status_session_closed);

        } else {

            subtitle.setText(R.string.chat_status_human);

        }

        if (input != null) {

            input.setEnabled(!closed);

            input.setHint(closed ? R.string.chat_session_closed_hint : R.string.seller_reply_hint);

        }

        if (sendButton != null) {

            sendButton.setEnabled(!closed);

            sendButton.setAlpha(closed ? 0.4f : 1f);

        }

    }



    private void confirmReopenSession() {

        new AlertDialog.Builder(requireContext())

                .setTitle(R.string.chat_reopen_session_title)

                .setMessage(R.string.chat_reopen_session_message)

                .setNegativeButton(R.string.account_management_cancel, null)

                .setPositiveButton(R.string.chat_reopen_session_confirm, (d, w) ->

                        viewModel.reopenHumanSession())

                .show();

    }



    private void confirmCloseSession() {

        new AlertDialog.Builder(requireContext())

                .setTitle(R.string.chat_close_session_title)

                .setMessage(R.string.chat_close_session_message)

                .setNegativeButton(R.string.account_management_cancel, null)

                .setPositiveButton(R.string.chat_close_session_confirm, (d, w) ->

                        viewModel.closeHumanSession())

                .show();

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

        input.setOnFocusChangeListener((v, hasFocus) -> {

            if (hasFocus && adapter.getItemCount() > 0) {

                recyclerView.post(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));

            }

        });

        ImageView mic = view.findViewById(R.id.chatMic);
        attach.setOnClickListener(v -> openImagePicker());
        if (mic != null) {
            mic.setOnClickListener(v ->
                    Toast.makeText(requireContext(), R.string.chat_voice_coming_soon, Toast.LENGTH_SHORT).show());
        }
    }

    private void openImagePicker() {
        if (viewModel.isGuest() && !viewModel.isSellerMode()) {
            Toast.makeText(requireContext(), R.string.chat_image_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sendingImage) {
            Toast.makeText(requireContext(), R.string.chat_image_sending, Toast.LENGTH_SHORT).show();
            return;
        }
        pickChatImageLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void uploadAndSendImage(@NonNull Uri uri) {
        if (!isAdded() || sendingImage) {
            return;
        }
        sendingImage = true;
        Toast.makeText(requireContext(), R.string.chat_image_sending, Toast.LENGTH_SHORT).show();
        FirebaseManager.getInstance().uploadImage(uri)
                .addOnSuccessListener(uploaded -> {
                    sendingImage = false;
                    if (!isAdded()) {
                        return;
                    }
                    if (uploaded == null) {
                        Toast.makeText(requireContext(), R.string.chat_image_send_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.sendUserImage(uploaded.toString());
                    if (recyclerView != null && adapter != null && adapter.getItemCount() > 0) {
                        recyclerView.post(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1));
                    }
                })
                .addOnFailureListener(e -> {
                    sendingImage = false;
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.chat_image_send_failed, Toast.LENGTH_SHORT).show();
                    }
                });
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

        View chipSeller = view.findViewById(R.id.chipSeller);

        chipSeller.setOnClickListener(v -> viewModel.requestHumanHandoff());

    }



    private void submit() {

        String text = input.getText() != null ? input.getText().toString() : "";

        if (text.trim().isEmpty()) {

            return;

        }

        viewModel.sendUserText(text);

        input.setText("");

        input.requestFocus();

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

                updateSellerConversationTitle(conversation);

                updateSellerHeaderActions();

                updateClosedSessionUi();

                return;

            }

            boolean human = conversation != null && conversation.isHumanMode();

            if (human) {

                subtitle.setText(R.string.chat_status_human);

            } else {

                subtitle.setText(R.string.chat_status_online);

            }

            updateQuickChipsVisibility(human);

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



    private void updateSellerConversationTitle(@Nullable Conversation conversation) {

        if (conversation == null) {

            title.setText(R.string.conversation_buyer_fallback);

            return;

        }

        String username = conversation.getBuyerUsername();

        if (!TextUtils.isEmpty(username)) {

            title.setText(username.startsWith("@") ? username : "@" + username);

            return;

        }

        String buyerName = conversation.getBuyerName();

        if (buyerName != null && !buyerName.trim().isEmpty()) {

            title.setText(buyerName);

        } else {

            title.setText(R.string.conversation_buyer_fallback);

        }

    }



    @Override

    public void onOrderCardClick(@NonNull ChatMessage message) {

        Intent intent = new Intent(requireContext(), OrderDetailActivity.class);

        intent.putExtra(OrderDetailActivity.EXTRA_ORDER_ID, message.getOrderId());

        startActivity(intent);

    }



    @Override

    public void onLoginActionClick() {

        startActivity(new Intent(requireContext(), LoginActivity.class));

    }



    @Override

    public void onProductAddToCart(@NonNull ChatMessage message) {
        loadProductForCard(message, false);
    }

    @Override
    public void onProductBuyNow(@NonNull ChatMessage message) {
        loadProductForCard(message, true);
    }

    private void loadProductForCard(@NonNull ChatMessage message, boolean isBuyNow) {

        String productId = message.getProductId();

        if (productId == null || productId.isEmpty()) {

            Toast.makeText(requireContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show();

            return;

        }

        FirebaseFirestore.getInstance().collection("products").document(productId).get()

                .addOnSuccessListener(doc -> {

                    Product product = Product.fromDocument(doc);

                    if (product == null) {

                        Toast.makeText(requireContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show();

                        return;

                    }
                    product.setId(doc.getId());

                    VariantBottomSheetFragment sheet = VariantBottomSheetFragment.newInstance(
                            product, isBuyNow, (variant, quantity) -> {
                                if (isBuyNow) {
                                    performBuyNow(product, variant, quantity);
                                } else {
                                    CartHelper.addToCart(requireContext(), product, variant, quantity);
                                }
                            });
                    sheet.show(getParentFragmentManager(), "VariantSelection");

                })

                .addOnFailureListener(e ->

                        Toast.makeText(requireContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show());

    }



    private void performBuyNow(@NonNull Product product,

                               @Nullable Product.ProductVariant variant,

                               int quantity) {

        CartItem buyNowItem = new CartItem(product.getId(), product, quantity, null);

        if (variant != null) {

            buyNowItem.setVariantId(variant.getId());

            buyNowItem.setVariantName(variant.getName());

            buyNowItem.setPrice(variant.getPrice());

        } else {

            buyNowItem.setPrice(product.getPrice());

        }



        ArrayList<CartItem> checkoutItems = new ArrayList<>();

        checkoutItems.add(buyNowItem);



        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.putExtra("return_to_previous", true);

        if (user == null) {

            CheckoutIntentHelper.savePendingCheckout(requireContext(), checkoutItems);

            intent.putExtra("navigate_to", "phone_verification");

            startActivity(intent);

            return;

        }



        buyNowItem.setUserId(user.getUid());

        PhoneVerifiedHelper.requireForCheckout(new PhoneVerifiedHelper.Callback() {

            @Override

            public void onVerified() {

                intent.putExtra("navigate_to", "checkout");

                intent.putExtra("checkout_items", checkoutItems);

                startActivity(intent);

            }



            @Override

            public void onNeedPhoneVerification() {

                Toast.makeText(requireContext(), R.string.checkout_need_phone_verified, Toast.LENGTH_LONG).show();

                CheckoutIntentHelper.savePendingCheckout(requireContext(), checkoutItems);

                intent.putExtra("navigate_to", "phone_verification");

                startActivity(intent);

            }



            @Override

            public void onError(@NonNull String message) {

                Toast.makeText(requireContext(), R.string.register_error_generic, Toast.LENGTH_SHORT).show();

            }

        });

    }



    private void updateQuickChipsVisibility(boolean humanMode) {

        if (quickChipsRow == null) {

            return;

        }

        quickChipsRow.setVisibility(humanMode ? View.GONE : View.VISIBLE);

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

        if (viewModel != null) {

            viewModel.detachListeners();

        }

        super.onDestroyView();

    }


}

