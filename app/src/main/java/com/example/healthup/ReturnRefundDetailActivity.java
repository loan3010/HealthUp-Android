package com.example.healthup;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityReturnRefundDetailBinding;
import com.example.healthup.databinding.DialogLoadingBinding;
import com.example.healthup.databinding.DialogSuccessBinding;
import com.example.healthup.databinding.ItemProductSelectedDetailBinding;
import com.example.healthup.databinding.LayoutBottomSheetChooseReasonBinding;
import com.example.healthup.databinding.LayoutBottomSheetSelectProductBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.ReturnHandling;
import com.example.models.ReturnReason;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReturnRefundDetailActivity extends BaseAppCompatActivity {
    private ActivityReturnRefundDetailBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
    private ArrayList<OrderItem> allOrderItems;
    private Map<OrderItem, Integer> selectedItemsMap = new HashMap<>();
    private String paymentMethod;
    private String shippingAddress;
    private String orderId;
    private boolean isMissingItemsRequest;
    
    private List<Uri> mediaUris = new ArrayList<>();
    private MediaAdapter mediaAdapter;
    private Uri photoUri;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        ClipData clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            addMedia(clipData.getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        addMedia(data.getData());
                    }
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    addMedia(photoUri);
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCameraIntent();
                } else {
                    Toast.makeText(this, "Bạn cần cấp quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                if (!uris.isEmpty()) {
                    int remaining = 5 - mediaUris.size();
                    int toAdd = Math.min(uris.size(), remaining);
                    for (int i = 0; i < toAdd; i++) {
                        addMedia(uris.get(i));
                    }
                    if (uris.size() > remaining && remaining > 0) {
                        Toast.makeText(this, "Chỉ lấy thêm " + remaining + " ảnh/video để đủ tối đa 5", Toast.LENGTH_SHORT).show();
                    } else if (remaining <= 0) {
                        Toast.makeText(this, "Tối đa 5 ảnh/video", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReturnRefundDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix: Xử lý lề hệ thống để tránh bị thanh điều hướng che mất nút dưới cùng
        View root = findViewById(R.id.return_refund_detail_root);
        if (root != null) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return windowInsets;
            });
        }

        allOrderItems = (ArrayList<OrderItem>) getIntent().getSerializableExtra("items");
        paymentMethod = getIntent().getStringExtra("paymentMethod");
        shippingAddress = getIntent().getStringExtra("shippingAddress");
        orderId = getIntent().getStringExtra("orderId");
        String initialReason = getIntent().getStringExtra("reason");
        isMissingItemsRequest = "Thiếu hàng".equals(initialReason);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.cvSelectProduct.setOnClickListener(v -> showProductSelectBottomSheet());
        binding.rlReason.setOnClickListener(v -> showReasonBottomSheet());
        binding.rlHandlingMethod.setOnClickListener(v -> showHandlingBottomSheet());

        setupUIByRequestType();
        setupDescriptionValidation();
        setupMediaRecyclerView();
        setupRefundMethodUI();
        setupShippingAddressUI();

        binding.btnSubmit.setOnClickListener(v -> {
            validateAndSubmit();
        });

        updateSelectedProductsUI();
    }

    private void validateAndSubmit() {
        boolean isValid = true;
        View firstInvalidView = null;

        binding.tvProductWarning.setVisibility(View.GONE);
        binding.tvDescriptionWarning.setVisibility(View.GONE);
        binding.tvMediaWarning.setVisibility(View.GONE);

        if (selectedItemsMap.isEmpty()) {
            binding.tvProductWarning.setVisibility(View.VISIBLE);
            firstInvalidView = binding.tvProductHeader;
            isValid = false;
        }

        String description = binding.etDescription.getText().toString().trim();
        if (!isMissingItemsRequest && description.length() < 10) {
            binding.tvDescriptionWarning.setVisibility(View.VISIBLE);
            if (firstInvalidView == null) firstInvalidView = binding.etDescription;
            isValid = false;
        }

        if (mediaUris.isEmpty()) {
            binding.tvMediaWarning.setVisibility(View.VISIBLE);
            if (firstInvalidView == null) firstInvalidView = binding.rvMedia;
            isValid = false;
        }

        if (!isValid) {
            View finalFirstInvalidView = firstInvalidView;
            binding.scrollView.post(() -> {
                if (finalFirstInvalidView != null) {
                    binding.scrollView.smoothScrollTo(0, finalFirstInvalidView.getTop() - 100);
                }
            });
            return;
        }

        submitToFirebase(description);
    }

    private void submitToFirebase(String description) {
        AlertDialog loadingDialog = showLoadingDialog();
        
        // 1. Upload images to Storage first
        FirebaseManager.getInstance().uploadMultipleImages(mediaUris).addOnSuccessListener(urls -> {
            List<String> stringUrls = new ArrayList<>();
            for (Uri u : urls) stringUrls.add(u.toString());

            String reason = isMissingItemsRequest ? "Thiếu hàng" : binding.tvSelectedReason.getText().toString();
            String handling = isMissingItemsRequest ? binding.tvSelectedHandling.getText().toString() : "Trả hàng & Hoàn tiền";

            // 2. Submit request to Firestore
            java.util.List<java.util.Map<String, Object>> returnItems = new java.util.ArrayList<>();
            for (Map.Entry<OrderItem, Integer> entry : selectedItemsMap.entrySet()) {
                OrderItem item = entry.getKey();
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("productId", item.getProductId() != null ? item.getProductId() : "");
                row.put("variantId", item.getVariantId() != null ? item.getVariantId() : "");
                row.put("name", item.getName() != null ? item.getName() : "");
                row.put("variantLabel", item.getVariantLabel() != null ? item.getVariantLabel() : "");
                row.put("quantity", entry.getValue());
                row.put("price", item.getPrice());
                row.put("imageUrl", item.getImageUrl() != null ? item.getImageUrl() : "");
                returnItems.add(row);
            }

            FirebaseManager.getInstance().submitReturnRequest(orderId, reason, description, stringUrls, handling, returnItems)
                .addOnSuccessListener(aVoid -> {
                    loadingDialog.dismiss();
                    showSuccessDialog("Gửi yêu cầu thành công", "returned_tab");
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    Toast.makeText(this, "Lỗi khi gửi yêu cầu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }).addOnFailureListener(e -> {
            loadingDialog.dismiss();
            Toast.makeText(this, "Lỗi tải ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private AlertDialog showLoadingDialog() {
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(getLayoutInflater());
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();
        return loadingDialog;
    }

    private void showSuccessDialog(String message, String targetTab) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        DialogSuccessBinding dialogBinding = DialogSuccessBinding.inflate(getLayoutInflater());
        builder.setView(dialogBinding.getRoot());
        dialogBinding.tvMessage.setText(message);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogBinding.btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigate_to", targetTab);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        dialog.setCancelable(false);
        dialog.show();
    }

    private void setupUIByRequestType() {
        if (isMissingItemsRequest) {
            binding.tvBannerInfo.setText("Loại yêu cầu: Hoàn tiền – Với trường hợp giao thiếu hàng, HealthUp sẽ hoàn lại số tiền tương ứng với giá trị sản phẩm bị thiếu.");
            binding.tvProductHeader.setText("Sản phẩm bị thiếu");
            binding.lnReasonSection.setVisibility(View.GONE);
            binding.lnHandlingSection.setVisibility(View.VISIBLE);
            updateHandlingUI("Hoàn tiền sản phẩm bị thiếu");
            binding.etDescription.setHint("Mô tả về việc giao thiếu hàng...");
        } else {
            binding.tvBannerInfo.setText("Loại yêu cầu: Trả hàng & Hoàn tiền – Với trường hợp sai hàng/lỗi hàng, bạn cần gửi trả lại sản phẩm để được hoàn tiền.");
            binding.tvProductHeader.setText("Sản phẩm bị lỗi/hư hỏng");
            binding.lnReasonSection.setVisibility(View.VISIBLE);
            binding.lnHandlingSection.setVisibility(View.GONE);
            binding.lnRefundMethodSection.setVisibility(View.VISIBLE);
            binding.rlRefundPreview.setVisibility(View.VISIBLE);
            binding.lnShippingSection.setVisibility(View.VISIBLE);
            binding.tvShippingHeader.setText("Shipper đến lấy tại địa chỉ giao hàng");
            binding.etDescription.setHint("Mô tả thêm về tình trạng sản phẩm...");
        }
    }

    private void updateHandlingUI(String method) {
        binding.tvSelectedHandling.setText(method);
        if ("Hoàn tiền sản phẩm bị thiếu".equals(method)) {
            binding.lnRefundMethodSection.setVisibility(View.VISIBLE);
            binding.rlRefundPreview.setVisibility(View.VISIBLE);
            binding.lnShippingSection.setVisibility(View.GONE);
        } else {
            binding.lnRefundMethodSection.setVisibility(View.GONE);
            binding.rlRefundPreview.setVisibility(View.GONE);
            binding.lnShippingSection.setVisibility(View.VISIBLE);
            binding.tvShippingHeader.setText("Shipper giao hàng bổ sung đến địa chỉ giao hàng");
        }
    }

    private void setupDescriptionValidation() {
        binding.etDescription.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isMissingItemsRequest && s.length() > 0 && s.length() < 10) {
                    binding.tvDescriptionWarning.setVisibility(View.VISIBLE);
                } else {
                    binding.tvDescriptionWarning.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupMediaRecyclerView() {
        mediaAdapter = new MediaAdapter(mediaUris, new MediaAdapter.OnMediaClickListener() {
            @Override public void onAddClick() { showImageSourceDialog(); }
            @Override public void onRemoveClick(int position) {
                mediaUris.remove(position);
                mediaAdapter.notifyDataSetChanged();
            }
        });
        binding.rvMedia.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvMedia.setAdapter(mediaAdapter);
    }

    private void showImageSourceDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        com.example.healthup.databinding.LayoutBottomSheetImageSourceBinding dialogBinding =
                com.example.healthup.databinding.LayoutBottomSheetImageSourceBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        dialogBinding.btnCamera.setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });

        dialogBinding.btnGallery.setOnClickListener(v -> {
            dialog.dismiss();
            launchGallery();
        });

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void launchGallery() {
        int remaining = 5 - mediaUris.size();
        if (remaining <= 0) {
            Toast.makeText(this, "Tối đa 5 ảnh/video", Toast.LENGTH_SHORT).show();
            return;
        }
        pickMultipleMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                .build());
    }

    private void launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraIntent();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
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

    private void addMedia(Uri uri) {
        if (mediaUris.size() < 5) {
            mediaUris.add(uri);
            mediaAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Tối đa 5 ảnh/video", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRefundMethodUI() {
        if (paymentMethod == null) return;
        String refundMethod = paymentMethod;
        int iconRes = R.drawable.ic_payment_wallet;
        
        String pm = paymentMethod.toLowerCase();
        // Cập nhật mapping code -> text đầy đủ
        if (pm.contains("cod") || pm.contains("nhận hàng")) {
            refundMethod = "Tài khoản Ngân hàng liên kết";
            iconRes = R.drawable.ic_payment_card;
        } else if (pm.contains("momo")) {
            refundMethod = "Ví MoMo";
            iconRes = R.drawable.ic_payment_wallet;
        } else if (pm.contains("zalopay")) {
            refundMethod = "Ví ZaloPay";
            iconRes = R.drawable.ic_payment_wallet;
        } else if (pm.contains("vnpay")) {
            refundMethod = "Ví VNPAY";
            iconRes = R.drawable.ic_payment_wallet;
        } else if (pm.contains("card") || pm.contains("thẻ") || pm.contains("tài khoản")) {
            refundMethod = "Thẻ Tín dụng / Ghi nợ";
            iconRes = R.drawable.ic_payment_card;
        }

        String displayInfo = refundMethod;
        if (refundMethod.contains("Ví")) displayInfo += " – 09xx xxx 567";
        else if (refundMethod.contains("Ngân hàng") || refundMethod.contains("Thẻ")) displayInfo += " – **** 1234";

        binding.tvRefundMethod.setText(displayInfo);
        binding.imgRefundMethod.setImageResource(iconRes);
        binding.rlRefundMethod.setOnClickListener(null);
    }

    private void setupShippingAddressUI() {
        if (shippingAddress != null) binding.tvShippingAddress.setText(shippingAddress);
    }

    private void showProductSelectBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LayoutBottomSheetSelectProductBinding dialogBinding = LayoutBottomSheetSelectProductBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        dialogBinding.rvSelectProducts.setLayoutManager(new LinearLayoutManager(this));
        ProductSelectAdapter adapter = new ProductSelectAdapter(allOrderItems, selectedItemsMap);
        dialogBinding.rvSelectProducts.setAdapter(adapter);
        dialogBinding.btnClose.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirm.setOnClickListener(v -> {
            selectedItemsMap = adapter.getSelectedItems();
            updateSelectedProductsUI();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showReasonBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LayoutBottomSheetChooseReasonBinding dialogBinding = LayoutBottomSheetChooseReasonBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        List<ReturnReason> reasons = Arrays.asList(
                new ReturnReason("Hàng bị lỗi/hư hỏng", "Sản phẩm bị móp, vỡ, rách bao bì hoặc hư hỏng"),
                new ReturnReason("Hàng hết hạn sử dụng", "Sản phẩm nhận được đã hết hạn hoặc cận hạn"),
                new ReturnReason("Sản phẩm khác mô tả/hình ảnh", "Không đúng như thông tin hiển thị khi đặt hàng"),
                new ReturnReason("Bao bì bị bóp méo/rách", "Bao bì bên ngoài hư hại trong quá trình vận chuyển"),
                new ReturnReason("Lý do khác", "Sản phẩm không phù hợp hoặc lý do cá nhân khác")
        );
        dialogBinding.rvReasons.setLayoutManager(new LinearLayoutManager(this));
        ReturnReasonAdapter adapter = new ReturnReasonAdapter(reasons, binding.tvSelectedReason.getText().toString());
        dialogBinding.rvReasons.setAdapter(adapter);
        dialogBinding.btnClose.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmReason.setOnClickListener(v -> {
            ReturnReason selected = adapter.getSelectedReason();
            if (selected != null) binding.tvSelectedReason.setText(selected.getTitle());
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showHandlingBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LayoutBottomSheetChooseReasonBinding dialogBinding = LayoutBottomSheetChooseReasonBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        List<ReturnHandling> methods = Arrays.asList(
                new ReturnHandling("Hoàn tiền sản phẩm bị thiếu", "HealthUp sẽ hoàn lại số tiền tương ứng với giá trị sản phẩm"),
                new ReturnHandling("Nhận bổ sung sản phẩm bị thiếu", "HealthUp sẽ gửi bù sản phẩm thiếu đến địa chỉ của bạn")
        );
        dialogBinding.rvReasons.setLayoutManager(new LinearLayoutManager(this));
        ReturnHandlingAdapter adapter = new ReturnHandlingAdapter(methods, binding.tvSelectedHandling.getText().toString());
        dialogBinding.rvReasons.setAdapter(adapter);
        dialogBinding.btnClose.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmReason.setOnClickListener(v -> {
            ReturnHandling selected = adapter.getSelectedMethod();
            if (selected != null) updateHandlingUI(selected.getTitle());
            dialog.dismiss();
        });
        dialog.show();
    }

    private void updateSelectedProductsUI() {
        binding.lnSelectedProductsContainer.removeAllViews();
        double totalRefund = 0;
        for (Map.Entry<OrderItem, Integer> entry : selectedItemsMap.entrySet()) {
            OrderItem item = entry.getKey();
            int qty = entry.getValue();
            totalRefund += item.getPrice() * qty;
            ItemProductSelectedDetailBinding itemBinding = ItemProductSelectedDetailBinding.inflate(getLayoutInflater(), binding.lnSelectedProductsContainer, false);
            itemBinding.tvProductName.setText(item.getName());
            itemBinding.tvVariant.setText(item.getVariantLabel());
            itemBinding.tvPrice.setText(df.format(item.getPrice()));
            itemBinding.tvQty.setText(String.valueOf(qty));
            itemBinding.tvMaxQtyInfo.setText("(trên tổng " + item.getQuantity() + " đã đặt)");
            itemBinding.tvQtyHeader.setText(isMissingItemsRequest ? "Số lượng bị thiếu" : "Số lượng bị lỗi");
            itemBinding.icCheck.setOnClickListener(v -> { selectedItemsMap.remove(item); updateSelectedProductsUI(); });
            
            // Click product image or name to see product details
            View.OnClickListener toProductDetail = v -> {
                if (item.getProductId() != null) {
                    Intent detailIntent = new Intent(this, ProductDetailActivity.class);
                    detailIntent.putExtra("productId", item.getProductId());
                    startActivity(detailIntent);
                }
            };
            itemBinding.imgProduct.setOnClickListener(toProductDetail);
            itemBinding.tvProductName.setOnClickListener(toProductDetail);

            // Xử lý hiển thị ảnh sản phẩm từ assets hoặc URL
            String imagePath = item.getImageUrl();
            if (imagePath != null && !imagePath.isEmpty()) {
                String cleanPath = imagePath.startsWith("/") ? imagePath.substring(1) : imagePath;
                Object loadTarget;

                if (cleanPath.startsWith("images/")) {
                    loadTarget = "file:///android_asset/" + cleanPath;
                } else if (imagePath.startsWith("http")) {
                    loadTarget = imagePath;
                } else {
                    loadTarget = "file:///android_asset/images/products/" + cleanPath;
                }

                Glide.with(this)
                        .load(loadTarget)
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .into(itemBinding.imgProduct);
            } else {
                itemBinding.imgProduct.setImageResource(R.drawable.ic_launcher_background);
            }

            itemBinding.btnPlus.setOnClickListener(v -> {
                if (selectedItemsMap.get(item) < item.getQuantity()) {
                    selectedItemsMap.put(item, selectedItemsMap.get(item) + 1);
                    updateSelectedProductsUI();
                }
            });
            itemBinding.btnMinus.setOnClickListener(v -> {
                int currentQty = selectedItemsMap.get(item);
                if (currentQty > 1) selectedItemsMap.put(item, currentQty - 1);
                else selectedItemsMap.remove(item);
                updateSelectedProductsUI();
            });
            binding.lnSelectedProductsContainer.addView(itemBinding.getRoot());
        }
        binding.tvRefundAmount.setText(df.format(totalRefund));
    }
}
