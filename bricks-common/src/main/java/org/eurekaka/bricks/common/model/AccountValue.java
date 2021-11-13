package org.eurekaka.bricks.common.model;

/**
 * 账户资产，返回每个asset的数量
 */
public class AccountValue {
    // 资产名称
    public final String asset;

    // 账户名称
    public final String account;

    // 总余额 = wallet balance + unrealized pnl
    public final double totalBalance;

    // 钱包余额 = availableBalance + used margins 占用的保证金
    // = realized pnl + 初始余额
    // used margin = order margin + position margin (future contracts)
    public final double walletBalance;

    // 可用余额
    public final double availableBalance;

    public AccountValue(String asset, String account, double totalBalance, double availableBalance) {
        this(asset, account, totalBalance, totalBalance, availableBalance);
    }

    public AccountValue(String asset, String account, double totalBalance,
                        double walletBalance, double availableBalance) {
        this.asset = asset;
        this.account = account;
        this.totalBalance = totalBalance;
        this.walletBalance = walletBalance;
        this.availableBalance = availableBalance;
    }

    @Override
    public String toString() {
        return "AccountValue{" +
                "asset='" + asset + '\'' +
                ", account='" + account + '\'' +
                ", totalBalance=" + totalBalance +
                ", walletBalance=" + walletBalance +
                ", availableBalance=" + availableBalance +
                '}';
    }
}
