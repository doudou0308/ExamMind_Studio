package com.exam.controller;

import com.exam.config.AuthContext;
import com.exam.dao.ExamDao;
import com.exam.dao.ExamRecordDao;
import com.exam.dao.UserDao;
import com.exam.model.Exam;
import com.exam.model.ExamRecord;
import com.exam.model.User;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩导出控制器
 * 功能：将指定考试的学生成绩导出为 Excel 文件（.xlsx）
 * 输出列：序号、姓名、得分、总分
 */
@CrossOrigin(origins = "*")                             // 允许前端跨域请求
@RestController                                         // 标识为 REST 控制器，所有方法返回值自动序列化为 JSON
@RequestMapping("/api/admin/exam")                      // 请求路径前缀：/api/admin/exam
public class ExportController {

    private final ExamDao examDao = new ExamDao();             // 考试表数据库访问
    private final ExamRecordDao recordDao = new ExamRecordDao(); // 答卷记录表数据库访问
    private final UserDao userDao = new UserDao();             // 用户表数据库访问

    /**
     * 导出考试成绩为 Excel
     * GET /api/admin/exam/export/{examId}?token=xxx
     *
     * @param examId   路径参数：考试ID
     * @param token    查询参数：管理员认证令牌
     * @param response HTTP 响应对象，用于向浏览器输出 Excel 文件流
     */
    @GetMapping("/export/{examId}")                                // 映射 GET 请求，{examId} 为路径变量
    public void export(@PathVariable long examId,                   // @PathVariable：从 URL 路径中提取考试ID
                       @RequestParam String token,                  // @RequestParam：从 URL 查询参数中提取 token
                       HttpServletResponse response) throws Exception {

        // ========== 步骤1：身份认证 ==========
        if (token == null || AuthContext.getUserByToken(token) == null) {  // token 为空或无效
            response.setStatus(401);                                       // HTTP 401 Unauthorized
            response.getWriter().write("{\"ok\":false,\"msg\":\"token无效\"}"); // 返回 JSON 错误信息
            return;                                                        // 终止请求
        }

        // ========== 步骤2：查询考试信息 ==========
        Exam exam = examDao.getById(examId);                               // 根据考试ID从数据库查询考试
        if (exam == null) {                                                // 考试不存在
            response.setStatus(404);                                       // HTTP 404 Not Found
            response.getWriter().write("{\"ok\":false,\"msg\":\"考试不存在\"}");
            return;
        }

        // ========== 步骤3：计算试卷总分 ==========
        Map<Integer, Integer> scoreMap = examDao.listQuestionScores(examId); // 查询每题的分值：{题目ID → 分值}
        int totalScore = 0;                                                  // 试卷满分，初始为0
        for (int s : scoreMap.values()) totalScore += s;                     // 累加所有题目的分值得到总分

        // ========== 步骤4：加载答卷记录 ==========
        List<ExamRecord> records = recordDao.listByExamId(examId);           // 查询该考试下的所有学生答卷
        Map<Integer, User> userCache = new HashMap<>();                      // 学生ID→用户对象缓存，避免重复查库

        // ========== 步骤5：创建 Excel 工作簿 ==========
        Workbook wb = new XSSFWorkbook();                                    // 创建 .xlsx 格式的工作簿
        Sheet sheet = wb.createSheet("成绩表");                               // 创建工作表，名称为"成绩表"

        // ---------- 5.1 创建表头样式 ----------
        CellStyle headerStyle = wb.createCellStyle();                        // 创建样式对象
        Font headerFont = wb.createFont();                                   // 创建字体对象
        headerFont.setBold(true);                                            // 字体加粗
        headerStyle.setFont(headerFont);                                     // 将字体绑定到样式
        headerStyle.setAlignment(HorizontalAlignment.CENTER);                // 水平居中

        // ---------- 5.2 写入表头行（第0行） ----------
        Row headerRow = sheet.createRow(0);                                  // 在第0行创建表头行
        String[] titles = {"序号", "姓名", "得分", "总分"};                   // 4个列标题
        for (int i = 0; i < titles.length; i++) {                            // 遍历列标题
            Cell cell = headerRow.createCell(i);                              // 在第i列创建单元格
            cell.setCellValue(titles[i]);                                     // 设置单元格文本
            cell.setCellStyle(headerStyle);                                   // 应用表头样式
        }

        // ---------- 5.3 逐学生写入数据行 ----------
        for (int i = 0; i < records.size(); i++) {                           // 遍历所有答卷记录
            ExamRecord r = records.get(i);                                    // 当前答卷记录

            // 获取学生姓名（带缓存，避免重复查数据库）
            User student = userCache.computeIfAbsent(                        // 如果缓存中没有这个学生
                    (int) r.getStudentId(),                                  // 用学生ID作为key
                    userDao::findById);                                      // 没缓存时从数据库查
            String name = student != null ? student.getUsername() : "未知";   // 获取用户名，不存在则显示"未知"

            Row row = sheet.createRow(i + 1);                                // 创建数据行（第1行开始）
            row.createCell(0).setCellValue(i + 1);                           // A列：序号（从1开始编号）
            row.createCell(1).setCellValue(name);                            // B列：学生姓名
            row.createCell(2).setCellValue(r.getScore());                    // C列：该生得分
            row.createCell(3).setCellValue(totalScore);                      // D列：试卷总分
        }

        // ---------- 5.4 设置列宽 ----------
        sheet.setColumnWidth(0, 6 * 256);                                    // A列列宽：约6个中文字符
        sheet.setColumnWidth(1, 14 * 256);                                   // B列列宽：约14个中文字符
        sheet.setColumnWidth(2, 10 * 256);                                   // C列列宽：约10个字符
        sheet.setColumnWidth(3, 10 * 256);                                   // D列列宽：约10个字符

        // ========== 步骤6：输出 Excel 文件 ==========
        String fileName = URLEncoder.encode(                                 // URL编码文件名中的中文
                exam.getExamName() + "_成绩.xlsx", "UTF-8");                 // 文件名：考试名_成绩.xlsx
        response.setContentType(                                             // 设置响应内容类型
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); // Excel 的 MIME 类型
        response.setHeader("Content-Disposition",                            // 设置响应头
                "attachment; filename*=UTF-8''" + fileName);                 // attachment=下载，filename*=文件名
        wb.write(response.getOutputStream());                                // 将工作簿写入 HTTP 响应输出流
        wb.close();                                                          // 关闭工作簿，释放资源
    }
}
