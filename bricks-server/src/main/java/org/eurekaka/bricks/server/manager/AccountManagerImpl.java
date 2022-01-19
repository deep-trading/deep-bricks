package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.ClzUtils;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class AccountManagerImpl implements AccountManager {
    private final static Logger logger = LoggerFactory.getLogger(AccountManagerImpl.class);

    private final Map<String, Exchange> accountMap;
    private final AccountConfigState accountConfigState;
    private final InfoState<Info0, ?> infoState;
    private final BlockingQueue<Notification> blockingQueue;

    public AccountManagerImpl(AccountConfigState accountConfigState,
                              InfoState<Info0, ?> infoState) {
        this.accountConfigState = accountConfigState;
        this.accountMap = new ConcurrentHashMap<>();
        this.blockingQueue = new LinkedBlockingQueue<>();

        this.infoState = infoState;
    }

    @Override
    public Exchange getAccount(String account) {
        return accountMap.get(account);
    }

    @Override
    public List<Exchange> getAccounts(int type) {
        return accountConfigState.getAccountConfigs().stream()
                .filter(e -> type == 0 ||
                        e.getType() == type && accountMap.containsKey(e.getName()))
                .map(e -> accountMap.get(e.getName()))
                .collect(Collectors.toList());
    }

    public void start() {
        // 启动account
        for (AccountConfig accountConfig : accountConfigState.getAccountConfigs()) {
            try {
                addAccount(accountConfig);
            } catch (ExchangeException e) {
                throw new InitializeException("failed to initialize account: " + accountConfig, e);
            }
        }

        for (Info<?> info : infoState.getInfos()) {
            addSymbol(info);
        }

        // 检查所有symbols准备完成
        boolean isReady = false;
        while (!isReady) {
            isReady = true;
            for (Info0 info : infoState.getInfos()) {
                Exchange ex = accountMap.get(info.getAccount());
                if (ex != null && info.getType() > 0 && ex.isAlive()) {
                    ExMessage<?> message = ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                            new DepthPricePair(info.getName(), info.getSymbol(), 5)));
                    if (message.getType() == ExMessage.ExMsgType.ERROR) {
                        isReady = false;
                        logger.info("waiting, exchange manager is not ready with all exchanges," +
                                " exchange {}, info name {}", ex.getName(), info.getName());
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            logger.error("failed to start exchange manager, interrupted.");
                        }
                    }
                }
            }
        }

        postStart();
    }

    protected void postStart() {}

    public void stop() {
        logger.info("closing exchange manager");
        preStop();
        accountMap.values().forEach(Exchange::stop);
    }

    protected void preStop() {}

    public void addSymbol(Info<?> info) {
        Exchange ex = accountMap.get(info.getAccount());
        if (ex != null) {
            logger.info("register symbol {} for account {}", info.getName(), info.getAccount());
            ex.process(new ExAction<>(ExAction.ActionType.ADD_SYMBOL,
                    new SymbolPair(info.getName(), info.getSymbol())));
            postAddSymbol(info);
        }
    }

    protected void postAddSymbol(Info<?> info) {}

    public void removeSymbol(Info<?> info) {
        Exchange ex = accountMap.get(info.getAccount());
        if (ex != null) {
            logger.info("remove symbol {} for account {}", info.getName(), info.getAccount());
            ex.process(new ExAction<>(ExAction.ActionType.REMOVE_SYMBOL,
                    new SymbolPair(info.getName(), info.getSymbol())));
            postRemoveSymbol(info);
        }
    }

    protected void postRemoveSymbol(Info<?> info) {}

    /**
     * 添加 enable 状态的accountConfig，并且启动对应的exchange
     * @param accountConfig 账户配置
     */
    public void addAccount(AccountConfig accountConfig) throws ExchangeException {
        if (accountMap.containsKey(accountConfig.getName())) {
            logger.error("add existed exchange account: {}", accountConfig.getName());
            return;
        }
        // 只能通过外部查询有效的exchange，判断是否加载成功
        Exchange exchange = ClzUtils.createExchange(accountConfig.getClz(), accountConfig);
        exchange.start();
        accountMap.put(accountConfig.getName(), exchange);
        // 新的account不存在交易对，所以只需要注册 notification queue
        if (accountConfig.getWebsocket() != null) {
            exchange.process(new ExAction<>(ExAction.ActionType.REGISTER_QUEUE, blockingQueue));
        }
        logger.info("started account: {}", accountConfig);
    }

    public void removeAccount(String account) {
        for (Info<?> info : infoState.getInfos()) {
            if (info.getAccount().equals(account)) {
                logger.error("symbol {} is still using the account, can not remove.", info.getName());
                return;
            }
        }
        Exchange exchange = accountMap.get(account);
        if (exchange != null) {
            exchange.stop();
            accountMap.remove(account);
            logger.info("stopped account: {}", account);
        }
    }

    public BlockingQueue<Notification> getBlockingQueue() {
        return blockingQueue;
    }
}
