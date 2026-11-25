package com.exam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 服务层 —— 封装所有与 DeepSeek Chat API 的交互
 * <p>
 * 职责：
 * <ol>
 *   <li>构建 HTTP 请求调用 DeepSeek API</li>
 *   <li>解析 AI 返回的 JSON 响应</li>
 *   <li>提取 Markdown 代码块中的纯 JSON 内容</li>
 *   <li>统一管理 API Key 和 Base URL</li>
 * </ol>
 */


@Service
public class DeepSeekService {

    private final String apiKey;
    private final String apiUrl;

    public DeepSeekService(@Value("${deepseek.api-key:}") String apiKey,
                           @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.apiUrl = baseUrl.replaceAll("/+$", "") + "/v1/chat/completions";
    }

    // HTTP 客户端（30秒连接超时 + 120秒读取超时，防止 Connection reset）
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    // JSONSeek API 响应解析器
    private final ObjectMapper mapper = new ObjectMapper();




    /**
     * 调用 DeepSeek Chat API
     *
     * @param prompt      用户提示词
     * @param maxTokens   最大 token 数
     * @param temperature 温度参数（0.0=确定, 1.0=随机）
     * @return AI 返回的纯文本内容（已去除 Markdown 代码块标记）
     */
    // 标记该方法为处理POST请求的处理程序，返回值为String
    // 自动将返回值转换为JSON响应
    public String call(String prompt, int maxTokens, double temperature) {




        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("尚未配置 DEEPSEEK_API_KEY，AI 功能暂不可用");
        }
        try {
            // 构建请求体
            // model: 模型名称，deepseek-chat
            // messages: 包含用户提示词的 JSON 数组
            // max_tokens: 最大 token 数
            // temperature: 温度参数，0.0=确定, 1.0=随机
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "deepseek-chat");
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);

            // 转换为 JSON 字符串
            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                // 解析 JSON 响应
            @SuppressWarnings("unchecked")
            Map<String, Object> respBody = mapper.readValue(resp.body(), Map.class);
            if (!respBody.containsKey("choices")) {
                throw new RuntimeException("AI接口异常");
            }
            // 提取第一个 choice 的 message 内容
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) respBody.get("choices");
            String raw = (String) ((Map<String, Object>) choices.get(0)
                    .get("message")).get("content");
            // 去除 AI 返回内容中的 Markdown 代码块标记
            return raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AI接口调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 AI 返回文本中提取 JSON 对象（找到第一个 { 到最后一个 } 之间的内容）
     *
     * @param content AI 返回的文本内容
     * @return JSON 字符串
     */
    public String extractJson(String content) {
        // 查找第一个 { 和最后一个 } 的索引
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        /// 校验是否找到 JSON 对象
        if (start >= 0 && end > start) {
            // 提取 JSON 对象
            return content.substring(start, end + 1);
        }
        return content;
    }
}
