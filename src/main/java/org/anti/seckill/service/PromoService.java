package org.anti.seckill.service;

import org.anti.seckill.service.model.PromoModel;

public interface PromoService {
    //根据itemid获取正在进行或即将进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);

    //活动发布
    void publishPromo(Integer promoId);

    //生成秒杀令牌
    String generateSeckillToken(Integer promoId, Integer userId, Integer itemId);
}
