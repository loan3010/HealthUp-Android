package com.example.healthup;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ItemMediaAddBinding;
import com.example.healthup.databinding.ItemMediaBinding;
import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_ADD = 0;
    private static final int TYPE_MEDIA = 1;
    private static final int MAX_MEDIA = 5;

    private List<Uri> mediaUris;
    private OnMediaClickListener listener;
    private boolean isViewOnly = false;

    public interface OnMediaClickListener {
        void onAddClick();
        void onRemoveClick(int position);
    }

    public interface OnImageClickListener {
        void onImageClick(int position, Uri uri);
    }

    public MediaAdapter(List<Uri> mediaUris, OnMediaClickListener listener) {
        this.mediaUris = mediaUris;
        this.listener = listener;
    }

    public void setViewOnly(boolean viewOnly) {
        this.isViewOnly = viewOnly;
    }

    private OnImageClickListener imageClickListener;

    public void setOnImageClickListener(OnImageClickListener imageClickListener) {
        this.imageClickListener = imageClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < mediaUris.size()) {
            return TYPE_MEDIA;
        }
        return TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MEDIA) {
            ItemMediaBinding binding = ItemMediaBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new MediaViewHolder(binding);
        } else {
            ItemMediaAddBinding binding = ItemMediaAddBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new AddViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MediaViewHolder) {
            ((MediaViewHolder) holder).bind(mediaUris.get(position), position);
        } else if (holder instanceof AddViewHolder) {
            ((AddViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        if (isViewOnly) return mediaUris.size();
        
        if (mediaUris.size() < MAX_MEDIA) {
            return mediaUris.size() + 1;
        }
        return mediaUris.size();
    }

    class MediaViewHolder extends RecyclerView.ViewHolder {
        private ItemMediaBinding binding;

        public MediaViewHolder(ItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Uri uri, int position) {
            Glide.with(itemView.getContext())
                    .load(uri)
                    .centerCrop()
                    .into(binding.imgMedia);
            
            if (isViewOnly) {
                binding.btnRemove.setVisibility(View.GONE);
                itemView.setOnClickListener(v -> {
                    if (imageClickListener != null) {
                        imageClickListener.onImageClick(position, uri);
                    }
                });
            } else {
                binding.btnRemove.setVisibility(View.VISIBLE);
                binding.btnRemove.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onRemoveClick(position);
                    }
                });
                itemView.setOnClickListener(null);
            }
        }
    }

    class AddViewHolder extends RecyclerView.ViewHolder {
        public AddViewHolder(ItemMediaAddBinding binding) {
            super(binding.getRoot());
        }

        public void bind() {
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddClick();
                }
            });
        }
    }
}
