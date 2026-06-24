package org.example.api_playground.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.api_playground.llm.client.LlmApiClient;
import org.example.api_playground.llm.model.ApiCallRecord;
import org.example.api_playground.llm.model.ChatMessage;
import org.example.api_playground.llm.model.ChatRequest;
import org.example.api_playground.llm.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {

    private final ObjectMapper objectMapper;
    private final LlmApiClient llmApiClient;
    private final ToolExecutionService toolExecutionService;
    private final RecordService recordService;
    private final ConversationService conversationService;

    public ChatService(ObjectMapper objectMapper, LlmApiClient llmApiClient,
                       ToolExecutionService toolExecutionService, RecordService recordService,
                       ConversationService conversationService) {
        this.objectMapper = objectMapper;
        this.llmApiClient = llmApiClient;
        this.toolExecutionService = toolExecutionService;
        this.recordService = recordService;
        this.conversationService = conversationService;
    }

    private List<Object> buildTools() {
        List<Object> tools = new ArrayList<>();
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_current_time",
                        "description", "获取当前日期和时间。当你需要确认今天是什么日期、星期几、当前时间时调用此工具。重要：你无法从训练数据得知当前时间，必须调用此工具获取。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of()
                        )
                )
        ));
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_weather",
                        "description", "查询指定城市的实时天气信息，返回温度、天气状况和湿度",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "city", Map.of(
                                                "type", "string",
                                                "description", "城市名称，例如 Beijing、Shanghai、Tokyo"
                                        )
                                ),
                                "required", List.of("city")
                        )
                )
        ));
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_football_match_info",
                        "description", "【仅限俱乐部赛事】查询欧洲俱乐部联赛（英超/德甲/西甲/意甲/法甲/欧冠等）球队的近期状态和交手信息。只需一个队名即可查询，两个队名则会对比。当用户提到俱乐部名称（如曼联、皇马、拜仁、巴萨等）或联赛名称时调用此工具。⚠️ 如果用户提到国家队（如巴西、法国、阿根廷、德国、英格兰等）或国际赛事（世界杯、欧洲杯、美洲杯），请使用 get_world_cup_match_info。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "team1", Map.of(
                                                "type", "string",
                                                "description", "主队名称（俱乐部），例如 Manchester United、Real Madrid、Bayern Munich"
                                        ),
                                        "team2", Map.of(
                                                "type", "string",
                                                "description", "客队名称（俱乐部），可选。只传 team1 时可查询单队近期状态。例如 Liverpool、Barcelona、Borussia Dortmund"
                                        ),
                                        "need_prediction", Map.of(
                                                "type", "boolean",
                                                "description", "是否需要预测比分和赔率。仅在用户明确要求'预测比分'、'比分是多少'、'谁会赢'、'赔率'时才设为 true，默认 false。仅查状态时不要设为 true。"
                                        )
                                ),
                                "required", List.of("team1")
                        )
                )
        ));
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_world_cup_match_info",
                        "description", "【仅限国家队赛事】查询国家队（世界杯/欧洲杯/美洲杯/亚洲杯/欧国联/友谊赛等国际赛事）的近期状态和交手信息，数据来源 api-football.com。当用户提到国家队名称（如巴西、法国、阿根廷、德国、英格兰、日本、韩国等）或国际赛事名称时调用此工具。只需一个队名即可查询，两个队名则会对比。⚠️ 如果用户提到俱乐部（如曼联、皇马、拜仁等）或联赛（英超、西甲等），请使用 get_football_match_info。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "team1", Map.of(
                                                "type", "string",
                                                "description", "要查询的国家队名称，例如 Brazil、France、Argentina、Germany。只需一个队名即可。"
                                        ),
                                        "team2", Map.of(
                                                "type", "string",
                                                "description", "对手国家队名称，可选。例如 England、Spain、Portugal、Japan。不传则只查 team1 的近期状态。"
                                        ),
                                        "need_prediction", Map.of(
                                                "type", "boolean",
                                                "description", "是否需要预测比分和赔率。仅在用户明确要求'预测比分'、'比分是多少'、'谁会赢'、'赔率'时才设为 true，默认 false。仅查状态时不要设为 true。"
                                        )
                                ),
                                "required", List.of("team1")
                        )
                )
        ));
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_football_news",
                        "description", "【足球信息最终确认】通过 DuckDuckGo 网页搜索引擎实时检索足球比赛的最新资讯、新闻报道和赛前分析。在完成 get_football_match_info 或 get_world_cup_match_info 之后，必须调用此工具进行网页交叉验证，确保信息是最新的。参数：team1(必填，主队名称)、team2(可选，客队名称)、keyword(可选，自定义搜索关键词，不传则自动拼接球队名称和\"football match latest news\")。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "team1", Map.of(
                                                "type", "string",
                                                "description", "主队名称，例如 Manchester United、Brazil"
                                        ),
                                        "team2", Map.of(
                                                "type", "string",
                                                "description", "客队名称，可选。例如 Liverpool、Argentina"
                                        ),
                                        "keyword", Map.of(
                                                "type", "string",
                                                "description", "自定义搜索关键词，可选。不传则自动生成搜索词"
                                        )
                                ),
                                "required", List.of("team1")
                        )
                )
        ));
        return tools;
    }

    public List<Map<String, Object>> getAvailableTools() {
        return List.of(
                Map.of(
                        "name", "get_current_time",
                        "title", "当前时间",
                        "description", "获取当前日期、时间和星期几，适用于需要确认今天是什么日期的场景。",
                        "example", "今天是几号？现在是什么时间？",
                        "outputFormat", "【当前时间】yyyy-MM-dd HH:mm:ss\n【日期】yyyy-MM-dd\n【星期】星期X\n【时区】Asia/Shanghai"
                ),
                Map.of(
                        "name", "get_weather",
                        "title", "天气查询",
                        "description", "查询城市天气，目前支持 Beijing、Shanghai、Tokyo，其他城市返回 mock 兜底数据。",
                        "example", "北京今天天气怎么样？",
                        "outputFormat", "请按以下格式输出天气信息：\n【城市】城市名\n【温度】XX°C\n【天气】晴/多云/雨等\n【湿度】XX%\n【风力】风向 + 风力等级\n【数据来源】和风天气实时数据 或 mock数据\n\n并根据以上天气信息，补充以下生活建议：\n【出行建议】是否适合出门，需要注意什么\n【带伞建议】是否需要带伞\n【穿衣建议】根据温度推荐穿什么衣服（如薄外套、羽绒服、短袖等）\n【交通建议】推荐开车、骑车还是公共交通，如有大风大雨建议减少骑行"
                ),
                Map.of(
                        "name", "get_football_match_info",
                        "title", "俱乐部足球",
                        "description", "查询欧洲俱乐部联赛（英超、德甲、西甲等）球队近期状态；仅在用户明确要求预测比分时才包含赔率。",
                        "example", "帮我预测一下 Bayern Munich 对 Borussia Dortmund 的比赛结果",
                        "outputFormat", "【对阵】主队名称 vs 客队名称\n【近期状态】主队近N场：W/D/L... | 客队近N场：W/D/L...\n【数据周期】YYYY-MM-DD ~ YYYY-MM-DD\n（以下仅在用户要求预测比分时输出）\n【赔率参考（胜平负概率）】主胜 XX% | 平局 XX% | 客胜 XX%\n【预测比分】X - Y\n【简评】一两句分析\n【数据来源】football-data.org 真实数据 或 mock模拟数据"
                ),
                Map.of(
                        "name", "get_world_cup_match_info",
                        "title", "国家队/世界杯",
                        "description", "查询国家队国际赛事（世界杯、欧洲杯、美洲杯等）近期状态；仅在用户明确要求预测比分时才包含赔率。数据来源 api-football.com。",
                        "example", "帮我预测一下 Brazil 对 Argentina 的世界杯比赛结果",
                        "outputFormat", "【对阵】主队国家队 vs 客队国家队\n【赛事类型】根据recent_matches中的competition字段区分\n【近期状态】主队近N场：W/D/L... | 客队近N场：W/D/L...\n（以下仅在用户要求预测比分时输出）\n【赔率参考（胜平负概率）】主胜 XX% | 平局 XX% | 客胜 XX%\n【预测比分】X - Y\n【简评】一两句分析\n【数据来源】api-football.com 实时数据 或 mock模拟数据"
                ),
                Map.of(
                        "name", "search_football_news",
                        "title", "网页搜索验证",
                        "description", "通过 DuckDuckGo 实时搜索网页，获取足球比赛的最新资讯、新闻报道和赛前分析，用于交叉验证 API 数据的时效性。",
                        "example", "搜索一下 Manchester United vs Liverpool 的最新新闻",
                        "outputFormat", "【网页搜索】已执行 / 未执行\n【搜索状态】成功 / 失败（附原因）\n【搜索引擎】DuckDuckGo\n【搜索结果摘要】实时检索到的新闻摘要\n【数据来源】网页实时检索（DuckDuckGo）或 网页搜索失败，降级为内部数据"
                )
        );
    }

    private Map<String, String> getToolOutputFormats() {
        Map<String, String> formats = new HashMap<>();
        formats.put("get_weather", "请按以下格式输出天气信息：\n【城市】城市名\n【温度】XX°C\n【天气】晴/多云/雨等\n【湿度】XX%\n【风力】风向 + 风力等级\n【数据来源】和风天气实时数据 或 mock数据\n\n并根据以上天气信息，补充以下生活建议：\n【出行建议】是否适合出门，需要注意什么\n【带伞建议】是否需要带伞\n【穿衣建议】根据温度推荐穿什么衣服（如薄外套、羽绒服、短袖等）\n【交通建议】推荐开车、骑车还是公共交通，如有大风大雨建议减少骑行");
        formats.put("get_football_match_info", "请按以下格式输出俱乐部足球信息：\n【对阵】主队名称 vs 客队名称\n【近期状态】主队近N场：W/D/L...（最新比赛日期：YYYY-MM-DD）| 客队近N场：W/D/L...（最新比赛日期：YYYY-MM-DD）\n【数据周期】所查数据的时间范围\n（以下内容仅在用户明确要求预测比分、胜负、赔率时才输出）\n【赔率参考】主胜 XX% | 平局 XX% | 客胜 XX%\n【预测比分】X - Y\n【简评】一两句分析\n【数据来源】football-data.org 真实数据 / 若无赔率则标注\"赔率未请求\"");
        formats.put("get_world_cup_match_info", "请按以下格式输出国家队足球信息：\n【对阵】主队国家队 vs 客队国家队\n【数据说明】数据来自 api-football.com，输出时务必根据 recent_matches 中每场比赛的 competition 字段区分赛事类型（World Cup/Friendlies/Qualifier等）！\n【近期状态】主队近N场：W/D/L...（最新比赛日期：YYYY-MM-DD）| 客队近N场：W/D/L...（最新比赛日期：YYYY-MM-DD）\n（以下内容仅在用户明确要求预测比分、胜负、赔率时才输出）\n【赔率参考】主胜 XX% | 平局 XX% | 客胜 XX%\n【预测比分】X - Y\n【简评】一两句分析\n【数据来源】api-football.com 实时数据");
        formats.put("search_football_news", "请按以下格式输出网页搜索结果：\n【网页搜索】已执行\n【搜索状态】成功 / 失败（附失败原因）\n【搜索引擎】DuckDuckGo\n【搜索结果摘要】从网页检索到的相关新闻和资讯摘要\n【数据来源】网页实时检索（DuckDuckGo）或 网页搜索失败，降级为内部数据");
        return formats;
    }

    public void injectToolFormatHints(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) return;
        ChatMessage systemMsg = request.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .findFirst().orElse(null);
        if (systemMsg == null) return;
        if (systemMsg.getContent() != null && systemMsg.getContent().contains("--- 工具输出格式规范 ---")) return;

        String userPrompt = systemMsg.getContent() != null ? systemMsg.getContent() : "";
        boolean isWeatherMode = userPrompt.contains("天气");
        boolean isFootballMode = userPrompt.contains("足球") || userPrompt.contains("球队")
                || userPrompt.contains("世界杯") || userPrompt.contains("联赛")
                || userPrompt.contains("俱乐部") || userPrompt.contains("国家队");

        LocalDate today = LocalDate.now();
        Map<String, String> formats = getToolOutputFormats();
        StringBuilder hint = new StringBuilder();

        hint.append("\n\n--- 系统环境信息 ---\n");
        hint.append("当前日期：").append(today.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))).append("（星期");
        String[] weekDays = {"日", "一", "二", "三", "四", "五", "六"};
        hint.append(weekDays[today.getDayOfWeek().getValue() % 7]).append("）\n");

        // ──── 三个模式各自独立的后端提示词 ────
        if (isWeatherMode) {
            hint.append("\n--- 天气专家专属指导 ---\n");
            hint.append("你是专业的天气和生活顾问。处理天气查询时，必须按以下顺序执行：\n\n");
            hint.append("【第0步 - 确认时间】\n");
            hint.append("先调用 get_current_time 确认今天是哪一天、什么季节，这直接影响穿衣和出行建议的准确性。\n\n");
            hint.append("【第1步 - 获取天气】\n");
            hint.append("调用 get_weather 工具获取实时天气数据，严禁凭训练数据编造天气信息。\n\n");
            hint.append("【第2步 - 给出建议】\n");
            hint.append("仔细检查工具返回的 source 字段：\n");
            hint.append("  - 如果 source 包含[数据不可用]或[模拟数据]或[降级]，说明当前无法获取实时天气，你必须明确告诉用户：\n");
            hint.append("    [抱歉，我暂时无法获取实时天气数据，以下信息为模拟数据，仅供参考，不代表实际情况。]\n");
            hint.append("    然后输出模拟数据，但不要对模拟数据展开分析或给建议，因为那不是真实天气。\n");
            hint.append("  - 如果 source 是[和风天气实时数据]，说明数据真实，则正常给出以下建议：\n");
            hint.append("    穿衣建议（薄外套/羽绒服/短袖等，具体到衣物类型）\n");
            hint.append("    带伞建议（根据雨雪情况明确判断）\n");
            hint.append("    出行建议和交通方式（开车、骑车还是公共交通）\n");
            hint.append("语气亲切温暖，像朋友给出门前建议一样。\n\n");
            hint.append("--- Few-shot 示例 ---\n\n");
            hint.append("【示例 1：真实数据】\n");
            hint.append("用户：北京今天天气怎么样？\n");
            hint.append("正确做法：\n");
            hint.append("1. 调用 get_current_time → 得到 {\"datetime\":\"2026-06-16 10:30:00\",\"date\":\"2026-06-16\",\"day_of_week\":\"TUESDAY\"}\n");
            hint.append("2. 调用 get_weather(city=\"Beijing\") → 得到 {\"city\":\"北京\",\"temperature\":28,\"condition\":\"晴\",\"humidity\":35,\"wind\":\"北风 2级\",\"source\":\"和风天气实时数据\"}\n");
            hint.append("3. 检查 source=[和风天气实时数据]，是真实数据，正常输出：\n\n");
            hint.append("【城市】北京\n【温度】28°C\n【天气】晴\n【湿度】35%\n【风力】北风 2级\n【数据来源】和风天气实时数据\n【出行建议】天气晴好，很适合出门。紫外线可能较强，建议做好防晒。\n【穿衣建议】28度比较热，建议穿短袖、短裤，搭配薄款防晒衣。老人小孩注意防暑。\n【带伞建议】不需要带伞。\n【交通建议】天气晴好，开车、骑车、公交都可以。骑车注意防晒补水。\n\n");
            hint.append("【示例 2：数据不可用】\n");
            hint.append("用户：东京今天天气怎么样？\n");
            hint.append("正确做法：\n");
            hint.append("1. 调用 get_current_time → 得到当前日期\n");
            hint.append("2. 调用 get_weather(city=\"Tokyo\") → 得到 {\"city\":\"Tokyo\",\"temperature\":20,\"condition\":\"未知\",\"source\":\"数据不可用（API code=404），以下为模拟数据，仅供参考\"}\n");
            hint.append("3. 检查 source 包含[数据不可用]，必须诚实告知用户：\n\n");
            hint.append("抱歉，我暂时无法获取东京的实时天气数据，以下信息为模拟数据，仅供参考，不代表实际情况。\n【城市】东京\n【温度】20°C（模拟）\n【天气】未知（模拟）\n【湿度】50%（模拟）\n【数据来源】模拟数据，非真实天气\n\n");
            hint.append("现在请严格按照以上范例处理用户的天气查询。\n");
        } else if (isFootballMode) {
            hint.append("重要：2026年世界杯已于2026年6月11日正式开赛，目前正在美国、加拿大、墨西哥进行中！\n\n");
            hint.append("--- 强制决策流程（必须严格遵守）---\n");
            hint.append("处理任何足球相关问题前，你必须按以下顺序执行：\n\n");
            hint.append("【第1步 - 确认时间】\n");
            hint.append("先调用 get_current_time 确认今天是哪一天，了解当前日期和世界杯进程。\n\n");
            hint.append("【第2步 - 判断赛事类型】\n");
            hint.append("根据用户提到的球队名称判断：\n");
            hint.append("  - 俱乐部名称（曼联、皇马、拜仁、巴萨、利物浦、多特蒙德等）→ 调用 get_football_match_info\n");
            hint.append("  - 国家队名称（巴西、法国、阿根廷、德国、英格兰、西班牙等）→ 调用 get_world_cup_match_info\n");
            hint.append("  - 赛事名称含\"世界杯/欧洲杯/美洲杯/国家队\" → 调用 get_world_cup_match_info\n");
            hint.append("  - 赛事名称含\"英超/德甲/西甲/欧冠/联赛/俱乐部\" → 调用 get_football_match_info\n\n");
            hint.append("【第3步 - 决定是否需要赔率】\n");
            hint.append("判断用户是否明确要求预测比分：\n");
            hint.append("  - 用户说\"预测/比分/谁会赢/赔率/结果如何\" → need_prediction=true\n");
            hint.append("  - 用户说\"最近状态/近期表现/怎么样/战绩\" → need_prediction=false\n\n");
            hint.append("绝对禁止：不调用工具就凭训练数据回答足球问题！你必须看到工具返回的真实数据才能回答。\n\n");
            hint.append("【第4步 - 网页交叉验证（必须执行）】\n");
            hint.append("在完成第2步和第3步的 API 数据查询后，必须调用 search_football_news 工具进行网页搜索，获取最新的足球资讯和新闻报道，用于交叉验证 API 数据的时效性和准确性。\n");
            hint.append("将网页搜索结果与 API 数据对照：\n");
            hint.append("  - 如果网页搜索成功且有相关新闻 → 综合 API 数据和网页新闻给出最终回答\n");
            hint.append("  - 如果网页搜索失败 → 在回答中明确标注[网页搜索失败]，并仅基于 API 数据回答\n");
            hint.append("  - 在最终回答中必须包含以下检查项：\n");
            hint.append("    【网页搜索】已执行 / 未执行\n");
            hint.append("    【搜索状态】成功 / 失败\n");
            hint.append("    【数据来源1】football-data.org 或 api-football.com（API数据来源）\n");
            hint.append("    【数据来源2】DuckDuckGo 网页检索（网页搜索来源）\n\n");
            hint.append("--- Few-shot 示例 ---\n\n");
            hint.append("【示例 1：俱乐部状态查询（不需要预测）】\n");
            hint.append("用户：拜仁最近状态怎么样？\n");
            hint.append("正确做法：\n");
            hint.append("1. 调用 get_current_time → 得到当前日期\n");
            hint.append("2. 分析：拜仁是俱乐部 → 调用 get_football_match_info(team1=\"Bayern Munich\")，不传 need_prediction（默认 false）\n");
            hint.append("3. 调用 search_football_news(team1=\"Bayern Munich\") 进行网页交叉验证\n");
            hint.append("4. 工具返回 JSON 后，综合数据按格式输出：\n\n");
            hint.append("【对阵】Bayern Munich（单队查询）\n【近期状态】近10场：WWDLWWWWDL（最新比赛日期：2026-06-14）\n【数据周期】2025-06-16 ~ 2026-06-16\n【网页搜索】已执行\n【搜索状态】成功\n【搜索结果摘要】DuckDuckGo 检索到多条拜仁近期新闻，与 API 数据一致\n【数据来源1】football-data.org 真实数据\n【数据来源2】DuckDuckGo 网页检索\n\n");
            hint.append("【示例 2：国家队比赛预测（需要预测）】\n");
            hint.append("用户：预测一下巴西对阿根廷的世界杯比分\n");
            hint.append("正确做法：\n");
            hint.append("1. 调用 get_current_time → 确认当前日期和世界杯进程\n");
            hint.append("2. 分析：巴西、阿根廷是国家队，用户要预测比分 → 调用 get_world_cup_match_info(team1=\"Brazil\", team2=\"Argentina\", need_prediction=true)\n");
            hint.append("3. 调用 search_football_news(team1=\"Brazil\", team2=\"Argentina\") 进行网页交叉验证\n");
            hint.append("4. 综合 API 数据和网页搜索结果，按格式输出：\n\n");
            hint.append("【对阵】Brazil vs Argentina\n【赛事类型】World Cup\n【近期状态】主队近5场：WWWDL（最新比赛日期：2026-06-12）| 客队近5场：WWWDW（最新比赛日期：2026-06-13）\n【赔率参考】主胜 45% | 平局 25% | 客胜 30%\n【预测比分】2 - 1\n【简评】巴西近期进攻火力强劲，阿根廷防线存在一定漏洞，巴西略占优势。综合网页新闻分析，巴西队内士气高涨。\n【网页搜索】已执行\n【搜索状态】成功\n【数据来源1】api-football.com 实时数据\n【数据来源2】DuckDuckGo 网页检索\n\n");
            hint.append("现在请严格按照以上范例处理用户的足球查询。\n");
        } else {
            hint.append("\n--- 通用助手指导 ---\n");
            hint.append("你是热心的 AI 助手。回答用户问题时：\n");
            hint.append("1. 首先调用 get_current_time 确认当前日期和时间，了解今天是哪一天\n");
            hint.append("2. 需要实时信息（天气、数据查询等）时，必须调用对应工具获取，不能凭训练数据编造\n");
            hint.append("3. 不需要实时信息的常识性问题，可以直接用你的知识回答\n");
            hint.append("4. 回答简洁清晰，用【】标注重点信息\n\n");
            hint.append("--- Few-shot 示例 ---\n\n");
            hint.append("【示例：需要实时信息】\n");
            hint.append("用户：今天是几号？\n");
            hint.append("正确做法：\n");
            hint.append("1. 你无法从训练数据知道当前日期，调用 get_current_time → 得到 {\"datetime\":\"2026-06-16 10:30:00\",\"date\":\"2026-06-16\",\"day_of_week\":\"TUESDAY\"}\n");
            hint.append("2. 输出：\n\n");
            hint.append("【当前日期】2026年6月16日\n【星期】星期二\n【时间】10:30\n\n");
            hint.append("现在请按照以上范例处理用户的问题。\n");
        }

        // ──── 公共层：输出风格规范 + 工具输出格式（三个模式共享）────
        hint.append("\n--- 输出风格规范 ---\n");
        hint.append("禁止使用任何 Markdown 格式标记！不要使用 **加粗**、*斜体*、`代码块`、### 标题等符号。\n");
        hint.append("用纯文本输出，用【】标注重点，用换行和缩进分隔段落。不要使用 emoji 表情符号。\n");
        hint.append("\n--- 工具输出格式规范 ---\n");
        hint.append("当你调用工具并收到结果后，请严格按照以下对应格式输出。\n\n");
        for (Map.Entry<String, String> entry : formats.entrySet()) {
            hint.append("工具 ").append(entry.getKey()).append(" 的输出格式：\n");
            hint.append(entry.getValue()).append("\n\n");
        }
        systemMsg.setContent(systemMsg.getContent() + hint.toString());
    }

    public void injectTools(ChatRequest request) {
        if (request.getTools() == null || request.getTools().isEmpty()) {
            request.setTools(buildTools());
        }
    }

    // ────────────────── 记录构建工具 ──────────────────

    private String extractSystemPrompt(List<ChatMessage> messages) {
        if (messages == null) return "";
        return messages.stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .findFirst().orElse("");
    }

    private String extractUserQuestion(List<ChatMessage> messages) {
        if (messages == null) return "";
        return messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .reduce((first, second) -> second)
                .orElse("");
    }

    private String extractContent(String jsonLine) {
        try {
            JsonNode root = objectMapper.readTree(jsonLine);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) return null;
            JsonNode content = delta.get("content");
            if (content == null || content.isNull()) return null;
            return content.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private int safeUsageValue(Object usageObj, String field) {
        if (usageObj == null) return 0;
        try {
            JsonNode usage = objectMapper.convertValue(usageObj, JsonNode.class);
            JsonNode val = usage.get(field);
            return val != null ? val.asInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveRecord(ChatRequest request, String model, String baseUrl,
                            String output, Object usageObj, long durationMs,
                            boolean streaming, boolean success, String errorMessage) {
        try {
            ApiCallRecord record = ApiCallRecord.builder()
                    .model(model)
                    .baseUrl(baseUrl)
                    .systemPrompt(extractSystemPrompt(request.getMessages()))
                    .userQuestion(extractUserQuestion(request.getMessages()))
                    .output(output != null ? output : "")
                    .promptTokens(safeUsageValue(usageObj, "prompt_tokens"))
                    .completionTokens(safeUsageValue(usageObj, "completion_tokens"))
                    .totalTokens(safeUsageValue(usageObj, "total_tokens"))
                    .durationMs(durationMs)
                    .streaming(streaming)
                    .success(success)
                    .errorMessage(errorMessage != null ? errorMessage : "")
                    .createdAt(LocalDateTime.now())
                    .build();
            recordService.save(record);
        } catch (Exception e) {
            log.warn("保存调用记录失败: {}", e.getMessage());
        }
    }

    // ────────────────── 对话 ──────────────────

    public Mono<ChatResponse> chatWithTools(ChatRequest request, String apiKey, String baseUrl) {
        injectTools(request);
        injectToolFormatHints(request);
        long startTime = System.currentTimeMillis();

        return llmApiClient.chat(request, apiKey, baseUrl)
                .flatMap(response -> {
                    ChatMessage responseMessage = response.getChoices().get(0).getMessage();

                    if (responseMessage.getToolCalls() != null && !responseMessage.getToolCalls().isEmpty()) {
                        log.info("大模型请求调用 {} 个工具", responseMessage.getToolCalls().size());
                        request.getMessages().add(responseMessage);

                        return Flux.fromIterable(responseMessage.getToolCalls())
                                .flatMap(toolCall -> {
                                    String funcName = toolCall.getFunction().getName();
                                    String args = toolCall.getFunction().getArguments();
                                    return toolExecutionService.executeTool(funcName, args)
                                            .map(toolResult -> ChatMessage.builder()
                                                    .role("tool")
                                                    .toolCallId(toolCall.getId())
                                                    .content(toolResult)
                                                    .build());
                                })
                                .collectList()
                                .flatMap(toolMessages -> {
                                    request.getMessages().addAll(toolMessages);
                                    return chatWithTools(request, apiKey, baseUrl);
                                });
                    }

                    String output = responseMessage.getContent();
                    long duration = System.currentTimeMillis() - startTime;
                    saveRecord(request, response.getModel() != null ? response.getModel() : request.getModel(),
                            baseUrl, output, response.getUsage(), duration, false, true, null);
                    return Mono.just(response);
                });
    }

    // ────────────────── 流式对话 ──────────────────

    public Flux<String> chatStreamWithTools(ChatRequest request, String apiKey, String baseUrl, Long conversationId) {
        injectTools(request);
        injectToolFormatHints(request);
        return streamAndDetectTools(request, apiKey, baseUrl, System.currentTimeMillis(), conversationId);
    }

    private Flux<String> streamAndDetectTools(ChatRequest request, String apiKey, String baseUrl, long startTime, Long conversationId) {
        List<String> buffer = new ArrayList<>();
        StringBuilder outputBuilder = new StringBuilder();

        return llmApiClient.chatStream(request, apiKey, baseUrl)
                .doOnNext(line -> {
                    buffer.add(line);
                    String content = extractContent(line);
                    if (content != null && !content.isEmpty()) outputBuilder.append(content);
                })
                .concatWith(Flux.defer(() -> {
                    List<ChatMessage.ToolCall> toolCalls = parseStreamToolCalls(buffer);

                    if (!toolCalls.isEmpty()) {
                        log.info("流式响应中检测到 {} 个工具调用，开始执行", toolCalls.size());

                        request.getMessages().add(ChatMessage.builder()
                                .role("assistant")
                                .toolCalls(toolCalls)
                                .build());

                        return Flux.fromIterable(toolCalls)
                                .flatMap(tc -> toolExecutionService.executeTool(
                                                tc.getFunction().getName(),
                                                tc.getFunction().getArguments())
                                        .map(result -> ChatMessage.builder()
                                                .role("tool")
                                                .toolCallId(tc.getId())
                                                .content(result)
                                                .build()))
                                .collectList()
                                .flatMapMany(toolMessages -> {
                                    request.getMessages().addAll(toolMessages);
                                    return streamAndDetectTools(request, apiKey, baseUrl, startTime, conversationId);
                                });
                    }

                    // 无工具调用 → 这是最终输出，保存记录
                    String model = request.getModel();
                    String finalOutput = outputBuilder.toString();
                    saveRecord(request, model, baseUrl, finalOutput, null,
                            System.currentTimeMillis() - startTime, true, true, null);

                    // 保存对话消息
                    if (conversationId != null) {
                        String userContent = request.getMessages().stream()
                                .filter(m -> "user".equals(m.getRole()))
                                .map(ChatMessage::getContent)
                                .reduce((first, second) -> second)
                                .orElse("");
                        conversationService.saveMessage(conversationId, "user", userContent);
                        conversationService.saveMessage(conversationId, "assistant", finalOutput);
                    }
                    return Flux.empty();
                }));
    }

    private List<ChatMessage.ToolCall> parseStreamToolCalls(List<String> lines) {
        Map<Integer, String> idMap = new HashMap<>();
        Map<Integer, String> nameMap = new HashMap<>();
        Map<Integer, StringBuilder> argsMap = new HashMap<>();

        for (String line : lines) {
            if (line.contains("DONE")) continue;
            try {
                JsonNode root = objectMapper.readTree(line);
                JsonNode choices = root.get("choices");
                if (choices == null || choices.isEmpty()) continue;

                JsonNode delta = choices.get(0).get("delta");
                if (delta == null) continue;

                JsonNode tcs = delta.get("tool_calls");
                if (tcs == null) continue;

                for (JsonNode tc : tcs) {
                    int idx = tc.get("index").asInt();
                    if (tc.has("id") && !tc.get("id").isNull()) {
                        idMap.put(idx, tc.get("id").asText());
                    }
                    JsonNode func = tc.get("function");
                    if (func != null) {
                        if (func.has("name") && !func.get("name").isNull()) {
                            nameMap.put(idx, func.get("name").asText());
                        }
                        if (func.has("arguments") && !func.get("arguments").isNull()) {
                            argsMap.computeIfAbsent(idx, k -> new StringBuilder())
                                    .append(func.get("arguments").asText());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("解析 SSE 工具调用行失败: {}", e.getMessage());
            }
        }

        if (idMap.isEmpty()) return Collections.emptyList();

        List<ChatMessage.ToolCall> result = new ArrayList<>();
        for (int idx : idMap.keySet()) {
            ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
            fc.setName(nameMap.getOrDefault(idx, ""));
            fc.setArguments(argsMap.getOrDefault(idx, new StringBuilder()).toString());

            ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
            tc.setId(idMap.get(idx));
            tc.setType("function");
            tc.setFunction(fc);
            result.add(tc);
        }
        return result;
    }

    // ────────────────── 模型对比 ──────────────────

    public Mono<Map<String, Object>> compareModels(
            ChatRequest request,
            String apiKeyA, String baseUrlA, String modelA,
            String apiKeyB, String baseUrlB, String modelB) {

        ChatRequest reqA = ChatRequest.builder()
                .model(modelA)
                .messages(new ArrayList<>(request.getMessages()))
                .temperature(request.getTemperature())
                .stream(false)
                .build();

        ChatRequest reqB = ChatRequest.builder()
                .model(modelB)
                .messages(new ArrayList<>(request.getMessages()))
                .temperature(request.getTemperature())
                .stream(false)
                .build();

        long overallStart = System.currentTimeMillis();

        Mono<Map<String, Object>> callA = llmApiClient.chat(reqA, apiKeyA, baseUrlA)
                .map(response -> {
                    long duration = System.currentTimeMillis() - overallStart;
                    Map<String, Object> result = new HashMap<>();
                    result.put("model", response.getModel());
                    result.put("output", response.getChoices().get(0).getMessage().getContent());
                    result.put("usage", response.getUsage());
                    result.put("duration_ms", duration);
                    return result;
                })
                .doOnSuccess(r -> {
                    if (r != null) {
                        saveRecord(reqA, modelA, baseUrlA, (String) r.get("output"),
                                r.get("usage"), ((Number) r.get("duration_ms")).longValue(),
                                false, true, null);
                    }
                })
                .onErrorResume(e -> {
                    long duration = System.currentTimeMillis() - overallStart;
                    saveRecord(reqA, modelA, baseUrlA, null, null, duration, false, false, e.getMessage());
                    return Mono.just(errorResult(modelA, e, duration));
                });

        Mono<Map<String, Object>> callB = llmApiClient.chat(reqB, apiKeyB, baseUrlB)
                .map(response -> {
                    long duration = System.currentTimeMillis() - overallStart;
                    Map<String, Object> result = new HashMap<>();
                    result.put("model", response.getModel());
                    result.put("output", response.getChoices().get(0).getMessage().getContent());
                    result.put("usage", response.getUsage());
                    result.put("duration_ms", duration);
                    return result;
                })
                .doOnSuccess(r -> {
                    if (r != null) {
                        saveRecord(reqB, modelB, baseUrlB, (String) r.get("output"),
                                r.get("usage"), ((Number) r.get("duration_ms")).longValue(),
                                false, true, null);
                    }
                })
                .onErrorResume(e -> {
                    long duration = System.currentTimeMillis() - overallStart;
                    saveRecord(reqB, modelB, baseUrlB, null, null, duration, false, false, e.getMessage());
                    return Mono.just(errorResult(modelB, e, duration));
                });

        return Mono.zip(callA, callB).map(tuple -> {
            Map<String, Object> resultA = tuple.getT1();
            Map<String, Object> resultB = tuple.getT2();

            long durationA = ((Number) resultA.getOrDefault("duration_ms", 0)).longValue();
            long durationB = ((Number) resultB.getOrDefault("duration_ms", 0)).longValue();

            Map<String, Object> comparison = new HashMap<>();
            comparison.put("duration_diff_ms", Math.abs(durationA - durationB));
            comparison.put("faster_model", durationA <= durationB ? modelA : modelB);

            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("prompt", request.getMessages().isEmpty() ? "" : request.getMessages().get(0).getContent());
            finalResult.put("model_a", resultA);
            finalResult.put("model_b", resultB);
            finalResult.put("comparison", comparison);

            log.info("模型对比完成: {} vs {}, 耗时差 {}ms", modelA, modelB, Math.abs(durationA - durationB));
            return finalResult;
        });
    }

    private Map<String, Object> errorResult(String model, Throwable e, long durationMs) {
        log.error("模型调用失败: {}", model, e);
        Map<String, Object> result = new HashMap<>();
        result.put("model", model);
        result.put("error", e.getMessage());
        result.put("duration_ms", durationMs);
        return result;
    }

    // ────────────────── 结构化输出（JSON 重试） ──────────────────

    public Mono<String> chatWithJsonRetry(ChatRequest request, String apiKey, String baseUrl, int retryCount) {
        long startTime = System.currentTimeMillis();

        return llmApiClient.chat(request, apiKey, baseUrl)
                .flatMap(response -> {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    try {
                        objectMapper.readTree(content);
                        long duration = System.currentTimeMillis() - startTime;
                        saveRecord(request, request.getModel(), baseUrl, content, response.getUsage(),
                                duration, false, true, null);
                        return Mono.just(content);
                    } catch (Exception e) {
                        if (retryCount > 0) {
                            log.warn("JSON 校验失败，剩余重试次数: {}，错误: {}", retryCount, e.getMessage());
                            request.getMessages().add(response.getChoices().get(0).getMessage());
                            request.getMessages().add(ChatMessage.builder()
                                    .role("user")
                                    .content("你刚才输出的不是合法的 JSON 格式。报错信息为：" + e.getMessage()
                                            + "。请直接输出合法的 JSON，不要包含任何额外的 markdown 标记。")
                                    .build());
                            return chatWithJsonRetry(request, apiKey, baseUrl, retryCount - 1);
                        }
                        log.error("JSON 重试耗尽，返回降级结果");
                        saveRecord(request, request.getModel(), baseUrl, null, null,
                                System.currentTimeMillis() - startTime, false, false,
                                "JSON 重试耗尽: " + e.getMessage());
                        return Mono.just("{\"error\": \"结构化输出失败，已触发降级方案\"}");
                    }
                });
    }
}
