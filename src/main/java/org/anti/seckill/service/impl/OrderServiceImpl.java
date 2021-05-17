package org.anti.seckill.service.impl;

import org.anti.seckill.domain.OrderDO;
import org.anti.seckill.domain.SequenceDO;
import org.anti.seckill.domain.StockLogDO;
import org.anti.seckill.error.BusinessException;
import org.anti.seckill.error.EmBusinessError;
import org.anti.seckill.mapper.OrderDOMapper;
import org.anti.seckill.mapper.SequenceDOMapper;
import org.anti.seckill.mapper.StockLogDOMapper;
import org.anti.seckill.service.ItemService;
import org.anti.seckill.service.OrderService;
import org.anti.seckill.service.UserService;
import org.anti.seckill.service.model.ItemModel;
import org.anti.seckill.service.model.OrderModel;
import org.anti.seckill.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    //来源于事务型mq
    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException {

        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");

        if (amount < 1 || amount > 99)
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "购买数量不正确");

        //落单减库存（本例），支付减库存（其他方式）
        //更新redis内库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if (!result) throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);

        //生成交易订单
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if (promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //增加商品销量 todo
        itemService.increaseSales(itemId, amount);

        //设置库存流水状态成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null) throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

        //异步更新sql中库存，事务提交成功后异步发送
        //但是如果消息发送失败，则无能为力
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//                @Override
//                public void afterCommit(){
//                    boolean mqResult = itemService.asyncDecreaseStock(itemId,amount);
//                    if (!mqResult) {
//                        itemService.increaseStock(itemId, amount);
//                        throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
//                    }
//                }
//        });

        return orderModel;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo() {
        StringBuilder sb = new StringBuilder();
        //16位 = 年月日 8 + 自增 6 + 分库分表 2
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        sb.append(nowDate);

        int sq = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        sq = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String str = String.valueOf(sq);
        for (int i = 0; i < 6 - str.length(); i++) sb.append(0);
        sb.append(str);

        sb.append("00");

        return sb.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel) {
        if (orderModel == null) return null;
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
