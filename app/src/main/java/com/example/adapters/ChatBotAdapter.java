package com.example.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.healthup.util.FullscreenImagePager;
import com.example.healthup.util.ImageLoadHelper;
import com.example.healthup.R;
import com.example.healthup.chat.ChatBubbleHelper;
import com.example.healthup.chat.OrderStatusCopy;
import com.example.healthup.chat.SuggestionProvider;
import com.example.models.ChatMessage;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private static final int TYPE_PRODUCT_CARD = 6;
    private static final int TYPE_LOGIN_ACTION = 7;
    private static final int TYPE_IMAGE_USER = 8;
    private static final int TYPE_IMAGE_INCOMING = 9;
    private static final int TYPE_ORDER_CAROUSEL = 10;
    private static final int TYPE_ACTION_PROMPT = 11;
    private static final int TYPE_CATEGORY_PICK = 12;

    public interface Listener {
        void onOrderCardClick(@NonNull ChatMessage message);

        /** Buyer picked an order from the carousel (status / cancel flow). */
        void onOrderSelect(@NonNull ChatMessage message);

        void onSuggestionQuestionClick(@NonNull String question);

        void onSuggestionChange();

        void onLoginActionClick();

        void onActionPromptClick(@NonNull ChatMessage message);

        void onProductAddToCart(@NonNull ChatMessage message);

        void onProductBuyNow(@NonNull ChatMessage message);

        void onCategorySelected(@NonNull String category);
    }

    private final List<ChatMessage> items = new ArrayList<>();
    private final Listener listener;
    private List<SuggestionProvider.Item> suggestionItems = SuggestionProvider.getSet(0);
    /** When true (seller/admin view), own replies appear on the right. */
    private boolean staffView;
    /** Seller-mode label for buyer bubbles (username / name / phone). */
    @Nullable
    private String customerDisplayName;

    public ChatBotAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setStaffView(boolean staffView) {
        if (this.staffView == staffView) {
            return;
        }
        this.staffView = staffView;
        notifyDataSetChanged();
    }

    public void setCustomerDisplayName(@Nullable String customerDisplayName) {
        String next = customerDisplayName != null ? customerDisplayName.trim() : null;
        if (TextUtils.equals(this.customerDisplayName, next)) {
            return;
        }
        this.customerDisplayName = next;
        if (staffView) {
            notifyDataSetChanged();
        }
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
        if (ChatMessage.TYPE_ORDER_CAROUSEL.equals(m.getType())) {
            return TYPE_ORDER_CAROUSEL;
        }
        if (ChatMessage.TYPE_ACTION_PROMPT.equals(m.getType())) {
            return TYPE_ACTION_PROMPT;
        }
        if (ChatMessage.TYPE_PRODUCT_CARD.equals(m.getType())) {
            return TYPE_PRODUCT_CARD;
        }
        if (ChatMessage.TYPE_LOGIN_ACTION.equals(m.getType())) {
            return TYPE_LOGIN_ACTION;
        }
        if (ChatMessage.TYPE_CATEGORY_PICK.equals(m.getType())) {
            return TYPE_CATEGORY_PICK;
        }
        if (ChatMessage.TYPE_SYSTEM.equals(m.getType())
                || ChatMessage.SENDER_SYSTEM.equals(m.getSenderType())) {
            return TYPE_SYSTEM;
        }
        if (ChatMessage.TYPE_IMAGE.equals(m.getType())) {
            if (staffView) {
                return ChatMessage.SENDER_SELLER.equals(m.getSenderType())
                        ? TYPE_IMAGE_USER
                        : TYPE_IMAGE_INCOMING;
            }
            return ChatMessage.SENDER_USER.equals(m.getSenderType())
                    ? TYPE_IMAGE_USER
                    : TYPE_IMAGE_INCOMING;
        }
        if (staffView) {
            if (ChatMessage.SENDER_SELLER.equals(m.getSenderType())) {
                return TYPE_USER;
            }
            if (ChatMessage.SENDER_USER.equals(m.getSenderType())) {
                return TYPE_BOT;
            }
        } else if (ChatMessage.SENDER_USER.equals(m.getSenderType())) {
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
            case TYPE_ORDER_CAROUSEL:
                return new OrderCarouselVH(inflater.inflate(R.layout.item_chat_order_carousel, parent, false));
            case TYPE_ACTION_PROMPT:
                return new ActionPromptVH(inflater.inflate(R.layout.item_chat_login_action, parent, false));
            case TYPE_SUGGESTION:
                return new SuggestionVH(inflater.inflate(R.layout.item_chat_suggestion, parent, false));
            case TYPE_PRODUCT_CARD:
                return new ProductCardVH(inflater.inflate(R.layout.item_chat_product_card, parent, false));
            case TYPE_LOGIN_ACTION:
                return new LoginActionVH(inflater.inflate(R.layout.item_chat_login_action, parent, false));
            case TYPE_CATEGORY_PICK:
                return new CategoryPickVH(inflater.inflate(R.layout.item_chat_category_pick, parent, false));
            case TYPE_IMAGE_USER:
                return new ImageUserVH(inflater.inflate(R.layout.item_chat_message_image_user, parent, false));
            case TYPE_IMAGE_INCOMING:
                return new ImageIncomingVH(inflater.inflate(R.layout.item_chat_message_image_incoming, parent, false));
            case TYPE_BOT:
            default:
                return new BotVH(inflater.inflate(R.layout.item_chat_message, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage m = items.get(position);
        ChatBubbleHelper.GroupPosition groupPosition = ChatBubbleHelper.resolveGroupPosition(items, position);
        if (holder instanceof UserVH) {
            ((UserVH) holder).bind(m, groupPosition, items, position);
        } else if (holder instanceof SystemVH) {
            ((SystemVH) holder).text.setText(m.getText());
        } else if (holder instanceof BotVH) {
            ((BotVH) holder).bind(m, groupPosition, items, position, staffView);
        } else if (holder instanceof OrderCardVH) {
            ((OrderCardVH) holder).bind(m, listener);
        } else if (holder instanceof OrderCarouselVH) {
            ((OrderCarouselVH) holder).bind(m, listener);
        } else if (holder instanceof ActionPromptVH) {
            ((ActionPromptVH) holder).bind(m, listener);
        } else if (holder instanceof SuggestionVH) {
            ((SuggestionVH) holder).bind(suggestionItems, listener);
        } else if (holder instanceof ProductCardVH) {
            ((ProductCardVH) holder).bind(m, listener);
        } else if (holder instanceof LoginActionVH) {
            ((LoginActionVH) holder).bind(m, listener);
        } else if (holder instanceof CategoryPickVH) {
            ((CategoryPickVH) holder).bind(m, listener);
        } else if (holder instanceof ImageUserVH) {
            ((ImageUserVH) holder).bind(m, items, position);
        } else if (holder instanceof ImageIncomingVH) {
            ((ImageIncomingVH) holder).bind(m, items, position, staffView);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ---- ViewHolders -----------------------------------------------------

    static class ImageUserVH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView time;

        ImageUserVH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.chatImage);
            time = v.findViewById(R.id.chatImageTime);
        }

        void bind(@NonNull ChatMessage message,
                  @NonNull List<ChatMessage> items,
                  int position) {
            ImageLoadHelper.loadInto(image, message.getImageUrl());
            image.setOnClickListener(v -> openFullscreen(v.getContext(), message.getImageUrl()));
            if (ChatBubbleHelper.shouldShowTimestamp(items, position)) {
                time.setVisibility(View.VISIBLE);
                time.setText(formatMessageTime(message));
            } else {
                time.setVisibility(View.GONE);
            }
        }
    }

    static class ImageIncomingVH extends RecyclerView.ViewHolder {
        final View avatarContainer;
        final ImageView avatar;
        final TextView name;
        final ImageView image;
        final TextView time;

        ImageIncomingVH(@NonNull View v) {
            super(v);
            avatarContainer = v.findViewById(R.id.chatBotAvatarContainer);
            avatar = v.findViewById(R.id.chatBotAvatar);
            name = v.findViewById(R.id.chatSenderName);
            image = v.findViewById(R.id.chatImage);
            time = v.findViewById(R.id.chatImageTime);
        }

        void bind(@NonNull ChatMessage message,
                  @NonNull List<ChatMessage> items,
                  int position,
                  boolean staffView) {
            ImageLoadHelper.loadInto(image, message.getImageUrl());
            if (avatarContainer != null) {
                avatarContainer.setVisibility(View.VISIBLE);
            }
            if (avatar != null) {
                if (ChatMessage.SENDER_SELLER.equals(message.getSenderType())) {
                    avatar.setImageResource(R.drawable.ic_chat_person);
                } else {
                    avatar.setImageResource(R.drawable.ic_chat_bot);
                }
            }
            if (ChatMessage.SENDER_SELLER.equals(message.getSenderType())) {
                name.setVisibility(View.VISIBLE);
                String staffLabel = itemView.getContext().getString(R.string.chat_sender_staff);
                if (message.getSenderName() != null && !message.getSenderName().trim().isEmpty()) {
                    name.setText(staffLabel + " · " + message.getSenderName().trim());
                } else {
                    name.setText(staffLabel);
                }
            } else if (staffView && ChatMessage.SENDER_USER.equals(message.getSenderType())) {
                name.setVisibility(View.VISIBLE);
                name.setText(!TextUtils.isEmpty(customerDisplayName)
                        ? customerDisplayName
                        : itemView.getContext().getString(R.string.chat_sender_customer));
            } else {
                name.setVisibility(View.GONE);
            }
            if (ChatBubbleHelper.shouldShowTimestamp(items, position)) {
                time.setVisibility(View.VISIBLE);
                time.setText(formatMessageTime(message));
            } else {
                time.setVisibility(View.GONE);
            }
            image.setOnClickListener(v -> openFullscreen(v.getContext(), message.getImageUrl()));
        }
    }

    private static void openFullscreen(@NonNull android.content.Context context, @Nullable String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return;
        }
        FullscreenImagePager.show(context, Collections.singletonList(imageUrl.trim()), 0);
    }

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView time;

        UserVH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chatUserText);
            time = v.findViewById(R.id.chatUserTime);
        }

        void bind(@NonNull ChatMessage message,
                  @NonNull ChatBubbleHelper.GroupPosition groupPosition,
                  @NonNull List<ChatMessage> items,
                  int position) {
            text.setText(message.getText());
            text.setBackgroundResource(ChatBubbleHelper.userBubbleBackground(groupPosition));
            applyVerticalPadding(itemView, groupPosition);
            if (ChatBubbleHelper.shouldShowTimestamp(items, position)) {
                time.setVisibility(View.VISIBLE);
                time.setText(formatMessageTime(message));
            } else {
                time.setVisibility(View.GONE);
            }
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
        final View avatarContainer;
        final ImageView avatar;
        final TextView name;
        final TextView text;
        final TextView time;

        BotVH(@NonNull View v) {
            super(v);
            avatarContainer = v.findViewById(R.id.chatBotAvatarContainer);
            avatar = v.findViewById(R.id.chatBotAvatar);
            name = v.findViewById(R.id.chatSenderName);
            text = v.findViewById(R.id.chatBotText);
            time = v.findViewById(R.id.chatBotTime);
        }

        void bind(@NonNull ChatMessage message,
                  @NonNull ChatBubbleHelper.GroupPosition groupPosition,
                  @NonNull List<ChatMessage> items,
                  int position,
                  boolean staffView) {
            text.setText(htmlOrPlain(message.getText()));
            text.setBackgroundResource(ChatBubbleHelper.incomingBubbleBackground(groupPosition));
            applyVerticalPadding(itemView, groupPosition);

            if (ChatBubbleHelper.shouldShowAvatar(items, position)) {
                if (avatarContainer != null) {
                    avatarContainer.setVisibility(View.VISIBLE);
                }
                if (avatar != null) {
                    if (ChatMessage.SENDER_SELLER.equals(message.getSenderType())) {
                        avatar.setImageResource(R.drawable.ic_chat_person);
                    } else {
                        avatar.setImageResource(R.drawable.ic_chat_bot);
                    }
                }
            } else if (avatarContainer != null) {
                avatarContainer.setVisibility(View.INVISIBLE);
            }

            if (ChatMessage.SENDER_SELLER.equals(message.getSenderType())) {
                name.setVisibility(View.VISIBLE);
                String staffLabel = itemView.getContext().getString(R.string.chat_sender_staff);
                if (message.getSenderName() != null && !message.getSenderName().trim().isEmpty()) {
                    name.setText(staffLabel + " · " + message.getSenderName().trim());
                } else {
                    name.setText(staffLabel);
                }
            } else if (ChatMessage.SENDER_BOT.equals(message.getSenderType())) {
                name.setVisibility(View.VISIBLE);
                name.setText(R.string.chat_sender_bot);
            } else if (staffView && ChatMessage.SENDER_USER.equals(message.getSenderType())) {
                name.setVisibility(View.VISIBLE);
                name.setText(!TextUtils.isEmpty(customerDisplayName)
                        ? customerDisplayName
                        : itemView.getContext().getString(R.string.chat_sender_customer));
            } else {
                name.setVisibility(View.GONE);
            }

            if (ChatBubbleHelper.shouldShowTimestamp(items, position)) {
                time.setVisibility(View.VISIBLE);
                time.setText(formatMessageTime(message));
            } else {
                time.setVisibility(View.GONE);
            }
        }
    }

    static class ProductCardVH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView variant;
        final TextView price;
        final View btnAddCart;
        final View btnBuyNow;

        ProductCardVH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.productCardImage);
            name = v.findViewById(R.id.productCardName);
            variant = v.findViewById(R.id.productCardVariant);
            price = v.findViewById(R.id.productCardPrice);
            btnAddCart = v.findViewById(R.id.btnProductAddCart);
            btnBuyNow = v.findViewById(R.id.btnProductBuyNow);
        }

        void bind(@NonNull ChatMessage message, @Nullable Listener listener) {
            name.setText(message.getProductName() != null ? message.getProductName() : "");
            if (message.getProductVariant() != null && !message.getProductVariant().isEmpty()) {
                variant.setVisibility(View.VISIBLE);
                variant.setText(message.getProductVariant());
            } else {
                variant.setVisibility(View.GONE);
            }
            price.setText(formatCurrency(message.getProductPrice()));
            ImageLoadHelper.loadInto(image, message.getProductImageUrl());
            btnAddCart.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductAddToCart(message);
                }
            });
            btnBuyNow.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProductBuyNow(message);
                }
            });
        }

        private String formatCurrency(double value) {
            String formatted = new DecimalFormat("#,###").format(value);
            return formatted.replace(',', '.') + "đ";
        }
    }

    static class OrderCardVH extends RecyclerView.ViewHolder {
        final View root;
        final TextView code;
        final TextView status;
        final TextView items;
        final TextView total;
        final TextView cta;

        OrderCardVH(@NonNull View v) {
            super(v);
            root = v.findViewById(R.id.orderCardRoot);
            code = v.findViewById(R.id.orderCardCode);
            status = v.findViewById(R.id.orderCardStatus);
            items = v.findViewById(R.id.orderCardItems);
            total = v.findViewById(R.id.orderCardTotal);
            cta = v.findViewById(R.id.orderCardCta);
        }

        void bind(ChatMessage m, Listener listener) {
            String orderCode = m.getOrderCode() != null ? m.getOrderCode() : "";
            code.setText("Đơn hàng #" + orderCode);
            status.setText(OrderStatusCopy.statusLabel(m.getOrderStatus()));
            items.setText(itemView.getContext()
                    .getString(R.string.chat_order_card_items, m.getOrderItemCount()));
            total.setText("Tổng: " + formatCurrency(m.getOrderTotal()));
            boolean selectMode = ChatMessage.ORDER_PURPOSE_STATUS.equals(m.getOrderPurpose())
                    || ChatMessage.ORDER_PURPOSE_CANCEL.equals(m.getOrderPurpose());
            if (cta != null) {
                if (m.getActionLabel() != null && !m.getActionLabel().isEmpty()) {
                    cta.setText(m.getActionLabel());
                } else if (selectMode) {
                    cta.setText(R.string.chat_order_pick_button);
                } else if (ChatMessage.ORDER_PURPOSE_DETAIL.equals(m.getOrderPurpose())) {
                    cta.setText(R.string.chat_order_status_cta);
                } else {
                    cta.setText(R.string.chat_order_card_view_detail);
                }
            }
            root.setOnClickListener(v -> {
                if (listener == null) {
                    return;
                }
                if (selectMode) {
                    listener.onOrderSelect(m);
                } else {
                    listener.onOrderCardClick(m);
                }
            });
        }

        private String formatCurrency(double value) {
            String formatted = new DecimalFormat("#,###").format(value);
            return formatted.replace(',', '.') + "đ";
        }
    }

    static class OrderCarouselVH extends RecyclerView.ViewHolder {
        final RecyclerView list;

        OrderCarouselVH(@NonNull View v) {
            super(v);
            list = v.findViewById(R.id.orderCarouselList);
            list.setLayoutManager(new LinearLayoutManager(
                    v.getContext(), LinearLayoutManager.HORIZONTAL, false));
            list.setNestedScrollingEnabled(false);
        }

        void bind(@NonNull ChatMessage message, @Nullable Listener listener) {
            List<ChatMessage> choices = message.getOrderChoices();
            if (choices == null) {
                choices = Collections.emptyList();
            }
            list.setAdapter(new OrderPickAdapter(choices, listener));
        }
    }

    static class OrderPickAdapter extends RecyclerView.Adapter<OrderPickAdapter.VH> {
        private final List<ChatMessage> choices;
        private final Listener listener;

        OrderPickAdapter(@NonNull List<ChatMessage> choices, @Nullable Listener listener) {
            this.choices = choices;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_order_pick_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(choices.get(position), listener);
        }

        @Override
        public int getItemCount() {
            return choices.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView code;
            final TextView product;
            final TextView status;
            final TextView meta;
            final TextView button;

            VH(@NonNull View v) {
                super(v);
                thumb = v.findViewById(R.id.orderPickThumb);
                code = v.findViewById(R.id.orderPickCode);
                product = v.findViewById(R.id.orderPickProduct);
                status = v.findViewById(R.id.orderPickStatus);
                meta = v.findViewById(R.id.orderPickMeta);
                button = v.findViewById(R.id.orderPickButton);
            }

            void bind(@NonNull ChatMessage m, @Nullable Listener listener) {
                String orderCode = m.getOrderCode() != null ? m.getOrderCode() : "";
                code.setText("Đơn #" + orderCode);
                status.setText(OrderStatusCopy.statusLabel(m.getOrderStatus()));
                String productName = m.getProductName();
                if (productName != null && !productName.trim().isEmpty()) {
                    product.setText(productName.trim());
                    product.setVisibility(View.VISIBLE);
                } else {
                    product.setText("Đơn hàng HealthUp");
                }
                if (m.getProductImageUrl() != null && !m.getProductImageUrl().isEmpty()) {
                    ImageLoadHelper.loadInto(thumb, m.getProductImageUrl());
                } else {
                    thumb.setImageResource(R.drawable.ic_chat_order);
                }
                String total = formatCurrency(m.getOrderTotal());
                meta.setText(itemView.getContext().getString(
                        R.string.chat_order_card_meta, m.getOrderItemCount(), total));
                if (m.getActionLabel() != null && !m.getActionLabel().isEmpty()) {
                    button.setText(m.getActionLabel());
                } else {
                    button.setText(R.string.chat_order_pick_button);
                }
                View.OnClickListener click = v -> {
                    if (listener != null) {
                        listener.onOrderSelect(m);
                    }
                };
                itemView.setOnClickListener(click);
                button.setOnClickListener(click);
            }

            private String formatCurrency(double value) {
                String formatted = new DecimalFormat("#,###").format(value);
                return formatted.replace(',', '.') + "đ";
            }
        }
    }

    static class ActionPromptVH extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView button;

        ActionPromptVH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chatLoginPromptText);
            button = v.findViewById(R.id.btnChatLogin);
        }

        void bind(@NonNull ChatMessage message, @Nullable Listener listener) {
            text.setText(message.getText());
            if (message.getActionLabel() != null && !message.getActionLabel().isEmpty()) {
                button.setText(message.getActionLabel());
            } else {
                button.setText(R.string.chat_order_browse_cta);
            }
            button.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActionPromptClick(message);
                }
            });
        }
    }

    static class LoginActionVH extends RecyclerView.ViewHolder {
        final TextView text;
        final View loginButton;

        LoginActionVH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chatLoginPromptText);
            loginButton = v.findViewById(R.id.btnChatLogin);
        }

        void bind(@NonNull ChatMessage message, @Nullable Listener listener) {
            text.setText(message.getText());
            if (loginButton instanceof TextView) {
                ((TextView) loginButton).setText(R.string.chat_login_action);
            }
            loginButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLoginActionClick();
                }
            });
        }
    }

    static class CategoryPickVH extends RecyclerView.ViewHolder {
        final TextView text;
        final com.google.android.material.chip.ChipGroup chipGroup;

        CategoryPickVH(@NonNull View v) {
            super(v);
            text = v.findViewById(R.id.chatCategoryPromptText);
            chipGroup = v.findViewById(R.id.chatCategoryChipGroup);
        }

        void bind(@NonNull ChatMessage message, @Nullable Listener listener) {
            text.setText(message.getText());
            chipGroup.removeAllViews();
            List<String> cats = message.getCategoryChoices();
            if (cats == null) {
                return;
            }
            for (String cat : cats) {
                if (cat == null || cat.trim().isEmpty()) {
                    continue;
                }
                String name = cat.trim();
                com.google.android.material.chip.Chip chip =
                        new com.google.android.material.chip.Chip(chipGroup.getContext());
                chip.setText(name);
                chip.setCheckable(false);
                chip.setClickable(true);
                chip.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onCategorySelected(name);
                    }
                });
                chipGroup.addView(chip);
            }
        }
    }

    /** Renders bot HTML (`<b>`) when present; otherwise plain text. */
    @NonNull
    private static CharSequence htmlOrPlain(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.contains("<b>") || raw.contains("<br")) {
            return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY);
        }
        return raw;
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

    private static void applyVerticalPadding(@NonNull View itemView,
                                             @NonNull ChatBubbleHelper.GroupPosition groupPosition) {
        float density = itemView.getResources().getDisplayMetrics().density;
        int top = (int) (ChatBubbleHelper.verticalPaddingTopDp(groupPosition) * density);
        int bottom = (int) (ChatBubbleHelper.verticalPaddingBottomDp(groupPosition) * density);
        itemView.setPadding(itemView.getPaddingLeft(), top, itemView.getPaddingRight(), bottom);
    }

    private static String formatMessageTime(@NonNull ChatMessage message) {
        long millis = message.getSortTime();
        if (millis <= 10L && message.getCreatedAt() != null) {
            millis = message.getCreatedAt().getTime();
        }
        if (millis <= 10L) {
            return "";
        }
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
