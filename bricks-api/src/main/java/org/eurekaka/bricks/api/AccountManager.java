package org.eurekaka.bricks.api;

import java.util.List;

/**
 * 账户管理器
 */
public interface AccountManager {

    /**
     * 获取对应的账户exchange客户端
     * @param account 账户名称
     * @return 账户客户端
     */
    Exchange getAccount(String account);

    /**
     * 根据账户类型，获取所有该类型的账户
     * @param type 类型
     * @return 该类型可用账户客户端列表
     */
    List<Exchange> getAccounts(int type);

    /**
     * 获取所有账户名称
     * @return 账户客户端列表
     */
    default List<Exchange> getAccounts() {
        return getAccounts(0);
    }
}
