package com.example.healthup.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.healthup.R;
import com.example.models.Product;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Product share: preview sheet + intent with branded image card and product link.
 * Apps like WhatsApp show the image; caption carries the tap-to-open link.
 */
public final class ProductShareHelper {

    /** Firebase Hosting path the app can deep-link; keeps product id for open-in-app. */
    private static final String SHARE_HOST = "https://healthup-f6eff.firebaseapp.com/san-pham/";

    private ProductShareHelper() {
    }

    public static void showShareSheet(@NonNull AppCompatActivity activity, @NonNull Product product) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.BottomSheetDialogTheme);
        View sheet = activity.getLayoutInflater().inflate(R.layout.layout_bottom_sheet_share_product, null);
        dialog.setContentView(sheet);

        ImageView ivPreview = sheet.findViewById(R.id.ivSharePreview);
        TextView tvPreviewName = sheet.findViewById(R.id.tvSharePreviewName);
        TextView tvPreviewPrice = sheet.findViewById(R.id.tvSharePreviewPrice);
        TextView tvPreviewMeta = sheet.findViewById(R.id.tvSharePreviewMeta);

        String imageUrl = product.getImageUrl();
        if (ivPreview != null) {
            ImageLoadHelper.loadInto(ivPreview, imageUrl);
        }
        if (tvPreviewName != null) {
            tvPreviewName.setText(product.getName());
        }

        NumberFormat currency = NumberFormat.getInstance(new Locale("vi", "VN"));
        if (tvPreviewPrice != null) {
            tvPreviewPrice.setText(currency.format(product.getDisplayPrice()) + "đ");
        }
        if (tvPreviewMeta != null) {
            tvPreviewMeta.setText(buildPreviewMeta(activity, product));
        }

        View btnShareApps = sheet.findViewById(R.id.btnShareApps);
        View btnCopyLink = sheet.findViewById(R.id.btnCopyLink);
        View btnCancel = sheet.findViewById(R.id.btnCancelShare);

        if (btnShareApps != null) {
            btnShareApps.setOnClickListener(v -> {
                dialog.dismiss();
                shareToApps(activity, product);
            });
        }
        if (btnCopyLink != null) {
            btnCopyLink.setOnClickListener(v -> {
                copyLink(activity, product);
                dialog.dismiss();
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private static void shareToApps(@NonNull AppCompatActivity activity, @NonNull Product product) {
        String shareText = buildShareText(activity, product);
        String imageUrl = product.getImageUrl();

        if (TextUtils.isEmpty(imageUrl)) {
            Bitmap card = createShareCard(activity, null, product);
            if (card != null) {
                File imageFile = writeShareImage(activity, product.getId(), card);
                if (imageFile != null) {
                    launchImageShare(activity, imageFile, shareText);
                    return;
                }
            }
            launchTextShare(activity, shareText);
            return;
        }

        Toast.makeText(activity, R.string.product_share_preparing, Toast.LENGTH_SHORT).show();
        Glide.with(activity)
                .asBitmap()
                .load(ImageLoadHelper.resolveLoadTarget(imageUrl))
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap resource,
                            @Nullable Transition<? super Bitmap> transition
                    ) {
                        if (activity.isFinishing()) return;
                        Bitmap card = createShareCard(activity, resource, product);
                        File imageFile = writeShareImage(activity, product.getId(),
                                card != null ? card : resource);
                        if (imageFile != null) {
                            launchImageShare(activity, imageFile, shareText);
                        } else {
                            launchTextShare(activity, shareText);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                        if (activity.isFinishing()) return;
                        Bitmap card = createShareCard(activity, null, product);
                        if (card != null) {
                            File imageFile = writeShareImage(activity, product.getId(), card);
                            if (imageFile != null) {
                                launchImageShare(activity, imageFile, shareText);
                                return;
                            }
                        }
                        launchTextShare(activity, shareText);
                    }
                });
    }

    /**
     * Branded share preview card: product photo + name + price + HealthUp footer.
     * Recipients see this image immediately (WhatsApp / Zalo / Messenger).
     */
    @Nullable
    private static Bitmap createShareCard(
            @NonNull Context context,
            @Nullable Bitmap productImage,
            @NonNull Product product
    ) {
        final int width = 1080;
        final int height = 1440;
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);

        int brand = ContextCompat.getColor(context, R.color.brand_primary);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(brand);
        canvas.drawRect(0, 0, width, 120, bar);

        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setColor(Color.WHITE);
        brandPaint.setTextSize(56f);
        brandPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("HealthUp", 48, 78, brandPaint);

        int photoTop = 120;
        int photoHeight = 900;
        if (productImage != null && !productImage.isRecycled()) {
            RectF dst = new RectF(0, photoTop, width, photoTop + photoHeight);
            canvas.drawBitmap(productImage, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            Paint fill = new Paint();
            fill.setColor(0xFFF3F4F6);
            canvas.drawRect(0, photoTop, width, photoTop + photoHeight, fill);
        }

        int textTop = photoTop + photoHeight + 40;
        TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(0xFF1A1A1A);
        namePaint.setTextSize(48f);
        namePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String name = product.getName() == null ? "" : product.getName().trim();
        StaticLayout nameLayout = StaticLayout.Builder
                .obtain(name, 0, name.length(), namePaint, width - 96)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        canvas.save();
        canvas.translate(48, textTop);
        nameLayout.draw(canvas);
        canvas.restore();

        NumberFormat currency = NumberFormat.getInstance(new Locale("vi", "VN"));
        TextPaint pricePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        pricePaint.setColor(brand);
        pricePaint.setTextSize(52f);
        pricePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float priceY = textTop + nameLayout.getHeight() + 64;
        canvas.drawText(currency.format(product.getDisplayPrice()) + "đ", 48, priceY, pricePaint);

        TextPaint linkPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        linkPaint.setColor(0xFF6B7280);
        linkPaint.setTextSize(32f);
        String link = buildProductLink(product);
        canvas.drawText(link, 48, height - 48, linkPaint);

        return out;
    }

    @Nullable
    private static File writeShareImage(@NonNull Context context, @Nullable String productId, @NonNull Bitmap bitmap) {
        String safeId = TextUtils.isEmpty(productId) ? "product" : productId.replaceAll("[^a-zA-Z0-9_-]", "_");
        File out = new File(context.getCacheDir(), "share_" + safeId + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(out)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                return null;
            }
            return out;
        } catch (IOException e) {
            return null;
        }
    }

    private static void launchImageShare(
            @NonNull AppCompatActivity activity,
            @NonNull File imageFile,
            @NonNull String shareText
    ) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                imageFile
        );
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        intent.putExtra(Intent.EXTRA_SUBJECT, extractSubject(shareText));
        intent.putExtra(Intent.EXTRA_TITLE, extractSubject(shareText));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(
                activity.getContentResolver(),
                "HealthUp product",
                uri
        ));

        Intent chooser = Intent.createChooser(
                intent,
                activity.getString(R.string.product_share_chooser_title)
        );
        List<ResolveInfo> targets = activity.getPackageManager()
                .queryIntentActivities(intent, 0);
        for (ResolveInfo info : targets) {
            activity.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        }
        activity.startActivity(chooser);
    }

    private static void launchTextShare(@NonNull AppCompatActivity activity, @NonNull String shareText) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        intent.putExtra(Intent.EXTRA_SUBJECT, extractSubject(shareText));
        activity.startActivity(Intent.createChooser(
                intent,
                activity.getString(R.string.product_share_chooser_title)
        ));
    }

    private static void copyLink(@NonNull Context context, @NonNull Product product) {
        String link = buildProductLink(product);
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("product_link", link));
        }
        Toast.makeText(context, R.string.product_share_link_copied, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private static String buildShareText(@NonNull Context context, @NonNull Product product) {
        boolean en = "en".equals(LocaleHelper.getLanguage(context));
        NumberFormat currency = NumberFormat.getInstance(new Locale("vi", "VN"));
        String name = product.getName() == null ? "" : product.getName().trim();
        String price = currency.format(product.getDisplayPrice()) + "đ";
        String link = buildProductLink(product);

        StringBuilder sb = new StringBuilder();
        if (en) {
            sb.append(name).append('\n');
            sb.append("Price: ").append(price);
            appendDiscountLine(sb, product, currency, true);
            appendRatingLine(sb, product, true);
            sb.append("\n\n").append(link);
            sb.append("\n\nHealthUp — Live healthier every day");
        } else {
            sb.append(name).append('\n');
            sb.append("Giá: ").append(price);
            appendDiscountLine(sb, product, currency, false);
            appendRatingLine(sb, product, false);
            sb.append("\n\n").append(link);
            sb.append("\n\nHealthUp — Sống khỏe mỗi ngày");
        }
        return sb.toString();
    }

    private static void appendDiscountLine(
            @NonNull StringBuilder sb,
            @NonNull Product product,
            @NonNull NumberFormat currency,
            boolean en
    ) {
        double price = product.getDisplayPrice();
        double displayOriginal = product.getDisplayOriginalPrice();
        double original = displayOriginal > price
                ? displayOriginal
                : (product.getOldPrice() > price ? product.getOldPrice() : 0);
        if (original <= price) return;

        int percent = (int) Math.round(((original - price) / original) * 100);
        if (en) {
            sb.append(" (was ").append(currency.format(original)).append("đ, -").append(percent).append("%)");
        } else {
            sb.append(" (giá gốc ").append(currency.format(original)).append("đ, giảm ").append(percent).append("%)");
        }
    }

    private static void appendRatingLine(@NonNull StringBuilder sb, @NonNull Product product, boolean en) {
        float rating = product.getRating();
        int reviews = product.getReviewCount();
        int sold = product.getSoldCount();
        if (rating <= 0 && reviews <= 0 && sold <= 0) return;

        sb.append('\n');
        if (rating > 0) {
            sb.append(en ? "Rating: " : "Đánh giá: ")
                    .append(ReviewStatsHelper.formatAvgRating(rating));
            if (reviews > 0) {
                sb.append(en ? " (" : " (")
                        .append(reviews)
                        .append(en ? " reviews)" : " đánh giá)");
            }
        }
        if (sold > 0) {
            if (rating > 0) sb.append(" · ");
            sb.append(en ? "Sold: " : "Đã bán: ").append(formatSoldCount(sold));
        }
    }

    @NonNull
    private static String buildPreviewMeta(@NonNull Context context, @NonNull Product product) {
        boolean en = "en".equals(LocaleHelper.getLanguage(context));
        StringBuilder sb = new StringBuilder();
        float rating = product.getRating();
        if (rating > 0) {
            sb.append(en ? "Rating " : "Đánh giá ")
                    .append(ReviewStatsHelper.formatAvgRating(rating));
        }
        int sold = product.getSoldCount();
        if (sold > 0) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(en ? "Sold " : "Đã bán ").append(formatSoldCount(sold));
        }
        if (sb.length() == 0) {
            return en ? "HealthUp product" : "Sản phẩm HealthUp";
        }
        return sb.toString();
    }

    @NonNull
    public static String buildProductLink(@NonNull Product product) {
        String id = product.getId();
        if (!TextUtils.isEmpty(id)) {
            return SHARE_HOST + id.trim();
        }
        return SHARE_HOST + buildSlug(product);
    }

    @NonNull
    private static String buildSlug(@NonNull Product product) {
        String code = product.getProductCode();
        if (!TextUtils.isEmpty(code)) {
            return slugify(code);
        }
        return slugify(product.getName());
    }

    @NonNull
    private static String slugify(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) return "san-pham";
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return TextUtils.isEmpty(normalized) ? "san-pham" : normalized;
    }

    @NonNull
    private static String formatSoldCount(int sold) {
        if (sold >= 1_000_000) {
            return String.format(Locale.getDefault(), "%.1ftr+", sold / 1_000_000f);
        }
        if (sold >= 10_000) {
            return String.format(Locale.getDefault(), "%.0fw+", sold / 10_000f);
        }
        if (sold >= 1_000) {
            return String.format(Locale.getDefault(), "%.1fk+", sold / 1_000f);
        }
        return sold + "+";
    }

    @NonNull
    private static String extractSubject(@NonNull String shareText) {
        int lineBreak = shareText.indexOf('\n');
        String firstLine = lineBreak > 0 ? shareText.substring(0, lineBreak) : shareText;
        if (firstLine.length() > 64) {
            return firstLine.substring(0, 61) + "...";
        }
        return firstLine;
    }
}
