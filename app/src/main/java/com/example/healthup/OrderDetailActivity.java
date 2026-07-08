package com.example.healthup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.example.healthup.databinding.ActivityOrderDetailBinding;
import com.example.healthup.databinding.DialogLoadingBinding;
import com.example.healthup.databinding.DialogSuccessBinding;
import com.example.healthup.databinding.ItemOrderProductBinding;
import com.example.healthup.databinding.LayoutBottomSheetCancelOrderBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.ReturnReason;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private ActivityOrderDetailBinding binding;
    private DecimalFormat df = new DecimalFormat("#,###đ");
    private SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
    private SimpleDateFormat sdfDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private Order currentOrder;

    private final ActivityResultLauncher<Intent> addressLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    com.example.models.Address address = (com.example.models.Address) result.getData().getSerializableExtra("selected_address");
                    if (address != null) {
                        updateOrderAddress(address);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrderDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix: Xử lý lề hệ thống để tránh bị thanh điều hướng che mất các nút ở dưới cùng
        View root = findViewById(R.id.order_detail_root);
        if (root != null) {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets systemBars = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return windowInsets;
            });
        }

        currentOrder = (Order) getIntent().getSerializableExtra("order");
        String orderIdFallback = getIntent().getStringExtra(EXTRA_ORDER_ID);

        if (currentOrder != null) {
            if (currentOrder.getId() == null || currentOrder.getId().isEmpty()) {
                currentOrder.setId(orderIdFallback);
            }
            setupOrder(currentOrder);
        }

        String fetchId = orderIdFallback;
        if ((fetchId == null || fetchId.isEmpty()) && currentOrder != null) {
            fetchId = currentOrder.getId();
        }
        if (fetchId != null && !fetchId.isEmpty()) {
            loadOrderFromFirestore(fetchId);
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnCancelOrder.setOnClickListener(v -> showCancelOrderBottomSheet());
        binding.btnConfirmReceived.setOnClickListener(v -> handleConfirmReceived());

        setupSupportAndContactListeners();
    }

    private void setupSupportAndContactListeners() {
        binding.rowFAQ.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigate_to", "faq");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // Cả 3 mục Chat, Phone, Email đều dẫn tới lựa chọn liên hệ nhanh như yêu cầu
        View.OnClickListener contactClick = v -> {
            if (currentOrder != null) {
                showContactOptions(currentOrder);
            }
        };

        binding.rowChat.setOnClickListener(contactClick);
        binding.rowContactPhone.setOnClickListener(contactClick);
        binding.rowContactEmail.setOnClickListener(contactClick);
    }

    private void showContactOptions(Order order) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        com.example.healthup.databinding.LayoutBottomSheetContactOptionsBinding dialogBinding = 
            com.example.healthup.databinding.LayoutBottomSheetContactOptionsBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        dialogBinding.btnChat.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(ChatActivity.buyerIntentForOrder(this, order.getOrderCode(), order.getId()));
        });

        dialogBinding.btnCall.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(android.net.Uri.parse("tel:0769845728"));
            startActivity(intent);
        });

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void loadOrderFromFirestore(String orderId) {
        FirebaseFirestore.getInstance()
                .collection("orders")
                .document(orderId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Order order = doc.toObject(Order.class);
                        if (order != null) {
                            order.setId(doc.getId());
                            normalizeOrderItems(doc, order);
                            setupOrder(order);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Không thể tải đơn hàng", Toast.LENGTH_SHORT).show());
    }

    private void normalizeOrderItems(DocumentSnapshot doc, Order order) {
        if (order.getItems() == null) return;
        Object rawItems = doc.get("items");
        if (!(rawItems instanceof List)) return;
        List<?> rawList = (List<?>) rawItems;
        List<OrderItem> items = order.getItems();
        for (int i = 0; i < items.size() && i < rawList.size(); i++) {
            OrderItem item = items.get(i);
            if (item.getVariantLabel() != null && !item.getVariantLabel().trim().isEmpty()) {
                continue;
            }
            if (rawList.get(i) instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) rawList.get(i);
                Object variantName = map.get("variantName");
                if (variantName == null) variantName = map.get("variantLabel");
                if (variantName != null) {
                    String label = String.valueOf(variantName).trim();
                    if (!label.isEmpty() && !"null".equalsIgnoreCase(label)) {
                        item.setVariantLabel(label);
                    }
                }
            }
        }
    }

    private void setupOrder(Order order) {
        currentOrder = order;
        populateUI(order);
        binding.btnReturnRefundDetail.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReturnRefundActivity.class);
            intent.putExtra("orderId", currentOrder.getId());
            intent.putExtra("orderCode", currentOrder.getOrderCode());
            intent.putExtra("items", new java.util.ArrayList<>(currentOrder.getItems()));
            intent.putExtra("paymentMethod", currentOrder.getPaymentMethod());
            intent.putExtra("shippingAddress", currentOrder.getAddress().getAddressDetail());
            startActivity(intent);
        });

        binding.btnUpdateAddress.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddressBookActivity.class);
            intent.putExtra("select_mode", true);
            addressLauncher.launch(intent);
        });
    }

    private void updateOrderAddress(com.example.models.Address newAddress) {
        if (currentOrder == null || newAddress == null) return;

        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(getLayoutInflater());
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("address", newAddress);
        updates.put("updatedAt", com.google.firebase.Timestamp.now());

        FirebaseFirestore.getInstance().collection("orders").document(currentOrder.getId())
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                loadingDialog.dismiss();
                currentOrder.setAddress(newAddress);
                populateUI(currentOrder);
                Toast.makeText(this, "Cập nhật địa chỉ thành công", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                loadingDialog.dismiss();
                Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void handleConfirmReceived() {
        showLoadingAndThenUpdateFirebase("delivered", null);
    }

    private void showCancelOrderBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetDialogTheme);
        LayoutBottomSheetCancelOrderBinding dialogBinding = LayoutBottomSheetCancelOrderBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        List<ReturnReason> reasons = Arrays.asList(
                new ReturnReason("Tôi muốn thay đổi địa chỉ giao hàng", ""),
                new ReturnReason("Tôi muốn thay đổi sản phẩm", ""),
                new ReturnReason("Đặt nhầm sản phẩm", ""),
                new ReturnReason("Tìm thấy giá tốt hơn ở nơi khác", ""),
                new ReturnReason("Thời gian giao hàng quá lâu", ""),
                new ReturnReason("Lý do khác", "")
        );

        dialogBinding.rvReasons.setLayoutManager(new LinearLayoutManager(this));
        ReturnReasonAdapter adapter = new ReturnReasonAdapter(reasons, null);
        dialogBinding.rvReasons.setAdapter(adapter);

        dialogBinding.btnBack.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmCancel.setOnClickListener(v -> {
            ReturnReason selected = adapter.getSelectedReason();
            if (selected == null) {
                Toast.makeText(this, "Vui lòng chọn lý do hủy đơn", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showLoadingAndThenUpdateFirebase("cancelled", selected.getTitle());
        });

        dialog.show();
    }

    private void showLoadingAndThenUpdateFirebase(String targetStatus, String reason) {
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(getLayoutInflater());
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();

        com.google.android.gms.tasks.Task<Void> updateTask;
        String targetTab;
        String orderId = currentOrder.getId();

        if (orderId == null || orderId.isEmpty()) {
            loadingDialog.dismiss();
            Toast.makeText(this, "Lỗi: Không tìm thấy ID đơn hàng", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("cancelled".equals(targetStatus)) {
            updateTask = FirebaseManager.getInstance().cancelOrder(orderId, reason, currentOrder.getTotalPrice());
            targetTab = "cancelled_tab";
        } else {
            // Update to delivered
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("status", "delivered");
            updates.put("updatedAt", new java.util.Date());
            updates.put("deliveredAt", new java.util.Date());
            updateTask = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("orders").document(orderId).update(updates);
            targetTab = "delivered_tab";
        }

        updateTask.addOnSuccessListener(aVoid -> {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                loadingDialog.dismiss();
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("navigate_to", targetTab);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }, 1500); // Tăng lên 1.5s cho mượt
        }).addOnFailureListener(e -> {
            loadingDialog.dismiss();
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("permission")) {
                errorMsg = "Bạn không có quyền cập nhật đơn hàng này.";
            }
            Toast.makeText(this, "Lỗi cập nhật: " + errorMsg, Toast.LENGTH_LONG).show();
        });
    }

    private void performRebuy() {
        if (currentOrder == null) return;
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(getLayoutInflater());
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();

        FirebaseManager.getInstance().rebuyOrder(currentOrder.getItems())
            .addOnSuccessListener(aVoid -> {
                loadingDialog.dismiss();
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("navigate_to", "cart_tab");
                intent.putExtra("is_rebuy", true);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            })
            .addOnFailureListener(e -> {
                loadingDialog.dismiss();
                Toast.makeText(this, "Lỗi mua lại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void populateUI(Order order) {
        String status = order.getStatus().toLowerCase();
        
        if ("pending".equals(status)) {
            binding.tvStatusBanner.setText("CHỜ XÁC NHẬN");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        } else if ("confirmed".equals(status)) {
            binding.tvStatusBanner.setText("CHỜ LẤY HÀNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        } else if ("shipping".equals(status)) {
            binding.tvStatusBanner.setText("CHỜ GIAO HÀNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_pending);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.text_main));
        } else if ("delivered".equals(status)) {
            binding.tvStatusBanner.setText("ĐÃ GIAO HÀNG");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_delivered);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        } else if ("cancelled".equals(status)) {
            binding.tvStatusBanner.setText("ĐÃ HỦY");
            binding.tvStatusBanner.setBackgroundResource(R.drawable.bg_status_cancelled);
            binding.tvStatusBanner.setTextColor(getResources().getColor(R.color.white));
        }

        String paymentMethod = order.getPaymentMethod();
        if ("cod".equalsIgnoreCase(paymentMethod)) paymentMethod = "Thanh toán khi nhận hàng (COD)";
        else if ("momo".equalsIgnoreCase(paymentMethod)) paymentMethod = "Ví MoMo";
        else if ("zalopay".equalsIgnoreCase(paymentMethod)) paymentMethod = "Ví ZaloPay";
        else if ("vnpay".equalsIgnoreCase(paymentMethod)) paymentMethod = "Ví VNPAY";
        else if ("card".equalsIgnoreCase(paymentMethod)) paymentMethod = "Thẻ Tín dụng / Ghi nợ";

        binding.tvPaymentMethod.setText("Thanh toán bằng " + paymentMethod);
        if (paymentMethod.contains("Thẻ") || paymentMethod.contains("Tài khoản") || paymentMethod.contains("card")) {
            binding.imgPaymentIcon.setImageResource(R.drawable.ic_payment_card);
        } else {
            binding.imgPaymentIcon.setImageResource(R.drawable.ic_payment_wallet);
        }

        if (order.getAddress() != null) {
            binding.tvCustomerName.setText(order.getAddress().getRecipientName());
            binding.tvCustomerPhone.setText("(+84) " + order.getAddress().getPhone().substring(1));
            binding.tvShippingAddress.setText(order.getAddress().getAddressDetail());
        }

        if ("shipping".equals(status) || "delivered".equals(status) || "cancelled".equals(status) || "returned".equals(status)) {
            binding.btnUpdateAddress.setVisibility(View.GONE);
        } else {
            binding.btnUpdateAddress.setVisibility(View.VISIBLE);
        }

        binding.tvOrderCode.setText(order.getOrderCode());
        binding.btnCopyOrderCode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Order Code", order.getOrderCode());
            clipboard.setPrimaryClip(clip);
        });

        binding.lnItemsContainer.removeAllViews();
        List<OrderItem> items = order.getItems();
        for (OrderItem item : items) {
            ItemOrderProductBinding pBinding = ItemOrderProductBinding.inflate(
                    LayoutInflater.from(this), binding.lnItemsContainer, false);
            pBinding.tvProductName.setText(item.getName());
            pBinding.tvVariant.setText(item.getVariantLabel());
            pBinding.tvPrice.setText(df.format(item.getPrice()));
            pBinding.tvQuantity.setText("x" + item.getQuantity());

            if (item.getOriginalPrice() > item.getPrice() && item.getOriginalPrice() > 0) {
                pBinding.tvPriceOld.setVisibility(View.VISIBLE);
                pBinding.tvPriceOld.setText(df.format(item.getOriginalPrice()));
                pBinding.tvPriceOld.setPaintFlags(pBinding.tvPriceOld.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                pBinding.tvPriceOld.setVisibility(View.GONE);
            }

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
                        .into(pBinding.imgProduct);
            } else {
                pBinding.imgProduct.setImageResource(R.drawable.ic_launcher_background);
            }

            pBinding.btnAskProduct.setOnClickListener(v -> openProductChat(item));

            // Click product image or name to see product details
            View.OnClickListener toProductDetail = v -> {
                if (item.getProductId() != null) {
                    Intent detailIntent = new Intent(this, ProductDetailActivity.class);
                    detailIntent.putExtra("productId", item.getProductId());
                    startActivity(detailIntent);
                }
            };
            pBinding.imgProduct.setOnClickListener(toProductDetail);
            pBinding.tvProductName.setOnClickListener(toProductDetail);

            binding.lnItemsContainer.addView(pBinding.getRoot());
        }

        if ("confirmed".equals(status)) {
            long shipBeforeTime = (order.getCreatedAt() != null ? order.getCreatedAt().getTime() : System.currentTimeMillis()) + 86400000L;
            binding.tvOrderTime.setText("Đơn hàng sẽ được gửi đi trước " + sdf.format(new java.util.Date(shipBeforeTime)));
        } else if ("shipping".equals(status)) {
            if (order.isShopConfirmedDelivery()) {
                java.util.Date deliveredDate = order.getDeliveredAt() != null ? order.getDeliveredAt() : new java.util.Date();
                binding.tvOrderTime.setText("Đơn hàng đã được giao thành công vào " + sdf.format(deliveredDate));
            } else {
                long deliveryBeforeTime = (order.getCreatedAt() != null ? order.getCreatedAt().getTime() : System.currentTimeMillis()) + 3 * 86400000L;
                binding.tvOrderTime.setText("Đơn hàng sẽ được giao đến bạn trước ngày " + sdfDate.format(new java.util.Date(deliveryBeforeTime)));
            }
        } else if ("delivered".equals(status)) {
            java.util.Date deliveryDate = order.getDeliveredAt() != null ? order.getDeliveredAt() : 
                               (order.getCreatedAt() != null ? new java.util.Date(order.getCreatedAt().getTime() + 2 * 86400000L) : new java.util.Date());
            binding.tvOrderTime.setText("Đơn hàng đã được giao thành công vào " + sdf.format(deliveryDate));
        } else {
            String timeStr = order.getCreatedAt() != null ? sdf.format(order.getCreatedAt()) : "N/A";
            binding.tvOrderTime.setText("Thời gian đặt hàng: " + timeStr);
        }

        binding.btnCancelOrder.setVisibility(View.GONE);
        binding.btnConfirmReceived.setVisibility(View.GONE);
        binding.lnDeliveredActions.setVisibility(View.GONE);
        binding.cvCancelledInfo.setVisibility(View.GONE);
        binding.btnRebuyFull.setVisibility(View.GONE);

        if ("pending".equals(status)) {
            binding.btnCancelOrder.setVisibility(View.VISIBLE);
        } else if ("shipping".equals(status)) {
            binding.btnConfirmReceived.setVisibility(View.VISIBLE);
            if (order.isShopConfirmedDelivery()) {
                binding.btnConfirmReceived.setEnabled(true);
                binding.btnConfirmReceived.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary)));
                binding.btnConfirmReceived.setTextColor(getResources().getColor(R.color.white));
            } else {
                binding.btnConfirmReceived.setEnabled(false);
                binding.btnConfirmReceived.setBackgroundTintList(ColorStateList.valueOf(0xFFF0F0F0));
                binding.btnConfirmReceived.setTextColor(0xFF888888);
            }
        } else if ("delivered".equals(status)) {
            binding.lnDeliveredActions.setVisibility(View.VISIBLE);
            binding.btnRebuyDetail.setOnClickListener(v -> performRebuy());
            if (order.isReviewed()) {
                binding.btnReviewDetail.setText("Xem đánh giá");
                binding.btnReviewDetail.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ViewReviewsActivity.class);
                    intent.putExtra("order", order);
                    intent.putExtra("extra_order_id", order.getId());
                    startActivity(intent);
                });
            } else {
                binding.btnReviewDetail.setText("Đánh giá");
                binding.btnReviewDetail.setOnClickListener(v -> {
                    Intent intent = new Intent(this, WriteReviewActivity.class);
                    intent.putExtra("order", order);
                    startActivity(intent);
                });
            }
        } else if ("cancelled".equals(status)) {
            binding.cvCancelledInfo.setVisibility(View.VISIBLE);
            binding.btnRebuyFull.setVisibility(View.VISIBLE);
            binding.btnRebuyFull.setOnClickListener(v -> performRebuy());
            if (order.getUpdatedAt() != null) {
                binding.tvCancelledTime.setText(sdf.format(order.getUpdatedAt()));
            }
            String method = order.getPaymentMethod();
            binding.tvPaymentMethodCancelled.setText(method.contains("Thanh toán khi nhận hàng") ? "COD" : method);

            if (!method.contains("Thanh toán khi nhận hàng")) {
                binding.tvRefundCancelledInfo.setVisibility(View.VISIBLE);
                String refundMsg;
                if (method.contains("MoMo")) refundMsg = "Tiền sẽ được hoàn về ví MoMo của bạn trong vòng 24h làm việc.";
                else if (method.contains("ZaloPay")) refundMsg = "Tiền sẽ được hoàn về ví ZaloPay của bạn trong vòng 24h làm việc.";
                else if (method.contains("VNPAY")) refundMsg = "Tiền sẽ được hoàn về tài khoản VNPAY của bạn trong vòng 1-3 ngày làm việc.";
                else if (method.contains("Thẻ ATM") || method.contains("Tài khoản")) refundMsg = "Tiền sẽ được hoàn về đúng thẻ/tài khoản bạn đã dùng thanh toán trong vòng 3-7 ngày làm việc.";
                else refundMsg = "Tiền sẽ được hoàn về tài khoản của bạn trong vòng 3-7 ngày làm việc.";
                binding.tvRefundCancelledInfo.setText(refundMsg);
            } else {
                binding.tvRefundCancelledInfo.setVisibility(View.GONE);
            }
        }
    }


    private void openProductChat(OrderItem item) {
        if (item == null) {
            return;
        }
        OrderChatHelper.openProductChat(this, currentOrder, getDisplayedOrderCode(), item);
    }

    @Nullable
    private String getDisplayedOrderCode() {
        CharSequence label = binding.tvOrderCode.getText();
        return label != null ? label.toString() : null;
    }
}
