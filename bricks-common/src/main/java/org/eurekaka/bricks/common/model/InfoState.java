package org.eurekaka.bricks.common.model;

import org.eurekaka.bricks.common.exception.StoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * symbol info 存储更新管理器
 */
public class InfoState<T extends Info<T>, S extends InfoStore<T>> {
    private static final Logger logger = LoggerFactory.getLogger(InfoState.class);
    protected Map<Integer, T> infoMap;

    protected S infoStore;

    public InfoState(S infoStore) {
        this.infoStore = infoStore;

        this.infoMap = new ConcurrentHashMap<>();

        try {
            for (T info : infoStore.query()) {
                if (info.isEnabled()) {
                    infoMap.put(info.getId(), info);
                }
            }
            logger.info("initial state info: {}", infoMap);
            initial();
        } catch (StoreException e) {
            throw new RuntimeException("failed to initial info state.", e);
        }
    }

    // do state initial work here.
    protected void initial() {}

    public void updateInfo(T info) throws StoreException {
        if (info.getId() == 0) {
            infoStore.store(info);
        } else {
            infoStore.update(info);
        }

        if (infoMap.containsKey(info.getId())) {
            infoMap.get(info.getId()).copy(info);
//            logger.info("update info: {}", infoMap.get(info.id));
        }
    }

    public void deleteInfo(int id) throws StoreException {
        if (infoMap.containsKey(id)) {
            throw new StoreException("can not delete enabled info: " + infoMap.get(id));
        }
        infoStore.delete(id);
    }

    public List<T> queryAllInfos() throws StoreException {
        return infoStore.query();
    }

    public T queryInfo(int id) throws StoreException {
        return infoStore.queryInfo(id);
    }

    public void disableInfo(int id) throws StoreException {
        if (infoMap.containsKey(id)) {
            logger.info("disable info: {}", infoMap.get(id));
            infoMap.remove(id);
            infoStore.updateEnabled(id, false);
        }
    }

    public void enableInfo(int id) throws StoreException {
        if (!infoMap.containsKey(id)) {
            T info = infoStore.queryInfo(id);
            if (info != null) {
                info.setEnabled(true);
                infoStore.updateEnabled(id, true);
                infoMap.put(id, info);
                logger.info("enable info: {}", infoMap.get(id));
            }
        }
    }

    public List<T> getInfoByName(String name) {
        return infoMap.values().stream()
                .filter(info -> name.equals(info.getName()))
                .collect(Collectors.toList());
    }

    public List<T> getInfos() {
        return new ArrayList<>(infoMap.values());
    }

    // could be null
    public T getInfo(int id) {
        return infoMap.get(id);
    }
}
