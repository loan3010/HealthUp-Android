package com.example.healthup;

import android.text.Layout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ItemReviewBinding;
import com.example.healthup.util.FullscreenImagePager;
import com.example.models.Review;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductReviewEntryAdapter extends RecyclerView.Adapter<ProductReviewEntryAdapter.ReviewViewHolder> {

    private final List<Review> reviews;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", new Locale("vi", "VN"));
    private static final Map<String, String> avatarCache = new HashMap<>();
    private static final Map<String, String> nameCache = new HashMap<>();

    public ProductReviewEntryAdapter(List<Review> reviews) {
        this.reviews = reviews;
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReviewBinding binding = ItemReviewBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ReviewViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        holder.bind(reviews.get(position));
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    class ReviewViewHolder extends RecyclerView.ViewHolder {
        private final ItemReviewBinding binding;

        public ReviewViewHolder(ItemReviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Review review) {
            String uid = review.getUserId();
            
            // Mặc định từ dữ liệu denormalized trong review
            String displayName = !TextUtils.isEmpty(review.getUserName()) ? review.getUserName() : "Khách hàng ẩn danh";
            String avatarUrl = review.getUserAvatar();

            // Nếu có userId, kiểm tra cache hoặc fetch mới để luôn cập nhật avatar/tên mới nhất
            if (!TextUtils.isEmpty(uid)) {
                if (avatarCache.containsKey(uid)) {
                    avatarUrl = avatarCache.get(uid);
                    displayName = nameCache.getOrDefault(uid, displayName);
                } else {
                    // Fetch live data từ Firestore
                    FirebaseFirestore.getInstance().collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    String liveAvatar = doc.getString("avatarUrl");
                                    String liveUsername = doc.getString("username");
                                    String liveName = doc.getString("fullName");
                                    
                                    String finalName = "Khách hàng ẩn danh";
                                    if (!TextUtils.isEmpty(liveUsername)) {
                                        finalName = "@" + liveUsername;
                                    } else if (!TextUtils.isEmpty(liveName)) {
                                        finalName = liveName;
                                    }
                                    
                                    if (!TextUtils.isEmpty(liveAvatar)) avatarCache.put(uid, liveAvatar);
                                    nameCache.put(uid, finalName);
                                    
                                    // Re-bind nếu dữ liệu thay đổi
                                    notifyItemChanged(getBindingAdapterPosition());
                                }
                            });
                }
            }

            binding.tvUserName.setText(displayName);
            binding.ratingBarSmall.setRating(review.getRating());
            binding.tvReviewDate.setText(formatDate(review));

            if (!TextUtils.isEmpty(avatarUrl)) {
                Glide.with(binding.ivUserAvatar.getContext())
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .centerCrop()
                        .into(binding.ivUserAvatar);
            } else {
                binding.ivUserAvatar.setImageResource(R.drawable.ic_profile);
            }

            if (!TextUtils.isEmpty(review.getVariantLabel())) {
                binding.tvVariantLabel.setVisibility(View.VISIBLE);
                binding.tvVariantLabel.setText("Phân loại: " + review.getVariantLabel());
            } else {
                binding.tvVariantLabel.setVisibility(View.GONE);
            }

            binding.tvComment.setText(
                    !TextUtils.isEmpty(review.getComment()) ? review.getComment() : "");

            // ✅ FIX: Xử lý "Xem thêm" / "Thu gọn" cho comment dài > 3 dòng
            binding.tvComment.setMaxLines(3);
            binding.tvComment.setEllipsize(TextUtils.TruncateAt.END);
            binding.tvToggleExpand.setVisibility(View.GONE);

            binding.tvComment.post(() -> {
                Layout layout = binding.tvComment.getLayout();
                if (layout != null) {
                    int lineCount = layout.getLineCount();
                    // Nếu số dòng > 3 hoặc có dấu ba chấm ở dòng cuối
                    if (lineCount >= 3 && (lineCount > 3 || layout.getEllipsisCount(lineCount - 1) > 0)) {
                        binding.tvToggleExpand.setVisibility(View.VISIBLE);
                        binding.tvToggleExpand.setOnClickListener(v -> {
                            if (binding.tvComment.getMaxLines() == 3) {
                                binding.tvComment.setMaxLines(Integer.MAX_VALUE);
                                binding.tvComment.setEllipsize(null);
                                binding.tvToggleExpand.setText("Thu gọn");
                            } else {
                                binding.tvComment.setMaxLines(3);
                                binding.tvComment.setEllipsize(TextUtils.TruncateAt.END);
                                binding.tvToggleExpand.setText("Xem thêm");
                            }
                        });
                    } else {
                        binding.tvToggleExpand.setVisibility(View.GONE);
                    }
                }
            });

            bindReviewImages(review);
        }

        private String formatDate(Review review) {
            java.util.Date createdAt = review.getCreatedAt();
            if (createdAt != null) {
                return dateFormat.format(createdAt);
            }
            return !TextUtils.isEmpty(review.getDate()) ? review.getDate() : "";
        }

        private void bindReviewImages(Review review) {
            List<String> media = review.getMediaUris();

            if (media == null || media.isEmpty()) {
                binding.layoutReviewImages.setVisibility(View.GONE);
                binding.ivReviewImage1.setOnClickListener(null);
                binding.ivReviewImage2.setOnClickListener(null);
                binding.ivReviewImage3.setOnClickListener(null);
                return;
            }

            List<String> urls = new ArrayList<>();
            for (String item : media) {
                if (item != null && !item.trim().isEmpty()) {
                    urls.add(item.trim());
                }
            }
            if (urls.isEmpty()) {
                binding.layoutReviewImages.setVisibility(View.GONE);
                return;
            }

            binding.layoutReviewImages.setVisibility(View.VISIBLE);

            binding.ivReviewImage1.setVisibility(View.VISIBLE);
            loadImage(urls.get(0), binding.ivReviewImage1);
            binding.ivReviewImage1.setOnClickListener(v ->
                    FullscreenImagePager.show(v.getContext(), urls, 0));

            if (urls.size() >= 2) {
                binding.ivReviewImage2.setVisibility(View.VISIBLE);
                loadImage(urls.get(1), binding.ivReviewImage2);
                binding.ivReviewImage2.setOnClickListener(v ->
                        FullscreenImagePager.show(v.getContext(), urls, 1));
            } else {
                binding.ivReviewImage2.setVisibility(View.GONE);
                binding.ivReviewImage2.setOnClickListener(null);
            }

            if (urls.size() >= 3) {
                binding.ivReviewImage3.setVisibility(View.VISIBLE);
                loadImage(urls.get(2), binding.ivReviewImage3);
                binding.ivReviewImage3.setOnClickListener(v ->
                        FullscreenImagePager.show(v.getContext(), urls, 2));

                if (urls.size() > 3) {
                    binding.tvMoreImages.setVisibility(View.VISIBLE);
                    binding.tvMoreImages.setText("+" + (urls.size() - 3));
                    binding.tvMoreImages.setOnClickListener(v ->
                            FullscreenImagePager.show(v.getContext(), urls, 2));
                } else {
                    binding.tvMoreImages.setVisibility(View.GONE);
                    binding.tvMoreImages.setOnClickListener(null);
                }
            } else {
                binding.ivReviewImage3.setVisibility(View.GONE);
                binding.ivReviewImage3.setOnClickListener(null);
                binding.tvMoreImages.setVisibility(View.GONE);
                binding.tvMoreImages.setOnClickListener(null);
            }
        }

        private void loadImage(String url, com.google.android.material.imageview.ShapeableImageView imageView) {
            Glide.with(imageView.getContext())
                    .load(url)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(imageView);
        }
    }
}
