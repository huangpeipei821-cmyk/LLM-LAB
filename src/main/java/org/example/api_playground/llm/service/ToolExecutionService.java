package org.example.api_playground.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class ToolExecutionService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient.Builder webClientBuilder;
    private final String qweatherKey;
    private final String qweatherHost;
    private final String footballApiKey;
    private final String footballBaseUrl;
    private final String apiFootballKey;
    private final String apiFootballBaseUrl;
    private final String oddsApiKey;
    private final String oddsBaseUrl;

    // 俱乐部联赛对应的 the-odds-api sport keys
    private static final List<String> CLUB_SPORT_KEYS = List.of(
            "soccer_uefa_champions_league", "soccer_epl",
            "soccer_germany_bundesliga", "soccer_spain_la_liga",
            "soccer_italy_serie_a", "soccer_france_ligue_one");

    // 国家队赛事对应的 the-odds-api sport keys
    private static final List<String> INTL_SPORT_KEYS = List.of(
            "soccer_fifa_world_cup_winner", "soccer_uefa_euro",
            "soccer_uefa_nations_league", "soccer_copa_america");

    public ToolExecutionService(WebClient.Builder webClientBuilder,
                                @Value("${weather.qweather.key}") String qweatherKey,
                                @Value("${weather.qweather.host}") String qweatherHost,
                                @Value("${football.data.api.key}") String footballApiKey,
                                @Value("${football.data.base-url}") String footballBaseUrl,
                                @Value("${api-football.key}") String apiFootballKey,
                                @Value("${api-football.base-url}") String apiFootballBaseUrl,
                                @Value("${odds.api.key}") String oddsApiKey,
                                @Value("${odds.base-url}") String oddsBaseUrl) {
        this.webClientBuilder = webClientBuilder;
        this.qweatherKey = qweatherKey;
        this.qweatherHost = qweatherHost;
        this.footballApiKey = footballApiKey;
        this.footballBaseUrl = footballBaseUrl;
        this.apiFootballKey = apiFootballKey;
        this.apiFootballBaseUrl = apiFootballBaseUrl;
        this.oddsApiKey = oddsApiKey;
        this.oddsBaseUrl = oddsBaseUrl;
    }

    public Mono<String> executeTool(String functionName, String argumentsJson) {
        log.info("本地准备执行工具: {}, 参数: {}", functionName, argumentsJson);

        try {
            JsonNode args = objectMapper.readTree(argumentsJson);

            if ("get_current_time".equals(functionName)) {
                return Mono.just(getCurrentTime());
            }

            if ("get_weather".equals(functionName)) {
                String city = args.get("city").asText();
                return getWeather(city);
            }

            if ("get_football_match_info".equals(functionName)) {
                String team1 = args.has("team1") ? args.get("team1").asText() : "";
                String team2 = args.has("team2") && !args.get("team2").isNull() ? args.get("team2").asText() : "";
                boolean needPrediction = args.has("need_prediction") && args.get("need_prediction").asBoolean();
                if (team1.isEmpty()) {
                    return Mono.just("{\"error\": \"请指定至少一支球队名称\"}");
                }
                if (team2.isEmpty()) {
                    return getSingleTeamForm(team1, false);
                }
                return getFootballMatchInfo(team1, team2, needPrediction);
            }

            if ("get_world_cup_match_info".equals(functionName)) {
                String team1 = args.has("team1") ? args.get("team1").asText() : "";
                String team2 = args.has("team2") && !args.get("team2").isNull() ? args.get("team2").asText() : "";
                boolean needPrediction = args.has("need_prediction") && args.get("need_prediction").asBoolean();
                if (team1.isEmpty()) {
                    return Mono.just("{\"error\": \"请指定至少一支球队名称\"}");
                }
                if (team2.isEmpty()) {
                    return getSingleTeamForm(team1, true);
                }
                return getWorldCupMatchInfo(team1, team2, needPrediction);
            }

            if ("search_football_news".equals(functionName)) {
                String team1 = args.has("team1") ? args.get("team1").asText() : "";
                String team2 = args.has("team2") && !args.get("team2").isNull() ? args.get("team2").asText() : "";
                String keyword = args.has("keyword") && !args.get("keyword").isNull() ? args.get("keyword").asText() : "";
                return searchFootballNews(team1, team2, keyword);
            }

            return Mono.just("{\"error\": \"未找到该工具\"}");
        } catch (Exception e) {
            log.error("工具执行失败", e);
            return Mono.just("{\"error\": \"参数解析失败\"}");
        }
    }

    private String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        return String.format(
                "{\"datetime\": \"%s\", \"date\": \"%s\", \"time\": \"%s\", " +
                "\"day_of_week\": \"%s\", \"timezone\": \"Asia/Shanghai\", " +
                "\"note\": \"2026年世界杯已于6月11日开赛，目前正在美国、加拿大、墨西哥进行中\"}",
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                now.getDayOfWeek().toString());
    }


    private Mono<String> getWeather(String city) {
        WebClient client = webClientBuilder.build();
        String base = "https://" + qweatherHost;
        String geoUrl = base + "/geo/v2/city/lookup?location={city}&key={key}";

        return client.get()
                .uri(geoUrl, city, qweatherKey)
                .header("User-Agent", "API-Playground/1.0")
                .header("Accept", "application/json")
                .exchangeToMono(geoResponse -> {
                    log.info("QWeather 城市查询 HTTP {} Content-Type={} Content-Encoding={} Headers={}",
                            geoResponse.statusCode().value(),
                            geoResponse.headers().contentType().orElse(null),
                            geoResponse.headers().asHttpHeaders().getFirst("Content-Encoding"),
                            geoResponse.headers().asHttpHeaders());
                    if (!geoResponse.statusCode().is2xxSuccessful()) {
                        return Mono.<String>error(new RuntimeException("HTTP " + geoResponse.statusCode().value()));
                    }
                    return geoResponse.bodyToMono(String.class);
                })
                .flatMap(cityResponse -> {
                    try {
                        JsonNode cityJson = objectMapper.readTree(cityResponse);
                        String code = cityJson.get("code").asText();
                        if (!"200".equals(code)) {
                            return Mono.<String>error(new RuntimeException("API code=" + code));
                        }

                        JsonNode location = cityJson.get("location").get(0);
                        String locationId = location.get("id").asText();
                        String cityName = location.get("name").asText();

                        String weatherUrl = base + "/v7/weather/now?location={id}&key={key}";

                        return client.get()
                                .uri(weatherUrl, locationId, qweatherKey)
                                .header("User-Agent", "API-Playground/1.0")
                                .header("Accept", "application/json")
                                .exchangeToMono(weatherResponse -> {
                                    log.info("QWeather 天气查询 HTTP {} Content-Type={}",
                                            weatherResponse.statusCode().value(),
                                            weatherResponse.headers().contentType().orElse(null));
                                    if (!weatherResponse.statusCode().is2xxSuccessful()) {
                                        return Mono.<String>error(new RuntimeException("HTTP " + weatherResponse.statusCode().value()));
                                    }
                                    return weatherResponse.bodyToMono(String.class);
                                })
                                .map(weatherBody -> {
                                    try {
                                        JsonNode weatherJson = objectMapper.readTree(weatherBody);
                                        String weatherCode = weatherJson.get("code").asText();
                                        if (!"200".equals(weatherCode)) {
                                            throw new RuntimeException("API code=" + weatherCode);
                                        }

                                        JsonNode now = weatherJson.get("now");
                                        String condition = now.get("text").asText();
                                        String temp = now.get("temp").asText();
                                        String humidity = now.get("humidity").asText();
                                        String windDir = now.get("windDir").asText();
                                        String windScale = now.get("windScale").asText();

                                        return String.format(
                                                "{\"city\": \"%s\", \"temperature\": %s, \"condition\": \"%s\", " +
                                                        "\"humidity\": %s, \"wind\": \"%s %s级\", \"source\": \"和风天气实时数据\"}",
                                                cityName, temp, condition, humidity, windDir, windScale);
                                    } catch (Exception e) {
                                        throw new RuntimeException("天气响应解析失败: " + e.getMessage(), e);
                                    }
                                });
                    } catch (Exception e) {
                        throw new RuntimeException("城市响应解析失败: " + e.getMessage(), e);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("天气查询失败({})，降级到 mock 数据", e.getMessage());
                    return Mono.just(mockWeather(city, e.getMessage()));
                });
    }

    private String mockWeather(String city, String reason) {
        return switch (city) {
            case "Beijing", "北京" -> "{\"city\": \"北京\", \"temperature\": 28, \"condition\": \"晴\", \"humidity\": 35, \"source\": \"数据不可用（" + reason + "），以下为模拟数据，仅供参考\"}";
            case "Shanghai", "上海" -> "{\"city\": \"上海\", \"temperature\": 25, \"condition\": \"阴转小雨\", \"humidity\": 78, \"source\": \"数据不可用（" + reason + "），以下为模拟数据，仅供参考\"}";
            default -> "{\"city\": \"" + city + "\", \"temperature\": 20, \"condition\": \"未知\", \"humidity\": 50, \"source\": \"数据不可用（" + reason + "），以下为模拟数据，仅供参考\"}";
        };
    }

    // ======================== 俱乐部足球预测 ========================

    private Mono<String> getFootballMatchInfo(String team1, String team2, boolean needPrediction) {
        if ("YOUR_FREE_API_KEY".equals(footballApiKey) || footballApiKey == null || footballApiKey.isBlank()) {
            log.info("足球 API Key 未配置，使用 mock 预测");
            return Mono.just(mockFootballMatch(team1, team2));
        }

        WebClient client = webClientBuilder.build();

        Mono<Integer> team1IdMono = fetchTeamId(client, team1);
        Mono<Integer> team2IdMono = fetchTeamId(client, team2);

        return Mono.zip(team1IdMono, team2IdMono)
                .flatMap(tuple -> {
                    int id1 = tuple.getT1();
                    int id2 = tuple.getT2();
                    if (id1 == 0 || id2 == 0) {
                        log.warn("未找到球队: {} (id={}) 或 {} (id={})，降级 mock", team1, id1, team2, id2);
                        return Mono.just(mockFootballMatch(team1, team2));
                    }
                    Mono<String> form1Mono = fetchTeamForm(client, id1, team1);
                    Mono<String> form2Mono = fetchTeamForm(client, id2, team2);
                    Mono<String> oddsMono = needPrediction
                            ? fetchOdds(client, team1, team2, false)
                            : Mono.just("{}");
                    return Mono.zip(form1Mono, form2Mono, oddsMono)
                            .map(t -> buildClubPrediction(team1, team2, t.getT1(), t.getT2(), t.getT3()));
                })
                .onErrorResume(e -> {
                    log.error("足球 API 调用失败，降级 mock: {}", e.getMessage());
                    return Mono.just(mockFootballMatch(team1, team2));
                });
    }

    private Mono<Integer> fetchTeamId(WebClient client, String teamName) {
        return client.get()
                .uri(footballBaseUrl + "/teams?name={name}", teamName)
                .header("X-Auth-Token", footballApiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode teams = root.get("teams");
                        if (teams == null || teams.isEmpty()) {
                            log.info("搜不到球队 '{}'（免费版仅覆盖欧洲俱乐部联赛）", teamName);
                            return 0;
                        }
                        for (JsonNode t : teams) {
                            if (t.get("name").asText().equalsIgnoreCase(teamName)) {
                                return t.get("id").asInt();
                            }
                        }
                        for (JsonNode t : teams) {
                            String name = t.get("name").asText().toLowerCase();
                            if (name.contains(teamName.toLowerCase()) || teamName.toLowerCase().contains(name)) {
                                log.info("球队 '{}' 模糊匹配到: {}", teamName, t.get("name").asText());
                                return t.get("id").asInt();
                            }
                        }
                        log.info("搜不到球队 '{}'（搜索结果 {} 条但无匹配）", teamName, teams.size());
                        return 0;
                    } catch (Exception e) {
                        log.warn("解析球队搜索响应失败: {}", e.getMessage());
                        return 0;
                    }
                })
                .onErrorResume(e -> {
                    log.warn("搜索球队 '{}' 失败: {}", teamName, e.getMessage());
                    return Mono.just(0);
                });
    }

    private Mono<String> fetchTeamForm(WebClient client, int teamId, String teamName) {
        LocalDate today = LocalDate.now();
        String dateFrom = today.minusMonths(12).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTo = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return client.get()
                .uri(footballBaseUrl + "/teams/{id}/matches?limit=10&status=FINISHED&dateFrom={from}&dateTo={to}",
                        teamId, dateFrom, dateTo)
                .header("X-Auth-Token", footballApiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode matches = root.get("matches");
                        int wins = 0, draws = 0, losses = 0, goalsFor = 0, goalsAgainst = 0;
                        StringBuilder recent = new StringBuilder();
                        String latestDate = "";

                        if (matches != null && matches.isArray()) {
                            int count = 0;
                            for (JsonNode m : matches) {
                                if (count >= 10) break;
                                String utcDate = m.has("utcDate") ? m.get("utcDate").asText().substring(0, 10) : "";
                                if (count == 0) latestDate = utcDate;
                                String homeName = m.get("homeTeam").get("name").asText();
                                String awayName = m.get("awayTeam").get("name").asText();
                                JsonNode score = m.get("score").get("fullTime");
                                int homeScore = score.get("home").asInt();
                                int awayScore = score.get("away").asInt();
                                boolean isHome = homeName.equalsIgnoreCase(teamName);
                                int gf = isHome ? homeScore : awayScore;
                                int ga = isHome ? awayScore : homeScore;
                                goalsFor += gf;
                                goalsAgainst += ga;
                                if (gf > ga) { wins++; recent.append("W"); }
                                else if (gf == ga) { draws++; recent.append("D"); }
                                else { losses++; recent.append("L"); }
                                count++;
                            }
                        }

                        return String.format(
                                "{\"team\": \"%s\", \"matches_played\": %d, \"wins\": %d, \"draws\": %d, \"losses\": %d, " +
                                "\"goals_for\": %d, \"goals_against\": %d, \"recent_form\": \"%s\", " +
                                "\"data_period\": \"%s ~ %s\", \"latest_match_date\": \"%s\"}",
                                teamName, wins + draws + losses, wins, draws, losses, goalsFor, goalsAgainst,
                                recent.toString(), dateFrom, dateTo, latestDate);
                    } catch (Exception e) {
                        log.warn("解析球队赛程失败: {}", e.getMessage());
                        return "{\"team\": \"" + teamName + "\", \"error\": \"数据解析失败\"}";
                    }
                })
                .onErrorResume(e -> {
                    log.warn("获取球队 '{}' 赛程失败: {}", teamName, e.getMessage());
                    return Mono.just("{\"team\": \"" + teamName + "\", \"error\": \"API 请求失败\"}");
                });
    }

    private String buildClubPrediction(String team1, String team2, String form1Json, String form2Json, String oddsJson) {
        try {
            JsonNode f1 = objectMapper.readTree(form1Json);
            JsonNode f2 = objectMapper.readTree(form2Json);

            if (f1.has("error") || f2.has("error")) {
                log.warn("球队 form 数据异常（{} | {}），降级 mock",
                        f1.has("error") ? f1.get("error").asText() : "ok",
                        f2.has("error") ? f2.get("error").asText() : "ok");
                return mockFootballMatch(team1, team2);
            }

            int w1 = f1.has("wins") ? f1.get("wins").asInt() : 0;
            int d1 = f1.has("draws") ? f1.get("draws").asInt() : 0;
            int l1 = f1.has("losses") ? f1.get("losses").asInt() : 0;
            int gf1 = f1.has("goals_for") ? f1.get("goals_for").asInt() : 0;
            int ga1 = f1.has("goals_against") ? f1.get("goals_against").asInt() : 0;
            String rf1 = f1.has("recent_form") ? f1.get("recent_form").asText() : "";

            int w2 = f2.has("wins") ? f2.get("wins").asInt() : 0;
            int d2 = f2.has("draws") ? f2.get("draws").asInt() : 0;
            int l2 = f2.has("losses") ? f2.get("losses").asInt() : 0;
            int gf2 = f2.has("goals_for") ? f2.get("goals_for").asInt() : 0;
            int ga2 = f2.has("goals_against") ? f2.get("goals_against").asInt() : 0;
            String rf2 = f2.has("recent_form") ? f2.get("recent_form").asText() : "";

            int total1 = w1 + d1 + l1;
            int total2 = w2 + d2 + l2;

            double form1 = total1 > 0 ? (w1 * 3.0 + d1 * 1.0) / (total1 * 3.0) : 0.5;
            double form2 = total2 > 0 ? (w2 * 3.0 + d2 * 1.0) / (total2 * 3.0) : 0.5;
            double adjustedForm1 = form1 * 1.15;
            double total = adjustedForm1 + form2;
            double homeWinProb = Math.round(adjustedForm1 / total * 80.0);
            double drawProb = Math.round((100.0 - homeWinProb) * 0.4);
            double awayWinProb = 100.0 - homeWinProb - drawProb;

            int homeGoals = (int) Math.round((gf1 + ga2) / Math.max(total1 + total2, 1) * 1.5 + 1);
            int awayGoals = (int) Math.round((gf2 + ga1) / Math.max(total1 + total2, 1) * 1.2);
            if (homeGoals < 0) homeGoals = 0;
            if (awayGoals < 0) awayGoals = 0;

            String oddsField = buildOddsField(oddsJson);

            return String.format(
                    "{\"team1\": \"%s\", \"team2\": \"%s\", " +
                    "\"predicted_score\": \"%d - %d\", " +
                    "\"win_probability\": %.0f%%, " +
                    "\"draw_probability\": %.0f%%, " +
                    "\"loss_probability\": %.0f%%, " +
                    "\"team1_recent\": %s, " +
                    "\"team2_recent\": %s, " +
                    "%s" +
                    "\"analysis\": \"主队近%d场%s，客队近%d场%s。基于真实战绩加权预测。\", " +
                    "\"source\": \"football-data.org 真实数据\"}",
                    team1, team2, homeGoals, awayGoals, homeWinProb, drawProb, awayWinProb,
                    form1Json, form2Json,
                    oddsField,
                    total1, rf1.isEmpty() ? "无数据" : rf1,
                    total2, rf2.isEmpty() ? "无数据" : rf2);
        } catch (Exception e) {
            log.error("构建预测失败，降级 mock: {}", e.getMessage());
            return mockFootballMatch(team1, team2);
        }
    }

    private String mockFootballMatch(String team1, String team2) {
        int seed = (team1 + " vs " + team2).hashCode();
        double r = (Math.abs(seed) % 1000) / 1000.0;

        int homeGoals = (int) (r * 4);
        int awayGoals = (int) ((1 - r) * 3);
        double homeWinProb = Math.round(r * 60.0 + 15.0);
        double drawProb = Math.round((1 - Math.abs(r - 0.5) * 2) * 30.0);
        double awayWinProb = 100.0 - homeWinProb - drawProb;

        return String.format(
                "{\"team1\": \"%s\", \"team2\": \"%s\", " +
                "\"predicted_score\": \"%d - %d\", " +
                "\"win_probability\": %.0f%%, " +
                "\"draw_probability\": %.0f%%, " +
                "\"loss_probability\": %.0f%%, " +
                "\"team1_recent\": {\"recent_form\": \"WLWDW\", \"note\": \"mock数据\"}, " +
                "\"team2_recent\": {\"recent_form\": \"DLWWL\", \"note\": \"mock数据\"}, " +
                "\"odds\": {\"note\": \"mock数据，请注册 the-odds-api.com 获取真实赔率\"}, " +
                "\"analysis\": \"基于历史交锋数据和近期状态模拟预测（mock），仅供参考。\", " +
                "\"source\": \"mock模拟预测\"}",
                team1, team2, homeGoals, awayGoals, homeWinProb, drawProb, awayWinProb);
    }

    // ======================== 赔率获取（the-odds-api.com）========================

    private Mono<String> fetchOdds(WebClient client, String team1, String team2, boolean isInternational) {
        if ("YOUR_ODDS_API_KEY".equals(oddsApiKey) || oddsApiKey == null || oddsApiKey.isBlank()) {
            return Mono.just("{}");
        }

        List<String> sportKeys = isInternational ? INTL_SPORT_KEYS : CLUB_SPORT_KEYS;

        // 并发尝试前 3 个最可能的联赛 key，取第一个命中
        return Flux.fromIterable(sportKeys.subList(0, Math.min(3, sportKeys.size())))
                .flatMap(sportKey -> client.get()
                        .uri(oddsBaseUrl + "/sports/{sportKey}/odds/?apiKey={key}&regions=eu&markets=h2h",
                                sportKey, oddsApiKey)
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(body -> findMatchInOdds(body, team1, team2, sportKey))
                        .onErrorResume(e -> {
                            log.debug("赔率查询 {} 失败: {}", sportKey, e.getMessage());
                            return Mono.just("");
                        }))
                .filter(result -> !result.isEmpty())
                .next()
                .defaultIfEmpty("{}")
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("赔率查询超时或异常: {}", e.getMessage());
                    return Mono.just("{}");
                });
    }

    // 从赔率 API 响应中查找匹配两队队名的比赛
    private String findMatchInOdds(String body, String team1, String team2, String sportKey) {
        try {
            JsonNode matches = objectMapper.readTree(body);
            if (!matches.isArray()) return "";

            for (JsonNode m : matches) {
                String home = m.get("home_team").asText();
                String away = m.get("away_team").asText();
                if ((home.equalsIgnoreCase(team1) || home.toLowerCase().contains(team1.toLowerCase()))
                        && (away.equalsIgnoreCase(team2) || away.toLowerCase().contains(team2.toLowerCase()))) {
                    return extractBestOdds(m);
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    // 从单场比赛提取最佳赔率（取 Bet365 或第一个 bookmaker 的 h2h 市场）
    private String extractBestOdds(JsonNode match) {
        try {
            JsonNode bookmakers = match.get("bookmakers");
            if (bookmakers == null || !bookmakers.isArray() || bookmakers.isEmpty()) return "";

            // 优先取 Bet365
            JsonNode bestBm = bookmakers.get(0);
            for (JsonNode bm : bookmakers) {
                if ("bet365".equals(bm.get("key").asText())) {
                    bestBm = bm;
                    break;
                }
            }

            JsonNode markets = bestBm.get("markets");
            if (markets == null || !markets.isArray()) return "";

            JsonNode h2h = null;
            for (JsonNode mk : markets) {
                if ("h2h".equals(mk.get("key").asText())) {
                    h2h = mk;
                    break;
                }
            }
            if (h2h == null) return "";

            JsonNode outcomes = h2h.get("outcomes");
            StringBuilder sb = new StringBuilder("\"odds\": {\"bookmaker\": \"");
            sb.append(bestBm.get("title").asText());
            sb.append("\", \"h2h\": {");
            for (int i = 0; i < outcomes.size(); i++) {
                JsonNode o = outcomes.get(i);
                if (i > 0) sb.append(", ");
                sb.append("\"").append(o.get("name").asText()).append("\": ");
                sb.append(o.get("price").asDouble());
            }
            sb.append("}}, ");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String buildOddsField(String oddsJson) {
        if (oddsJson == null || oddsJson.equals("{}") || oddsJson.isBlank()) {
            return "\"odds\": {\"note\": \"本场比赛暂无实时赔率数据\"}, ";
        }
        return oddsJson; // already formatted as "\"odds\": {...}, "
    }

    // ======================== 单队查询 ========================

    private Mono<String> getSingleTeamForm(String teamName, boolean isInternational) {
        log.info("单队查询: {} (international={})", teamName, isInternational);
        WebClient client = webClientBuilder.build();

        if (isInternational) {
            // 用 api-football 查国家队
            if ("YOUR_API_FOOTBALL_KEY".equals(apiFootballKey) || apiFootballKey == null || apiFootballKey.isBlank()) {
                return Mono.just(String.format(
                        "{\"team\": \"%s\", \"note\": \"api-football key 未配置，无法查询\", \"source\": \"无\"}", teamName));
            }
            return fetchApiFootballTeamId(client, teamName)
                    .flatMap(id -> {
                        if (id == 0) {
                            return Mono.just(String.format(
                                    "{\"team\": \"%s\", \"note\": \"api-football 未找到该球队\", \"source\": \"api-football.com\"}", teamName));
                        }
                        return fetchApiFootballTeamForm(client, id, teamName);
                    });
        } else {
            // 用 football-data.org 查俱乐部
            if ("YOUR_FREE_API_KEY".equals(footballApiKey) || footballApiKey == null || footballApiKey.isBlank()) {
                return Mono.just(String.format(
                        "{\"team\": \"%s\", \"note\": \"football-data.org key 未配置，无法查询\", \"source\": \"无\"}", teamName));
            }
            return fetchTeamId(client, teamName)
                    .flatMap(id -> {
                        if (id == 0) {
                            return Mono.just(String.format(
                                    "{\"team\": \"%s\", \"note\": \"football-data.org 未找到该球队（免费版仅覆盖欧洲俱乐部联赛）\", \"source\": \"football-data.org\"}", teamName));
                        }
                        return fetchTeamForm(client, id, teamName);
                    });
        }
    }

    // ======================== 世界杯/国家队预测（api-football.com）========================

    private Mono<String> getWorldCupMatchInfo(String team1, String team2, boolean needPrediction) {
        if ("YOUR_API_FOOTBALL_KEY".equals(apiFootballKey) || apiFootballKey == null || apiFootballKey.isBlank()) {
            log.info("api-football Key 未配置，使用 mock 预测");
            return Mono.just(mockFootballMatch(team1, team2));
        }

        WebClient client = webClientBuilder.build();

        Mono<Integer> team1IdMono = fetchApiFootballTeamId(client, team1);
        Mono<Integer> team2IdMono = fetchApiFootballTeamId(client, team2);

        return Mono.zip(team1IdMono, team2IdMono)
                .flatMap(tuple -> {
                    int id1 = tuple.getT1();
                    int id2 = tuple.getT2();
                    if (id1 == 0 || id2 == 0) {
                        log.warn("api-football 未找到国家队: {} (id={}) 或 {} (id={})，降级 mock", team1, id1, team2, id2);
                        return Mono.just(mockFootballMatch(team1, team2));
                    }
                    Mono<String> form1Mono = fetchApiFootballTeamForm(client, id1, team1);
                    Mono<String> form2Mono = fetchApiFootballTeamForm(client, id2, team2);
                    Mono<String> oddsMono = needPrediction
                            ? fetchOdds(client, team1, team2, true)
                            : Mono.just("{}");
                    return Mono.zip(form1Mono, form2Mono, oddsMono)
                            .map(t -> buildIntlPrediction(team1, team2, t.getT1(), t.getT2(), t.getT3()));
                })
                .onErrorResume(e -> {
                    log.error("api-football API 调用失败，降级 mock: {}", e.getMessage());
                    return Mono.just(mockFootballMatch(team1, team2));
                });
    }

    private Mono<Integer> fetchApiFootballTeamId(WebClient client, String teamName) {
        return client.get()
                .uri(apiFootballBaseUrl + "/teams?search={name}", teamName)
                .header("x-apisports-key", apiFootballKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode response = root.get("response");
                        if (response == null || response.isEmpty()) {
                            log.info("api-football 搜不到国家队 '{}'", teamName);
                            return 0;
                        }
                        // 精确匹配优先
                        for (JsonNode item : response) {
                            JsonNode team = item.get("team");
                            if (team.get("name").asText().equalsIgnoreCase(teamName)) {
                                log.info("api-football 精确匹配 '{}' -> id={}", teamName, team.get("id").asInt());
                                return team.get("id").asInt();
                            }
                        }
                        // 模糊匹配
                        for (JsonNode item : response) {
                            JsonNode team = item.get("team");
                            String name = team.get("name").asText().toLowerCase();
                            if (name.contains(teamName.toLowerCase()) || teamName.toLowerCase().contains(name)) {
                                log.info("api-football 模糊匹配 '{}' -> '{}' id={}", teamName, team.get("name").asText(), team.get("id").asInt());
                                return team.get("id").asInt();
                            }
                        }
                        log.info("api-football 搜不到国家队 '{}'（{} 条结果无匹配）", teamName, response.size());
                        return 0;
                    } catch (Exception e) {
                        log.warn("解析 api-football 球队搜索失败: {}", e.getMessage());
                        return 0;
                    }
                })
                .onErrorResume(e -> {
                    log.warn("api-football 搜索 '{}' 失败: {}", teamName, e.getMessage());
                    return Mono.just(0);
                });
    }

    private Mono<String> fetchApiFootballTeamForm(WebClient client, int teamId, String teamName) {
        // api-football 要求必须传 season 参数。免费版数据通常滞后1-2年。
        // 尝试当前年份和前两年，取第一个有数据的。
        int currentYear = LocalDate.now().getYear();
        return tryFetchFormWithSeason(client, teamId, teamName, currentYear)
                .flatMap(result -> {
                    if (result != null) return Mono.just(result);
                    return tryFetchFormWithSeason(client, teamId, teamName, currentYear - 1);
                })
                .flatMap(result -> {
                    if (result != null) return Mono.just(result);
                    return tryFetchFormWithSeason(client, teamId, teamName, currentYear - 2);
                })
                .defaultIfEmpty(String.format(
                        "{\"team\": \"%s\", \"matches_played\": 0, \"wins\": 0, \"draws\": 0, \"losses\": 0, " +
                        "\"goals_for\": 0, \"goals_against\": 0, \"recent_form\": \"\", " +
                        "\"latest_match_date\": \"\", \"recent_matches\": [], " +
                        "\"data_source\": \"api-football.com 免费版暂无该球队近年比赛数据\"}",
                        teamName));
    }

    // 返回 null 表示该赛季无数据
    private Mono<String> tryFetchFormWithSeason(WebClient client, int teamId, String teamName, int season) {
        return client.get()
                .uri(apiFootballBaseUrl + "/fixtures?team={id}&season={season}&last=10", teamId, season)
                .header("x-apisports-key", apiFootballKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode response = root.get("response");
                        if (response == null || !response.isArray() || response.isEmpty()) {
                            log.info("api-football {} 赛季 {} 无数据", teamName, season);
                            return null;
                        }

                        int wins = 0, draws = 0, losses = 0, goalsFor = 0, goalsAgainst = 0;
                        StringBuilder recent = new StringBuilder();
                        StringBuilder matchesDetail = new StringBuilder();
                        String latestDate = "";
                        int count = 0;

                        for (JsonNode item : response) {
                            if (count >= 10) break;
                            JsonNode fixture = item.get("fixture");
                            JsonNode teams = item.get("teams");
                            JsonNode goals = item.get("goals");
                            JsonNode league = item.get("league");

                            // 只统计已完成的比赛
                            String status = fixture.has("status") ? fixture.get("status").get("short").asText() : "";
                            if (!"FT".equals(status) && !"AET".equals(status) && !"PEN".equals(status)) continue;

                            String homeName = teams.get("home").get("name").asText();
                            String awayName = teams.get("away").get("name").asText();
                            if (goals.get("home").isNull() || goals.get("away").isNull()) continue;

                            int homeScore = goals.get("home").asInt();
                            int awayScore = goals.get("away").asInt();
                            String matchDate = fixture.get("date").asText().substring(0, 10);
                            String competition = league.has("name") ? league.get("name").asText() : "未知赛事";
                            String round = league.has("round") && !league.get("round").isNull() ? league.get("round").asText() : "";
                            if (count == 0) latestDate = matchDate;

                            boolean isHome = homeName.equalsIgnoreCase(teamName);
                            int gf = isHome ? homeScore : awayScore;
                            int ga = isHome ? awayScore : homeScore;
                            goalsFor += gf;
                            goalsAgainst += ga;
                            if (gf > ga) { wins++; recent.append("W"); }
                            else if (gf == ga) { draws++; recent.append("D"); }
                            else { losses++; recent.append("L"); }

                            if (count > 0) matchesDetail.append(", ");
                            String competitionInfo = competition + (round.isEmpty() ? "" : " " + round);
                            matchesDetail.append(String.format(
                                    "{\"date\":\"%s\",\"competition\":\"%s\",\"result\":\"%s %d-%d %s\"}",
                                    matchDate, competitionInfo, homeName, homeScore, awayName));
                            count++;
                        }

                        if (count == 0) {
                            log.info("api-football {} 赛季 {} 无已完赛比赛", teamName, season);
                            return null;
                        }

                        return String.format(
                                "{\"team\": \"%s\", \"matches_played\": %d, \"wins\": %d, \"draws\": %d, \"losses\": %d, " +
                                "\"goals_for\": %d, \"goals_against\": %d, \"recent_form\": \"%s\", " +
                                "\"latest_match_date\": \"%s\", " +
                                "\"recent_matches\": [%s], " +
                                "\"data_season\": \"%d\", " +
                                "\"data_source\": \"api-football.com (%d赛季数据，免费版数据有1-2年延迟)\"}",
                                teamName, wins + draws + losses, wins, draws, losses, goalsFor, goalsAgainst,
                                recent.toString(), latestDate, matchesDetail.toString(), season, season);
                    } catch (Exception e) {
                        log.warn("解析 api-football 赛程失败: {}", e.getMessage());
                        return null;
                    }
                })
                .onErrorResume(e -> {
                    log.warn("api-football {} 赛季 {} 请求失败: {}", teamName, season, e.getMessage());
                    return Mono.justOrEmpty(null);
                });
    }

    private String buildIntlPrediction(String team1, String team2, String form1Json, String form2Json, String oddsJson) {
        try {
            JsonNode f1 = objectMapper.readTree(form1Json);
            JsonNode f2 = objectMapper.readTree(form2Json);

            if (f1.has("error") || f2.has("error")) {
                log.warn("国家队 form 数据异常，降级 mock");
                return mockFootballMatch(team1, team2);
            }

            int w1 = f1.has("wins") ? f1.get("wins").asInt() : 0;
            int d1 = f1.has("draws") ? f1.get("draws").asInt() : 0;
            int l1 = f1.has("losses") ? f1.get("losses").asInt() : 0;
            int total1 = w1 + d1 + l1;
            String rf1 = f1.has("recent_form") ? f1.get("recent_form").asText() : "";

            int w2 = f2.has("wins") ? f2.get("wins").asInt() : 0;
            int d2 = f2.has("draws") ? f2.get("draws").asInt() : 0;
            int l2 = f2.has("losses") ? f2.get("losses").asInt() : 0;
            int total2 = w2 + d2 + l2;
            String rf2 = f2.has("recent_form") ? f2.get("recent_form").asText() : "";

            double form1 = total1 > 0 ? (w1 * 3.0 + d1 * 1.0) / (total1 * 3.0) : 0.5;
            double form2 = total2 > 0 ? (w2 * 3.0 + d2 * 1.0) / (total2 * 3.0) : 0.5;
            double adjustedForm1 = form1 * 1.15;
            double total = adjustedForm1 + form2;
            double homeProb = Math.round(adjustedForm1 / total * 80.0);
            double drawProb = Math.round((100.0 - homeProb) * 0.4);
            double awayProb = 100.0 - homeProb - drawProb;

            String oddsField = buildOddsField(oddsJson);

            return String.format(
                    "{\"team1\": \"%s\", \"team2\": \"%s\", " +
                    "\"predicted_score\": \"基于攻防数据模拟\", " +
                    "\"win_probability\": %.0f%%, " +
                    "\"draw_probability\": %.0f%%, " +
                    "\"loss_probability\": %.0f%%, " +
                    "\"team1_recent\": %s, " +
                    "\"team2_recent\": %s, " +
                    "%s" +
                    "\"analysis\": \"主队近%d场%s，客队近%d场%s。基于 api-football.com 真实战绩预测。\", " +
                    "\"source\": \"api-football.com 实时数据\"}",
                    team1, team2, homeProb, drawProb, awayProb,
                    form1Json, form2Json,
                    oddsField,
                    total1, rf1.isEmpty() ? "无数据" : rf1,
                    total2, rf2.isEmpty() ? "无数据" : rf2);
        } catch (Exception e) {
            log.error("构建世界杯预测失败，降级 mock: {}", e.getMessage());
            return mockFootballMatch(team1, team2);
        }
    }

    // ======================== 网页搜索足球资讯 ========================

    private Mono<String> searchFootballNews(String team1, String team2, String keyword) {
        WebClient client = webClientBuilder.build();

        // 构建搜索关键词
        StringBuilder queryBuilder = new StringBuilder();
        if (!keyword.isEmpty()) {
            queryBuilder.append(keyword);
        } else {
            queryBuilder.append(team1);
            if (!team2.isEmpty()) {
                queryBuilder.append(" vs ").append(team2);
            }
            queryBuilder.append(" football match 2026 latest news");
        }
        String query = queryBuilder.toString();
        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        } catch (Exception e) {
            encodedQuery = query;
        }

        String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;
        log.info("网页搜索足球资讯: query={}", query);

        return client.get()
                .uri(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .retrieve()
                .bodyToMono(String.class)
                .map(htmlBody -> {
                    String summary = extractSearchSummary(htmlBody);
                    return String.format(
                            "{\"search_query\": \"%s\", \"search_engine\": \"DuckDuckGo\", " +
                            "\"search_success\": true, \"search_result_count\": %d, " +
                            "\"search_summary\": \"%s\", " +
                            "\"source\": \"网页实时检索（DuckDuckGo）\"}",
                            query, countSearchResults(htmlBody),
                            escapeJson(summary));
                })
                .onErrorResume(e -> {
                    log.warn("网页搜索失败({})，降级到模拟搜索", e.getMessage());
                    return Mono.just(String.format(
                            "{\"search_query\": \"%s\", \"search_engine\": \"DuckDuckGo\", " +
                            "\"search_success\": false, \"search_error\": \"%s\", " +
                            "\"search_result_count\": 0, " +
                            "\"search_summary\": \"网页搜索请求失败，无法获取实时足球资讯。以下信息来自内部知识库，可能不是最新数据。\", " +
                            "\"source\": \"网页搜索失败，降级为内部数据\"}",
                            query, escapeJson(e.getMessage())));
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.warn("网页搜索超时或异常({})，降级到模拟搜索", e.getMessage());
                    return Mono.just(String.format(
                            "{\"search_query\": \"%s\", \"search_engine\": \"DuckDuckGo\", " +
                            "\"search_success\": false, \"search_error\": \"搜索超时或网络异常\", " +
                            "\"search_result_count\": 0, " +
                            "\"search_summary\": \"网页搜索超时，无法获取实时足球资讯。\", " +
                            "\"source\": \"网页搜索超时，降级为内部数据\"}",
                            query));
                });
    }

    // 从 DuckDuckGo HTML 提取搜索结果摘要
    private String extractSearchSummary(String html) {
        if (html == null || html.isEmpty()) return "未获取到搜索结果";
        StringBuilder sb = new StringBuilder();
        try {
            // 简单提取 result__snippet 或 result__body 中的文本
            // DuckDuckGo HTML 版的结果在 class="result__snippet" 中
            String[] lines = html.split("\n");
            int snippetCount = 0;
            for (String line : lines) {
                if (line.contains("result__snippet") || line.contains("result__body")) {
                    String text = line.replaceAll("<[^>]+>", "").trim();
                    if (!text.isEmpty()) {
                        if (snippetCount > 0) sb.append(" | ");
                        sb.append(text);
                        snippetCount++;
                        if (snippetCount >= 5) break;  // 最多取 5 条
                    }
                }
            }
        } catch (Exception e) {
            log.debug("解析搜索结果摘要失败: {}", e.getMessage());
        }
        if (sb.isEmpty()) {
            return "网页搜索已执行，但未能解析到结构化摘要。建议用户自行搜索获取最新资讯。";
        }
        return sb.toString();
    }

    // 统计搜索结果数量
    private int countSearchResults(String html) {
        if (html == null || html.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = html.indexOf("result__body", idx)) != -1) {
            count++;
            idx++;
        }
        return count > 0 ? count : (html.contains("result") ? 1 : 0);
    }

    // 简单的 JSON 字符串转义
    private String escapeJson(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "")
                .replace("\t", " ");
    }
}
