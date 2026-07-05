package com.example.models;

import java.io.Serializable;

public class Address implements Serializable {

    public static final String TYPE_HOME = "Nhà riêng";
    public static final String TYPE_OFFICE = "Văn phòng";

    private String id;
    private String recipientName;
    private String phone;
    private String province;       // Tỉnh/Thành phố
    private String district;       // Quận/Huyện
    private String ward;           // Phường/Xã
    private String detailAddress;  // Số nhà, tên đường...
    private String type;           // TYPE_HOME hoặc TYPE_OFFICE
    private boolean isDefault;

    public Address() {
    }

    public Address(String id, String recipientName, String phone, String province, String district, String ward,
                   String detailAddress, String type, boolean isDefault) {
        this.id = id;
        this.recipientName = recipientName;
        this.phone = phone;
        this.province = province;
        this.district = district;
        this.ward = ward;
        this.detailAddress = detailAddress;
        this.type = type;
        this.isDefault = isDefault;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getDetailAddress() { return detailAddress; }
    public void setDetailAddress(String detailAddress) { this.detailAddress = detailAddress; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (detailAddress != null && !detailAddress.isEmpty()) sb.append(detailAddress);
        if (ward != null && !ward.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ward);
        }
        if (district != null && !district.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(district);
        }
        if (province != null && !province.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(province);
        }
        return sb.toString();
    }
}
