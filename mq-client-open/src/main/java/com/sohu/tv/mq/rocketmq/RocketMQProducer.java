package com.sohu.tv.mq.rocketmq;

import java.util.Map;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import com.sohu.index.tv.mq.common.Result;
import com.sohu.tv.mq.common.AbstractConfig;
import com.sohu.tv.mq.common.SohuSendMessageHook;
import com.sohu.tv.mq.stats.StatsHelper;

/**
 * rocketmq producer 封装
 * 
 * @date 2018年1月17日
 * @author copy from indexmq
 */
public class RocketMQProducer extends AbstractConfig {
    // rocketmq 实际生产者
    private final DefaultMQProducer producer;
    
    // 统计助手
    private StatsHelper statsHelper;
    
    /**
     * 同样消息的Producer，归为同一个Group，应用必须设置，并保证命名唯一
     */
    public RocketMQProducer(String producerGroup, String topic) {
        super(producerGroup, topic);
        producer = new DefaultMQProducer(producerGroup);
    }
    
    /**
     * 启动
     */
    public void start() {
        try {
            // 初始化配置
            initConfig(producer);
            // 数据采样
            if(isSampleEnabled()) {
                // 注册回调钩子
                SohuSendMessageHook hook = new SohuSendMessageHook(producer);
                statsHelper = hook.getStatsHelper();
                statsHelper.setMqCloudDomain(getMqCloudDomain());
                producer.getDefaultMQProducerImpl().registerSendMessageHook(hook);
            }
            producer.start();
            logger.info("topic:{} group:{} start", topic, group);
        } catch (MQClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 发送消息
     *
     * @param messageMap 消息数据
     * @return 发送结果
     */
    public Result<SendResult> publish(Map<String, Object> messageMap) {
        return publish((Object) messageMap);
    }

    /**
     * 发送消息
     *
     * @param messageObject 消息数据
     * @return 发送结果
     */
    public Result<SendResult> publish(Object messageObject) {
        return publish(messageObject, "");
    }

    /**
     * 发送消息
     *
     * @param messageObject 消息数据
     * @param keys key
     * @return 发送结果
     */
    public Result<SendResult> publishWithException(Object messageObject, String keys) throws Exception {
        Result<SendResult> result = publish(messageObject, keys, null);
        if (result.getException() != null) {
            throw result.getException();
        }
        return result;
    }

    /**
     * 发送消息
     *
     * @param messageObject 消息数据
     * @param keys key
     * @return 发送结果
     */
    public Result<SendResult> publish(Object messageObject, String keys) {
        return publish(messageObject, keys, null);
    }

    /**
     * 发送消息
     *
     * @param messageObject 消息数据
     * @param keys key
     * @param delayLevel 延时级别
     * @return 发送结果
     */
    public Result<SendResult> publish(Object messageObject, String keys, MessageDelayLevel delayLevel) {
        return publish(messageObject, "", keys, delayLevel);
    }
    
    
    /**
     * 构建消息
     * @param messageObject 消息
     * @param tags tags
     * @param keys key
     * @param delayLevel 延时级别
     * @return
     */
    public Message buildMessage(Object messageObject, String tags, String keys, MessageDelayLevel delayLevel) {
        byte[] bytes = getMessageSerializer().serialize(messageObject);
        Message message = new Message(topic, tags, keys, bytes);
        if (delayLevel != null) {
            message.setDelayTimeLevel(delayLevel.getLevel());
        }
        return message;
    }
    
    /**
     * 发送消息
     * 
     * @param messageObject 消息数据
     * @param tags tags
     * @param keys key
     * @param delayLevel 延时级别
     * @return 发送结果
     */
    public Result<SendResult> publish(Object messageObject, String tags, String keys, MessageDelayLevel delayLevel) {
        Message message = null;
        try {
            message = buildMessage(messageObject, tags, keys, delayLevel);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new Result<SendResult>(false, e);
        }
        return publish(message);
    }
    
    /**
     * 发送消息
     *
     * @param message 消息
     * @return 发送结果
     */
    public Result<SendResult> publish(Message message) {
        try {
            SendResult sendResult = producer.send(message);
            return new Result<SendResult>(true, sendResult);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new Result<SendResult>(false, e);
        }
    }

    /**
     * 发送有序消息
     *
     * @param messageMap 消息数据
     * @param selector 队列选择器，发送时会回调
     * @param order 回调队列选择器时，此参数会传入队列选择方法,提供配需规则
     * @return 发送结果
     */
    public Result<SendResult> publishOrder(Map<String, Object> messageMap, MessageQueueSelector selector,
            Object order) {
        return publishOrder((Object) messageMap, selector, order);
    }

    /**
     * 发送有序消息
     *
     * @param messageMap 消息数据
     * @param selector 队列选择器，发送时会回调
     * @param order 回调队列选择器时，此参数会传入队列选择方法,提供配需规则
     * @return 发送结果
     */
    public Result<SendResult> publishOrder(Object messageObject, MessageQueueSelector selector, Object order) {
        return publishOrder(messageObject, selector, order, null);
    }

    /**
     * 发送有序消息
     *
     * @param messageMap 消息数据
     * @param selector 队列选择器，发送时会回调
     * @param order 回调队列选择器时，此参数会传入队列选择方法,提供配需规则
     * @param delayLevel 发送延时消息 @MessageDelayLevel
     * @return 发送结果
     */
    public Result<SendResult> publishOrder(Object messageObject, MessageQueueSelector selector, Object order,
            MessageDelayLevel delayLevel) {
        Message message = null;
        try {
            message = buildMessage(messageObject, null, null, delayLevel);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new Result<SendResult>(false, e);
        }
        return publishOrder(message, selector, order);
    }
    
    /**
     * 发送有序消息
     *
     * @param message 消息数据
     * @param selector 队列选择器，发送时会回调
     * @param order 回调队列选择器时，此参数会传入队列选择方法,提供配需规则
     * @return 发送结果
     */
    public Result<SendResult> publishOrder(Message message, MessageQueueSelector selector, Object order) {
        try {
            SendResult sendResult = producer.send(message, selector, order);
            return new Result<SendResult>(true, sendResult);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new Result<SendResult>(false, e);
        }
    }

    /**
     * 发送异步消息
     * 
     * @param messageObject 消息
     * 
     * @param keys key
     * @param delayLevel 延时级别
     * @param sendCallback 回调函数
     */
    public void publishAsync(Object messageObject, String keys, MessageDelayLevel delayLevel,
            SendCallback sendCallback) {
        publishAsync(messageObject, "", keys, delayLevel, sendCallback);
    }
    
    /**
     * 发送异步消息
     * 
     * @param messageObject 消息
     * @param tags tags
     * @param keys key
     * @param delayLevel 延时级别
     * @param sendCallback 回调函数
     */
    public void publishAsync(Object messageObject, String tags, String keys, MessageDelayLevel delayLevel,
            SendCallback sendCallback) {
        Message message = null;
        try {
            message = buildMessage(messageObject, tags, keys, delayLevel);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            sendCallback.onException(e);
        }
        publishAsync(message, sendCallback);
    }
    
    /**
     * 发送异步消息
     * 
     * @param message 消息
     * @param sendCallback 回调函数
     */
    public void publishAsync(Message message, SendCallback sendCallback) {
        try {
            producer.send(message, sendCallback);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            sendCallback.onException(e);
        }
    }

    /**
     * 发送异步消息
     * 
     * @param messageMap 消息数据
     * @param sendCallback 回调函数
     */
    public void publishAsync(Map<String, Object> messageMap, SendCallback sendCallback) {
        publishAsync((Object) messageMap, sendCallback);
    }

    /**
     * 发送异步消息
     * 
     * @param messageMap 消息数据
     * @param sendCallback 回调函数
     */
    public void publishAsync(Object messageObject, SendCallback sendCallback) {
        publishAsync(messageObject, "", sendCallback);
    }

    /**
     * 发送异步消息
     * 
     * @param messageMap 消息数据
     * @param messageMap key
     * @param sendCallback 回调函数
     */
    public void publishAsyncWithException(Object messageObject, String keys, SendCallback sendCallback)
            throws Exception {
        publishAsyncWithException(messageObject, "", keys, sendCallback);
    }
    
    /**
     * 发送异步消息
     * 
     * @param messageObject 消息数据
     * @param tags tags
     * @param String keys
     * @param sendCallback 回调函数
     */
    public void publishAsyncWithException(Object messageObject, String tags, String keys, SendCallback sendCallback)
            throws Exception {
        Message message = null;
        try {
            message = buildMessage(messageObject, tags, keys, null);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            sendCallback.onException(e);
            throw e;
        }
        publishAsyncWithException(message, sendCallback);
    }
    
    /**
     * 发送异步消息
     * 
     * @param messageObject 消息数据
     * @param sendCallback 回调函数
     */
    public void publishAsyncWithException(Message message, SendCallback sendCallback)
            throws Exception {
        try {
            producer.send(message, sendCallback);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            sendCallback.onException(e);
            throw e;
        }
    }

    /**
     * 发送异步消息
     * 
     * @param messageMap 消息数据
     * @param messageMap key
     * @param sendCallback 回调函数
     */
    public void publishAsync(Object messageObject, String keys, SendCallback sendCallback) {
        publishAsync(messageObject, keys, null, sendCallback);
    }

    /**
     * 发送Oneway消息
     * 
     * @param messageObject 消息
     * @param keys key
     * @param delayLevel 延时级别
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOneway(Object messageObject, String keys, MessageDelayLevel delayLevel) {
        return publishOneway(messageObject, "", keys, delayLevel);
    }
    
    /**
     * 发送Oneway消息
     * 
     * @param messageObject 消息
     * @param String tags
     * @param keys key
     * @param delayLevel 延时级别
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOneway(Object messageObject, String tags, String keys, MessageDelayLevel delayLevel) {
        Message message = null;
        try {
            message = buildMessage(messageObject, tags, keys, delayLevel);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new Result<SendResult>(false, e);
        }
        return publishOneway(message);
    }

    /**
     * 发送Oneway消息
     * 
     * @param message 消息
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOneway(Message message) {
        try {
            producer.sendOneway(message);
            return new Result<SendResult>(true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new Result<SendResult>(false, e);
        }
    }
    
    /**
     * 发送Oneway消息
     * 
     * @param messageMap 消息
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOneway(Map<String, Object> messageMap) {
        return publishOneway((Object) messageMap);
    }

    /**
     * 发送Oneway消息
     * 
     * @param messageMap 消息
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOneway(Object messageObject) {
        return publishOneway(messageObject, "");
    }

    /**
     * 发送Oneway消息
     * 
     * @param messageObject 消息
     * @param keys key
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOnewayWithExcetpion(Object messageObject, String keys) throws Exception {
        Result<SendResult> result = publishOneway(messageObject, keys, null);
        if (result.getException() != null) {
            throw result.getException();
        }
        return result;
    }

    /**
     * 发送Oneway消息
     * 
     * @param messageObject 消息
     * @param keys key
     * @return Result.true or false with exception
     */
    public Result<SendResult> publishOneway(Object messageObject, String keys) {
        return publishOneway(messageObject, keys, null);
    }

    public void shutdown() {
        producer.shutdown();
    }

    public DefaultMQProducer getProducer() {
        return producer;
    }

    /**
     * 参考@com.alibaba.rocketmq.store.config.MessageStoreConfig.messageDelayLevel定义
     */
    public enum MessageDelayLevel {
        LEVEL_1_SECOND(1, 1000), 
        LEVEL_5_SECONDS(2, 5000), 
        LEVEL_10_SECONDS(3, 10000), 
        LEVEL_30_SECONDS(4, 30000), 
        LEVEL_1_MINUTE(5, 60000), 
        LEVEL_2_MINUTES(6, 120000), 
        LEVEL_3_MINUTES(7, 180000), 
        LEVEL_4_MINUTES(8, 240000), 
        LEVEL_5_MINUTES(9, 300000), 
        LEVEL_6_MINUTES(10, 360000), 
        LEVEL_7_MINUTES(11, 420000), 
        LEVEL_8_MINUTES(12, 480000), 
        LEVEL_9_MINUTES(13, 540000), 
        LEVEL_10_MINUTES(14, 600000), 
        LEVEL_20_MINUTES(15, 1200000), 
        LEVEL_30_MINUTES(16, 1800000), 
        LEVEL_1_HOUR(17, 3600000), 
        LEVEL_2_HOURS(18, 7200000),
        ;

        private int level;
        private long delayTimeMillis;

        private MessageDelayLevel(int level, long delayTimeMillis) {
            this.level = level;
            this.delayTimeMillis = delayTimeMillis;
        }

        public static MessageDelayLevel findByLevel(int level) {
            for (MessageDelayLevel lev : values()) {
                if (level == lev.getLevel()) {
                    return lev;
                }
            }
            return null;
        }

        public long getDelayTimeMillis() {
            return delayTimeMillis;
        }

        public int getLevel() {
            return level;
        }
    }
    
    public StatsHelper getStatsHelper() {
        return statsHelper;
    }

    @Override
    protected int role() {
        return PRODUCER;
    }
}