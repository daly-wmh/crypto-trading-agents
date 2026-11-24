package com.tradingagents.trading.service;

import com.tradingagents.api.repository.AnalysisReportRepository;
import com.tradingagents.market.CryptoMarketService;
import com.tradingagents.trading.dto.AccountSummary;
import com.tradingagents.trading.dto.PaperSignal;
import com.tradingagents.trading.dto.PositionVO;
import com.tradingagents.trading.dto.TradeRequest;
import com.tradingagents.trading.entity.TradeOrder;
import com.tradingagents.trading.entity.TradePosition;
import com.tradingagents.trading.entity.TradingAccount;
import com.tradingagents.trading.repository.TradeOrderRepository;
import com.tradingagents.trading.repository.TradePositionRepository;
import com.tradingagents.trading.repository.TradingAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static com.tradingagents.trading.dto.PaperSignal.Action;

@Service
public class TradingService {

    private static final int SCALE = 8;
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");
    private static final BigDecimal BUY_COST = BigDecimal.ONE.add(FEE_RATE);

    private final TradingAccountRepository accounts;
    private final TradeOrderRepository orders;
    private final TradePositionRepository positions;
    private final AnalysisReportRepository reports;
    private final CryptoMarketService market;

    public TradingService(TradingAccountRepository accounts, TradeOrderRepository orders,
                          TradePositionRepository positions, AnalysisReportRepository reports,
                          CryptoMarketService market) {
        this.accounts = accounts;
        this.orders = orders;
        this.positions = positions;
        this.reports = reports;
        this.market = market;
    }

    @Transactional
    public TradeOrder trade(TradeRequest request, Action action) {
        String pair = market.requirePair(request.stockCode());
        TradingAccount account = lockAccount(request.accountId());
        return fill(account, pair, action, getLatestPrice(pair), request.quantity(), null);
    }

    @Transactional
    public TradeOrder executeReport(Long accountId, Long reportId) {
        TradingAccount account = lockAccount(accountId);
        var filled = orders.findByAccountIdAndAnalysisId(accountId, reportId);
        if (filled.isPresent()) return filled.get();

        var report = reports.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "报告不存在"));
        if (!"COMPLETED".equals(report.getStatus())) {
            throw badRequest("请先完成分析");
        }
        PaperSignal signal = PaperSignal.from(report.getFinalDecision())
                .filter(value -> value.action() != Action.HOLD)
                .orElseThrow(() -> badRequest("报告没有可执行的模拟买卖信号"));
        String pair = market.requirePair(report.getStockCode());
        BigDecimal price = getLatestPrice(pair);
        BigDecimal fraction = BigDecimal.valueOf(signal.percent(), 2);
        BigDecimal quantity = switch (signal.action()) {
            case BUY -> account.getAvailable().multiply(fraction)
                    .divide(price.multiply(BUY_COST), SCALE, RoundingMode.DOWN);
            case SELL -> requirePosition(accountId, pair).getQuantity().multiply(fraction)
                    .setScale(SCALE, RoundingMode.DOWN);
            case HOLD -> throw badRequest("该报告建议观望");
        };
        return fill(account, pair, signal.action(), price, quantity, reportId);
    }

    private TradeOrder fill(TradingAccount account, String pair, Action action,
                            BigDecimal price, BigDecimal quantity, Long reportId) {
        BigDecimal amount = money(price.multiply(quantity));
        if (amount.signum() <= 0) throw badRequest("成交金额太小");
        BigDecimal fee = money(amount.multiply(FEE_RATE));
        String remark;

        if (action == Action.BUY) {
            BigDecimal cost = amount.add(fee);
            if (cost.compareTo(account.getAvailable()) > 0) throw badRequest("可用 USDT 不足");

            TradePosition position = positions.findByAccountIdAndStockCode(account.getId(), pair)
                    .orElseGet(() -> newPosition(account.getId(), pair));
            BigDecimal oldCost = position.getAvgCost().multiply(position.getQuantity());
            BigDecimal newQuantity = position.getQuantity().add(quantity);
            position.setAvgCost(oldCost.add(cost).divide(newQuantity, SCALE, RoundingMode.HALF_UP));
            position.setQuantity(newQuantity);
            positions.save(position);

            account.setBalance(account.getBalance().subtract(cost));
            remark = "模拟买入";
        } else {
            TradePosition position = requirePosition(account.getId(), pair);
            BigDecimal remaining = position.getQuantity().subtract(quantity);
            if (remaining.signum() < 0) throw badRequest("持仓数量不足");

            BigDecimal proceeds = amount.subtract(fee);
            BigDecimal realized = money(proceeds.subtract(position.getAvgCost().multiply(quantity)));
            if (remaining.signum() == 0) {
                positions.delete(position);
            } else {
                position.setQuantity(remaining);
            }
            account.setBalance(account.getBalance().add(proceeds));
            remark = "已实现盈亏 " + realized.stripTrailingZeros().toPlainString() + " USDT";
        }

        TradeOrder order = new TradeOrder();
        order.setAccountId(account.getId());
        order.setStockCode(pair);
        order.setDirection(action.name());
        order.setQuantity(quantity);
        order.setPrice(price);
        order.setAmount(amount);
        order.setFee(fee);
        order.setAnalysisId(reportId);
        order.setRemark(remark);
        return orders.save(order);
    }

    @Transactional(readOnly = true)
    public AccountSummary getAccountSummary(Long accountId) {
        TradingAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模拟账户不存在"));
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal positionCost = BigDecimal.ZERO;
        List<PositionVO> values = new ArrayList<>();

        for (TradePosition position : positions.findByAccountId(accountId)) {
            BigDecimal price = getLatestPrice(position.getStockCode());
            marketValue = marketValue.add(position.marketValue(price));
            positionCost = positionCost.add(position.getAvgCost().multiply(position.getQuantity()));
            values.add(PositionVO.from(position, price));
        }

        BigDecimal pnl = marketValue.subtract(positionCost);
        BigDecimal percent = positionCost.signum() > 0
                ? pnl.movePointRight(2).divide(positionCost, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new AccountSummary(account.getId(), account.getName(), account.getBalance(),
                account.getFrozen(), account.getAvailable(), money(marketValue),
                money(account.getBalance().add(marketValue)), money(pnl), percent, values);
    }

    public List<TradeOrder> getOrderHistory(Long accountId) {
        return orders.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    public BigDecimal getLatestPrice(String pair) {
        BigDecimal price = money(market.ticker(pair).price());
        if (price.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "公开报价无效");
        }
        return price;
    }

    private TradingAccount lockAccount(Long accountId) {
        return accounts.lockById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模拟账户不存在"));
    }

    private TradePosition requirePosition(Long accountId, String pair) {
        return positions.findByAccountIdAndStockCode(accountId, pair)
                .orElseThrow(() -> badRequest("没有该交易对的模拟持仓"));
    }

    private static TradePosition newPosition(Long accountId, String pair) {
        TradePosition position = new TradePosition();
        position.setAccountId(accountId);
        position.setStockCode(pair);
        position.setQuantity(BigDecimal.ZERO);
        position.setAvgCost(BigDecimal.ZERO);
        return position;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
