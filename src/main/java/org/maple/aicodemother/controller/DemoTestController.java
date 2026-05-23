package org.maple.aicodemother.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
public class DemoTestController {

    // 假设你直接实例化 RestTemplate，实际项目中通常通过 @Autowired 注入
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/test-python-ai")
    public String testPythonAi() {
        // 1. 目标 Python 服务的地址
        String pythonUrl = "http://localhost:8000/api/chat";

        // 2. 组装请求参数 (对应 Python 的 SimpleChatRequest)
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("message", "你好，你是谁？");

        System.out.println("开始向 Python 服务发送请求...");

        try {
            // 3. 发送 POST 请求，期望返回一个 Map (JSON)
            Map<String, Object> response = restTemplate.postForObject(pythonUrl, requestBody, Map.class);
            
            System.out.println("成功收到 Python 服务的响应！");
            
            // 4. 解析并返回给浏览器
            if (response != null && response.containsKey("data")) {
                return "Python 端 AI 回答: " + response.get("data");
            } else {
                return "请求成功，但未解析到有效数据: " + response;
            }

        } catch (Exception e) {
            return "调用 Python 服务失败: " + e.getMessage();
        }
    }
}