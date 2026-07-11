package com.example.models;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DeliveryFailure implements Serializable {
    private int attempt;
    private String reason;
    private String note;
    private Date at;
    private String byUid;
    private String byEmail;

    public DeliveryFailure() {}

    public DeliveryFailure(int attempt, String reason, String note, Date at, String byUid, String byEmail) {
        this.attempt = attempt;
        this.reason = reason;
        this.note = note;
        this.at = at;
        this.byUid = byUid;
        this.byEmail = byEmail;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("attempt", attempt);
        map.put("reason", reason != null ? reason : "");
        map.put("note", note != null ? note : "");
        map.put("at", at != null ? new com.google.firebase.Timestamp(at) : com.google.firebase.Timestamp.now());
        map.put("byUid", byUid != null ? byUid : "");
        map.put("byEmail", byEmail != null ? byEmail : "");
        return map;
    }

    @SuppressWarnings("unchecked")
    public static DeliveryFailure fromMap(Map<String, Object> map) {
        if (map == null) return null;
        DeliveryFailure f = new DeliveryFailure();
        Object attemptObj = map.get("attempt");
        if (attemptObj instanceof Number) {
            f.attempt = ((Number) attemptObj).intValue();
        }
        f.reason = map.get("reason") != null ? String.valueOf(map.get("reason")) : "";
        f.note = map.get("note") != null ? String.valueOf(map.get("note")) : "";
        Object atObj = map.get("at");
        if (atObj instanceof com.google.firebase.Timestamp) {
            f.at = ((com.google.firebase.Timestamp) atObj).toDate();
        } else if (atObj instanceof Date) {
            f.at = (Date) atObj;
        }
        f.byUid = map.get("byUid") != null ? String.valueOf(map.get("byUid")) : "";
        f.byEmail = map.get("byEmail") != null ? String.valueOf(map.get("byEmail")) : "";
        return f;
    }

    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Date getAt() { return at; }
    public void setAt(Date at) { this.at = at; }

    public String getByUid() { return byUid; }
    public void setByUid(String byUid) { this.byUid = byUid; }

    public String getByEmail() { return byEmail; }
    public void setByEmail(String byEmail) { this.byEmail = byEmail; }
}
