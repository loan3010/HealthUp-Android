package com.example.healthup.admin;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AdminAssetImageHelper {

    private static final String ASSET_DIR = "images/products";

    private AdminAssetImageHelper() {
    }

    @NonNull
    public static List<String> listProductImages(@NonNull Context context) {
        try {
            String[] files = context.getAssets().list(ASSET_DIR);
            if (files == null || files.length == 0) {
                return Collections.emptyList();
            }
            Arrays.sort(files, String::compareToIgnoreCase);
            List<String> result = new ArrayList<>();
            for (String file : files) {
                if (file != null && !file.trim().isEmpty()) {
                    result.add(file.trim());
                }
            }
            return result;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
