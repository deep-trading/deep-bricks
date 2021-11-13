package org.eurekaka.bricks.common.model;

import org.eurekaka.bricks.common.exception.StoreException;

import java.util.List;

/**
 * symbol info 存储，实际为配置文件，不占用太多计算资源
 * @param <T>
 */
public interface InfoStore<T> {
    /**
     * 存储symbol info
     * 此处不返回对象，若插入成功，直接更新info内id
     * @param info symbol info
     * @throws StoreException 存储失败异常
     */
    void store(T info) throws StoreException;

    /**
     * 查询所有的symbol info配置
     * @return 返回所有的symbol info
     * @throws StoreException 存储失败异常
     */
    List<T> query() throws StoreException;

    T queryInfo(int id) throws StoreException;

    /**
     * 更新symbol info信息
     * @param info symbol info
     * @throws StoreException 存储失败异常
     */
    void update(T info) throws StoreException;

    /**
     * 只有false状态的info才能删除
     * @param id 待删除的info
     * @throws StoreException 存储失败异常
     */
    void delete(int id) throws StoreException;

    /**
     * symbol info 是否生效
     * @param id info id
     * @param enabled true代表生效
     * @throws StoreException 存储失败异常
     */
    void updateEnabled(int id, boolean enabled) throws StoreException;
}
