package com.openapi.common.util.db;

public class WhereStringBuilder {
    private StringBuilder mSelectWhere = new StringBuilder(" ");

    @Override
    public String toString() {
        return mSelectWhere.toString();
    }

    /**
     * 增加一个 ")"
     *
     * @return
     */
    public WhereStringBuilder rP() {
        mSelectWhere.append(")");
        return this;
    }

    /**
     * 增加一个 "("
     *
     * @return
     */
    public WhereStringBuilder lP() {
        mSelectWhere.append("(");
        return this;
    }

    /**
     * 增加一个 " OR "
     *
     * @return
     */
    public WhereStringBuilder or() {
        mSelectWhere.append(" OR ");
        return this;
    }

    /**
     * 增加一个 " AND "
     *
     * @return
     */
    public WhereStringBuilder and() {
        mSelectWhere.append(" AND ");
        return this;
    }

    /**
     * 增加变量<br/>
     * " xxx=?"形式<br/>
     *
     * @param column
     * @return
     */
    public WhereStringBuilder append(String column) {
        mSelectWhere.append(column).append("=").append("?");
        return this;
    }

    /**
     * 增加不等变量
     *
     * @param column
     * @return
     */
    public WhereStringBuilder appendNE(String column) {
        mSelectWhere.append(column).append("<>").append("?");
        return this;
    }

    /**
     * 增加变量<br/>
     * " xxx>?"形式<br/>
     *
     * @param column
     * @return
     */
    public WhereStringBuilder appendGreater(String column) {
        mSelectWhere.append(column).append(">").append("?");
        return this;
    }

    /**
     * 增加变量<br/>
     * " xxx < ?"形式<br/>
     *
     * @param column
     * @return
     */
    public WhereStringBuilder appendLess(String column) {
        mSelectWhere.append(column).append("<").append("?");
        return this;
    }

    public StringBuilder getStringBuilder() {
        return mSelectWhere;
    }
}
