package com.group.healthup;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.group.healthup.databinding.ItemProductReviewEntryBinding;
import com.group.models.Review;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductReviewEntryAdapter extends RecyclerView.Adapter<ProductReviewEntryAdapter.ViewHolder> {
    private List<Review> reviews;
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

    public ProductReviewEntryAdapter(List<Review> reviews) {
        this.reviews = reviews;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProductReviewEntryBinding binding = ItemProductReviewEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(reviews.get(position));
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ItemProductReviewEntryBinding binding;

        public ViewHolder(ItemProductReviewEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Review review) {
            binding.tvUserName.setText(review.getUserName() != null ? review.getUserName() : "Khách hàng");
            binding.ratingBar.setRating(review.getRating());
            binding.tvDate.setText(sdf.format(new Date(review.getCreatedAt())));
            binding.tvComment.setText(review.getComment());
            
            if (review.getComment() == null || review.getComment().isEmpty()) {
                binding.tvComment.setVisibility(ViewGroup.GONE);
            } else {
                binding.tvComment.setVisibility(ViewGroup.VISIBLE);
            }

            if (review.getMediaUris() != null && !review.getMediaUris().isEmpty()) {
                List<Uri> uris = new ArrayList<>();
                for (String s : review.getMediaUris()) uris.add(Uri.parse(s));
                MediaAdapter adapter = new MediaAdapter(uris, null);
                adapter.setViewOnly(true);
                binding.rvMedia.setLayoutManager(new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
                binding.rvMedia.setAdapter(adapter);
                binding.rvMedia.setVisibility(ViewGroup.VISIBLE);
            } else {
                binding.rvMedia.setVisibility(ViewGroup.GONE);
            }
        }
    }
}
