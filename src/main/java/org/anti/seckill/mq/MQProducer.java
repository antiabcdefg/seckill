package org.anti.seckill.mq;

import com.alibaba.fastjson.JSON;
import org.anti.seckill.domain.StockLogDO;
import org.anti.seckill.error.BusinessException;
import org.anti.seckill.mapper.StockLogDOMapper;
import org.anti.seckill.service.ItemService;
import org.anti.seckill.service.OrderService;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class MQProducer {

    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    @Value("${mq.topicname}")
    private String topicName;

    private DefaultMQProducer producer;

    private TransactionMQProducer transactionMQProducer;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object args) {
                //消费端拿到消息后等待二阶段提交，等待本方法commit再执行

                //创建订单
                Integer userId = (Integer) ((Map) args).get("userId");
                Integer itemId = (Integer) ((Map) args).get("itemId");
                Integer promoId = (Integer) ((Map) args).get("promoId");
                Integer amount = (Integer) ((Map) args).get("amount");
                String stockLogId = (String) ((Map) args).get("stockLogId");
                try {
                    orderService.createOrder(userId, itemId, promoId, amount, stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    //如果发生异常，createOrder已经回滚，此时要设置对应的stockLog为回滚状态
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    itemService.increaseStock(itemId, amount);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                //根据是否扣减库存成功，来判断要返回COMMIT, ROLLBACK还是继续UNKNOWN
                String jsonString = new String(msg.getBody());
                Map<String, Object> bodyMap = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) bodyMap.get("itemId");
                Integer amount = (Integer) bodyMap.get("amount");
                String stockLogId = (String) bodyMap.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if (stockLogDO == null) return LocalTransactionState.UNKNOW;
                //订单操作已经完成，等着异步扣减库存，那么就提交事务型消息
                if (stockLogDO.getStatus().intValue() == 2) return LocalTransactionState.COMMIT_MESSAGE;
                //订单操作还未完成，需要执行下单操作，那么就维持为prepare状态
                else if (stockLogDO.getStatus().intValue() == 1) return LocalTransactionState.UNKNOW;
                itemService.increaseStock(itemId, amount);
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }

    //事务型消息同步创建订单
    public boolean transactionAsyncCreateOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        bodyMap.put("stockLogId", stockLogId);

        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("stockLogId", stockLogId);
        Message message = new Message(topicName, "increase", JSON.toJSON(bodyMap).toString().getBytes(StandardCharsets.UTF_8));
        TransactionSendResult result;
        try {
            result = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if (result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) return false;
        else if (result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) return true;
        else return false;
    }

    //非事务消息同步库存扣减消息
//    public boolean asyncReduceStock(Integer itemId, Integer amount) {
//        Map<String, Object> bodyMap = new HashMap<>();
//        bodyMap.put("itemId", itemId);
//        bodyMap.put("amount", amount);
//        Message message = new Message(topicName, "reduce", JSON.toJSON(bodyMap).toString().getBytes(StandardCharsets.UTF_8));
//        try {
//            producer.send(message);
//        } catch (MQClientException e) {
//            e.printStackTrace();
//            return false;
//        } catch (RemotingException e) {
//            e.printStackTrace();
//            return false;
//        } catch (MQBrokerException e) {
//            e.printStackTrace();
//            return false;
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            return false;
//        }
//        return true;
//    }
}
