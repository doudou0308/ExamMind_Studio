package com.exam.model;

/**
 * 用户实体类 —— 对应数据库中的 user 表
 * 存储系统中所有用户（管理员和学生）的基本信息
 */
public class User {

    /** 用户唯一标识（主键） */
    private int id;

    /** 用户名，用于登录 */
    private String username;

    /** 登录密码（数据库中存储的是 MD5 加密后的值） */
    private String password;

    /** 角色：admin（管理员）或 student（学生） */
    private String role;

    /**
     * 是否首次登录
     * true = 首次登录，需要强制修改密码
     * false = 已修改过密码，正常登录
     */
    private boolean isFirstLogin;

    // ======================== Getter / Setter ========================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isFirstLogin() { return isFirstLogin; }
    public void setFirstLogin(boolean firstLogin) { isFirstLogin = firstLogin; }
}
