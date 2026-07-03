package com.example.healthup;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityWriteReviewBinding;
import com.example.healthup.databinding.DialogLoadingBinding;
import com.example.healthup.databinding.DialogSuccessBinding;
import com.example.healthup.databinding.ItemWriteReviewBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.Review;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WriteReviewActivity extends AppCompatActivity {
    private ActivityWriteReviewBinding binding;
    private Order order;
    private Map<Integer, List<Uri>> mediaMap = new HashMap<>();
    private Map<Integer, MediaAdapter> adapterMap = new HashMap<>();
    private int currentTargetIndex = -1;
    private Uri photoUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (!uris.isEmpty() && currentTargetIndex != -1) {
                    List<Uri> currentMedia = mediaMap.get(currentTargetIndex);
                    int remaining = 5 - currentMedia.size();
                    int toAdd = Math.min(uris.size(), remaining);
                    for (int i = 0; i < toAdd; i++) {
                        currentMedia.add(uris.get(i));
                    }
                    adapterMap.get(currentTargetIndex).notifyDataSetChanged();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && currentTargetIndex != -1) {
                    mediaMap.get(currentTargetIndex).add(photoUri);
                    adapterMap.get(currentTargetIndex).notifyDataSetChanged();
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCameraIntent();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWriteReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        order = (Order) getIntent().getSerializableExtra("order");
        if (order == null) {
            finish();
            return;
        }

        setupUI();
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSubmitReview.setOnClickListener(v -> submitReviews());
    }

    private void setupUI() {
        binding.lnReviewContainer.removeAllViews();
        List<OrderItem> items = order.getItems();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            if (item.getReview() != null) continue;

            ItemWriteReviewBinding itemBinding = ItemWriteReviewBinding.inflate(getLayoutInflater(), binding.lnReviewContainer, false);
            itemBinding.tvProductName.setText(item.getName());
            itemBinding.tvVariant.setText(item.getVariantLabel());
            Glide.with(this).load(item.getImageUrl()).placeholder(R.drawable.ic_launcher_background).into(itemBinding.imgProduct);

            final int index = i;
            List<Uri> uris = new ArrayList<>();
            mediaMap.put(index, uris);

            MediaAdapter adapter = new MediaAdapter(uris, new MediaAdapter.OnMediaClickListener() {
                @Override public void onAddClick() { currentTargetIndex = index; showImageSourceDialog(); }
                @Override public void onRemoveClick(int position) {
                    uris.remove(position);
                    adapterMap.get(index).notifyDataSetChanged();
                }
            });
            adapterMap.put(index, adapter);
            itemBinding.rvMedia.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            itemBinding.rvMedia.setAdapter(adapter);

            itemBinding.ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
                String label = "Vui lòng đánh giá";
                if (rating == 1) label = "Tệ";
                else if (rating == 2) label = "Không hài lòng";
                else if (rating == 3) label = "Bình thường";
                else if (rating == 4) label = "Hài lòng";
                else if (rating == 5) label = "Tuyệt vời";
                itemBinding.tvRatingLabel.setText(label);
                itemBinding.tvRatingLabel.setTextColor(rating > 0 ? 0xFFFFC107 : 0xFFA09894);
            });
            binding.lnReviewContainer.addView(itemBinding.getRoot());
        }
    }

    private void showImageSourceDialog() {
        String[] options = {"Chụp ảnh", "Chọn từ thư viện"};
        new AlertDialog.Builder(this).setTitle("Thêm minh chứng").setItems(options, (dialog, which) -> {
            if (which == 0) launchCamera(); else launchGallery();
        }).show();
    }

    private void launchGallery() {
        pickMultipleMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE).build());
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCameraIntent();
        else requestPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void launchCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try { photoFile = createImageFile(); } catch (IOException ex) {}
        if (photoFile != null) {
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(takePictureIntent);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", getExternalFilesDir(null));
    }

    private void submitReviews() {
        boolean atLeastOneReviewed = false;
        for (int i = 0; i < binding.lnReviewContainer.getChildCount(); i++) {
            RatingBar rb = binding.lnReviewContainer.getChildAt(i).findViewById(R.id.ratingBar);
            if (rb.getRating() > 0) { atLeastOneReviewed = true; break; }
        }
        if (!atLeastOneReviewed) {
            Toast.makeText(this, "Vui lòng đánh giá ít nhất 1 sản phẩm", Toast.LENGTH_SHORT).show();
            return;
        }
        processUploadsAndSave();
    }

    private void processUploadsAndSave() {
        AlertDialog loadingDialog = showLoadingDialog();
        
        // Map of index to List of uploaded URLs
        Map<Integer, List<String>> uploadedUrlsMap = new HashMap<>();
        List<com.google.android.gms.tasks.Task<?>> allUploadTasks = new ArrayList<>();

        for (Map.Entry<Integer, List<Uri>> entry : mediaMap.entrySet()) {
            final int index = entry.getKey();
            List<Uri> uris = entry.getValue();
            if (uris.isEmpty()) continue;

            allUploadTasks.add(FirebaseManager.getInstance().uploadMultipleImages(uris).addOnSuccessListener(urls -> {
                List<String> stringUrls = new ArrayList<>();
                for (Uri u : urls) stringUrls.add(u.toString());
                uploadedUrlsMap.put(index, stringUrls);
            }));
        }

        com.google.android.gms.tasks.Tasks.whenAllComplete(allUploadTasks).addOnCompleteListener(task -> {
            // Save reviews
            List<com.google.android.gms.tasks.Task<Void>> saveTasks = new ArrayList<>();
            for (int i = 0; i < binding.lnReviewContainer.getChildCount(); i++) {
                View view = binding.lnReviewContainer.getChildAt(i);
                RatingBar rb = view.findViewById(R.id.ratingBar);
                float rating = rb.getRating();
                if (rating > 0) {
                    String comment = ((EditText) view.findViewById(R.id.etComment)).getText().toString();
                    String title = ((TextView) view.findViewById(R.id.tvProductName)).getText().toString();
                    
                    int realIndex = -1;
                    for (int j = 0; j < order.getItems().size(); j++) {
                        if (order.getItems().get(j).getName().equals(title)) { realIndex = j; break; }
                    }

                    if (realIndex != -1) {
                        Review review = new Review(rating, comment, uploadedUrlsMap.get(realIndex), System.currentTimeMillis());
                        saveTasks.add(FirebaseManager.getInstance().updateItemReview(order.getId(), realIndex, review, order.getItems()));
                    }
                }
            }

            com.google.android.gms.tasks.Tasks.whenAll(saveTasks).addOnSuccessListener(aVoid -> {
                loadingDialog.dismiss();
                showSuccessPopup();
            }).addOnFailureListener(e -> {
                loadingDialog.dismiss();
                Toast.makeText(this, "Lỗi lưu đánh giá: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private AlertDialog showLoadingDialog() {
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(getLayoutInflater());
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        loadingDialog.show();
        return loadingDialog;
    }

    private void showSuccessPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        DialogSuccessBinding successBinding = DialogSuccessBinding.inflate(getLayoutInflater());
        builder.setView(successBinding.getRoot());
        successBinding.tvMessage.setText("Gửi đánh giá thành công");
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        successBinding.btnConfirm.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        dialog.setCancelable(false);
        dialog.show();
    }
}
