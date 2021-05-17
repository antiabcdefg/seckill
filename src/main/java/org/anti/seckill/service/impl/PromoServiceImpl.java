package org.anti.seckill.service.impl;

import org.anti.seckill.domain.PromoDO;
import org.anti.seckill.error.BusinessException;
import org.anti.seckill.error.EmBusinessError;
import org.anti.seckill.mapper.PromoDOMapper;
import org.anti.seckill.service.ItemService;
import org.anti.seckill.service.PromoService;
import org.anti.seckill.service.UserService;
import org.anti.seckill.service.model.ItemModel;
import org.anti.seckill.service.model.PromoModel;
import org.anti.seckill.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        //date -> obj
        PromoModel promoModel = convertFromDataObject(promoDO);
        if (promoModel == null) return null;
        //判断秒杀活动状态
        if (promoModel.getStartTime().isAfterNow()) promoModel.setStatus(1);
        else if (promoModel.getEndTime().isBeforeNow()) promoModel.setStatus(3);
        else promoModel.setStatus(2);

        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        //通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if (promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0) return;
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());

        //将库存同步到redis内
        redisTemplate.opsForValue().set("promo_item_stock_" + itemModel.getId(), itemModel.getStock());

        //将闸门的限制数字设到redis内
        redisTemplate.opsForValue().set("promo_door_count_" + promoId, itemModel.getStock().intValue() * 5);
    }

    @Override
    public String generateSeckillToken(Integer promoId, Integer userId, Integer itemId) {

        //判断库存是否已售罄，若对应的售罄key存在，则直接返回下单失败
        if (redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)) return null;

        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertFromDataObject(promoDO);
        if (promoModel == null) return null;

        //判断当前时间是否秒杀活动即将开始或正在进行
        if (promoModel.getStartTime().isAfterNow()) promoModel.setStatus(1);
        else if (promoModel.getEndTime().isBeforeNow()) promoModel.setStatus(3);
        else promoModel.setStatus(2);
        if (promoModel.getStatus().intValue() != 2) return null;

        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) return null;

        UserModel userModel = userService.getUserByIdInCache(userId);
        if (userModel == null) return null;

        //获取秒杀闸门数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_" + promoId, -1);
        if (result < 0) return null;
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("promo_token_" + promoId + "_userId_" + userId + "itemId_" + itemId, token);
        redisTemplate.expire("promo_token_" + promoId + "_userId_" + userId + "itemId_" + itemId, 5, TimeUnit.MINUTES);
        return token;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO) {
        if (promoDO == null) return null;
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartTime(new DateTime(promoDO.getStartTime()));
        promoModel.setEndTime(new DateTime(promoDO.getEndTime()));
        return promoModel;
    }
}
