package com.openapi.myapp.bean.vo;

/** 观看券状态，与服务端 RewardStatusVo 字段一一对应。 */
public class RewardStatusVo {
    public int tickets;
    public int todayCount;
    public int dailyLimit;
    public int cooldownSec;
    public int redeemTickets;
    public int redeemHours;
    public String vipEnd;
    /** 24 小时内即将过期的观看券数量。 */
    public int expiringTickets;
}
