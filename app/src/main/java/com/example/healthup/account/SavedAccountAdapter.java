package com.example.healthup.account;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.healthup.R;
import com.example.healthup.auth.UserProfileBuilder;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SavedAccountAdapter extends RecyclerView.Adapter<SavedAccountAdapter.Holder> {

    public interface Listener {
        void onCardClick(@NonNull SavedAccount account);

        void onRemoveClick(@NonNull SavedAccount account);
    }

    private final List<SavedAccount> items = new ArrayList<>();
    @NonNull
    private final Listener listener;
    @NonNull
    private String activeUid = "";

    public SavedAccountAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<SavedAccount> accounts, @NonNull String activeUid) {
        items.clear();
        items.addAll(accounts);
        this.activeUid = activeUid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_account, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SavedAccount account = items.get(position);
        boolean active = account.uid.equals(activeUid);

        holder.tvName.setText(TextUtils.isEmpty(account.displayName)
                ? holder.itemView.getContext().getString(R.string.account_management_unnamed)
                : account.displayName);

        String subtitle = buildSubtitle(holder, account);
        holder.tvSubtitle.setText(subtitle);

        holder.ivActiveCheck.setVisibility(active ? View.VISIBLE : View.GONE);

        if (!TextUtils.isEmpty(account.avatarUrl)) {
            Glide.with(holder.ivAvatar.getContext())
                    .load(account.avatarUrl)
                    .placeholder(R.drawable.ic_account_default)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_account_default);
        }

        if (active) {
            holder.btnAction.setVisibility(View.GONE);
            holder.btnAction.setOnClickListener(null);
        } else {
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setText(R.string.account_management_remove);
            holder.btnAction.setOnClickListener(v -> listener.onRemoveClick(account));
        }

        holder.itemView.setOnClickListener(v -> {
            if (!active) {
                listener.onCardClick(account);
            }
        });
    }

    private String buildSubtitle(@NonNull Holder holder, @NonNull SavedAccount account) {
        if (UserProfileBuilder.isRealEmail(account.email)) {
            return account.email;
        }
        if (!TextUtils.isEmpty(account.phone)) {
            return account.phone;
        }
        if (SavedAccount.PROVIDER_GOOGLE.equals(account.authProvider)) {
            return holder.itemView.getContext().getString(R.string.account_management_google);
        }
        if (SavedAccount.PROVIDER_FACEBOOK.equals(account.authProvider)) {
            return holder.itemView.getContext().getString(R.string.account_management_facebook);
        }
        return "";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final ImageView ivActiveCheck;
        final TextView tvName;
        final TextView tvSubtitle;
        final MaterialButton btnAction;

        Holder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAccountAvatar);
            ivActiveCheck = itemView.findViewById(R.id.ivActiveCheck);
            tvName = itemView.findViewById(R.id.tvAccountName);
            tvSubtitle = itemView.findViewById(R.id.tvAccountSubtitle);
            btnAction = itemView.findViewById(R.id.btnAccountAction);
        }
    }
}
