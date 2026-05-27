package org.maple.aicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.maple.aicodemother.ai.model.enums.CodeGenTypeEnum;
import org.maple.aicodemother.constant.AppConstant;
import org.maple.aicodemother.core.AiCodeGeneratorFacade;
import org.maple.aicodemother.core.builder.VueProjectBuilder;
import org.maple.aicodemother.core.handler.StreamHandlerExecutor;
import org.maple.aicodemother.exception.BusinessException;
import org.maple.aicodemother.exception.ErrorCode;
import org.maple.aicodemother.exception.ThrowUtils;
import org.maple.aicodemother.model.dto.app.AppAddRequest;
import org.maple.aicodemother.model.dto.app.AppQueryRequest;
import org.maple.aicodemother.model.entity.App;
import org.maple.aicodemother.mapper.AppMapper;
import org.maple.aicodemother.model.entity.User;
import org.maple.aicodemother.model.enums.ChatHistoryMessageTypeEnum;
import org.maple.aicodemother.model.vo.AppVO;
import org.maple.aicodemother.model.vo.UserVO;
import org.maple.aicodemother.service.AppService;
import org.maple.aicodemother.service.ChatHistoryService;
import org.maple.aicodemother.service.ScreenshotService;
import org.maple.aicodemother.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/MapleWaning">Maple</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    private final UserService userService;

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;

    private final ChatHistoryService chatHistoryService;
    private final StreamHandlerExecutor streamHandlerExecutor;
    private final VueProjectBuilder vueProjectBuilder;
    private final ScreenshotService screenshotService;

    private final WebClient.Builder webClientBuilder;

    @Value("${code.deploy-host}")
    private String deployHost;

    @Value("${ai-service.python-base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 调用 Python AI 微服务智能选择代码生成类型
        CodeGenTypeEnum selectedCodeGenType = routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    private CodeGenTypeEnum routeCodeGenType(String initPrompt) {
        PythonRouteResponse routeResponse = webClientBuilder
                .baseUrl(pythonAiBaseUrl)
                .build()
                .post()
                .uri("/api/route")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(new PythonRouteRequest(initPrompt))
                .retrieve()
                .bodyToMono(PythonRouteResponse.class)
                .block(Duration.ofSeconds(60));
        if (routeResponse == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 路由服务无响应");
        }
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(routeResponse.codeGenType());
        if (codeGenType == null && StrUtil.isNotBlank(routeResponse.enumName())) {
            try {
                codeGenType = CodeGenTypeEnum.valueOf(routeResponse.enumName());
            } catch (IllegalArgumentException ignored) {
                // 交给下面的统一异常处理
            }
        }
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.SYSTEM_ERROR, "AI 路由服务返回了不支持的生成类型");
        return codeGenType;
    }

    private record PythonRouteRequest(String initPrompt) {
    }

    private record PythonRouteResponse(String codeGenType, String enumName, String reason) {
    }



    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if(!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限访问该应用");
        }
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        Flux<String> stringFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        return streamHandlerExecutor.doExecute(stringFlux,chatHistoryService,appId, loginUser,codeGenTypeEnum);
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf (appId == null || appId <= 0,ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户信未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if(!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限访问该应用");
        }
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码目录不存在");
        }
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT){
            // 如果是 Vue 项目，部署前需要先构建项目
            boolean buildResult = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildResult, ErrorCode.SYSTEM_ERROR, "Vue项目构建失败，无法部署");
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(),ErrorCode.SYSTEM_ERROR, "Vue项目部署失败，dist目录不存在，无法找到构建产物");
            sourceDir = distDir;
            log.info("Vue项目构建成功，准备部署dist目录：{}", distDir.getAbsolutePath());
        }
        String destDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        File destDir = new File(destDirPath);

// 3. 开始搬砖！把生成的文件全量复制到部署目录下
        try {
            // 覆盖式复制目录
            FileUtil.copyContent(sourceDir, destDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败，文件转移异常："+ e.getMessage());
        }
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "更新应用部署信息失败");
        String appDeployUrl = StrUtil.format("{}/{}/", deployHost, deployKey);
        generateAppScreenshotAsync(appId,appDeployUrl);
        // 这里直接返回部署的访问地址，实际项目中可能需要更复杂的部署流程
        return appDeployUrl;
    }

    @Override
    public boolean removeById(Serializable id) {
        if(id == null) {
            return false;
        }
        Long appId = Long.valueOf(id.toString());
        if(appId <= 0) {
            return false;
        }
        try {
            // 删除应用的同时，删除相关的对话历史
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 这里可以记录日志，或者做其他的错误处理
            log.error("删除应用关联历史记录失败： {}",e.getMessage());
        }
        return super.removeById(id);
    }



    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新应用封面字段
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    @Cacheable(
            value = "good_app_page",
            key = "T(org.maple.aicodemother.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest != null && #appQueryRequest.pageNum <= 10"
    )
    @Override
    public Page<AppVO> listGoodAppVO(AppQueryRequest appQueryRequest) {
        // 将 Controller 里的逻辑搬移到这里
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = this.getQueryWrapper(appQueryRequest);

        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();

        // 调用 MyBatis-Plus 自带的 page 方法
        Page<App> appPage = this.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = this.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        // 封装 VO
        return appVOPage; // 自定义一个转换方法
    }


}
