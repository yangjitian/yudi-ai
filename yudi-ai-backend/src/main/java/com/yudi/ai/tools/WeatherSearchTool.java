package com.yudi.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 天气查询工具类（支持城市名称）
 */
@Component
public class WeatherSearchTool {

    @Value("${weather-api.key}")
    private String apiKey;

    @Value("${weather-api.city-lookup-url}")
    private String cityLookupUrl;

    @Value("${weather-api.weather-url}")
    private String weatherUrl;

    @Value("${weather-api.weather-daily-url}")
    private String weatherDailyUrl;

    @Tool(name = "getWeather", description = "Get weather information based on city names。")
    public String getWeather(
            @ToolParam(description = "City name, for example: Beijing") String cityName,
            @ToolParam(description = "Optional. Used to specify the query type. 'Today' means today, 'tomorrow' means tomorrow, 'day_after_tomorrow' means the day after tomorrow, and 'next_N_days' (N is a number) means N days in the future. If not specified, all available information is returned.")
            String forecastType) {
        if (StrUtil.isBlank(cityName)) {
            return "城市名称不能为空";
        }

        // 1. 获取城市 ID
        String cityId = getCityId(cityName);
        if (cityId == null) {
            return "未找到对应的城市, 请检查城市名称是否正确";
        }

        // 2. 根据城市 ID 获取天气
        return getWeatherByCityId(cityId, cityName, forecastType);
    }

    private String getCityId(String cityName) {
        String url = String.format("%s?location=%s&key=%s", cityLookupUrl, cityName, apiKey);
        try {
            String body = HttpRequest.get(url).timeout(5000).execute().body();
            if (StrUtil.isBlank(body)) return null;
            JSONObject json = JSONUtil.parseObj(body);
            if (!"200".equals(json.getStr("code"))) return null;
            JSONArray locations = json.getJSONArray("location");
            if (locations != null && !locations.isEmpty()) {
                return locations.getJSONObject(0).getStr("id");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String getWeatherByCityId(String cityId, String cityName, String forecastType) {
        JSONObject nowWeather = getNowWeather(cityId);
        JSONArray dailyForecasts = getDailyForecast(cityId);

        if (dailyForecasts == null || dailyForecasts.isEmpty()) {
            return "无法获取天气预报信息";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("🏙️ 城市: %s\n", cityName));

        if (StrUtil.isBlank(forecastType)) {
            // 默认行为：返回今天+所有未来天气
            appendTodayForecast(sb, nowWeather, dailyForecasts.getJSONObject(0));
            appendFutureForecasts(sb, dailyForecasts, Math.min(dailyForecasts.size() - 1, 6));
        } else {
            switch (forecastType) {
                case "today":
                    appendTodayForecast(sb, nowWeather, dailyForecasts.getJSONObject(0));
                    break;
                case "tomorrow":
                    sb.append("--- 明日天气预报 ---\n");
                    appendSingleDayForecast(sb, dailyForecasts.getJSONObject(1));
                    break;
                case "day_after_tomorrow":
                    sb.append("--- 后天天气预报 ---\n");
                    appendSingleDayForecast(sb, dailyForecasts.getJSONObject(2));
                    break;
                default:
                    if (forecastType.startsWith("next_") && forecastType.endsWith("_days")) {
                        try {
                            String dayStr = forecastType.replace("next_", "").replace("_days", "");
                            int numDays = Integer.parseInt(dayStr);
                            if (numDays > 6) {
                                return "抱歉，我最多只能查询未来6天的天气信息哦，请您换个时间范围再试试吧。";
                            }
                            appendFutureForecasts(sb, dailyForecasts, numDays);
                        } catch (NumberFormatException e) {
                            // 无法解析数字，执行默认行为
                            appendTodayForecast(sb, nowWeather, dailyForecasts.getJSONObject(0));
                            appendFutureForecasts(sb, dailyForecasts, Math.min(dailyForecasts.size() - 1, 6));
                        }
                    } else {
                        // 未知类型，执行默认行为
                        appendTodayForecast(sb, nowWeather, dailyForecasts.getJSONObject(0));
                        appendFutureForecasts(sb, dailyForecasts, Math.min(dailyForecasts.size() - 1, 6));
                    }
                    break;
            }
        }

        sb.append("数据来源: 和风天气");
        return sb.toString();
    }

    private void appendTodayForecast(StringBuilder sb, JSONObject nowWeather, JSONObject todayForecast) {
        if (nowWeather == null || todayForecast == null) return;
        sb.append("--- 今日天气预报 ---\n");
        sb.append(String.format("📅 日期: %s (%s)\n", todayForecast.getStr("fxDate"), getDayOfWeekString(todayForecast.getStr("fxDate"))));
        sb.append(String.format("🌦️ 天气: %s (当前: %s)\n", todayForecast.getStr("textDay"), nowWeather.getStr("text"))); 
        sb.append(String.format("🌡️ 温度: %s℃ ~ %s℃ (当前: %s℃)\n", todayForecast.getStr("tempMin"), todayForecast.getStr("tempMax"), nowWeather.getStr("temp"))); 
        sb.append(String.format("🌡️ 体感温度: %s℃\n", nowWeather.getStr("feelsLike")));
        sb.append(String.format("💧 湿度: %s%%\n", nowWeather.getStr("humidity")));
        sb.append(String.format("🌬️ 风向: %s (当前: %s)\n", todayForecast.getStr("windDirDay"), nowWeather.getStr("windDir")));
        sb.append(String.format("💨 风力: %s级 (当前: %s级)\n", formatWindScale(todayForecast.getStr("windScaleDay")), formatWindScale(nowWeather.getStr("windScale"))));
    }

    private void appendFutureForecasts(StringBuilder sb, JSONArray dailyForecasts, int numDays) {
        if (numDays <= 0) return;
        sb.append(String.format("--- 未来%d天天气预报 ---\n", numDays));
        for (int i = 1; i <= numDays && i < dailyForecasts.size(); i++) {
            appendSingleDayForecast(sb, dailyForecasts.getJSONObject(i));
            if (i < numDays && i < dailyForecasts.size() - 1) {
                sb.append("--------------------\n");
            }
        }
    }

    private void appendSingleDayForecast(StringBuilder sb, JSONObject daily) {
        if (daily == null) return;
        sb.append(String.format("📅 日期: %s (%s)\n", daily.getStr("fxDate"), getDayOfWeekString(daily.getStr("fxDate"))));
        sb.append(String.format("🌦️ 天气: %s\n", daily.getStr("textDay")));
        sb.append(String.format("🌡️ 温度: %s℃ ~ %s℃\n", daily.getStr("tempMin"), daily.getStr("tempMax")));
        sb.append(String.format("🌬️ 风向: %s\n", daily.getStr("windDirDay")));
        sb.append(String.format("💨 风力: %s级\n", formatWindScale(daily.getStr("windScaleDay"))));
    }

    private JSONObject getNowWeather(String cityId) {
        String url = String.format("%s?location=%s&key=%s", weatherUrl, cityId, apiKey);
        try {
            String body = HttpRequest.get(url).timeout(5000).execute().body();
            if (StrUtil.isNotBlank(body)) {
                JSONObject json = JSONUtil.parseObj(body);
                if ("200".equals(json.getStr("code"))) return json.getJSONObject("now");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private JSONArray getDailyForecast(String cityId) {
        String url = String.format("%s?location=%s&key=%s", weatherDailyUrl, cityId, apiKey);
        try {
            String body = HttpRequest.get(url).timeout(5000).execute().body();
            if (StrUtil.isNotBlank(body)) {
                JSONObject json = JSONUtil.parseObj(body);
                if ("200".equals(json.getStr("code"))) return json.getJSONArray("daily");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String getDayOfWeekString(String dateString) {
        LocalDate date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return switch (dayOfWeek) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String formatWindScale(String rawWindScale) {
        if (StrUtil.isBlank(rawWindScale)) return "";
        if (rawWindScale.contains("-")) {
            String[] parts = rawWindScale.split("-");
            if (parts.length == 2) return parts[1];
        }
        return rawWindScale;
    }
}