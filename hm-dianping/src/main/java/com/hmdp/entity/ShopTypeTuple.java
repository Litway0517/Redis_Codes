package com.hmdp.entity;

import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.ZSetOperations;

public class ShopTypeTuple implements ZSetOperations.TypedTuple<String> {

    private ShopType shopType;
    private Double score;

    public ShopTypeTuple() {
    }

    public ShopTypeTuple(ShopType shopType, Double score) {
        this.shopType = shopType;
        this.score = score;
    }

    @Override
    public String getValue() {
        return JSONUtil.toJsonStr(shopType);
    }

    @Override
    public Double getScore() {
        return score;
    }

    public int compareTo(Double o) {

        double thisScore = (score == null ? 0.0 : score);
        double otherScore = (o == null ? 0.0 : o);

        return Double.compare(thisScore, otherScore);
    }

    @Override
    public int compareTo(ZSetOperations.TypedTuple<String> o) {

        if (o == null) {
            return compareTo(Double.valueOf(0));
        }

        return compareTo(o.getScore());
    }

}
