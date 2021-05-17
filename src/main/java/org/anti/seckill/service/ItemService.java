package org.anti.seckill.service;

import org.anti.seckill.error.BusinessException;
import org.anti.seckill.service.model.ItemModel;

import java.util.List;

public interface ItemService {
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    List<ItemModel> listItem();

    //商品详情浏览
    ItemModel getItemById(Integer id);

    //item及promo model缓存模型
    ItemModel getItemByIdInCache(Integer id);

    //库存扣减
    boolean decreaseStock(Integer itemId, Integer amount);
    //库存增加
    boolean increaseStock(Integer itemId, Integer amount);

    //异步更新库存
//    boolean asyncDecreaseStock(Integer itemId, Integer amount);

    //销量增加
    void increaseSales(Integer itemId, Integer amount);

    //初始化库存流水
    String initStockLog(Integer itemId, Integer amount);
}
