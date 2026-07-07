package com.example.healthup;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.healthup.databinding.DialogLoadingBinding;
import com.example.healthup.databinding.DialogSuccessBinding;
import com.example.healthup.databinding.ItemOrderBinding;
import com.example.healthup.databinding.ItemOrderProductBinding;
import com.example.healthup.databinding.LayoutBottomSheetCancelOrderBinding;
import com.example.models.Order;
import com.example.models.OrderItem;
import com.example.models.ReturnReason;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {
    private Context context;
    private List<Order> orders;
    private DecimalFormat df = new DecimalFormat("#,###đ");

    public OrderAdapter(Context context, List<Order> orders) {
        this.context = context;
        this.orders = orders;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemOrderBinding binding = ItemOrderBinding.inflate(LayoutInflater.from(context), parent, false);
        return new OrderViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order);
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    private void showCancelOrderBottomSheet(Order order) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        LayoutBottomSheetCancelOrderBinding dialogBinding = LayoutBottomSheetCancelOrderBinding.inflate(LayoutInflater.from(context));
        dialog.setContentView(dialogBinding.getRoot());

        List<ReturnReason> reasons = Arrays.asList(
                new ReturnReason("Tôi muốn thay đổi địa chỉ giao hàng", ""),
                new ReturnReason("Tôi muốn thay đổi sản phẩm", ""),
                new ReturnReason("Đặt nhầm sản phẩm", ""),
                new ReturnReason("Tìm thấy giá tốt hơn ở nơi khác", ""),
                new ReturnReason("Thời gian giao hàng quá lâu", ""),
                new ReturnReason("Lý do khác", "")
        );

        dialogBinding.rvReasons.setLayoutManager(new LinearLayoutManager(context));
        ReturnReasonAdapter adapter = new ReturnReasonAdapter(reasons, null);
        dialogBinding.rvReasons.setAdapter(adapter);

        dialogBinding.btnBack.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmCancel.setOnClickListener(v -> {
            ReturnReason selected = adapter.getSelectedReason();
            if (selected == null) {
                android.widget.Toast.makeText(context, "Vui lòng chọn lý do hủy đơn", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            
            String orderId = order.getId();
            if (orderId == null || orderId.isEmpty()) {
                android.widget.Toast.makeText(context, "Lỗi: Không tìm thấy ID đơn hàng", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            
            AlertDialog loadingDialog = showLoadingDialog();
            FirebaseManager.getInstance().cancelOrder(orderId, selected.getTitle(), order.getTotalPrice())
                .addOnSuccessListener(aVoid -> {
                    // Chờ 1.5s cho cảm giác đang xử lý như yêu cầu
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        loadingDialog.dismiss();
                        // Chuyển thẳng về tab đã hủy trong MainActivity
                        Intent intent = new Intent(context, MainActivity.class);
                        intent.putExtra("navigate_to", "cancelled_tab");
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        context.startActivity(intent);
                        android.widget.Toast.makeText(context, "Đã hủy đơn hàng thành công", android.widget.Toast.LENGTH_SHORT).show();
                    }, 1500);
                })
                .addOnFailureListener(e -> {
                    loadingDialog.dismiss();
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("permission")) {
                        errorMsg = "Bạn không có quyền hủy đơn hàng này hoặc chưa đăng nhập.";
                    }
                    android.widget.Toast.makeText(context, "Lỗi hủy đơn: " + errorMsg, android.widget.Toast.LENGTH_LONG).show();
                });
        });

        dialog.show();
    }

    private AlertDialog showLoadingDialog() {
        AlertDialog.Builder loadingBuilder = new AlertDialog.Builder(context);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(LayoutInflater.from(context));
        loadingBuilder.setView(loadingBinding.getRoot());
        loadingBuilder.setCancelable(false);
        AlertDialog loadingDialog = loadingBuilder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();
        return loadingDialog;
    }


    class OrderViewHolder extends RecyclerView.ViewHolder {
        private ItemOrderBinding binding;

        public OrderViewHolder(ItemOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Order order) {
            // Demo logic: Click on status text to advance status (Admin simulation)
            binding.tvStatus.setOnClickListener(v -> {
                String currentStatus = order.getStatus().toLowerCase();
                String orderId = order.getId();
                
                if ("shipping".equals(currentStatus)) {
                    // Demo: Shop confirm delivery -> Activate "Received" button
                    AlertDialog loading = showLoadingDialog();
                    FirebaseManager.getInstance().simulateShopConfirmedDelivery(orderId)
                        .addOnSuccessListener(aVoid -> {
                            loading.dismiss();
                            // Update local data to reflect change immediately
                            order.setShopConfirmedDelivery(true);
                            order.setDeliveredAt(com.google.firebase.Timestamp.now());
                            notifyItemChanged(getAdapterPosition());
                        })
                        .addOnFailureListener(e -> {
                            loading.dismiss();
                            android.widget.Toast.makeText(context, "Lỗi: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        });
                    return;
                }

                String nextStatus = null;
                String targetTab = null;

                switch (currentStatus) {
                    case "pending":
                        nextStatus = "confirmed";
                        targetTab = "confirmed_tab"; 
                        break;
                    case "confirmed":
                        nextStatus = "shipping";
                        targetTab = "shipping_tab";
                        break;
                }

                if (nextStatus != null) {
                    AlertDialog loading = showLoadingDialog();
                    String finalTargetTab = targetTab;
                    FirebaseManager.getInstance().updateOrderStatus(orderId, nextStatus)
                        .addOnSuccessListener(aVoid -> {
                            loading.dismiss();
                            Intent intent = new Intent(context, MainActivity.class);
                            intent.putExtra("navigate_to", finalTargetTab);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            context.startActivity(intent);
                        })
                        .addOnFailureListener(e -> {
                            loading.dismiss();
                            android.widget.Toast.makeText(context, "Lỗi cập nhật: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        });
                }
            });

            itemView.setOnClickListener(v -> {
                String status = order.getStatus() != null ? order.getStatus().toLowerCase() : "";
                String orderId = order.getId();
                
                android.content.Intent intent;
                if ("returned".equals(status) || "refunded".equals(status)) {
                    intent = new android.content.Intent(context, ReturnRefundHistoryDetailActivity.class);
                } else {
                    intent = new android.content.Intent(context, OrderDetailActivity.class);
                }
                
                intent.putExtra("order", order);
                intent.putExtra("extra_order_id", orderId); // Backup ID if serialization fails
                context.startActivity(intent);
            });

            setStatusUI(order);

            String priceStr = df.format(order.getTotalPrice());
            String labelStr = String.format("Tổng số tiền (%d sản phẩm): ", order.getItems().size());
            String fullStr = labelStr + priceStr;
            
            SpannableString spannable = new SpannableString(fullStr);
            int start = labelStr.length();
            int end = fullStr.length();
            
            spannable.setSpan(new ForegroundColorSpan(0xFF36873A), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            binding.tvTotalPrice.setText(spannable);

            binding.lnItemsContainer.removeAllViews();
            int maxInitial = 1;
            List<OrderItem> items = order.getItems();
            for (int i = 0; i < Math.min(items.size(), maxInitial); i++) {
                addProductView(items.get(i));
            }

            if (items.size() > maxInitial) {
                binding.tvShowMoreContainer.setVisibility(View.VISIBLE);
                binding.tvShowMore.setText("Xem thêm " + (items.size() - maxInitial) + " sản phẩm");
                binding.tvShowMoreContainer.setOnClickListener(v -> {
                    binding.tvShowMoreContainer.setVisibility(View.GONE);
                    for (int i = maxInitial; i < items.size(); i++) {
                        addProductView(items.get(i));
                    }
                });
            } else {
                binding.tvShowMoreContainer.setVisibility(View.GONE);
            }

            setButtonsUI(order);

            // Handle delivery info display for shipping state demo
            if ("shipping".equalsIgnoreCase(order.getStatus()) && order.isShopConfirmedDelivery()) {
                binding.tvDeliveryInfo.setVisibility(View.VISIBLE);
                String time = "vừa xong";
                if (order.getDeliveredAt() != null) {
                    java.text.SimpleDateFormat timeSdf = new java.text.SimpleDateFormat("HH:mm dd-MM", java.util.Locale.getDefault());
                    time = timeSdf.format(order.getDeliveredAt().toDate());
                }
                binding.tvDeliveryInfo.setText("Đơn hàng đã giao thành công vào " + time);
            } else {
                binding.tvDeliveryInfo.setVisibility(View.GONE);
            }
        }

    private void addProductView(OrderItem item) {
        ItemOrderProductBinding pBinding = ItemOrderProductBinding.inflate(
                LayoutInflater.from(context), binding.lnItemsContainer, false);
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

            Glide.with(context)
                    .load(loadTarget)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(pBinding.imgProduct);
        } else {
            pBinding.imgProduct.setImageResource(R.drawable.ic_launcher_background);
        }

        pBinding.btnAskProduct.setVisibility(View.GONE);

        // Click product image or name to see product details
        View.OnClickListener toProductDetail = v -> {
            if (item.getProductId() != null) {
                Intent detailIntent = new Intent(context, ProductDetailActivity.class);
                detailIntent.putExtra("productId", item.getProductId());
                context.startActivity(detailIntent);
            }
        };
        pBinding.imgProduct.setOnClickListener(toProductDetail);
        pBinding.tvProductName.setOnClickListener(toProductDetail);

        binding.lnItemsContainer.addView(pBinding.getRoot());
    }

        private void setStatusUI(Order order) {
            String status = order.getStatus();
            String displayStatus = "";
            int color = 0xFF36873A;

            switch (status.toLowerCase()) {
                case "pending":
                    displayStatus = "Chờ xác nhận"; color = 0xFFFF8F00; break;
                case "confirmed":
                    displayStatus = "Chờ lấy hàng"; color = 0xFFFF8F00; break;
                case "shipping":
                    displayStatus = "Chờ giao hàng"; color = 0xFFFF8F00; break;
                case "delivered":
                    displayStatus = "Hoàn thành"; color = 0xFF36873A; break;
                case "cancelled":
                    displayStatus = "Đã hủy"; color = 0xFFE53835; break;
                case "returned":
                case "refunded":
                    if ("refunded".equalsIgnoreCase(order.getPaymentStatus())) {
                        displayStatus = "Đã hoàn tiền"; color = 0xFF36873A;
                    } else {
                        displayStatus = "Đang xử lý"; color = 0xFFFF8F00;
                    }
                    break;
            }
            binding.tvStatus.setText(displayStatus);
            binding.tvStatus.setTextColor(color);
        }

        private void setButtonsUI(Order order) {
            String status = order.getStatus().toLowerCase();
            binding.btnActionLeft.setVisibility(View.GONE);
            binding.btnActionMiddle.setVisibility(View.GONE);
            binding.btnActionRight.setVisibility(View.GONE);

            switch (status) {
                case "pending":
                    setupButton(binding.btnActionMiddle, "Liên hệ", "outline");
                    binding.btnActionMiddle.setOnClickListener(v -> showContactOptions(order));
                    setupButton(binding.btnActionRight, "Hủy", "outline_error");
                    binding.btnActionRight.setOnClickListener(v -> showCancelOrderBottomSheet(order));
                    break;
                case "confirmed":
                    setupButton(binding.btnActionRight, "Liên hệ", "outline");
                    binding.btnActionRight.setOnClickListener(v -> showContactOptions(order));
                    break;
                case "shipping":
                    setupButton(binding.btnActionMiddle, "Liên hệ", "outline");
                    binding.btnActionMiddle.setOnClickListener(v -> showContactOptions(order));
                    if (!order.isShopConfirmedDelivery()) {
                        setupButton(binding.btnActionRight, "Đã nhận được hàng", "outline_disabled");
                        binding.btnActionRight.setEnabled(false);
                    } else {
                        setupButton(binding.btnActionRight, "Đã nhận được hàng", "outline_primary");
                        binding.btnActionRight.setEnabled(true);
                        binding.btnActionRight.setAlpha(1.0f);
                        binding.btnActionRight.setOnClickListener(v -> {
                            AlertDialog loadingDialog = showLoadingDialog();
                            FirebaseManager.getInstance().confirmReceived(order.getId())
                                .addOnSuccessListener(aVoid -> {
                                    loadingDialog.dismiss();
                                    Intent intent = new Intent(context, MainActivity.class);
                                    intent.putExtra("navigate_to", "delivered_tab");
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    context.startActivity(intent);
                                })
                                .addOnFailureListener(e -> {
                                    loadingDialog.dismiss();
                                    android.widget.Toast.makeText(context, "Lỗi cập nhật: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                                });
                        });
                    }
                    break;
                case "delivered":
                    boolean canReturn = !order.isReturnExpired();
                    boolean isAlreadyReviewed = order.isReviewed();
                    boolean canStillReview = !order.isReviewExpired();
                    
                    if (canReturn) {
                        // CHỈ KHI có 3 nút: Trả hàng + XEM ĐÁNH GIÁ + Mua lại -> Mới rút ngắn text
                        if (isAlreadyReviewed) {
                            setupButton(binding.btnActionLeft, "Trả/Hoàn", "outline");
                        } else {
                            setupButton(binding.btnActionLeft, "Trả hàng/Hoàn tiền", "outline");
                        }
                        
                        binding.btnActionLeft.setOnClickListener(v -> {
                            android.content.Intent intent = new android.content.Intent(context, ReturnRefundActivity.class);
                            intent.putExtra("orderId", order.getId());
                            intent.putExtra("orderCode", order.getOrderCode());
                            intent.putExtra("items", new java.util.ArrayList<>(order.getItems()));
                            intent.putExtra("paymentMethod", order.getPaymentMethod());
                            intent.putExtra("shippingAddress", order.getAddress().getAddressDetail());
                            context.startActivity(intent);
                        });
                    }

                    if (isAlreadyReviewed) {
                        setupButton(binding.btnActionMiddle, "Xem đánh giá", "outline");
                        binding.btnActionMiddle.setOnClickListener(v -> {
                            android.content.Intent intent = new android.content.Intent(context, ViewReviewsActivity.class);
                            intent.putExtra("order", order);
                            context.startActivity(intent);
                        });
                    } else if (canStillReview) {
                        setupButton(binding.btnActionMiddle, "Đánh giá", "outline");
                        binding.btnActionMiddle.setOnClickListener(v -> {
                            android.content.Intent intent = new android.content.Intent(context, WriteReviewActivity.class);
                            intent.putExtra("order", order);
                            context.startActivity(intent);
                        });
                    }

                    setupButton(binding.btnActionRight, "Mua lại", "filled");
                    binding.btnActionRight.setOnClickListener(v -> performRebuy(order));
                    break;
                case "cancelled":
                    setupButton(binding.btnActionMiddle, "Xem chi tiết đơn hủy", "outline");
                    binding.btnActionMiddle.setOnClickListener(v -> {
                        android.content.Intent intent = new android.content.Intent(context, OrderDetailActivity.class);
                        intent.putExtra("order", order);
                        context.startActivity(intent);
                    });
                    setupButton(binding.btnActionRight, "Mua lại", "filled");
                    binding.btnActionRight.setOnClickListener(v -> performRebuy(order));
                    break;
                case "returned":
                case "refunded":
                    setupButton(binding.btnActionMiddle, "Xem chi tiết hoàn tiền", "outline");
                    binding.btnActionMiddle.setOnClickListener(v -> {
                        android.content.Intent intent = new android.content.Intent(context, ReturnRefundHistoryDetailActivity.class);
                        intent.putExtra("order", order);
                        context.startActivity(intent);
                    });
                    setupButton(binding.btnActionRight, "Mua lại", "filled");
                    binding.btnActionRight.setOnClickListener(v -> performRebuy(order));
                    break;
            }
        }

        private void showContactOptions(Order order) {
            BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
            com.example.healthup.databinding.LayoutBottomSheetContactOptionsBinding dialogBinding = 
                com.example.healthup.databinding.LayoutBottomSheetContactOptionsBinding.inflate(LayoutInflater.from(context));
            dialog.setContentView(dialogBinding.getRoot());

            dialogBinding.btnChat.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = ChatActivity.buyerIntentForOrder(context, order.getOrderCode(), order.getId());
                context.startActivity(intent);
            });

            dialogBinding.btnCall.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(android.net.Uri.parse("tel:0769845728"));
                context.startActivity(intent);
            });

            dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }

        private void performRebuy(Order order) {
            AlertDialog loading = showLoadingDialog();
            FirebaseManager.getInstance().rebuyOrder(order.getItems())
                .addOnSuccessListener(aVoid -> {
                    loading.dismiss();
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.putExtra("navigate_to", "cart_tab");
                    intent.putExtra("is_rebuy", true); // Đánh dấu đây là luồng mua lại
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    android.widget.Toast.makeText(context, "Lỗi mua lại: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                });
        }

        private void setupButton(Button btn, String text, String type) {
            btn.setVisibility(View.VISIBLE);
            btn.setText(text);
            com.google.android.material.button.MaterialButton mBtn = (com.google.android.material.button.MaterialButton) btn;
            float density = btn.getContext().getResources().getDisplayMetrics().density;

            mBtn.setAlpha(1.0f);
            mBtn.setEnabled(true);
            switch (type) {
                case "filled":
                    mBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF36873A));
                    mBtn.setStrokeWidth(0); mBtn.setTextColor(0xFFFFFFFF); break;
                case "outline":
                    mBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
                    mBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE0D9D5));
                    mBtn.setStrokeWidth((int) (1 * density)); mBtn.setTextColor(0xFF1F1915); break;
                case "outline_error":
                    mBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
                    mBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE53835));
                    mBtn.setStrokeWidth((int) (1 * density)); mBtn.setTextColor(0xFFE53835); break;
                case "outline_primary":
                    mBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
                    mBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF36873A));
                    mBtn.setStrokeWidth((int) (1 * density)); mBtn.setTextColor(0xFF36873A); break;
                case "outline_disabled":
                    mBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
                    mBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE0D9D5));
                    mBtn.setStrokeWidth((int) (1 * density)); mBtn.setTextColor(0xFF888888);
                    mBtn.setAlpha(0.6f); break;
            }
        }
    }
}
