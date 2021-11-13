package org.eurekaka.bricks.common.model;

public class AccountAssetRecord {
    public final static String PROCESSING_STATUS = "processing";
    public final static String CANCELLED_STATUS = "cancelled";
    public final static String REJECTED_STATUS = "rejected";
    public final static String SUCCESS_STATUS = "success";
    public final static String FAILED_STATUS = "failed";


    public final long time;
    public final String asset;
    public final double amount;
    public final String status;
    public final String address;

    public AccountAssetRecord(long time, String asset, double amount, String status, String address) {
        this.time = time;
        this.asset = asset;
        this.amount = amount;
        this.status = status;
        this.address = address;
    }



    @Override
    public String toString() {
        return "AccountAssetRecord{" +
                "time=" + time +
                ", asset='" + asset + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}
