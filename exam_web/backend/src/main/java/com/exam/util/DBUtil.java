package com.exam.util;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接工具类
 * 封装了 Spring 框架的 DataSourceUtils，统一管理数据库连接的获取和释放
 * 被所有 DAO 类（UserDao、QuestionDao、ExamDao、ExamRecordDao）调用
 */
@Component// 标记为 Spring 组件，自动注入 DataSource
public class DBUtil {

    /** 数据库连接池（由 Spring 在启动时自动注入） */
    private static DataSource dataSource;

    /**
     * 构造函数 —— Spring 会自动将配置好的 DataSource 注入进来
     * @param dataSource Spring 管理的数据库连接池
     */
    public DBUtil(DataSource dataSource) {
        DBUtil.dataSource = dataSource;
    }

    /**
     * 从连接池获取一个数据库连接
     * 使用 DataSourceUtils 获取，确保在同一事务中返回同一个连接
     * @return 数据库连接对象
     * @throws SQLException 获取连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        return DataSourceUtils.getConnection(dataSource);
    }

    /**
     * 释放数据库连接（归还给连接池）
     * 不真正关闭连接，而是让连接池回收复用
     * @param conn 要释放的数据库连接
     */
    public static void close(Connection conn) {
        DataSourceUtils.releaseConnection(conn, dataSource);
    }
}
