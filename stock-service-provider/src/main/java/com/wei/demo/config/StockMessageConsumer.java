package com.wei.demo.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wei.demo.constant.MQConstant;
import com.wei.demo.constant.RedisConstant;
import com.wei.demo.entity.Stock;
import com.wei.demo.mq.Consumer;
import com.wei.demo.mq.Producer;
import com.wei.demo.service.IStockService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * @author weiwenfeng
 * @date 2019/4/18
 */
@Component
public class StockMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockMessageConsumer.class);

    @Autowired
    private Producer producer;

    @Autowired
    private Consumer consumer;

    @Autowired
    private IStockService stockService;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @PostConstruct
    @Transactional
    public void initConsumer() {
        consumer.subscribe(MQConstant.DEC_STOCK_TOPIC, null, "stockGroup", new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                for (MessageExt message : list) {
                    try {
                        Stock stock = JSONObject.parseObject(message.getBody(),Stock.class);
                        int updateNum = stockService.updateStockById(stock);
                        if (updateNum == 0) {
                            throw new RuntimeException("更新库存失败！");
                        }
                        stock.setSale(stock.getSale() + 1);
                        stock.setVersion(stock.getVersion() + 1);
                        redisTemplate.opsForValue().set(RedisConstant.STOCK_KEY_PREFIX + stock.getId(), JSON.toJSONString(stock));
                        producer.send(MQConstant.INCR_ORDER_TOPIC, null, JSON.toJSONBytes(stock));
                    } catch (RuntimeException e) {
                        log.error("Consume message fail：topic = " + MQConstant.DEC_STOCK_TOPIC +
                                ",fail time = " + message.getReconsumeTimes(), e);
                        return message.getReconsumeTimes() > 10 ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS :
                                ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
    }
}
